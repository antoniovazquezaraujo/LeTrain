package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import letrain.command.GrammarReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("2D editor: quick-reference preview text and wrapping (#828)")
class TerminalViewPreviewTest {

    @Test
    @DisplayName("COMMAND previews its snippet and scope")
    void commandPreview() {
        GrammarReference.Node command = GrammarReference.Node.command("train stop",
                "train <train> stop;", GrammarReference.Scope.BLOCK_ONLY);

        String preview = TerminalView.previewFor(command, true);

        assertTrue(preview.contains("train <train> stop;"));
        assertTrue(preview.contains("Scope: inside { }"));
    }

    @Test
    @DisplayName("Scenario/Config previews show the snippet without a PROGRAM scope hint")
    void commandPreviewOutsideProgrammingTab() {
        GrammarReference.Node command = GrammarReference.Node.command("new station", "new st;",
                GrammarReference.Scope.BODY_ONLY);

        String preview = TerminalView.previewFor(command, false);

        assertTrue(preview.contains("new st;"));
        assertFalse(preview.contains("Scope:"));
    }

    @Test
    @DisplayName("INFO previews its prose, never a snippet")
    void infoPreview() {
        GrammarReference.Node info =
                GrammarReference.Node.info("block terminator", "A closing brace ends the block.");

        assertEquals("A closing brace ends the block.", TerminalView.previewFor(info, true));
    }

    @Test
    @DisplayName("HEADING and GROUP have no preview")
    void structuralNodesHaveNoPreview() {
        assertEquals("", TerminalView.previewFor(GrammarReference.Node.heading("TRIGGERS"), true));
        assertEquals("", TerminalView.previewFor(GrammarReference.Node.group("sensor"), true));
    }

    @Test
    @DisplayName("preview wrapping keeps newlines and caps the rows")
    void previewWrapping() {
        String wrapped = TerminalView.wrapPreview("one two three four", 7, 4);

        assertEquals("one two\nthree\nfour", wrapped);
        assertFalse(wrapped.contains("\r"));
    }

    @Test
    @DisplayName("long previews end with an ellipsis and respect maxLines")
    void previewTruncation() {
        String wrapped = TerminalView.wrapPreview("aaaa\nbbbb\ncccc\ndddd", 4, 3);

        assertEquals("aaaa\nbbbb\nc...", wrapped);
    }
}
