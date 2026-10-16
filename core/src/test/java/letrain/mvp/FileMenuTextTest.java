package letrain.mvp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import letrain.mvp.FileMenuText.Command;
import letrain.mvp.FileMenuText.Item;
import letrain.mvp.FileMenuText.MenuState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #743/#746: the File menu is defined once for both clients. The structure, the words and the
 * states must not drift apart between the 2D terminal and the 3D HUD. Since S2 the 2D menu is
 * contextual: at the boot menu (no game) New works and Save/Settings are disabled (D8); in-game
 * Settings opens the S3b dialog (#764). Since S4 (#777) the 3D menu matches: New opens the 3D New
 * Game dialog, Save follows the game state and Settings stays disabled with the parity reason until
 * S4b.
 */
@DisplayName("File menu wording and states (#743/#746, settings #764)")
class FileMenuTextTest {

    private static List<Item> terminalItems(boolean hasGame) {
        return FileMenuText.items(MenuState.terminal(hasGame));
    }

    @Test
    @DisplayName("the menu is File, with New/Load/Save/Settings/Quit in this order")
    void should_DefineTheMenuStructure() {
        assertEquals("File", FileMenuText.TITLE);
        assertEquals(
                List.of(Command.NEW, Command.LOAD, Command.SAVE, Command.SETTINGS, Command.QUIT),
                terminalItems(true).stream().map(Item::command).toList());
    }

    @Test
    @DisplayName("2D in-game: every row is actionable, Settings included since S3b")
    void should_DefineTheInGameStates() {
        List<Item> items = terminalItems(true);

        assertTrue(items.get(0).enabled(), "New opens the New Game dialog since S2");
        assertTrue(items.get(1).enabled(), "Load works from the File menu");
        assertTrue(items.get(2).enabled(), "Save works while a game is running");
        assertTrue(items.get(3).enabled(), "Settings opens the in-game dialog (#764)");
        assertTrue(items.get(4).enabled(), "Quit replaces the old direct Esc exit");
    }

    @Test
    @DisplayName("2D boot menu (no game): New/Load/Quit work and Save/Settings are disabled")
    void should_DefineTheBootMenuStates() {
        List<Item> items = terminalItems(false);

        assertTrue(items.get(0).enabled(), "New starts the first game");
        assertTrue(items.get(1).enabled(), "Load can start the first game too");
        assertFalse(items.get(2).enabled(), "there is no game to save yet (D8)");
        assertEquals("no game in progress", items.get(2).disabledReason());
        assertFalse(items.get(3).enabled(), "settings need a running game (S3b)");
        assertEquals("no game in progress", items.get(3).disabledReason());
        assertTrue(items.get(4).enabled(), "Quit works without a game");
    }

    @Test
    @DisplayName("the locked rows carry the owner's exact reasons")
    void should_ShowTheLockReasons() {
        assertEquals("no game in progress", terminalItems(false).get(3).disabledReason());
        assertEquals(FileMenuText.PARITY_LOCK_REASON,
                FileMenuText.items(MenuState.graphic(true)).get(3).disabledReason());
    }

    @Test
    @DisplayName("3D in-game: New opens the dialog and Save works; Settings waits for S4b")
    void should_Define3DInGameStates() {
        List<Item> items = FileMenuText.items(MenuState.graphic(true));

        assertTrue(items.get(0).enabled(), "New opens the 3D New Game dialog since S4 (#777)");
        assertTrue(items.get(1).enabled(), "Load works from the File menu");
        assertTrue(items.get(2).enabled(), "Save works while a game is running");
        assertFalse(items.get(3).enabled(), "Settings waits for the S4b slice");
        assertEquals(FileMenuText.PARITY_LOCK_REASON, items.get(3).disabledReason());
        assertTrue(items.get(4).enabled(), "Quit replaces the old direct Esc exit");
    }

    @Test
    @DisplayName("3D boot menu (no game): New/Load/Quit work and Save is disabled; Settings blocked")
    void should_Define3DBootStates() {
        List<Item> items = FileMenuText.items(MenuState.graphic(false));

        assertTrue(items.get(0).enabled(), "New starts the first game (S4, #777)");
        assertTrue(items.get(1).enabled(), "Load can start the first game too");
        assertFalse(items.get(2).enabled(), "there is no game to save yet (D8)");
        assertEquals("no game in progress", items.get(2).disabledReason());
        assertFalse(items.get(3).enabled(), "Settings waits for the S4b slice");
        assertEquals(FileMenuText.PARITY_LOCK_REASON, items.get(3).disabledReason());
        assertTrue(items.get(4).enabled(), "Quit works without a game");
    }

    @Test
    @DisplayName("the player reads the label, and the reason in parentheses while locked")
    void should_RenderTheDisplayLabels() {
        List<Item> items = terminalItems(false);

        assertEquals("New", FileMenuText.displayLabel(items.get(0)));
        assertEquals("Load", FileMenuText.displayLabel(items.get(1)));
        assertEquals("Save (no game in progress)", FileMenuText.displayLabel(items.get(2)));
        assertEquals("Settings (no game in progress)", FileMenuText.displayLabel(items.get(3)));
        assertEquals("Quit", FileMenuText.displayLabel(items.get(4)));

        assertEquals("Settings (3D parity slice)",
                FileMenuText.displayLabel(FileMenuText.items(MenuState.graphic(false)).get(3)));
    }
}
