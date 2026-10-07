package letrain.mvp.impl.graphic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import letrain.mvp.MenuText;
import letrain.mvp.Model;
import letrain.mvp.Model.GameModeMenuOption;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
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
    @DisplayName("a disabled option is grey #808080 and loses the green shortcut")
    void should_PaintDisabledOptionInGrey() {
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

    @Test
    @DisplayName("the bottom menu block rows follow the 2D menu-box order")
    void should_OrderMenuBlockRowsLikeThe2dMenuBox() {
        assertEquals(List.of(Gdx3DHud.MenuRow.MENU, Gdx3DHud.MenuRow.TRAIN, Gdx3DHud.MenuRow.HINT,
                Gdx3DHud.MenuRow.KEYS), Gdx3DHud.MENU_BLOCK_ROWS);
    }

    @Test
    @DisplayName("the key row lists real 3D bindings in the 2D wording (sentence case, no fakes)")
    void should_ListReal3dKeys() {
        String keys = Gdx3DHud.keysText();

        assertEquals(
                "[Alt+▲▼ / Mouse Wheel]: Zoom | [Alt+◀▶]: Rotate | [z/Z]: Camera"
                        + " | [a/r/d/f/s/t/c/u/p/n]: Modes | [Tab]: Toggle Panel | [Esc]: Exit",
                keys);
        assertFalse(keys.contains("[PgUp"), "3D has no page-scroll binding");
        assertFalse(keys.contains("ZOOM") || keys.contains("ROTATE"),
                "actions are sentence case, not all caps: " + keys);
    }

    @Test
    @DisplayName("the status row keeps the 2D format: Train, notch bar, speed and wagons")
    void should_FormatTrainStatusLikeThe2dClient() {
        Locomotive loco = new Locomotive(7, "A");
        loco.setCurrentSpeed(3);
        loco.setTargetSpeed(5);
        Train train = new Train(1);
        train.pushBack(loco);

        String status = Gdx3DHud.trainStatusText(loco);

        assertEquals(
                "Train: 1 | Speed: [GREEN]■[][GREEN]■[][GREEN]■[]□[RED]■[]□□□□□ 3->5 | Wagons: 0",
                status);
    }

    @Test
    @DisplayName("no selected train means no status row")
    void should_FormatEmptyStatusWithoutSelection() {
        assertEquals("", Gdx3DHud.trainStatusText(null));
    }

    @Test
    @DisplayName("the notch bar marks current speed green, target red and empties grey")
    void should_MarkNotchBar() {
        assertEquals("[GREEN]■[][GREEN]■[]□□□□□□□□", Gdx3DHud.notchBar(2, 2, 10));
        assertEquals("[GREEN]■[]□[RED]■[]□□□□□□□", Gdx3DHud.notchBar(1, 3, 10));
    }
}
