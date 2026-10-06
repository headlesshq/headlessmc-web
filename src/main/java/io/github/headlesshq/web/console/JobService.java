package io.github.headlesshq.web.console;

import io.github.headlesshq.headlessmc.console.ArgSplitter;
import io.github.headlesshq.web.command.CommandLines;
import io.github.headlesshq.web.events.EventBus;
import io.quarkus.runtime.ShutdownEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.jspecify.annotations.Nullable;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Executes HeadlessMc command lines submitted from the browser.
 * <p>
 * HeadlessMc is a single user shell, its commands and services are not designed to run concurrently.
 * Jobs are therefore executed one after another on a single thread. While a job runs, the
 * {@link WebConsoleProvider} routes everything HeadlessMc writes to its console into the job,
 * and prompts for input are forwarded to the browser.
 */
@ApplicationScoped
public class JobService {
    public static final String STREAM_OUT = "out";
    public static final String STREAM_ERR = "err";
    public static final String STREAM_LOG = "log";

    private static final Logger LOG = Logger.getLogger(JobService.class);
    private static final int MAX_JOBS = 100;
    private static final String RED = "\u001B[31m";
    private static final String RESET = "\u001B[0m";
    /**
     * Commands that make no sense in the web ui, {@code exit} would call System.exit.
     */
    private static final Set<String> UNSUPPORTED = Set.of("exit", "quit");

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "hmc-web-command");
        thread.setDaemon(true);
        return thread;
    });

    private final Map<String, Job> jobs = new LinkedHashMap<>();
    private final AtomicReference<@Nullable Job> current = new AtomicReference<>();
    private final AtomicLong ids = new AtomicLong();

    private final CommandLines commandLines;
    private final ArgSplitter argSplitter;
    private final EventBus events;

    @Inject
    public JobService(CommandLines commandLines, ArgSplitter argSplitter, EventBus events) {
        this.commandLines = commandLines;
        this.argSplitter = argSplitter;
        this.events = events;
    }

    void onShutdown(@Observes ShutdownEvent event) {
        Job job = current.get();
        if (job != null) {
            job.requestCancel();
        }

        executor.shutdownNow();
    }

    /**
     * Submits a command line for execution.
     *
     * @param line   the command line, without the leading {@code headlessmc}.
     * @param origin where the command comes from.
     * @return the queued job.
     */
    public Job submit(String line, String origin) {
        Job job = new Job(String.valueOf(ids.incrementAndGet()), line.strip(), origin);
        synchronized (jobs) {
            jobs.put(job.getId(), job);
            while (jobs.size() > MAX_JOBS) {
                String oldest = jobs.keySet().iterator().next();
                if (!jobs.get(oldest).getStatus().isDone()) {
                    break;
                }

                jobs.remove(oldest);
            }
        }

        publish(job);
        executor.submit(() -> run(job));
        return job;
    }

    public Optional<Job> get(String id) {
        synchronized (jobs) {
            return Optional.ofNullable(jobs.get(id));
        }
    }

    public List<Job> list() {
        synchronized (jobs) {
            return new ArrayList<>(jobs.values());
        }
    }

    /**
     * @return the job that is currently running, if there is one.
     */
    public @Nullable Job current() {
        return current.get();
    }

    public boolean answer(String jobId, String promptId, @Nullable String value) {
        Job job = get(jobId).orElse(null);
        if (job == null) {
            return false;
        }

        // the job thread publishes the job once it continues
        return job.answer(promptId, value);
    }

    public boolean cancel(String jobId) {
        Job job = get(jobId).orElse(null);
        if (job == null || job.getStatus().isDone()) {
            return false;
        }

        job.requestCancel();
        if (job.getStatus() == Job.Status.QUEUED) {
            job.finish(Job.Status.CANCELLED, null);
        }

        publish(job);
        return true;
    }

    /**
     * Appends text to the output of the given job and pushes it to the browsers.
     */
    public void output(Job job, String text, String stream) {
        if (text.isEmpty()) {
            return;
        }

        job.appendOutput(text);
        events.publish("output", new Output(job.getId(), stream, text));
    }

    /**
     * Asks the browser for input, blocking the job thread until it has been answered.
     */
    String prompt(Job job, String kind, String message, @Nullable String initial) throws InterruptedException {
        Prompt prompt = new Prompt(String.valueOf(ids.incrementAndGet()), job.getId(), kind, message, initial);
        try {
            // the job is published once the prompt is pending, so browsers learn about it
            return job.awaitAnswer(prompt, () -> publish(job));
        } finally {
            publish(job);
        }
    }

    void publish(Job job) {
        events.publish("job", JobDto.of(job, false));
    }

    private void run(Job job) {
        if (job.getStatus().isDone()) { // cancelled while queued
            return;
        }

        current.set(job);
        job.start(Thread.currentThread());
        publish(job);
        Job.Status status = Job.Status.FAILED;
        Integer exitCode = null;
        try {
            exitCode = execute(job);
            status = exitCode == 0 ? Job.Status.SUCCEEDED : Job.Status.FAILED;
        } catch (Throwable throwable) { // report everything to the user, the job thread has to survive
            writeError(job, throwable);
        } finally {
            if (job.isCancelRequested()) {
                status = Job.Status.CANCELLED;
            }

            Thread.interrupted(); // clear interrupt flag of the job thread
            current.set(null);
            job.finish(status, exitCode);
            publish(job);
        }
    }

    private int execute(Job job) {
        String[] args = argSplitter.split(job.getLine());
        if (args.length == 0) {
            // without arguments HeadlessMcCommand would start its interactive shell loop
            return 0;
        }

        if (UNSUPPORTED.contains(args[0].toLowerCase())) {
            output(job, RED + "The " + args[0] + " command is not supported in the web ui." + RESET + "\n", STREAM_ERR);
            return 1;
        }

        PrintWriter out = new PrintWriter(new JobWriter(job, STREAM_OUT), true);
        PrintWriter err = new PrintWriter(new JobWriter(job, STREAM_ERR), true);
        CommandLine commandLine = commandLines.create(out, err, true);
        commandLine.setExecutionExceptionHandler((exception, cmd, parseResult) -> {
            writeError(job, exception);
            return cmd.getCommandSpec().exitCodeOnExecutionException();
        });

        try {
            return commandLine.execute(args);
        } finally {
            out.flush();
            err.flush();
        }
    }

    private void writeError(Job job, Throwable throwable) {
        LOG.debugf(throwable, "Job %s (%s) failed", job.getId(), job.getLine());
        StringBuilder message = new StringBuilder(RED).append("Error: ").append(describe(throwable));
        Throwable cause = throwable.getCause();
        int depth = 0;
        while (cause != null && cause != throwable && depth++ < 8) {
            message.append("\n  Caused by: ").append(describe(cause));
            throwable = cause;
            cause = cause.getCause();
        }

        output(job, message.append(RESET).append('\n').toString(), STREAM_ERR);
    }

    private static String describe(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return throwable.getClass().getSimpleName();
        }

        if (throwable instanceof IllegalArgumentException || throwable instanceof IllegalStateException) {
            return message;
        }

        return message + " (" + throwable.getClass().getSimpleName() + ")";
    }

    public record Output(String jobId, String stream, String text) {
    }

    /**
     * Forwards what picocli writes to {@link CommandLine#getOut()}/{@link CommandLine#getErr()} to a job.
     */
    private final class JobWriter extends Writer {
        private final Job job;
        private final String stream;

        private JobWriter(Job job, String stream) {
            this.job = job;
            this.stream = stream;
        }

        @Override
        public void write(char[] buffer, int offset, int length) {
            output(job, new String(buffer, offset, length), stream);
        }

        @Override
        public void flush() {
            // written immediately
        }

        @Override
        public void close() {
            // nothing to close
        }
    }

}
