package letrain.command;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * D3: the human-facing command metadata is checked against the real grammar. Every command snippet
 * of {@link GrammarReference} (console, build and program groups), every complete-statement example
 * of the user cheat sheets and every complete {@code letrain} block of the grammar pages must
 * parse; the snippets that are prose (not commands) are listed explicitly so the gap is a decision,
 * not an accident.
 */
@DisplayName("D3: help and cheat-sheet examples parse against the grammar")
class DslDocumentationSyntaxTest {

    /**
     * Reference snippets that are instructions, not commands: they carry prose and are rendered as
     * help text. Everything else must parse as a full player statement.
     */
    private static final List<String> PROSE_SNIPPETS = List.of(
            "} ends the block; a trailing ; is optional",
            "waypoints: a comma after the reference (and its direction) is mandatory when there is"
                    + " a plan; order: arrival, actions, departure");

    /** Every command snippet of the reference tree, flattened. */
    private static List<String> referenceSnippets() {
        List<String> out = new ArrayList<>();
        for (GrammarReference.Group group : GrammarReference.Group.values()) {
            if (group == GrammarReference.Group.CONFIG) {
                continue; // key=value settings, not DSL commands
            }
            collect(GrammarReference.getReferenceTree(group), out);
        }
        return out;
    }

    private static void collect(List<GrammarReference.Node> nodes, List<String> out) {
        for (GrammarReference.Node node : nodes) {
            if (node.snippet != null && !node.snippet.isBlank()) {
                out.add(node.snippet);
            }
            collect(node.children, out);
        }
    }

    @Test
    @DisplayName("every GrammarReference command snippet parses with the player grammar")
    void referenceSnippets_parse() {
        List<String> failures = new ArrayList<>();
        for (String snippet : referenceSnippets()) {
            if (PROSE_SNIPPETS.contains(snippet)) {
                continue;
            }
            String error = firstSyntaxError(snippet);
            if (error != null) {
                failures.add(snippet + " -> " + error);
            }
        }
        assertTrue(failures.isEmpty(),
                "help snippets that do not parse:\n" + String.join("\n", failures));
    }

    @Test
    @DisplayName("every prose snippet of the reference is explicitly marked (no silent exemptions)")
    void proseSnippets_areExplicit() {
        for (String snippet : PROSE_SNIPPETS) {
            assertTrue(referenceSnippets().contains(snippet),
                    "stale prose whitelist entry: " + snippet);
        }
    }

    @Test
    @DisplayName("every complete cheat-sheet example parses (EN and ES sheets)")
    void cheatSheetExamples_parse() throws IOException {
        List<String> failures = new ArrayList<>();
        int checked = 0;
        for (String sheet : List.of("cheatsheet.md", "cheatsheet_es.md")) {
            for (String example : cheatSheetExamples(sheet)) {
                String error = firstSyntaxError(example);
                checked++;
                if (error != null) {
                    failures.add(sheet + ": " + example + " -> " + error);
                }
            }
        }
        assertTrue(checked >= 30,
                "expected the cheat sheets to contribute examples, got " + checked);
        assertTrue(failures.isEmpty(),
                "cheat-sheet examples that do not parse:\n" + String.join("\n", failures));
    }

    @Test
    @DisplayName("every complete `letrain` block of the grammar pages parses (EN and ES)")
    void grammarPageBlocks_parse() throws IOException {
        List<String> failures = new ArrayList<>();
        int checked = 0;
        for (String page : List.of("grammar.md", "grammar_es.md")) {
            List<String> blocks = letrainBlocks(page);
            assertTrue(blocks.size() >= 2,
                    page + " must contribute complete `letrain` blocks, got " + blocks.size());
            for (String block : blocks) {
                String error = firstSyntaxErrorInProgram(block);
                checked++;
                if (error != null) {
                    failures.add(page + " [" + firstLine(block) + "] -> " + error);
                }
            }
        }
        assertTrue(checked >= 4,
                "expected the grammar pages to contribute examples, got " + checked);
        assertTrue(failures.isEmpty(),
                "grammar-page blocks that do not parse:\n" + String.join("\n", failures));
    }

