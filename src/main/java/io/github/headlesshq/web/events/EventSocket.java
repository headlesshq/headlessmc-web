package io.github.headlesshq.web.events;

import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.WebSocket;

/**
 * The websocket browsers connect to, to receive live {@link WebEvent}s.
 * Messages are only sent server to client, the browser uses the REST api for everything else.
 */
@WebSocket(path = "/ws/events")
public class EventSocket {
    @OnOpen
    public WebEvent onOpen() {
        return new WebEvent("hello", "HeadlessMc");
    }

}
