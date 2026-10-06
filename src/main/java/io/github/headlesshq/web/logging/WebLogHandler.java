package io.github.headlesshq.web.logging;

import io.github.headlesshq.web.console.Job;
import io.github.headlesshq.web.console.JobService;
import io.github.headlesshq.web.events.EventBus;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import jakarta.inject.Singleton;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logmanager.ExtLogRecord;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * HeadlessMc reports a lot through its loggers instead of its console (warnings, progress, errors).
 * This handler forwards HeadlessMc log records to the running {@link Job}, or,
 * if no job is running, as {@code log} event to the browsers.
 */
@Singleton // no client proxy, Handler has final methods
public class WebLogHandler extends Handler {
    private static final String HMC_PACKAGE = "io.github.headlesshq.headlessmc";
    private static final String YELLOW = "\u001B[33m";
    private static final String RED = "\u001B[31m";
    private static final String GRAY = "\u001B[90m";
    private static final String RESET = "\u001B[0m";

    private final JobService jobService;
    private final EventBus events;
    private final Level level;

    @Inject
    public WebLogHandler(
        JobService jobService,
        EventBus events,
        @ConfigProperty(name = "hmc.web.log-level", defaultValue = "INFO") String level
    ) {
        this.jobService = jobService;
        this.events = events;
        this.level = Level.parse(level);
    }

    void onStart(@Observes StartupEvent event) {
        Logger root = LogManager.getLogManager().getLogger("");
        root.addHandler(this);
    }

    void onStop(@Observes ShutdownEvent event) {
        Logger root = LogManager.getLogManager().getLogger("");
        root.removeHandler(this);
    }

    @Override
    public void publish(LogRecord record) {
        if (record == null
            || record.getLevel().intValue() < level.intValue()
            || record.getLoggerName() == null
            || !record.getLoggerName().startsWith(HMC_PACKAGE)) {
            return;
        }

        String message = format(record);
        if (message.isBlank()) {
            return;
        }

        Job job = jobService.current();
        if (job != null) {
            jobService.output(job, colored(record.getLevel(), message) + "\n", JobService.STREAM_LOG);
        } else {
            events.publish("log", new LogEntry(record.getLevel().getName(), record.getLoggerName(), message, record.getMillis()));
        }
    }

    static String format(LogRecord record) {
        String message = record instanceof ExtLogRecord ext ? ext.getFormattedMessage() : new SimpleFormatter().formatMessage(record);
        if (message == null) {
            message = "";
        }

        Throwable thrown = record.getThrown();
        if (thrown != null) {
            String thrownMessage = thrown.getMessage() == null ? thrown.getClass().getSimpleName() : thrown.getMessage();
            message = message.isBlank() ? thrownMessage : message + ": " + thrownMessage;
        }

        return message;
    }

    private static String colored(Level level, String message) {
        if (level.intValue() >= Level.SEVERE.intValue()) {
            return RED + "[ERROR] " + message + RESET;
        } else if (level.intValue() >= Level.WARNING.intValue()) {
            return YELLOW + "[WARN] " + message + RESET;
        }

        return GRAY + "[INFO] " + RESET + message;
    }

    @Override
    public void flush() {
        // nothing to flush
    }

    @Override
    public void close() {
        // nothing to close
    }

    public record LogEntry(String level, String logger, String message, long time) {
    }

}
