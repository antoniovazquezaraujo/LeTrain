package letrain.command;

import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

/**
 * Headless scenario compiler/validator (ADR-020 item 4, editor tooling). It parses a scenario text
 * and reports syntax diagnostics without touching a running game or executing any command, so an
 * external editor (vim, VS Code…) can "compile" a {@code .ltr} and show errors in its quickfix.
 *
 * <p>
 * Diagnostic format is compiler-friendly ({@code path:line:col: error: message}). This class is
 * UI-free and side-effect free; the CLI entry point lives in the launcher.
 */
public final class ScenarioCompiler {

    /** A single compiler diagnostic. */
    public record Diagnostic(int line, int col, String message) {
        @Override
        public String toString() {
            return line + ":" + col + ": error: " + message;
        }
    }

    /** Compilation outcome. */
    public record Result(boolean ok, List<Diagnostic> diagnostics) {}

    private ScenarioCompiler() {}

    /** Compiles {@code text}; never throws for content errors (they become diagnostics). */
    public static Result compile(String text) {
        List<Diagnostic> diagnostics = new ArrayList<>();
        if (text == null) {
            diagnostics.add(new Diagnostic(1, 1, "empty scenario"));
            return new Result(false, diagnostics);
        }

        // Seed header.
        try {
            ScenarioFile.parseSeed(text);
        } catch (IllegalArgumentException e) {
            diagnostics.add(new Diagnostic(1, 1, e.getMessage()));
        }

        // Commands, keeping the original line numbers (blank lines, comments and section markers are
        // skipped like the parser does).
        String[] lines = text.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            int lineNo = i + 1;
            if (line.isEmpty() || line.startsWith("#") || line.startsWith("seed ")) {
                continue;
            }
            String lower = line.toLowerCase();
            if (lower.startsWith("on build") || lower.startsWith("on start")
                    || line.equals("}")) {
                continue;
            }
            compileCommand(line, lineNo, diagnostics);
        }
        return new Result(diagnostics.isEmpty(), diagnostics);
    }

    private static void compileCommand(String command, int lineNo, List<Diagnostic> diagnostics) {
        try {
            LeTrainLexer lexer = new LeTrainLexer(CharStreams.fromString(command));
            lexer.removeErrorListeners();
            lexer.addErrorListener(new org.antlr.v4.runtime.BaseErrorListener() {
                @Override
                public void syntaxError(org.antlr.v4.runtime.Recognizer<?, ?> recognizer,
                        Object offendingSymbol, int line, int charPositionInLine, String msg,
                        org.antlr.v4.runtime.RecognitionException e) {
                    diagnostics.add(new Diagnostic(lineNo, charPositionInLine + 1, msg));
                }
            });
            PlayerCommandsParser parser =
                    new PlayerCommandsParser(new CommonTokenStream(lexer));
            parser.removeErrorListeners();
            parser.addErrorListener(new org.antlr.v4.runtime.BaseErrorListener() {
                @Override
                public void syntaxError(org.antlr.v4.runtime.Recognizer<?, ?> recognizer,
                        Object offendingSymbol, int line, int charPositionInLine, String msg,
                        org.antlr.v4.runtime.RecognitionException e) {
                    diagnostics.add(new Diagnostic(lineNo, charPositionInLine + 1, msg));
                }
            });
            parser.playerStart();
        } catch (Exception e) {
            diagnostics.add(new Diagnostic(lineNo, 1, e.getMessage()));
        }
    }
}
