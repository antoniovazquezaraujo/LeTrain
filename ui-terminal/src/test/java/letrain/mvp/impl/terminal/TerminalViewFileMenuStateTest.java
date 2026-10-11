package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import letrain.mvp.FileMenuText;
import letrain.mvp.FileMenuText.Command;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #746: the 2D File menu is contextual. At the boot menu (no game) Save is disabled with its
 * reason; once a game runs it is enabled again. The rows come from the shared {@link FileMenuText}.
 */
@DisplayName("2D File menu state (#746)")
class TerminalViewFileMenuStateTest {

    private static FileMenuText.Item item(List<FileMenuText.Item> items, Command command) {
        return items.stream().filter(i -> i.command() == command).findFirst().orElseThrow();
    }

    @Test
    @DisplayName("without a game: New works and Save is disabled with the no-game reason")
    void bootMenu_disablesSave() {
        List<FileMenuText.Item> items = TerminalView.fileMenuItems(false);

        assertTrue(item(items, Command.NEW).enabled(), "New starts the first game");
        assertFalse(item(items, Command.SAVE).enabled());
        assertTrue(FileMenuText.displayLabel(item(items, Command.SAVE))
                .contains(FileMenuText.SAVE_NO_GAME_REASON));
    }

    @Test
    @DisplayName("with a game running every row but Settings is actionable")
    void inGame_enablesSave() {
        List<FileMenuText.Item> items = TerminalView.fileMenuItems(true);

        assertTrue(item(items, Command.NEW).enabled());
        assertTrue(item(items, Command.SAVE).enabled());
        assertFalse(item(items, Command.SETTINGS).enabled(), "Settings still waits for its slice");
    }
}
