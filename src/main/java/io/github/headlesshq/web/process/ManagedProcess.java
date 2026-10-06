package io.github.headlesshq.web.process;

import io.github.headlesshq.headlessmc.launcher.process.McProcess;
import io.github.headlesshq.headlessmc.launcher.process.ProcessLauncher;
import org.jboss.logging.Logger;
import org.jspecify.annotations.Nullable;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * A Minecraft client or server process launched from the web ui, with piped IO.
 * Keeps a bounded history of its output and allows writing to its stdin, e.g. server console commands.
 */
public final class ManagedProcess {
    private static final Logger LOG = Logger.getLogger(ManagedProcess.class);
    static final int MAX_HISTORY = 5_000;

    public enum Status {
        RUNNING, EXITED, STOPPED
    }

    private final Deque<Line> history = new ArrayDeque<>();
    private final GameProcessManager manager;
    private final ProcessLauncher launcher;
    private final String id;
    private final String name;
    private final String directory;
    private final long started = System.currentTimeMillis();

    private volatile Process process;
    private volatile Status status = Status.RUNNING;
    private volatile @Nullable Integer exitCode;
    private volatile long finished;
    private volatile boolean stopRequested;
    private volatile int retriesLeft;
    private volatile int attempt = 1;
    private long lineCounter;

    ManagedProcess(GameProcessManager manager, String id, ProcessLauncher launcher, McProcess mcProcess, int retries) {
        this.manager = manager;
        this.id = id;
        this.launcher = launcher;
        this.name = launcher.getId();
        this.directory = launcher.getGameDir().toAbsolutePath().toString();
        this.retriesLeft = retries;
        attach(mcProcess);
    }

    private void attach(McProcess mcProcess) {
        Process process = mcProcess.process()
            .orElseThrow(() -> new IllegalStateException("Process " + mcProcess.id() + " did not start a process"));
        this.process = process;
        pump(process.getInputStream(), "out");
        pump(process.getErrorStream(), "err");
        process.onExit().thenAccept(this::onExit);
    }

    private void pump(InputStream stream, String type) {
        Thread.ofVirtual().name("hmc-web-" + id + "-" + type).start(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    addLine(type, line);
                }
            } catch (IOException e) {
                LOG.debugf(e, "Output stream %s of %s closed", type, id);
            }
        });
    }

    private void onExit(Process exited) {
        if (exited != process) {
            return;
        }

        int code = exited.exitValue();
        if (code != 0 && !stopRequested && retriesLeft > 0) {
            retriesLeft--;
            attempt++;
            addLine("hmc", "Process exited with code " + code + ", retrying (" + retriesLeft + " retries left)...");
            try {
                attach(launcher.launch(true));
                manager.publish(this);
                return;
            } catch (RuntimeException e) {
                addLine("hmc", "Failed to relaunch: " + e.getMessage());
            }
        }

        this.exitCode = code;
        this.finished = System.currentTimeMillis();
        this.status = stopRequested ? Status.STOPPED : Status.EXITED;
        addLine("hmc", "Process exited with code " + code);
        manager.publish(this);
    }

    void addLine(String type, String text) {
        Line line;
        synchronized (history) {
            line = new Line(++lineCounter, type, text);
            history.addLast(line);
            while (history.size() > MAX_HISTORY) {
                history.removeFirst();
            }
        }

        manager.publishLine(this, line);
    }

    /**
     * Writes a line to the stdin of the process, e.g. a server console command.
     */
    public void sendInput(String input) throws IOException {
        if (status != Status.RUNNING) {
            throw new IllegalStateException("Process " + id + " is not running");
        }

        OutputStream stdin = process.getOutputStream();
        synchronized (this) {
            stdin.write((input + System.lineSeparator()).getBytes(StandardCharsets.UTF_8));
            stdin.flush();
        }

        addLine("in", input);
    }

    /**
     * Asks the process to exit (SIGTERM), Minecraft servers save the world on shutdown.
     */
    public void stop() {
        stopRequested = true;
        process.destroy();
    }

    public void kill() {
        stopRequested = true;
        process.destroyForcibly();
    }

    public List<Line> getHistory() {
        synchronized (history) {
            return new ArrayList<>(history);
        }
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDirectory() {
        return directory;
    }

    public long getStarted() {
        return started;
    }

    public long getFinished() {
        return finished;
    }

    public long getPid() {
        return process.pid();
    }

    public Status getStatus() {
        return status;
    }

    public @Nullable Integer getExitCode() {
        return exitCode;
    }

    public int getAttempt() {
        return attempt;
    }

    /**
     * A line of output.
     *
     * @param n    sequence number of the line within the process.
     * @param type {@code out}, {@code err}, {@code in} (input sent by the user) or {@code hmc} (status messages).
     * @param text the text of the line.
     */
    public record Line(long n, String type, String text) {
    }

}
