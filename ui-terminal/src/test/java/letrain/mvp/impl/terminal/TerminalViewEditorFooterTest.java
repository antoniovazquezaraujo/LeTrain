package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import letrain.mvp.EditorText;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #740/#743: the 2D editor footer shows the shared {@link EditorText} labels with their Alt
 * mnemonics, in the same order as the 3D HUD. Save/Load left the editor for the File menu, so the
 * footer no longer carries them.
 */
@DisplayName("2D editor footer")
class TerminalViewEditorFooterTest {

    @Test
    @DisplayName("the footer matches the shared wording in display order, without Save/Load")
    void should_PinTheFooterLabels() {
        assertEquals(
                List.of(new TerminalView.FooterAction("Export", 'E'),
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
                actions.get(2));
        assertEquals(new TerminalView.FooterAction(EditorText.APPLY_PROGRAM, 'P'), actions.get(3));
        assertEquals(new TerminalView.FooterAction(EditorText.REBUILD_WORLD, 'B'), actions.get(4));
    }

    @Test
    @DisplayName("the editor keeps no Save/Load row (they live in the File menu)")
    void should_NotKeepSaveLoadInTheEditor() {
        List<String> labels = TerminalView.editorFooterActions().stream()
                .map(TerminalView.FooterAction::label).toList();

        assertFalse(labels.contains("Save"), labels.toString());
        assertFalse(labels.contains("Load"), labels.toString());
    }
}
