package io.github.headlesshq.web.console;

import io.github.headlesshq.headlessmc.console.Completions;
import io.github.headlesshq.headlessmc.console.Console;
import io.github.headlesshq.headlessmc.console.ConsoleException;
import io.github.headlesshq.headlessmc.console.ConsoleExtensions;
import io.github.headlesshq.headlessmc.console.Password;
import io.github.headlesshq.headlessmc.exceptions.UncheckedInterruptedException;
import io.github.headlesshq.web.command.CommandLines;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.concurrent.CancellationException;

/**
 * The HeadlessMc {@link Console} for a {@link Job}.
 * Output goes to the job (and the browsers), input is requested from the browser via {@link Prompt}s.
 * HeadlessMc requests a new console for every call, so all state lives in the job.
 */
final class WebConsole implements Console {
    private final JobService jobService;
    private final Job job;

    WebConsole(JobService jobService, Job job) {
        this.jobService = jobService;
        this.job = job;
    }

    @Override
    public void write(String message) {
        write(message, "\n");
    }

    @Override
    public void write(String message, String nextLine) {
        job.setPartialLine(nextLine.isEmpty() ? message : null);
        jobService.output(job, message + nextLine.replace(System.lineSeparator(), "\n"), JobService.STREAM_OUT);
    }

    @Override
    public String read() {
        return prompt(Prompt.KIND_LINE, consumePartialLine(), null);
    }

    @Override
    public String read(String message) {
        return prompt(Prompt.KIND_LINE, message, null);
    }

    @Override
    public Password readPassword() {
        return Password.of(prompt(Prompt.KIND_PASSWORD, consumePartialLine(), null));
    }

    @Override
    public Password readPassword(String message) {
        return Password.of(prompt(Prompt.KIND_PASSWORD, message, null));
    }

    @Override
    public Optional<ConsoleExtensions> extensions() {
        return Optional.of(new ConsoleExtensions() {
            @Override
            public String edit(String initialString) {
                return prompt(Prompt.KIND_EDIT, consumePartialLine(), initialString);
            }

            @Override
            public String read(String prompt, Completions completions) {
                return WebConsole.this.read(prompt);
            }

            @Override
            public int getWidth() {
                return CommandLines.USAGE_WIDTH;
            }
        });
    }

    private String consumePartialLine() {
        String message = job.setPartialLine(null);
        return message == null ? "" : message;
    }

    private String prompt(String kind, String message, @Nullable String initial) {
        try {
            String answer = jobService.prompt(job, kind, message.strip(), initial);
            String echo = Prompt.KIND_PASSWORD.equals(kind) ? "*".repeat(Math.min(answer.length(), 8)) : answer;
            jobService.output(job, (message.isBlank() ? "" : message.strip() + " ") + echo + "\n", JobService.STREAM_OUT);
            return answer;
        } catch (CancellationException e) {
            throw new ConsoleException("Input has been cancelled.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UncheckedInterruptedException(e);
        }
    }

}
