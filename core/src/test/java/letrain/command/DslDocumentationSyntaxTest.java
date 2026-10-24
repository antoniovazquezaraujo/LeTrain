package letrain.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * D3/#828: the human-facing quick reference is checked against the real grammar. Every PROGRAM
 * command must parse in its declared {@link GrammarReference.Scope} after replacing the
 * {@code <token>} placeholders with valid values; INFO nodes are prose and must never be inserted.
 * The cheat-sheet and grammar-page examples are checked too.
 */
@DisplayName("D3: help and cheat-sheet examples parse against the grammar")
class DslDocumentationSyntaxTest {

    /** Valid replacement for every allowed placeholder token. */
    private static final Map<String, String> PLACEHOLDER_VALUES = Map.ofEntries(
            Map.entry("station", "1"), Map.entry("sensor", "1"), Map.entry("train", "1"),
            Map.entry("fork", "1"), Map.entry("semaphore", "1"), Map.entry("signal", "1"),
            Map.entry("speed", "3"), Map.entry("limit", "40"), Map.entry("count", "1"),
            Map.entry("name", "Test"), Map.entry("time", "9:30"), Map.entry("dir", "e"));

    private record Scoped(String label, String snippet, GrammarReference.Scope scope) {
    }

    private static List<GrammarReference.Node> allNodes(GrammarReference.Group group) {
        List<GrammarReference.Node> out = new ArrayList<>();
        collectNodes(GrammarReference.getReferenceTree(group), out);
        return out;
    }

    private static void collectNodes(List<GrammarReference.Node> nodes,
            List<GrammarReference.Node> out) {
        for (GrammarReference.Node node : nodes) {
            out.add(node);
            collectNodes(node.children, out);
        }
    }

    private static List<Scoped> referenceCommands() {
        List<Scoped> out = new ArrayList<>();
        for (GrammarReference.Group group : GrammarReference.Group.values()) {
            if (group == GrammarReference.Group.CONFIG) {
                continue; // key=value settings, not DSL commands
            }
            for (GrammarReference.Node node : allNodes(group)) {
                if (node.kind == GrammarReference.Kind.COMMAND && node.snippet != null) {
                    out.add(new Scoped(node.label, node.snippet, node.scope));
                }
            }
        }
        return out;
    }

    @Test
    @DisplayName("every PROGRAM/console/build command parses in its declared scope")
    void referenceCommands_parseInTheirScope() {
        List<String> failures = new ArrayList<>();
        for (Scoped scoped : referenceCommands()) {
            String error = parseInScope(scoped);
            if (error != null) {
                failures.add(scoped.snippet() + " [" + scoped.scope() + "] -> " + error);
            }
        }
        assertTrue(failures.isEmpty(),
                "help snippets that do not parse:\n" + String.join("\n", failures));
    }

    @Test
    @DisplayName("no COMMAND snippet uses the comment character or an empty quoted name")
    void commands_haveNoHashOrEmptyNames() {
        for (Scoped scoped : referenceCommands()) {
            assertFalse(scoped.snippet().contains("#"),
                    "snippet uses the comment character: " + scoped.snippet());
            assertFalse(scoped.snippet().contains("\"\""),
                    "snippet has an empty quoted name: " + scoped.snippet());
        }
    }

    @Test
    @DisplayName("INFO nodes are prose: never a snippet, and they carry their text")
    void infoNodes_areNotes() {
        for (GrammarReference.Group group : GrammarReference.Group.values()) {
            for (GrammarReference.Node node : allNodes(group)) {
                if (node.kind == GrammarReference.Kind.INFO) {
                    assertNull(node.snippet, "INFO must not be insertable: " + node.label);
                    assertNotNull(node.info, "INFO without text: " + node.label);
                    assertFalse(node.info.isBlank(), "blank INFO: " + node.label);
                } else {
                    assertNull(node.info, "only INFO carries prose: " + node.label);
                }
            }
        }
    }

    @Test
    @DisplayName("every COMMAND has a scope; only COMMAND carries a snippet")
    void commands_haveScope() {
        for (GrammarReference.Group group : GrammarReference.Group.values()) {
            for (GrammarReference.Node node : allNodes(group)) {
                if (node.kind == GrammarReference.Kind.COMMAND) {
                    assertNotNull(node.scope, "COMMAND without scope: " + node.label);
                    assertNotNull(node.snippet, "COMMAND without snippet: " + node.label);
                } else {
                    assertNull(node.scope, "scope is COMMAND-only: " + node.label);
                    assertNull(node.snippet, "snippet is COMMAND-only: " + node.label);
                }
            }
        }
    }

    @Test
    @DisplayName("placeholders are well formed and belong to the allowed token set")
    void placeholders_areWhitelisted() {
        List<String> failures = new ArrayList<>();
        for (Scoped scoped : referenceCommands()) {
            for (int[] span : Placeholders.find(scoped.snippet())) {
                String token = Placeholders.tokenAt(scoped.snippet(), span);
                if (!Placeholders.isAllowedToken(token)) {
                    failures.add(scoped.snippet() + " -> <" + token + ">");
                }
            }
        }
        assertTrue(failures.isEmpty(), "unknown placeholders:\n" + String.join("\n", failures));
    }

