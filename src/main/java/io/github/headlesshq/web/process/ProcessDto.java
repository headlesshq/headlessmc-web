package io.github.headlesshq.web.process;

import org.jspecify.annotations.Nullable;

/**
 * Json view of a {@link ManagedProcess}.
 */
public record ProcessDto(
    String id,
    String name,
    String directory,
    long pid,
    ManagedProcess.Status status,
    @Nullable Integer exitCode,
    int attempt,
    long started,
    long finished
) {
    public static ProcessDto of(ManagedProcess process) {
        return new ProcessDto(
            process.getId(),
            process.getName(),
            process.getDirectory(),
            process.getPid(),
            process.getStatus(),
            process.getExitCode(),
            process.getAttempt(),
            process.getStarted(),
            process.getFinished()
        );
    }

}
