package letrain.mvp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import letrain.mvp.Model.GameModeMenuOption;
import letrain.mvp.impl.Model;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The {@code &} shortcut convention of the shared menu model: both clients parse it here, so the
 * format is verified once (issue #710). The clients render the parsed shortcut separated from the
 * name ({@code R:Rails}); {@code displayText()} keeps that wording in one place. Entries may
 * instead declare an explicit shortcut ({@code Planning} + {@code b}) when the letter is not in the
 * name.
 */
@DisplayName("Shared menu text (& shortcut, selected hint)")
class MenuTextTest {

    @Test
    @DisplayName("a leading & marks the first letter as the shortcut")
    void should_ParseLeadingMarker() {
        MenuText.Label label = MenuText.parse("&Rails");

        assertEquals("", label.prefix());
        assertEquals("R", label.hotkey());
        assertEquals("ails", label.suffix());
        assertEquals("Rails", label.plainText());
        assertEquals("R:Rails", label.displayText());
        assertTrue(label.hasHotkey());
    }

    @Test
    @DisplayName("a middle & keeps the letters around it: S&ensors -> S + e + nsors")
    void should_ParseMiddleMarker() {
        MenuText.Label label = MenuText.parse("S&ensors");

        assertEquals("S", label.prefix());
        assertEquals("e", label.hotkey());
        assertEquals("nsors", label.suffix());
        assertEquals("Sensors", label.plainText());
        assertEquals("E:Sensors", label.displayText());
    }

    @Test
    @DisplayName("an explicit shortcut overrides the label and is separated from the name")
    void should_DisplayExplicitShortcut() {
        assertEquals("B:Planning", MenuText.displayText("Planning", "b"));
        assertEquals("B", MenuText.displayHotkey("Planning", "b"));
        assertEquals("R:Rails", MenuText.displayText("&Rails", ""));
    }

    @Test
    @DisplayName("a label without a shortcut keeps its plain text")
    void should_DisplayLabelWithoutShortcut() {
        assertEquals("Program", MenuText.parse("Program").displayText());
    }

    @Test
    @DisplayName("a trailing & leaves the label without a shortcut")
    void should_TolerateTrailingMarker() {
        MenuText.Label label = MenuText.parse("Statio&");

        assertEquals("Statio", label.prefix());
        assertFalse(label.hasHotkey());
        assertEquals("Statio", label.plainText());
    }

    @Test
    @DisplayName("a label without & has no shortcut and keeps its whole text")
    void should_ParseWithoutMarker() {
        MenuText.Label label = MenuText.parse("Program");

        assertEquals("Program", label.prefix());
        assertFalse(label.hasHotkey());
        assertEquals("Program", label.plainText());
    }

    @Test
    @DisplayName("null, empty and malformed labels never break parsing")
    void should_TolerateMalformedLabels() {
        assertEquals(new MenuText.Label("", "", ""), MenuText.parse(null));
        assertEquals(new MenuText.Label("", "", ""), MenuText.parse(""));
        assertEquals(new MenuText.Label("", "", ""), MenuText.parse("&"));
        assertEquals(new MenuText.Label("A", "B", "C"), MenuText.parse("A&B&C"));
    }

    @Test
    @DisplayName("every entry of the shared menu model has exactly one shortcut")
    void should_ShareTheShortcutFormatAcrossTheMenuModel() {
        List<GameModeMenuOption> menu = new Model(1).getMenuModel();

        assertEquals(13, menu.size(), "the menu model is the shared entry list");
        for (GameModeMenuOption option : menu) {
            String hotkey = MenuText.displayHotkey(option.gameModeName(), option.shortcut());
            assertFalse(hotkey.isEmpty(), option.gameModeName());
            assertEquals(1, hotkey.length(), option.gameModeName());
            String plainText = MenuText.parse(option.gameModeName()).plainText();
            assertFalse(plainText.isEmpty(), option.gameModeName());
            assertFalse(plainText.contains("&"), option.gameModeName());
            assertTrue(MenuText.displayText(option.gameModeName(), option.shortcut())
                    .startsWith(hotkey + ":"), option.gameModeName());
        }
    }

    @Test
    @DisplayName("Planning is a real menu entry with the explicit B shortcut")
    void should_AddPlanningWithItsOwnShortcut() {
        GameModeMenuOption planning = new Model(1).getMenuModel().stream()
                .filter(option -> option.doWhenSelected().get() == Model.GameMode.PLANNED_WORKS)
                .findFirst().orElseThrow();

        assertEquals("Planning", MenuText.parse(planning.gameModeName()).plainText());
        assertEquals("b", planning.shortcut());
        assertEquals("B:Planning",
                MenuText.displayText(planning.gameModeName(), planning.shortcut()));
        assertEquals("Plan routes and manage planned works", planning.gameModeDescription());
    }

    @Test
    @DisplayName("the selected-mode hint is worded once, Record state included")
    void should_BuildSelectedHint() {
        assertEquals("desc | [R]:Record ON | [X]:Experiment", MenuText.selectedHint("desc", true));
        assertEquals("desc | [R]:Record OFF | [X]:Experiment",
                MenuText.selectedHint("desc", false));
    }

    @Test
    @DisplayName("every mode hint keeps the [key]:Action format with sentence-case words")
    void should_ShareTheHintStyleAcrossTheMenuModel() {
        for (GameModeMenuOption option : new Model(1).getMenuModel()) {
            String description = option.gameModeDescription();
            assertFalse(description.contains("]: "),
                    "no space between the colon and the action: " + description);
            assertFalse(description.contains("  "),
                    "elements are separated by a single space: " + description);
            assertFalse(description.matches(".*\\S\\[[^]]*\\]:.*"),
                    "missing space before a key element: " + description);
            String outsideKeys = description.replaceAll("\\[[^]]*\\]", "");
            assertFalse(outsideKeys.matches(".*\\b[A-Z]{3,}\\b.*"),
                    "all-caps word outside a key: " + description);
        }
    }

    @Test
    @DisplayName("the TRAINS hint uses the agreed wording: [A-Z]:Locomotive | [a-z]:Wagon | [Enter]:Finish")
    void should_WordTheTrainsHint() {
        String trainsHint = new Model(1).getMenuModel().stream()
                .filter(option -> option.gameModeName().equals("&Trains")).findFirst().orElseThrow()
                .gameModeDescription();

        assertEquals("[A-Z]:Locomotive | [a-z]:Wagon | [Enter]:Finish", trainsHint);
    }

    @Test
    @DisplayName("the RAILS hint drops the retired element shortcuts and sends the player to Add mode")
    void should_WordTheRailsHint() {
        String railsHint = new Model(1).getMenuModel().stream()
                .filter(option -> option.gameModeName().equals("&Rails")).findFirst().orElseThrow()
                .gameModeDescription();

        assertEquals("[⏴⏵⏶⏷/hjkl]:Move [Shift]:Add rail [a]:Add mode [#]:Steps [Space]:Reset steps",
                railsHint);
        assertTrue(railsHint.contains("[a]:Add mode"),
                "placing elements goes through Add mode now");
        for (String retired : List.of("[Ctrl]", "[Ins]", "[Home]", "[Del]", "[End]")) {
            assertFalse(railsHint.contains(retired),
                    retired + " is retired and must not be advertised: " + railsHint);
        }
    }
}
