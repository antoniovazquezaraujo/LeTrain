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

        String preview = TerminalView.previewFor(command);

        assertTrue(preview.contains("train <train> stop;"));
        assertTrue(preview.contains("Scope: inside { }"));
    }

    @Test
    @DisplayName("INFO previews its prose, never a snippet")
    void infoPreview() {
        GrammarReference.Node info =
                GrammarReference.Node.info("block terminator", "A closing brace ends the block.");

        assertEquals("A closing brace ends the block.", TerminalView.previewFor(info));
    }

    @Test
    @DisplayName("HEADING and GROUP have no preview")
    void structuralNodesHaveNoPreview() {
        assertEquals("", TerminalView.previewFor(GrammarReference.Node.heading("TRIGGERS")));
        assertEquals("", TerminalView.previewFor(GrammarReference.Node.group("sensor")));
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
