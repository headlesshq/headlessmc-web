package io.github.headlesshq.web.console;

import org.jspecify.annotations.Nullable;

/**
 * Json view of a {@link Job}.
 */
public record JobDto(
    String id,
    String line,
    String origin,
    long version,
    Job.Status status,
    @Nullable Integer exitCode,
    long created,
    long started,
    long finished,
    @Nullable Prompt prompt,
    @Nullable String output
) {
    public static JobDto of(Job job, boolean withOutput) {
        return new JobDto(
            job.getId(),
            job.getLine(),
            job.getOrigin(),
            job.getVersion(),
            job.getStatus(),
            job.getExitCode(),
            job.getCreated(),
            job.getStarted(),
            job.getFinished(),
            job.getPrompt(),
            withOutput ? job.getOutput() : null
        );
    }

}
