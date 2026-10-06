package io.github.headlesshq.web.events;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.websockets.next.OpenConnections;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

/**
 * Broadcasts {@link WebEvent}s to every browser connected to the {@link EventSocket}.
 */
@ApplicationScoped
public class EventBus {
    private static final Logger LOG = Logger.getLogger(EventBus.class);

    private final OpenConnections connections;
    private final ObjectMapper mapper;

    @Inject
    public EventBus(OpenConnections connections, ObjectMapper mapper) {
        this.connections = connections;
        this.mapper = mapper;
    }

    public void publish(String type, Object payload) {
        String json;
        try {
            json = mapper.writeValueAsString(new WebEvent(type, payload));
        } catch (JsonProcessingException e) {
            LOG.errorf(e, "Failed to serialize %s event", type);
            return;
        }

        for (WebSocketConnection connection : connections) {
            if (connection.isOpen()) {
                connection.sendText(json).subscribe().with(
                    ignored -> {},
                    failure -> LOG.debugf(failure, "Failed to send event to %s", connection.id())
                );
            }
        }
    }

}
