package io.github.headlesshq.web;

import io.github.headlesshq.headlessmc.console.Console;
import io.github.headlesshq.headlessmc.launcher.LifecycleService;
import io.github.headlesshq.headlessmc.progressbar.ProgressBar;
import io.github.headlesshq.headlessmc.progressbar.ProgressBarServiceManager;
import io.github.headlesshq.web.process.WebLifecycleService;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Verifies that the web layer's beans are plugged into HeadlessMc's extension points.
 */
@QuarkusTest
class WebIntegrationTest {
    @Inject
    LifecycleService lifecycleService;

    @Inject
    ProgressBarServiceManager progressBarServiceManager;

    @Inject
    Console console;

    @Test
    void launchesWithTheWebLifecycle() {
        assertInstanceOf(WebLifecycleService.class, lifecycleService);
    }

    @Test
    void displaysProgressBarsInTheBrowser() {
        try (ProgressBar bar = progressBarServiceManager.displayProgressBar(new ProgressBar.Configuration("test", 10))) {
            assertFalse(bar.isDummy());
            bar.step();
        }
    }

    @Test
    void writesOutsideOfJobsToTheDefaultConsole() {
        // without a running job the web console provider steps aside
        console.write("hello from a test");
    }

}
