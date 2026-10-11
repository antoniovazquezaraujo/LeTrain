package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import letrain.mvp.EditorText;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #740: the 2D editor footer shows the shared {@link EditorText} labels with their Alt
 * mnemonics, in the same order as the 3D HUD.
 */
@DisplayName("2D editor footer")
class TerminalViewEditorFooterTest {

    @Test
    @DisplayName("the footer matches the shared wording in display order")
    void should_PinTheFooterLabels() {
        assertEquals(
                List.of(new TerminalView.FooterAction("Save", 'S'),
                        new TerminalView.FooterAction("Load", 'L'),
                        new TerminalView.FooterAction("Export", 'E'),
                        new TerminalView.FooterAction("Import", 'I'),
                        new TerminalView.FooterAction("Reload from game", 'R'),
                        new TerminalView.FooterAction("Apply program", 'P'),
                        new TerminalView.FooterAction("Rebuild world", 'B'),
                        new TerminalView.FooterAction("Close", 'C')),
                TerminalView.editorFooterActions());
    }

    @Test
    @DisplayName("the renamed buttons keep the R/P/B mnemonics over the shared words")
    void should_UseTheSharedWords() {
        List<TerminalView.FooterAction> actions = TerminalView.editorFooterActions();

        assertEquals(new TerminalView.FooterAction(EditorText.RELOAD_FROM_GAME, 'R'),
                actions.get(4));
        assertEquals(new TerminalView.FooterAction(EditorText.APPLY_PROGRAM, 'P'), actions.get(5));
        assertEquals(new TerminalView.FooterAction(EditorText.REBUILD_WORLD, 'B'), actions.get(6));
    }
}
