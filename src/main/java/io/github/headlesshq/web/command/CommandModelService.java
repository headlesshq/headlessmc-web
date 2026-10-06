package io.github.headlesshq.web.command;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import picocli.CommandLine;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Model.PositionalParamSpec;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;

/**
 * Describes the HeadlessMc picocli command tree as {@link CommandModel}.
 */
@ApplicationScoped
public class CommandModelService {
    private static final Set<String> STANDARD_HELP_OPTIONS = Set.of("--help", "--version");

    private final CommandLines commandLines;
    private volatile CommandModel.Command model;

    @Inject
    public CommandModelService(CommandLines commandLines) {
        this.commandLines = commandLines;
    }

    /**
     * @return the model of the HeadlessMc command tree, starting at the {@code headlessmc} root command.
     */
    public CommandModel.Command getModel() {
        CommandModel.Command result = model;
        if (result == null) {
            result = describe(commandLines.create(), List.of());
            model = result;
        }

        return result;
    }

    CommandModel.Command describe(CommandLine commandLine, List<String> path) {
        CommandSpec spec = commandLine.getCommandSpec();
        List<CommandModel.Command> subcommands = new ArrayList<>();
        for (Map.Entry<String, CommandLine> entry : commandLine.getSubcommands().entrySet()) {
            // subcommands are registered under their name and all aliases
            if (entry.getKey().equals(entry.getValue().getCommandName())) {
                List<String> subPath = new ArrayList<>(path);
                subPath.add(entry.getKey());
                subcommands.add(describe(entry.getValue(), subPath));
            }
        }

        List<CommandModel.Option> options = spec.options().stream()
            .filter(option -> option.longestName() != null && !isStandardHelpOption(option))
            .map(this::describe)
            .toList();
        List<CommandModel.Positional> positionals = spec.positionalParameters().stream()
            .sorted(Comparator.comparingInt(positional -> positional.index().min()))
            .map(this::describe)
            .toList();

        return new CommandModel.Command(
            spec.name(),
            path,
            Arrays.asList(spec.aliases()),
            String.join(" ", spec.usageMessage().description()),
            spec.usageMessage().hidden(),
            isRunnable(spec.userObject()),
            options,
            positionals,
            subcommands
        );
    }

    private CommandModel.Option describe(OptionSpec option) {
        List<String> names = Arrays.stream(option.names())
            .sorted(Comparator.comparingInt(String::length).reversed())
            .toList();
        return new CommandModel.Option(
            names,
            String.join(" ", option.description()),
            option.paramLabel(),
            type(option),
            option.arity().min(),
            option.arity().isVariable() ? -1 : option.arity().max(),
            option.required(),
            option.defaultValue(),
            option.hidden(),
            option.splitRegex() == null || option.splitRegex().isEmpty() ? null : option.splitRegex(),
            option.completionCandidates() != null
        );
    }

    private CommandModel.Positional describe(PositionalParamSpec positional) {
        return new CommandModel.Positional(
            positional.index().toString(),
            positional.index().min(),
            String.join(" ", positional.description()),
            positional.paramLabel(),
            type(positional),
            positional.arity().min(),
            positional.arity().isVariable() ? -1 : positional.arity().max(),
            positional.required(),
            positional.hidden(),
            positional.completionCandidates() != null
        );
    }

    private static boolean isStandardHelpOption(OptionSpec option) {
        return (option.usageHelp() || option.versionHelp())
            && Arrays.stream(option.names()).anyMatch(STANDARD_HELP_OPTIONS::contains);
    }

    private static boolean isRunnable(Object userObject) {
        return userObject instanceof Runnable
            || userObject instanceof Callable<?>
            || userObject instanceof Method;
    }

    private static String type(ArgSpec arg) {
        Class<?> type = arg.type();
        if (arg.isMultiValue() || Collection.class.isAssignableFrom(type) || type.isArray()) {
            return "list";
        } else if (type == boolean.class || type == Boolean.class) {
            return "boolean";
        } else if (type == int.class || type == Integer.class || type == long.class || type == Long.class) {
            return "integer";
        } else if (type == double.class || type == Double.class || type == float.class || type == Float.class) {
            return "number";
        }

        return "string";
    }

}
