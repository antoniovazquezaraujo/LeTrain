package letrain.mvp.impl.graphic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import letrain.mvp.MenuText;
import letrain.mvp.Model;
import letrain.mvp.Model.GameModeMenuOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 3D menu buttons from the shared menu model (2D parity, issue #710). */
@DisplayName("3D menu buttons")
class Gdx3DHudMenuTest {

    /** Strips libGDX markup so the text can be compared with the shared parser output. */
    private static String withoutMarkup(String text) {
        return text.replaceAll("\\[[^]]*\\]", "");
    }

    @Test
    @DisplayName("a middle & paints the shortcut green between white text")
    void should_MarkMiddleShortcutGreen() {
        assertEquals("[WHITE]S[][GREEN]e[][WHITE]nsors[]",
                Gdx3DHud.getMenuButtonText("S&ensors", true));
    }

    @Test
    @DisplayName("a leading & marks the first letter")
    void should_MarkLeadingShortcutGreen() {
        assertEquals("[GREEN]R[][WHITE]ails[]", Gdx3DHud.getMenuButtonText("&Rails", true));
    }

    @Test
    @DisplayName("a disabled option is fully grey, shortcut included")
    void should_GreyDisabledOption() {
        String text = Gdx3DHud.getMenuButtonText("S&ensors", false);

        assertEquals("[GRAY]Sensors[]", text);
        assertFalse(text.contains("[GREEN]"), text);
    }

    @Test
    @DisplayName("a label without & has no coloured shortcut")
    void should_PaintPlainLabel() {
        assertEquals("[WHITE]Program[]", Gdx3DHud.getMenuButtonText("Program", true));
    }

    @Test
    @DisplayName("every shared menu entry shows the plain text of the shared parser")
    void should_ConsumeTheSharedMenuEntries() {
        List<GameModeMenuOption> menu = new letrain.mvp.impl.Model(1).getMenuModel();

        for (GameModeMenuOption option : menu) {
            MenuText.Label label = MenuText.parse(option.gameModeName());
            String text = Gdx3DHud.getMenuButtonText(option.gameModeName(), true);

            assertEquals(label.plainText(), withoutMarkup(text), option.gameModeName());
            if (label.hasHotkey()) {
                assertTrue(text.contains("[GREEN]" + label.hotkey() + "[]"), text);
            }
        }
    }

    @Test
    @DisplayName("the selected-mode description shows at full level only, plus the console")
    void should_MapDescriptionVisibility() {
        assertTrue(Gdx3DHud.showMenuDescription(HudHelp.FULL, Model.GameMode.RAILS));
        assertFalse(Gdx3DHud.showMenuDescription(HudHelp.COMPACT, Model.GameMode.RAILS));
        assertFalse(Gdx3DHud.showMenuDescription(HudHelp.HIDDEN, Model.GameMode.RAILS));
        assertTrue(Gdx3DHud.showMenuDescription(HudHelp.HIDDEN, Model.GameMode.COMMAND),
                "the command line is rendered in the description label");
    }
}
