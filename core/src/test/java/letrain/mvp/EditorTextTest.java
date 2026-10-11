package letrain.mvp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import letrain.mvp.Model.GameMode;
import letrain.mvp.Model.GameModeMenuOption;
import letrain.mvp.impl.Model;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #740 (decision D7): the Programming rename and the clarified editor buttons. The wording
 * lives in {@link EditorText} so the 2D terminal and the 3D HUD cannot drift apart.
 */
@DisplayName("Editor wording (#740): Programming tab and clarified buttons")
class EditorTextTest {

    private static GameModeMenuOption programOption() {
        return new Model(1).getMenuModel().stream()
                .filter(option -> option.doWhenSelected().get() == GameMode.PROGRAM).findFirst()
                .orElseThrow();
    }

    @Test
    @DisplayName("the mode menu entry is &Programming, and the retired &Program is gone")
    void should_RenameTheModeMenuEntry() {
        GameModeMenuOption option = programOption();

        assertEquals("&Programming", option.gameModeName());
        assertEquals("Programming", MenuText.parse(option.gameModeName()).plainText());
        assertTrue(new Model(1).getMenuModel().stream()
                .noneMatch(entry -> entry.gameModeName().equals("&Program")));
    }

    @Test
    @DisplayName("the mode description advertises the renamed actions, not the retired wording")
    void should_DescribeTheRenamedActions() {
        String description = programOption().gameModeDescription();

        assertEquals("Scenario editor (Reload from game/Apply program/Rebuild world)", description);
        assertFalse(description.contains("Refresh"), description);
        assertFalse(description.contains("Reprogram"), description);
    }

    @Test
    @DisplayName("the footer buttons carry the D7 names and keep Save/Load/Export/Import/Close")
    void should_WordTheFooterButtonsExactly() {
        assertEquals(
                List.of("Save", "Load", "Export", "Import", "Reload from game", "Apply program",
                        "Rebuild world", "Close"),
                List.of(EditorText.SAVE, EditorText.LOAD, EditorText.EXPORT, EditorText.IMPORT,
                        EditorText.RELOAD_FROM_GAME, EditorText.APPLY_PROGRAM,
                        EditorText.REBUILD_WORLD, EditorText.CLOSE));
    }

    @Test
    @DisplayName("the tab is named Programming; the program { ... } block keeps its DSL name")
    void should_NameTheProgrammingTab() {
        assertEquals("Programming", EditorText.TAB_PROGRAMMING);
        assertEquals(List.of("Scenario", "Programming", "Config"), List.of(EditorText.TAB_SCENARIO,
                EditorText.TAB_PROGRAMMING, EditorText.TAB_CONFIG));
    }

    @Test
    @DisplayName("the rebuild confirmation warns that the current world is discarded")
    void should_WarnBeforeRebuildingTheWorld() {
        assertEquals("Rebuild world", EditorText.REBUILD_CONFIRM_TITLE);
        assertTrue(EditorText.REBUILD_CONFIRM_MESSAGE.contains("discarded"),
                EditorText.REBUILD_CONFIRM_MESSAGE);
    }
}
