package io.github.headlesshq.web.command;

import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Json description of the picocli command tree, used by the browser to render forms for every command.
 */
public final class CommandModel {
    private CommandModel() {
        throw new AssertionError();
    }

    /**
     * @param name        the name of the command.
     * @param path        the names of all commands from the root (exclusive) to this command (inclusive).
     * @param aliases     other names of the command.
     * @param description the description of the command.
     * @param hidden      whether the command is hidden in the usage help.
     * @param runnable    whether the command can be executed itself, or needs a sub command.
     * @param options     the options of the command.
     * @param positionals the positional parameters of the command.
     * @param subcommands the sub commands.
     */
    public record Command(
        String name,
        List<String> path,
        List<String> aliases,
        String description,
        boolean hidden,
        boolean runnable,
        List<Option> options,
        List<Positional> positionals,
        List<Command> subcommands
    ) {
    }

    /**
     * @param names        all names of the option, longest first.
     * @param description  the description.
     * @param paramLabel   label of the option parameter.
     * @param type         {@code boolean}, {@code integer}, {@code number}, {@code string} or {@code list}.
     * @param arityMin     minimum amount of parameters.
     * @param arityMax     maximum amount of parameters, -1 for unbounded.
     * @param required     whether the option is required.
     * @param defaultValue the default value.
     * @param hidden       whether the option is hidden.
     * @param split        regex to split a single value into multiple values.
     * @param completions  whether candidates can be completed for this option.
     */
    public record Option(
        List<String> names,
        String description,
        String paramLabel,
        String type,
        int arityMin,
        int arityMax,
        boolean required,
        @Nullable String defaultValue,
        boolean hidden,
        @Nullable String split,
        boolean completions
    ) {
    }

    /**
     * @param index       index of the positional, e.g. {@code 0}, {@code 1..*}.
     * @param indexMin    the minimum index.
     * @param description the description.
     * @param paramLabel  label of the parameter.
     * @param type        {@code integer}, {@code number}, {@code string} or {@code list}.
     * @param arityMin    minimum amount of values.
     * @param arityMax    maximum amount of values, -1 for unbounded.
     * @param required    whether the positional is required.
     * @param hidden      whether the positional is hidden.
     * @param completions whether candidates can be completed for this positional.
     */
    public record Positional(
        String index,
        int indexMin,
        String description,
        String paramLabel,
        String type,
        int arityMin,
        int arityMax,
        boolean required,
        boolean hidden,
        boolean completions
    ) {
    }

}
