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
 * contextual: at the boot menu (no game) New works and Save is disabled (D8); the 3D client keeps
 * New/Settings disabled with the parity reason until S4.
 */
@DisplayName("File menu wording and states (#743/#746)")
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
    @DisplayName("2D in-game: New, Load, Save and Quit work; Settings waits for its slice")
    void should_DefineTheInGameStates() {
        List<Item> items = terminalItems(true);

        assertTrue(items.get(0).enabled(), "New opens the New Game dialog since S2");
        assertTrue(items.get(1).enabled(), "Load works from the File menu");
        assertTrue(items.get(2).enabled(), "Save works while a game is running");
        assertFalse(items.get(3).enabled(), "Settings arrives with its own slice");
        assertTrue(items.get(4).enabled(), "Quit replaces the old direct Esc exit");
    }

    @Test
    @DisplayName("2D boot menu (no game): New/Load/Quit work and Save is disabled with its reason")
    void should_DefineTheBootMenuStates() {
        List<Item> items = terminalItems(false);

        assertTrue(items.get(0).enabled(), "New starts the first game");
        assertTrue(items.get(1).enabled(), "Load can start the first game too");
        assertFalse(items.get(2).enabled(), "there is no game to save yet (D8)");
        assertEquals("no game in progress", items.get(2).disabledReason());
        assertTrue(items.get(4).enabled(), "Quit works without a game");
    }

    @Test
    @DisplayName("the locked rows carry the owner's exact reasons")
    void should_ShowTheLockReasons() {
        assertEquals("arrives with the settings slice",
                terminalItems(true).get(3).disabledReason());
    }

    @Test
    @DisplayName("3D keeps New and Settings disabled with the parity reason until S4")
    void should_Keep3DParityLocks() {
        List<Item> items = FileMenuText.items(MenuState.graphic());

        assertFalse(items.get(0).enabled(), "New waits for the 3D parity slice");
        assertFalse(items.get(3).enabled(), "Settings waits for the 3D parity slice");
        assertEquals(FileMenuText.PARITY_LOCK_REASON, items.get(0).disabledReason());
        assertEquals(FileMenuText.PARITY_LOCK_REASON, items.get(3).disabledReason());
        assertTrue(items.get(2).enabled(), "Save still works in 3D");
    }

    @Test
    @DisplayName("the player reads the label, and the reason in parentheses while locked")
    void should_RenderTheDisplayLabels() {
        List<Item> items = terminalItems(false);

        assertEquals("New", FileMenuText.displayLabel(items.get(0)));
        assertEquals("Load", FileMenuText.displayLabel(items.get(1)));
        assertEquals("Save (no game in progress)", FileMenuText.displayLabel(items.get(2)));
        assertEquals("Settings (arrives with the settings slice)",
                FileMenuText.displayLabel(items.get(3)));
        assertEquals("Quit", FileMenuText.displayLabel(items.get(4)));

        assertEquals("New (3D parity slice)",
                FileMenuText.displayLabel(FileMenuText.items(MenuState.graphic()).get(0)));
    }
}
