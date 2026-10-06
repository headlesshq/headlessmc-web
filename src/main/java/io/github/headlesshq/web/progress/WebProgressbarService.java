package io.github.headlesshq.web.progress;

import io.github.headlesshq.headlessmc.progressbar.ProgressBar;
import io.github.headlesshq.headlessmc.progressbar.ProgressbarService;
import io.github.headlesshq.web.events.EventBus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Plugs into HeadlessMc's {@link ProgressbarService} SPI and displays progress bars
 * (downloads of assets, libraries, java etc.) in the browser via {@code progress} events.
 */
@ApplicationScoped
public class WebProgressbarService implements ProgressbarService {
    /**
     * Minimum time between two progress events of the same bar.
     */
    private static final long THROTTLE_MS = 150;

    private final AtomicLong ids = new AtomicLong();
    private final EventBus events;

    @Inject
    public WebProgressbarService(EventBus events) {
        this.events = events;
    }

    @Override
    public String getName() {
        return "web";
    }

    @Override
    public ProgressBar displayProgressBar(ProgressBar.Configuration configuration) {
        WebProgressBar bar = new WebProgressBar(String.valueOf(ids.incrementAndGet()), configuration);
        bar.publish(true);
        return bar;
    }

    public record Progress(
        String id,
        String task,
        long current,
        long max,
        @Nullable String unit,
        long unitSize,
        boolean done
    ) {
    }

    private final class WebProgressBar implements ProgressBar {
        private final AtomicLong current = new AtomicLong();
        private final AtomicLong max = new AtomicLong();
        private final AtomicLong lastPublished = new AtomicLong();
        private final AtomicBoolean closed = new AtomicBoolean();
        private final ProgressBar.Configuration configuration;
        private final String id;

        private WebProgressBar(String id, ProgressBar.Configuration configuration) {
            this.id = id;
            this.configuration = configuration;
            this.max.set(configuration.initialMax());
        }

        @Override
        public boolean isDummy() {
            return false;
        }

        @Override
        public void stepBy(long n) {
            current.addAndGet(n);
            publish(false);
        }

        @Override
        public void stepTo(long n) {
            current.set(n);
            publish(false);
        }

        @Override
        public void step() {
            stepBy(1);
        }

        @Override
        public void maxHint(long n) {
            max.set(n);
            publish(false);
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                publish(true);
            }
        }

        void publish(boolean force) {
            long now = System.currentTimeMillis();
            long last = lastPublished.get();
            if (!force && now - last < THROTTLE_MS || !force && !lastPublished.compareAndSet(last, now)) {
                return;
            }

            lastPublished.set(now);
            ProgressBar.Configuration.Unit unit = configuration.unit();
            events.publish("progress", new Progress(
                id,
                configuration.taskName(),
                current.get(),
                max.get(),
                unit == null ? null : unit.name(),
                unit == null ? 1 : unit.size(),
                closed.get()
            ));
        }
    }

}
