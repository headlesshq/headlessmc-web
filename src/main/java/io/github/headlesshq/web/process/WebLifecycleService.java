package io.github.headlesshq.web.process;

import io.github.headlesshq.headlessmc.config.Holder;
import io.github.headlesshq.headlessmc.console.Console;
import io.github.headlesshq.headlessmc.launcher.LauncherConfig;
import io.github.headlesshq.headlessmc.launcher.LifecycleService;
import io.github.headlesshq.headlessmc.launcher.ProcessLifecycle;
import io.github.headlesshq.headlessmc.launcher.process.ProcessLauncher;
import io.github.headlesshq.headlessmc.test.CommandTestService;
import io.github.headlesshq.headlessmc.test.TestConfig;
import jakarta.annotation.Priority;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Replaces HeadlessMc's {@link LifecycleService}: HeadlessMc launches the game with inherited IO and
 * blocks until it exits. In the web ui we want to see the output of the game in the browser and keep
 * HeadlessMc usable while the game runs, so processes are launched with piped IO and handed to the
 * {@link GameProcessManager}.
 * <p>
 * If a HeadlessMc command test ({@code hmc.test.*}) is active the original blocking lifecycle is used.
 */
@Alternative
@Priority(1)
@Singleton // LifecycleService has no no-args constructor, so it cannot be proxied
public class WebLifecycleService extends LifecycleService {
    private final GameProcessManager processManager;
    private final CommandTestService testService;
    private final Holder<LauncherConfig> config;
    private final Holder<TestConfig> testConfig;
    private final Console console;

    @Inject
    public WebLifecycleService(
        CommandTestService testService,
        Holder<LauncherConfig> config,
        Holder<TestConfig> testConfig,
        Console console,
        GameProcessManager processManager
    ) {
        super(testService, config, testConfig, console);
        this.processManager = processManager;
        this.testService = testService;
        this.config = config;
        this.testConfig = testConfig;
        this.console = console;
    }

    @Override
    public ProcessLifecycle wrap(ProcessLauncher launcher, int retries) {
        if (testService.isTestActive()) {
            return super.wrap(launcher, retries);
        }

        ProcessLifecycle lifecycle = new ProcessLifecycle(
            launcher, testService, config.get(), testConfig.get(), console, launcher.getGameDir()
        ) {
            @Override
            public Integer call() {
                ManagedProcess process = processManager.launch(launcher, getRetries());
                console.write("Launched " + process.getName() + " (pid " + process.getPid()
                                  + "), see the Processes page for its output.");
                return 0;
            }
        };

        lifecycle.setRetries(retries);
        return lifecycle;
    }

}
