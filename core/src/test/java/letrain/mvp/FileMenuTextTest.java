package letrain.mvp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import letrain.mvp.FileMenuText.Command;
import letrain.mvp.FileMenuText.Item;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #743: the File menu is defined once for both clients. The structure, the words and the lock
 * states must not drift apart between the 2D terminal and the 3D HUD.
 */
@DisplayName("File menu wording and states (#743)")
class FileMenuTextTest {

    @Test
    @DisplayName("the menu is File, with New/Load/Save/Settings/Quit in this order")
    void should_DefineTheMenuStructure() {
        assertEquals("File", FileMenuText.TITLE);
        assertEquals(
                List.of(Command.NEW, Command.LOAD, Command.SAVE, Command.SETTINGS, Command.QUIT),
                FileMenuText.items().stream().map(Item::command).toList());
    }

    @Test
    @DisplayName("Load, Save and Quit work; New and Settings are visible but locked")
    void should_DefineTheEnabledStates() {
        List<Item> items = FileMenuText.items();

        assertFalse(items.get(0).enabled(), "New waits for the New Game settings flow");
        assertTrue(items.get(1).enabled(), "Load works from the File menu");
        assertTrue(items.get(2).enabled(), "Save works from the File menu");
        assertFalse(items.get(3).enabled(), "Settings arrives with its own slice");
        assertTrue(items.get(4).enabled(), "Quit replaces the old direct Esc exit");
    }

    @Test
    @DisplayName("the locked rows carry the owner's exact reasons")
    void should_ShowTheLockReasons() {
        List<Item> items = FileMenuText.items();

        assertEquals("settings arrive in the next slice", items.get(0).disabledReason());
        assertEquals("arrives with the settings slice", items.get(3).disabledReason());
    }

    @Test
    @DisplayName("the player reads the label, and the reason in parentheses while locked")
    void should_RenderTheDisplayLabels() {
        List<Item> items = FileMenuText.items();

        assertEquals("New (settings arrive in the next slice)",
                FileMenuText.displayLabel(items.get(0)));
        assertEquals("Load", FileMenuText.displayLabel(items.get(1)));
        assertEquals("Save", FileMenuText.displayLabel(items.get(2)));
        assertEquals("Settings (arrives with the settings slice)",
                FileMenuText.displayLabel(items.get(3)));
        assertEquals("Quit", FileMenuText.displayLabel(items.get(4)));
    }
}
