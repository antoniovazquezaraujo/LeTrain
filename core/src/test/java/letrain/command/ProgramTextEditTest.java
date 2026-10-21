package letrain.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("ProgramTextEdit: shared program-editor text operations (#828)")
class ProgramTextEditTest {

    @Test
    @DisplayName("inserts a snippet in a blank line with its indentation and selects the first placeholder")
    void insert_blankLineKeepsIndentAndFindsPlaceholder() {
        ProgramTextEdit.Insertion result =
                ProgramTextEdit.insert("  \nnext", 2, "train <train> stop;");

        assertEquals("  train <train> stop;\nnext", result.text());
        assertEquals(8, result.caret());
        assertEquals(List.of(new ProgramTextEdit.Span(8, 15)), result.placeholders());
    }

    @Test
    @DisplayName("inserts a snippet below a line with content instead of splitting it")
    void insert_contentLineGoesBelowWithIndent() {
        ProgramTextEdit.Insertion result =
                ProgramTextEdit.insert("  foo();\nbar", 5, "add station <station>, load");

        assertEquals("  foo();\n  add station <station>, load\nbar", result.text());
        assertEquals(23, result.caret());
    }

    @Test
    @DisplayName("indents every line of a multiline snippet with the base indentation")
    void insert_multilineSnippet() {
        ProgramTextEdit.Insertion result = ProgramTextEdit.insert("  \n", 2,
                "create \"<name>\" {\n  add station <station>, stop\n}");

        assertEquals("  create \"<name>\" {\n    add station <station>, stop\n  }\n",
                result.text());
        assertEquals(10, result.caret());
        assertEquals(2, result.placeholders().size());
    }

    @Test
    @DisplayName("placeholders inside quotes keep the quotes")
    void insert_placeholderInsideQuotes() {
        ProgramTextEdit.Insertion result =
                ProgramTextEdit.insert("", 0, "station <station> set name \"<name>\";");

        List<ProgramTextEdit.Span> spans = result.placeholders();
        assertEquals(2, spans.size());
        assertEquals("<station>",
                result.text().substring(spans.get(0).start(), spans.get(0).end()));
        assertEquals("<name>", result.text().substring(spans.get(1).start(), spans.get(1).end()));
        assertEquals("\"<name>\"",
                result.text().substring(spans.get(1).start() - 1, spans.get(1).end() + 1));
    }

    @Test
    @DisplayName("a snippet without placeholders leaves the caret at its end")
    void insert_withoutPlaceholders() {
        ProgramTextEdit.Insertion result = ProgramTextEdit.insert("", 0, "train 1 stop;");

        assertTrue(result.placeholders().isEmpty());
        assertEquals("train 1 stop;".length(), result.caret());
    }

    @Test
    @DisplayName("a snippet that already ends with a newline is not doubled")
    void insert_keepsSingleTrailingNewline() {
        ProgramTextEdit.Insertion result = ProgramTextEdit.insert("", 0, "every 1d {\n}\n");

        assertEquals("every 1d {\n}\n", result.text());
    }

    @Test
    @DisplayName("placeholder cycling wraps in both directions")
    void placeholderCycling_wraps() {
        List<ProgramTextEdit.Span> spans =
                List.of(new ProgramTextEdit.Span(0, 3), new ProgramTextEdit.Span(10, 13));
        ProgramTextEdit.Span first = spans.get(0);
        ProgramTextEdit.Span second = spans.get(1);

        assertEquals(second, ProgramTextEdit.nextPlaceholder(spans, first));
        assertEquals(first, ProgramTextEdit.nextPlaceholder(spans, second));
        assertEquals(second, ProgramTextEdit.previousPlaceholder(spans, first));
        assertEquals(first, ProgramTextEdit.previousPlaceholder(spans, second));
        assertNull(ProgramTextEdit.nextPlaceholder(List.of(), first));
    }

    @Test
    @DisplayName("replace swaps a whole placeholder span")
    void replace_replacesOnlyTheSpan() {
        String text = "train <train> stop;";

        assertEquals("train 5 stop;",
                ProgramTextEdit.replace(text, new ProgramTextEdit.Span(6, 13), "5"));
    }