    /**
     * Extracts complete statements from the cheat sheets: every backticked text in a table row that
     * ends with {@code ;} and has no placeholders ({@code <}, {@code [}, {@code |}, …). Examples
     * split by " / " or " o " inside a cell are collected individually.
     */
    private static List<String> cheatSheetExamples(String fileName) throws IOException {
        Path path = Path.of("..", "docs", "user", fileName).normalize();
        Assumptions.assumeTrue(Files.exists(path), "cheat sheet not found: " + path);
        List<String> examples = new ArrayList<>();
        Pattern backtick = Pattern.compile("`([^`]+)`");
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("|") || !trimmed.endsWith("|")) {
                continue;
            }
            String[] cells = trimmed.split("\\|");
            if (cells.length < 2) {
                continue;
            }
            String exampleCell = cells[cells.length - 1];
            Matcher matcher = backtick.matcher(exampleCell);
            while (matcher.find()) {
                String example = matcher.group(1).trim();
                if (isCompleteStatement(example)) {
                    examples.add(example);
                }
            }
        }
        return examples;
    }

    private static boolean isCompleteStatement(String example) {
        if (!example.endsWith(";")) {
            return false;
        }
        return example.chars().noneMatch(c -> c == '<' || c == '[' || c == ']' || c == '|'
                || c == '{' || c == '}' || c == '#');
    }

    /**
     * Extracts the fenced {@code ```letrain} blocks of a documentation page. Blocks that still
     * carry placeholders ({@code <}, {@code [}, {@code ]}, {@code |}) are prose templates, not
     * programs, and are skipped explicitly — the same best-effort approach as the cheat sheets.
     */
    private static List<String> letrainBlocks(String fileName) throws IOException {
        Path path = Path.of("..", "docs", "user", fileName).normalize();
        Assumptions.assumeTrue(Files.exists(path), "documentation page not found: " + path);
        List<String> blocks = new ArrayList<>();
        List<String> current = null;
        for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
            String trimmed = line.trim();
            if (current == null) {
                if (trimmed.equals("```letrain")) {
                    current = new ArrayList<>();
                }
            } else if (trimmed.equals("```")) {
                String block = String.join("\n", current);
                if (blockHasNoPlaceholders(block)) {
                    blocks.add(block);
                }
                current = null;
            } else {
                current.add(line);
            }
        }
        return blocks;
    }

    private static boolean blockHasNoPlaceholders(String block) {
        return block.chars().noneMatch(c -> c == '<' || c == '[' || c == ']' || c == '|');
    }

    private static String firstLine(String block) {
        return block.lines().findFirst().orElse("");
    }

    /**
     * Parses one snippet as console input; returns the first diagnostic, or null when it parses.
     */
    private static String firstSyntaxError(String snippet) {
        String text = snippet.replace("#", "1").trim();
        if (!text.endsWith(";") && !text.endsWith("}")) {
            text += ";"; // the console appends the semicolon when the user does not type it
        }
        Wrapped wrapped = wrapForContext(text);
        List<String> errors = new ArrayList<>();
        LeTrainLexer lexer = new LeTrainLexer(CharStreams.fromString(wrapped.text()));
        lexer.removeErrorListeners();
        lexer.addErrorListener(lexerListener(errors));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        if (wrapped.waypoint()) {
            // Waypoint snippets are fragments of a create-itinerary block: the standalone rule is
            // the real grammar for them.
            ScriptLogicParser parser = new ScriptLogicParser(tokens);
            parser.removeErrorListeners();
            parser.addErrorListener(collector(errors));
            parser.waypoint();
        } else {
            PlayerCommandsParser parser = new PlayerCommandsParser(tokens);
            parser.removeErrorListeners();
            parser.addErrorListener(collector(errors));
            parser.playerStart();
        }
        return errors.isEmpty() ? null : String.join(" | ", errors);
    }

    /**
     * Parses a whole {@code letrain} documentation block as console input (comments included);
     * returns the first diagnostic, or null when every statement parses.
     */
    private static String firstSyntaxErrorInProgram(String block) {
        List<String> errors = new ArrayList<>();
        LeTrainLexer lexer = new LeTrainLexer(CharStreams.fromString(block));
        lexer.removeErrorListeners();
        lexer.addErrorListener(lexerListener(errors));
        PlayerCommandsParser parser = new PlayerCommandsParser(new CommonTokenStream(lexer));
        parser.removeErrorListeners();
        parser.addErrorListener(collector(errors));
        parser.playerStart();
        return errors.isEmpty() ? null : String.join(" | ", errors);
    }

    private static BaseErrorListener lexerListener(List<String> errors) {
        return new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
                    int charPositionInLine, String msg, RecognitionException e) {
                errors.add("lexer " + line + ":" + charPositionInLine + " " + msg);
            }
        };
    }

    private record Wrapped(String text, boolean waypoint) {}

    /**
     * Some reference snippets are fragments, not full statements: waypoint lines live inside
     * {@code create itinerary { … }} and {@code train at …} lines live inside a trigger block. The
     * test parses them with the grammar rule the user is writing for, not with a bogus wrapper.
     */
    private static Wrapped wrapForContext(String text) {
        if (text.startsWith("add station ") || text.startsWith("add sensor ")) {
            return new Wrapped(text, true);
        }
        if (text.startsWith("train at ")) {
            return new Wrapped("sensor 1 on train enter { " + text + " }", false);
        }
        return new Wrapped(text, false);
    }

    private static BaseErrorListener collector(List<String> errors) {
        return new BaseErrorListener() {
            @Override
            public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line,
                    int charPositionInLine, String msg, RecognitionException e) {
                errors.add("parser " + line + ":" + charPositionInLine + " " + msg);
            }
        };
    }
}
