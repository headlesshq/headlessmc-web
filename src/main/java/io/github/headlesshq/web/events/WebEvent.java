package io.github.headlesshq.web.events;

/**
 * An event pushed to all connected browsers via {@link EventSocket}.
 *
 * @param type    the type of the event, e.g. {@code job}, {@code output}, {@code prompt}, {@code progress}.
 * @param payload the json serializable payload of the event.
 */
public record WebEvent(String type, Object payload) {
}
