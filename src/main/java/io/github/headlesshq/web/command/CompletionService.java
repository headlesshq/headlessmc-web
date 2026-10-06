package io.github.headlesshq.web.command;

import io.github.headlesshq.headlessmc.console.Completions;
import io.github.headlesshq.headlessmc.console.completions.PicocliCompletions;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.jline.reader.ParsedLine;
import org.jline.reader.Parser;
import org.jline.reader.impl.DefaultParser;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tab completions for HeadlessMc command lines, using the same completion machinery
 * as HeadlessMc's Jline shell ({@link PicocliCompletions}).
 */
@ApplicationScoped
public class CompletionService {
    private static final Logger LOG = Logger.getLogger(CompletionService.class);

    private final CommandLines commandLines;

    @Inject
    public CompletionService(CommandLines commandLines) {
        this.commandLines = commandLines;
    }

    /**
     * Completes the word at the cursor.
     *
     * @param line   the command line.
     * @param cursor the cursor position in the line.
     * @return the completion result.
     */
    public Result complete(String line, int cursor) {
        int actualCursor = Math.max(0, Math.min(cursor, line.length()));
        ParsedLine parsed = new DefaultParser().parse(line, actualCursor, Parser.ParseContext.COMPLETE);
        Completions.Line completionLine = new JlineLine(parsed);
        Map<String, Candidate> candidates = new LinkedHashMap<>();
        try {
            new PicocliCompletions(commandLines::create)
                .stream(completionLine)
                .filter(candidate -> !candidate.getName().isEmpty())
                .forEach(candidate -> candidates.putIfAbsent(
                    candidate.getName(),
                    new Candidate(candidate.getName(), blankToNull(candidate.getDescription()))
                ));
        } catch (RuntimeException e) {
            // completions are best effort, e.g. remote version lists might fail to load
            LOG.debugf(e, "Failed to complete '%s'", line);
        }

        // the start of the word being completed, replace from here up to the cursor
        int wordStart = actualCursor - parsed.wordCursor();
        String word = parsed.word() == null ? "" : parsed.word().substring(0, Math.min(parsed.wordCursor(), parsed.word().length()));
        List<Candidate> sorted = candidates.values().stream()
            .sorted(Comparator.comparing((Candidate candidate) -> candidate.value().startsWith("-"))
                        .thenComparing(Candidate::value))
            .toList();
        return new Result(Math.max(0, wordStart), actualCursor, word, sorted);
    }

    private static @Nullable String blankToNull(@Nullable String string) {
        return string == null || string.isBlank() ? null : string;
    }

    /**
     * @param start      start of the text to replace with a candidate.
     * @param end        end of the text to replace with a candidate.
     * @param word       the (unquoted) word that is being completed.
     * @param candidates the candidates for the word.
     */
    public record Result(int start, int end, String word, List<Candidate> candidates) {
    }

    /**
     * @param value       the full word to replace the current one with.
     * @param description optional description.
     */
    public record Candidate(String value, @Nullable String description) {
    }

    private record JlineLine(ParsedLine parsed) implements Completions.Line {
        @Override
        public String word() {
            return parsed.word();
        }

        @Override
        public int wordCursor() {
            return parsed.wordCursor();
        }

        @Override
        public int wordIndex() {
            return parsed.wordIndex();
        }

        @Override
        public List<String> words() {
            return parsed.words();
        }

        @Override
        public String line() {
            return parsed.line();
        }

        @Override
        public int cursor() {
            return parsed.cursor();
        }
    }

}