    @Test
    @DisplayName("Enter adds two spaces after an opening brace")
    void indentForNewLine_afterOpeningBrace() {
        assertEquals("  ", ProgramTextEdit.indentForNewLine("train 1 on enter {", 18));
        assertEquals("    ", ProgramTextEdit.indentForNewLine("  every 1d {  ", 13));
    }

    @Test
    @DisplayName("Enter just before a closing brace outdents one level")
    void indentForNewLine_beforeClosingBrace() {
        assertEquals("", ProgramTextEdit.indentForNewLine("  }", 2));
        assertEquals("  ", ProgramTextEdit.indentForNewLine("    }", 4));
    }

    @Test
    @DisplayName("Enter elsewhere keeps the current line indentation")
    void indentForNewLine_keepsCurrentIndent() {
        assertEquals("    ", ProgramTextEdit.indentForNewLine("    add station 1, load;", 20));
    }

    @Test
    @DisplayName("dedent removes a tab or up to two leading spaces")
    void dedent_rules() {
        assertEquals("x", ProgramTextEdit.dedent("  x"));
        assertEquals(" x", ProgramTextEdit.dedent("   x"));
        assertEquals("x", ProgramTextEdit.dedent("\tx"));
        assertEquals("x", ProgramTextEdit.dedent("x"));
    }

    @Test
    @DisplayName("dedentLines handles the whole touched block")
    void dedentLines_selection() {
        String text = "    a\n  b\nc";
        assertEquals("  a\nb\nc", ProgramTextEdit.dedentLines(text, 0, text.length()));
        assertEquals("  a\n  b\nc", ProgramTextEdit.dedentLines(text, 0, 0));
    }

    @Test
    @DisplayName("braceDepth ignores braces inside strings and comments")
    void braceDepth_ignoresStringsAndComments() {
        assertEquals(1, ProgramTextEdit.braceDepth("a { b", 5));
        assertEquals(0, ProgramTextEdit.braceDepth("\"{\"", 3));
        assertEquals(0, ProgramTextEdit.braceDepth("# }\n", 4));
        assertEquals(1, ProgramTextEdit.braceDepth("{ { }", 5));
        assertEquals(0, ProgramTextEdit.braceDepth("}", 1));
        assertEquals(0, ProgramTextEdit.braceDepth("\"a\\\"{\"", 5));
    }

    @Test
    @DisplayName("scope rules gate top-level and block-only commands")
    void insertionScope() {
        assertTrue(ProgramTextEdit.isInsertionAllowed(GrammarReference.Scope.BODY_ONLY, 0));
        assertFalse(ProgramTextEdit.isInsertionAllowed(GrammarReference.Scope.BODY_ONLY, 1));
        assertTrue(ProgramTextEdit.isInsertionAllowed(GrammarReference.Scope.BLOCK_ONLY, 2));
        assertFalse(ProgramTextEdit.isInsertionAllowed(GrammarReference.Scope.BLOCK_ONLY, 0));
        assertTrue(ProgramTextEdit.isInsertionAllowed(GrammarReference.Scope.BOTH, 0));
        assertTrue(ProgramTextEdit.isInsertionAllowed(GrammarReference.Scope.BOTH, 3));
    }

    @Test
    @DisplayName("scope hints and mismatch messages are shared by both clients")
    void scopeTexts() {
        assertEquals("top-level only", ProgramTextEdit.scopeHint(GrammarReference.Scope.BODY_ONLY));
        assertEquals("inside { }", ProgramTextEdit.scopeHint(GrammarReference.Scope.BLOCK_ONLY));
        assertEquals("", ProgramTextEdit.scopeHint(GrammarReference.Scope.BOTH));
        assertEquals("[top]", ProgramTextEdit.scopeTag(GrammarReference.Scope.BODY_ONLY));
        assertEquals("[in {}]", ProgramTextEdit.scopeTag(GrammarReference.Scope.BLOCK_ONLY));
        assertEquals("", ProgramTextEdit.scopeTag(GrammarReference.Scope.BOTH));
        assertTrue(ProgramTextEdit.mismatchMessage(GrammarReference.Scope.BODY_ONLY)
                .contains("top level"));
        assertTrue(ProgramTextEdit.mismatchMessage(GrammarReference.Scope.BLOCK_ONLY)
                .contains("trigger or time block"));
    }
}
