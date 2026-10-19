package letrain.mvp.impl.graphic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import letrain.mvp.FileMenuText;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #743/#777: the 3D HUD File menu consumes the shared {@link FileMenuText} rows, so the words
 * and the lock reasons read exactly like the 2D terminal's. Since S4 New is enabled (opens the 3D
 * New Game dialog) and since S4b (#778) Settings is enabled in-game too; only the boot menu
 * disables Save and Settings with the shared no-game reason.
 */
@DisplayName("3D File menu rows")
class Gdx3DHudFileMenuTest {

    @Test
    @DisplayName("every shared row shows the same bare words as 2D (#802)")
    void should_ShowTheSharedLabels() {
        List<FileMenuText.Item> items = FileMenuText.items(FileMenuText.MenuState.graphic(false));

        assertEquals("New", Gdx3DHud.fileMenuButtonText(items.get(0)));
        assertEquals("Load", Gdx3DHud.fileMenuButtonText(items.get(1)));
        assertEquals("Save", Gdx3DHud.fileMenuButtonText(items.get(2)));
        assertEquals("Settings", Gdx3DHud.fileMenuButtonText(items.get(3)));
        assertEquals("Quit", Gdx3DHud.fileMenuButtonText(items.get(4)));
    }

    @Test
    @DisplayName("in-game every row (Settings included) reads its bare label")
    void should_ShowEnabledLabels() {
        List<FileMenuText.Item> items = FileMenuText.items(FileMenuText.MenuState.graphic(true));

        assertEquals("Settings", Gdx3DHud.fileMenuButtonText(items.get(3)));
        assertEquals("Save", Gdx3DHud.fileMenuButtonText(items.get(2)));
    }

    @Test
    @DisplayName("the old Exit-only dialog is gone, not just hidden")
    void should_NotKeepTheOldExitDialog() {
        assertThrows(NoSuchMethodException.class,
                () -> Gdx3DHud.class.getDeclaredMethod("showExitMenu"),
                "Esc now opens the File menu");
        assertThrows(NoSuchFieldException.class,
                () -> Gdx3DHud.class.getDeclaredField("exitDialogOpen"),
                "the exit-dialog flag must stay removed");
    }
}