    @Test
    @DisplayName("scope is exact: BODY_ONLY never parses in a block, BLOCK_ONLY never at top level")
    void scopesMatchTheGrammarExactly() {
        List<String> bodyOnlyInBlock = new ArrayList<>();
        List<String> blockOnlyAtTop = new ArrayList<>();
        for (Scoped scoped : referenceCommands()) {
            String text = ensureTerminated(substitute(scoped.snippet()));
            if (scoped.scope() == GrammarReference.Scope.BODY_ONLY
                    && firstSyntaxErrorInStatement(wrapBlock(text)) == null) {
                bodyOnlyInBlock.add(scoped.label() + " :: " + scoped.snippet());
            }
            if (scoped.scope() == GrammarReference.Scope.BLOCK_ONLY
                    && firstSyntaxErrorAtTopLevel(text) == null) {
                blockOnlyAtTop.add(scoped.label() + " :: " + scoped.snippet());
            }
        }
        assertTrue(bodyOnlyInBlock.isEmpty(),
                "BODY_ONLY snippets that also parse inside a block (label should be BOTH): "
                        + bodyOnlyInBlock);
        assertTrue(blockOnlyAtTop.isEmpty(),
                "BLOCK_ONLY snippets that also parse at top level (label should be BOTH): "
                        + blockOnlyAtTop);
    }

    @Test
    @DisplayName("a BODY_ONLY command (signal) is rejected inside a block")
    void bodyOnlyCommand_failsInsideBlock() {
        String signal = substitute("signal <signal> set limit <limit>;");
        String wrapped = "sensor 1 on train enter { " + signal + " }";
        assertNotNull(firstSyntaxErrorInStatement(wrapped),
                "signal must not parse inside a trigger block: " + wrapped);
    }

    @Test
    @DisplayName("every complete cheat-sheet example parses (EN and ES sheets)")
    void cheatSheetExamples_parse() throws IOException {
        List<String> failures = new ArrayList<>();
        int checked = 0;
        for (String sheet : List.of("cheatsheet.md", "cheatsheet_es.md")) {
            for (String example : cheatSheetExamples(sheet)) {
                String error = firstSyntaxErrorInStatement(example);
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

    /** Replaces every allowed placeholder with a valid example value. */
    static String substitute(String text) {
        List<int[]> spans = Placeholders.find(text);
        StringBuilder out = new StringBuilder(text);
        for (int i = spans.size() - 1; i >= 0; i--) {
            int[] span = spans.get(i);
            String token = text.substring(span[0] + 1, span[1] - 1);
            String value = PLACEHOLDER_VALUES.get(token);
            if (value != null) {
                out.replace(span[0], span[1], value);
            }
        }
        return out.toString();
    }

    /**
     * Parses a command where its scope says it belongs: BODY_ONLY as a top-level statement,
     * BLOCK_ONLY wrapped in a trigger block, BOTH in both places. Waypoint snippets ({@code add
     * station/sensor …}) are fragments of {@code create itinerary} and use the {@code waypoint}
     * grammar rule.
     */
    private static String parseInScope(Scoped scoped) {
        String text = ensureTerminated(substitute(scoped.snippet()));
        if (scoped.scope() == GrammarReference.Scope.BLOCK_ONLY) {
            return firstSyntaxErrorInStatement(wrapBlock(text));
        }
        if (scoped.scope() == GrammarReference.Scope.BOTH) {
            String topLevel = firstSyntaxErrorInStatement(text);
            if (topLevel != null) {
                return "top-level: " + topLevel;
            }
            String insideBlock = firstSyntaxErrorInStatement(wrapBlock(text));
            return insideBlock == null ? null : "inside block: " + insideBlock;
        }
        return firstSyntaxErrorInStatement(text);
    }

    private static String ensureTerminated(String text) {
        if (!text.endsWith(";") && !text.endsWith("}")) {
            return text + ";";
        }
        return text;
    }

    private static String wrapBlock(String text) {
        return "sensor 1 on train enter { " + text + " }";
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
     * Waypoint fragments ({@code add station/sensor …}) are parsed with the grammar rule the user
     * is writing for, as they are only valid inside {@code create itinerary { … }}.
     */
    private static String firstSyntaxErrorInStatement(String snippet) {
        String text = snippet.trim();
        if (!text.endsWith(";") && !text.endsWith("}")) {
            text += ";"; // the console appends the semicolon when the user does not type it
        }
        // `train at …` is a block-only action: it is only valid inside a trigger/at/every block.
        if (text.startsWith("train at ")) {
            text = wrapBlock(text);
        }
        List<String> errors = new ArrayList<>();
        LeTrainLexer lexer = new LeTrainLexer(CharStreams.fromString(text));
        lexer.removeErrorListeners();
        lexer.addErrorListener(lexerListener(errors));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        if (text.startsWith("add station ") || text.startsWith("add sensor ")) {
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
     * Strict top-level parse (no {@code train at} auto-wrap): used to prove a BLOCK_ONLY command
     * really needs the block. Distinct from {@link #firstSyntaxErrorInStatement(String)}, which
     * wraps that fragment for the convenience of the scope-aware callers.
     */
    private static String firstSyntaxErrorAtTopLevel(String snippet) {
        String text = snippet.trim();
        if (!text.endsWith(";") && !text.endsWith("}")) {
            text += ";";
        }
        List<String> errors = new ArrayList<>();
        LeTrainLexer lexer = new LeTrainLexer(CharStreams.fromString(text));
        lexer.removeErrorListeners();
        lexer.addErrorListener(lexerListener(errors));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        if (text.startsWith("add station ") || text.startsWith("add sensor ")) {
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
