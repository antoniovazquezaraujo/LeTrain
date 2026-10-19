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
    @DisplayName("#803: every 3D row tints the shared N/L/S/E/Q mnemonic")
    void should_TintTheSharedMnemonics() {
        List<FileMenuText.Item> items = FileMenuText.items(FileMenuText.MenuState.graphic(false));
        List<String> expected = List.of("[GREEN]N[]ew", "[GREEN]L[]oad", "[GREEN]S[]ave",
                "S[GREEN]e[]ttings", "[GREEN]Q[]uit");

        for (int i = 0; i < items.size(); i++) {
            FileMenuText.Item item = items.get(i);
            assertEquals(expected.get(i),
                    Gdx3DHud.settingsHotkeyLabel(Gdx3DHud.fileMenuButtonText(item),
                            FileMenuText.mnemonic(item.command())),
                    item.command() + " mnemonic");
        }
    }

    @Test
    @DisplayName("#803: confirm buttons take their first non-C letter, C stays Cancel")
    void should_ChooseConfirmMnemonics() {
        assertEquals('D', Gdx3DHud.confirmMnemonic("Discard changes?"));
        assertEquals('R', Gdx3DHud.confirmMnemonic("Rebuild world"));
        assertEquals('A', Gdx3DHud.confirmMnemonic("Cancel"),
                "C is reserved for the Cancel button");
        assertEquals('Y', Gdx3DHud.confirmMnemonic("CCC"), "no usable letter falls back to Y");
        assertEquals('Y', Gdx3DHud.confirmMnemonic(""));
        assertEquals('Y', Gdx3DHud.confirmMnemonic(null));
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
