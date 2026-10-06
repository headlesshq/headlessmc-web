package io.github.headlesshq.web.command;

import io.github.headlesshq.headlessmc.commands.HeadlessMcCommand;
import jakarta.enterprise.context.ApplicationScoped;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Creates fresh {@link CommandLine}s for the HeadlessMc command tree.
 * A new instance is needed for every execution, because picocli stores parsed state in the command objects.
 */
@ApplicationScoped
public class CommandLines {
    public static final int USAGE_WIDTH = 120;

    /**
     * @return a new {@link CommandLine} with the {@link HeadlessMcCommand} at its root, writing nowhere.
     */
    public CommandLine create() {
        PrintWriter devNull = new PrintWriter(new StringWriter());
        return create(devNull, devNull, false);
    }

    /**
     * @param out  where picocli writes usage help etc.
     * @param err  where picocli writes parameter errors.
     * @param ansi whether to use ansi colors for usage help.
     * @return a new {@link CommandLine} with the {@link HeadlessMcCommand} at its root.
     */
    public CommandLine create(PrintWriter out, PrintWriter err, boolean ansi) {
        CommandLine commandLine = new CommandLine(HeadlessMcCommand.class, new CdiCommandFactory());
        commandLine.setOut(out);
        commandLine.setErr(err);
        commandLine.setUsageHelpWidth(USAGE_WIDTH);
        commandLine.setColorScheme(CommandLine.Help.defaultColorScheme(
            ansi ? CommandLine.Help.Ansi.ON : CommandLine.Help.Ansi.OFF
        ));
        return commandLine;
    }

}
