package io.github.headlesshq.web.console;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A HeadlessMc command line submitted from the browser, executed by the {@link JobService}.
 */
public class Job {
    /**
     * Maximum amount of output characters kept per job.
     */
    static final int MAX_OUTPUT = 512 * 1024;

    public enum Status {
        QUEUED, RUNNING, WAITING_FOR_INPUT, SUCCEEDED, FAILED, CANCELLED;

        public boolean isDone() {
            return this == SUCCEEDED || this == FAILED || this == CANCELLED;
        }
    }

    private final String id;
    private final String line;
    private final String origin;
    private final long created = System.currentTimeMillis();
    private final StringBuilder output = new StringBuilder();
    private volatile Status status = Status.QUEUED;
    /**
     * Incremented on every change of status or prompt, lets clients discard outdated snapshots of the job.
     */
    private final AtomicLong version = new AtomicLong();
    private volatile @Nullable Integer exitCode;
    private volatile long started;
    private volatile long finished;
    private volatile @Nullable Thread thread;
    private volatile @Nullable Prompt prompt;
    private volatile @Nullable CompletableFuture<String> answer;
    private volatile boolean cancelRequested;
    private boolean truncated;
    private @Nullable String partialLine;

    public Job(String id, String line, String origin) {
        this.id = id;
        this.line = line;
        this.origin = origin;
    }

    public String getId() {
        return id;
    }

    public String getLine() {
        return line;
    }

    /**
     * @return where the job has been submitted from, e.g. {@code console} or {@code gui}.
     */
    public String getOrigin() {
        return origin;
    }

    public long getCreated() {
        return created;
    }

    public long getStarted() {
        return started;
    }

    public long getFinished() {
        return finished;
    }

    public long getVersion() {
        return version.get();
    }

    public Status getStatus() {
        return status;
    }

    public @Nullable Integer getExitCode() {
        return exitCode;
    }

    public @Nullable Prompt getPrompt() {
        return prompt;
    }

    public synchronized String getOutput() {
        return truncated ? "[...]\n" + output : output.toString();
    }

    @JsonIgnore
    public boolean isCancelRequested() {
        return cancelRequested;
    }

    /**
     * Remembers text written without a line break, which is the message for a following prompt.
     *
     * @param partialLine the new partial line.
     * @return the previous partial line.
     */
    synchronized @Nullable String setPartialLine(@Nullable String partialLine) {
        String previous = this.partialLine;
        this.partialLine = partialLine;
        return previous;
    }

    synchronized void appendOutput(String text) {
        output.append(text);
        if (output.length() > MAX_OUTPUT) {
            output.delete(0, output.length() - MAX_OUTPUT);
            truncated = true;
        }
    }

    void start(Thread thread) {
        this.thread = thread;
        this.started = System.currentTimeMillis();
        this.status = Status.RUNNING;
        version.incrementAndGet();
    }

    void finish(Status status, @Nullable Integer exitCode) {
        this.exitCode = exitCode;
        this.status = status;
        this.finished = System.currentTimeMillis();
        this.thread = null;
        version.incrementAndGet();
    }

    /**
     * Blocks the calling (job) thread until the browser answers the given prompt.
     *
     * @param prompt    the prompt to answer.
     * @param onPending called once the prompt is pending.
     * @return the answer.
     * @throws CancellationException if the prompt or the job has been cancelled.
     * @throws InterruptedException  if the job thread has been interrupted.
     */
    String awaitAnswer(Prompt prompt, Runnable onPending) throws InterruptedException {
        CompletableFuture<String> future = new CompletableFuture<>();
        synchronized (this) {
            if (cancelRequested) {
                throw new CancellationException("Job has been cancelled");
            }

            this.answer = future;
            this.prompt = prompt;
            this.status = Status.WAITING_FOR_INPUT;
            version.incrementAndGet();
        }

        onPending.run();
        try {
            return future.get();
        } catch (ExecutionException e) {
            throw new CancellationException("Input has been cancelled");
        } finally {
            synchronized (this) {
                this.prompt = null;
                this.answer = null;
                if (!status.isDone()) {
                    this.status = Status.RUNNING;
                }

                version.incrementAndGet();
            }
        }
    }

    /**
     * Answers the pending prompt.
     *
     * @param promptId the id of the prompt to answer.
     * @param value    the answer, {@code null} to cancel the prompt.
     * @return {@code true} if the prompt was pending and has been answered.
     */
    synchronized boolean answer(String promptId, @Nullable String value) {
        Prompt current = prompt;
        CompletableFuture<String> future = answer;
        if (current == null || future == null || !current.id().equals(promptId)) {
            return false;
        }

        if (value == null) {
            return future.completeExceptionally(new CancellationException());
        }

        return future.complete(value);
    }

    synchronized void requestCancel() {
        cancelRequested = true;
        CompletableFuture<String> future = answer;
        if (future != null) {
            future.completeExceptionally(new CancellationException());
        }

        Thread thread = this.thread;
        if (thread != null) {
            thread.interrupt();
        }
    }

}
