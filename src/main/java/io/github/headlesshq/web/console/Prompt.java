package io.github.headlesshq.web.console;

import org.jspecify.annotations.Nullable;

/**
 * A request for input from the browser, made by a command running in a {@link Job}.
 *
 * @param id       the id of this prompt.
 * @param jobId    the job that is waiting for the input.
 * @param kind     {@link #KIND_LINE}, {@link #KIND_PASSWORD} or {@link #KIND_EDIT}.
 * @param message  the message to display.
 * @param initial  the initial value to edit, for {@link #KIND_EDIT}.
 */
public record Prompt(String id, String jobId, String kind, String message, @Nullable String initial) {
    public static final String KIND_LINE = "line";
    public static final String KIND_PASSWORD = "password";
    public static final String KIND_EDIT = "edit";

    public boolean isPassword() {
        return KIND_PASSWORD.equals(kind);
    }

}
