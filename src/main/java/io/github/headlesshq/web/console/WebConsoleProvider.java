package io.github.headlesshq.web.console;

import io.github.headlesshq.headlessmc.console.Console;
import io.github.headlesshq.headlessmc.console.ConsoleException;
import io.github.headlesshq.headlessmc.console.ConsoleProvider;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Plugs into HeadlessMc's {@link ConsoleProvider} SPI: HeadlessMc's default {@link Console} asks all
 * providers in order of {@link #sort()}, so while a {@link Job} is running, this provider hands out
 * the {@link WebConsole} of that job. Otherwise it fails and HeadlessMc falls back to its own consoles.
 */
@Web
@ApplicationScoped
public class WebConsoleProvider implements ConsoleProvider {
    private final JobService jobService;

    @Inject
    public WebConsoleProvider(JobService jobService) {
        this.jobService = jobService;
    }

    @Override
    public Console get() throws ConsoleException {
        Job job = jobService.current();
        if (job == null) {
            throw new ConsoleException("No web job is running");
        }

        return new WebConsole(jobService, job);
    }

    @Override
    public int sort() {
        return 0; // before ConsoleProvider.SORT_CACHE
    }

}
