package letrain.command;

import java.util.ArrayList;
import java.util.List;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;

/**
 * Headless scenario validator (ADR-020 item 4, editor tooling): parses a scenario text and reports
 * <b>syntax</b> diagnostics without touching a running game or executing any command, so an external
 * editor (vim, VS Code…) can "compile" a {@code .ltr} and show errors in its quickfix.
 *
 * <p>
 * Sections are read with a brace-depth counter (like {@link ScenarioFile}), so the {@code program}
 * section may nest blocks. {@code on build}/{@code on start} lines are validated as player commands
 * ({@code PlayerCommandsParser}); the {@code program} body is validated as an automation script
 * ({@code ScriptLogicParser}). Diagnostics are compiler-friendly ({@code line:col}).
 *
 * <p>
 * This class is UI-free and side-effect free. A CLI entry point lives in {@link ScenarioCheckCli}.
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

        // Walk the sections, validating each statement with the right parser.
        String[] lines = text.split("\\R", -1);
        String section = null;
        int depth = 0;
        StringBuilder program = new StringBuilder();
        int programStartLine = 1;
        for (int i = 0; i < lines.length; i++) {
            String raw = lines[i];
            String line = raw.trim();
            int lineNo = i + 1;
            if (depth == 0) {
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("seed ")) {
                    continue;
                }
                String lower = line.toLowerCase();
                if (lower.startsWith("configuration")) {
                    section = "configuration";
                    depth = 1;
                    continue;
                }
                if (lower.startsWith("on build")) {
                    section = "build";
                    depth = 1;
                    continue;
                }
                if (lower.startsWith("on start")) {
                    section = "start";
                    depth = 1;
                    continue;
                }
                if (lower.startsWith("program")) {
                    section = "program";
                    depth = 1;
                    programStartLine = lineNo + 1;
                    continue;
                }
                compilePlayerCommand(line, lineNo, diagnostics); // legacy flat file
                continue;
            }
            if ("program".equals(section)) {
                int delta = braceDelta(line);
                if (depth + delta > 0) {
                    if (program.length() > 0) {
                        program.append('\n');
                    }
                    program.append(raw);
                }
                depth += delta;
                if (depth <= 0) {
                    section = null;
                    depth = 0;
                }
            } else if ("configuration".equals(section)) {
                // Settings (key=value), not commands: nothing to compile.
                depth += braceDelta(line);
                if (depth <= 0) {
                    section = null;
                    depth = 0;
                }
            } else {
                if (line.isEmpty() || line.startsWith("#")) {
                    continue; // blank lines and comments carry no statements
                }
                int delta = braceDelta(line);
                if (depth + delta > 0) {
                    compilePlayerCommand(line, lineNo, diagnostics);
                }
                depth += delta;
                if (depth <= 0) {
                    section = null;
                    depth = 0;
                }
            }
        }

        if (program != null && !program.toString().isBlank()) {
            compileProgram(program.toString(), programStartLine, diagnostics);
        }
        return new Result(diagnostics.isEmpty(), diagnostics);
    }

    private static void compilePlayerCommand(String command, int lineNo,
            List<Diagnostic> diagnostics) {
        try {
            LeTrainLexer lexer = new LeTrainLexer(CharStreams.fromString(command));
            lexer.removeErrorListeners();
            lexer.addErrorListener(errorListener(diagnostics, lineNo, 1));
            PlayerCommandsParser parser = new PlayerCommandsParser(new CommonTokenStream(lexer));
            parser.removeErrorListeners();
            parser.addErrorListener(errorListener(diagnostics, lineNo, 1));
            parser.playerStart();
        } catch (Exception e) {
            diagnostics.add(new Diagnostic(lineNo, 1, safeMessage(e)));
        }
    }

    private static void compileProgram(String program, int startLine, List<Diagnostic> diagnostics) {
        try {
            LeTrainLexer lexer = new LeTrainLexer(CharStreams.fromString(program));
            lexer.removeErrorListeners();
            lexer.addErrorListener(errorListener(diagnostics, startLine, 1));
            ScriptLogicParser parser = new ScriptLogicParser(new CommonTokenStream(lexer));
            parser.removeErrorListeners();
            parser.addErrorListener(errorListener(diagnostics, startLine, 1));
            parser.scriptStart();
        } catch (Exception e) {
            diagnostics.add(new Diagnostic(startLine, 1, safeMessage(e)));
        }
    }

    /**
     * Error listener that maps the ANTLR 1-based line back to an absolute line. For single-line
     * inputs, {@code lineOffset} is the line number and the relative line is 1.
     */
    private static BaseErrorListener errorListener(List<Diagnostic> diagnostics, int lineOffset,
            int relativeBase) {
        return new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
                    int charPositionInLine, String msg, RecognitionException e) {
                int absoluteLine = lineOffset + Math.max(0, line - relativeBase);
                diagnostics.add(new Diagnostic(absoluteLine, charPositionInLine + 1,
                        SyntaxMessages.shorten(msg)));
            }
        };
    }

    private static int braceDelta(String line) {
        int delta = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == '{') {
                delta++;
            } else if (line.charAt(i) == '}') {
                delta--;
            }
        }
        return delta;
    }

    private static String safeMessage(Exception e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
    }
}
