package io.github.headlesshq.web.process;

import io.github.headlesshq.headlessmc.launcher.process.McProcess;
import io.github.headlesshq.headlessmc.launcher.process.ProcessLauncher;
import io.github.headlesshq.web.events.EventBus;
import io.quarkus.runtime.ShutdownEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Registry of the Minecraft processes launched from the web ui.
 */
@ApplicationScoped
public class GameProcessManager {
    private final Map<String, ManagedProcess> processes = new LinkedHashMap<>();
    private final AtomicLong ids = new AtomicLong();
    private final EventBus events;

    @Inject
    public GameProcessManager(EventBus events) {
        this.events = events;
    }

    /**
     * Launches a process with piped IO and registers it. Does not wait for the process to exit.
     *
     * @param launcher the launcher to launch the process with.
     * @param retries  how often to relaunch the process if it exits with a non-zero exit code.
     * @return the launched process.
     */
    public ManagedProcess launch(ProcessLauncher launcher, int retries) {
        McProcess mcProcess = launcher.launch(true);
        ManagedProcess process = new ManagedProcess(this, String.valueOf(ids.incrementAndGet()), launcher, mcProcess, retries);
        synchronized (processes) {
            processes.put(process.getId(), process);
        }

        publish(process);
        return process;
    }

    public List<ManagedProcess> list() {
        synchronized (processes) {
            return new ArrayList<>(processes.values());
        }
    }

    public Optional<ManagedProcess> get(String id) {
        synchronized (processes) {
            return Optional.ofNullable(processes.get(id));
        }
    }

    /**
     * Removes a process that is no longer running from the registry.
     *
     * @return {@code true} if the process has been removed.
     */
    public boolean remove(String id) {
        synchronized (processes) {
            ManagedProcess process = processes.get(id);
            if (process == null || process.getStatus() == ManagedProcess.Status.RUNNING) {
                return false;
            }

            processes.remove(id);
        }

        events.publish("process-removed", id);
        return true;
    }

    void publish(ManagedProcess process) {
        events.publish("process", ProcessDto.of(process));
    }

    void publishLine(ManagedProcess process, ManagedProcess.Line line) {
        events.publish("process-output", new LineEvent(process.getId(), line));
    }

    void onShutdown(@Observes ShutdownEvent event) {
        // the processes would lose their stdout/stdin with us anyway
        for (ManagedProcess process : list()) {
            if (process.getStatus() == ManagedProcess.Status.RUNNING) {
                process.stop();
            }
        }
    }

    public record LineEvent(String processId, ManagedProcess.Line line) {
    }

}
