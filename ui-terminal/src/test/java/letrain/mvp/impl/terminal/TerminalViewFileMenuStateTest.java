package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import letrain.mvp.FileMenuText;
import letrain.mvp.FileMenuText.Command;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #746: the 2D File menu is contextual. At the boot menu (no game) Save and Settings are
 * disabled with their reason; once a game runs both are enabled (Settings opens the S3b dialog,
 * #764). The rows come from the shared {@link FileMenuText}.
 */
@DisplayName("2D File menu state (#746, settings #764)")
class TerminalViewFileMenuStateTest {

    private static FileMenuText.Item item(List<FileMenuText.Item> items, Command command) {
        return items.stream().filter(i -> i.command() == command).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("without a game: New works and Save/Settings are disabled with the no-game reason")
    void bootMenu_disablesSave() {
        List<FileMenuText.Item> items = TerminalView.fileMenuItems(false);

        assertTrue(item(items, Command.NEW).enabled(), "New starts the first game");
        assertFalse(item(items, Command.SAVE).enabled());
        assertFalse(item(items, Command.SETTINGS).enabled(),
                "there is no running game to configure yet");
        // #802: the disabled state is enough; the reason stays in the item, not in the label.
        assertEquals("Save", FileMenuText.displayLabel(item(items, Command.SAVE)));
        assertEquals("Settings", FileMenuText.displayLabel(item(items, Command.SETTINGS)));
        assertEquals(FileMenuText.SAVE_NO_GAME_REASON, item(items, Command.SAVE).disabledReason());
        assertEquals(FileMenuText.SAVE_NO_GAME_REASON,
                item(items, Command.SETTINGS).disabledReason());
    }

    @Test
    @DisplayName("with a game running every row is actionable, Settings included (#764)")
    void inGame_enablesSaveAndSettings() {
        List<FileMenuText.Item> items = TerminalView.fileMenuItems(true);

        assertTrue(item(items, Command.NEW).enabled());
        assertTrue(item(items, Command.SAVE).enabled());
        assertTrue(item(items, Command.SETTINGS).enabled(),
                "Settings opens the in-game dialog since S3b");
    }
}
