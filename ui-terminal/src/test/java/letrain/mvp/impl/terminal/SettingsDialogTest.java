package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import letrain.mvp.SettingsText;
import letrain.settings.SettingCategory;
import letrain.settings.SettingDefinition;
import letrain.settings.SettingsApplyResult;
import letrain.settings.SettingsCatalog;
import letrain.time.GameClock;
import letrain.time.GameTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #764 (S3b): the pure pieces of the in-game Settings dialog. The field states, the footer
 * wrapping, the REAPPLY confirmation set, the state-mark column and the rejection message are
 * functions of the catalogue and the terminal width, so they are pinned here without opening a
 * terminal. Issue #767 removes the inline reasons and the white legend entry, shortens the new-game
 * button and takes the terrain block (pinned in {@code NewGameDialogTest}).
 */
@DisplayName("In-game Settings dialog (#764, polish #767)")
class SettingsDialogTest {

    @Test
    @DisplayName("LIVE/REAPPLY are editable; NEW_GAME and scenario-locked are not (#764)")
    void should_MapFieldStates() {
        Set<String> noScenario = Set.of();
        assertEquals(SettingsDialog.FieldState.NEW_GAME, SettingsDialog
                .fieldState(SettingsCatalog.definition("seed").orElseThrow(), noScenario));
        assertEquals(SettingsDialog.FieldState.NEW_GAME, SettingsDialog.fieldState(
                SettingsCatalog.definition("terrain.waterPercent").orElseThrow(), noScenario));
        assertEquals(SettingsDialog.FieldState.LIVE, SettingsDialog.fieldState(
                SettingsCatalog.definition("startingBalance").orElseThrow(), noScenario));
        assertEquals(SettingsDialog.FieldState.REAPPLY, SettingsDialog.fieldState(
                SettingsCatalog.definition("world.latitude").orElseThrow(), noScenario));

        assertTrue(SettingsDialog.FieldState.LIVE.editable());
        assertTrue(SettingsDialog.FieldState.REAPPLY.editable());
        assertFalse(SettingsDialog.FieldState.NEW_GAME.editable());
        assertFalse(SettingsDialog.FieldState.SCENARIO.editable());
    }

    @Test
    @DisplayName("the scenario lock wins over the catalogue state, with no colour of its own (#764/#767)")
    void should_LockScenarioKeys() {
        Set<String> scenario = Set.of("startingBalance");
        assertEquals(SettingsDialog.FieldState.SCENARIO,
                SettingsDialog.fieldState(
                        SettingsCatalog.definition("startingBalance").orElseThrow(), scenario),
                "a scenario-fixed key is locked even if the catalogue says LIVE");
        assertEquals(TerminalView.MENU_DISABLED_FG_COLOR,
                SettingsDialog.FieldState.SCENARIO.color(),
                "scenario fields render disabled, without their own colour");
        assertEquals(
                List.of(SettingsDialog.FieldState.LIVE, SettingsDialog.FieldState.REAPPLY,
                        SettingsDialog.FieldState.NEW_GAME),
                SettingsDialog.legendStates(), "the white legend entry is gone (#767)");
    }

    @Test
    @DisplayName("every catalogue field gets a state and a glyph (#764)")
    void should_StateEveryField() {
        for (SettingDefinition definition : SettingsCatalog.definitions()) {
            SettingsDialog.FieldState state = SettingsDialog.fieldState(definition, Set.of());
            assertNotNull(state, definition.key());
            assertFalse(state.glyph().isBlank(), definition.key());
            assertNotNull(state.color(), definition.key());
        }
    }

    @Test
    @DisplayName("70x20 fits the five buttons in two rows (order kept); 120x30 in one (#764)")
    void should_WrapTheFooter() {
        assertEquals("New game", SettingsText.NEW_GAME, "short label since #767");

        List<List<SettingsDialog.ActionButton>> narrow = SettingsDialog.footerRows(70);
        assertEquals(1, narrow.size(), "the short label makes the five buttons fit in one row");
        assertEquals(SettingsDialog.ACTIONS, narrow.get(0));

        List<List<SettingsDialog.ActionButton>> tiny = SettingsDialog.footerRows(50);
        assertEquals(2, tiny.size(), "still wraps keeping the order");
        assertEquals(
                List.of(SettingsDialog.APPLY_ID, SettingsDialog.RESTORE_ID,
                        SettingsDialog.NEW_GAME_ID),
                tiny.get(0).stream().map(SettingsDialog.ActionButton::id).toList());
        assertEquals(List.of(SettingsDialog.SAVE_DEFAULTS_ID, SettingsDialog.CLOSE_ID),
                tiny.get(1).stream().map(SettingsDialog.ActionButton::id).toList());
        for (List<SettingsDialog.ActionButton> row : tiny) {
            int width = row.stream().mapToInt(button -> button.label().length() + 4).sum()
                    + row.size() - 1;
            assertTrue(width <= 50, "row too wide for 50 columns: " + width);
        }

        assertEquals(1, SettingsDialog.footerRows(120).size());
    }

    @Test
    @DisplayName("only changed REAPPLY fields ask for the Apply confirmation (#764)")
    void should_DetectChangedReapplyKeys() {
        Map<String, String> baseline =
                Map.of("world.latitude", "40.0", "startingBalance", "0.0", "map.VIEW_RADIUS", "15");

        assertEquals(List.of("world.latitude"), SettingsDialog.changedReapplyKeys(baseline,
                Map.of("world.latitude", "50.0", "startingBalance", "100.0")));
        assertEquals(List.of(),
                SettingsDialog.changedReapplyKeys(baseline,
                        Map.of("world.latitude", " 40.0 ", "map.VIEW_RADIUS", "15")),
                "whitespace and LIVE changes do not confirm");
        assertEquals(List.of("world.latitude", "map.VIEW_RADIUS"),
                SettingsDialog.changedReapplyKeys(baseline,
                        Map.of("world.latitude", "41", "map.VIEW_RADIUS", "20")));
    }

    @Test
    @DisplayName("the value column adds the state mark but keeps the page column (#764)")
    void should_AlignWithTheStateMark() {
        List<String> terrain = NewGameDialog.fieldNames(SettingCategory.TERRAIN);
        int column = SettingsDialog.valueColumn(terrain);
        assertEquals(NewGameDialog.valueColumn(terrain) + SettingsDialog.STATE_COLUMNS, column,
                "the glyph prefix moves the column by two cells");
        String label = SettingsDialog.alignedLabel("Seed", column);
        assertEquals(column - SettingsDialog.STATE_COLUMNS, label.length());
        assertTrue(label.startsWith("Seed:"));
        assertTrue(label.endsWith(" "));
    }

    @Test
    @DisplayName("collect reads only the editable widgets when applying (#764)")
    void should_CollectEditableValues() {
        Map<String, Supplier<String>> readers = new LinkedHashMap<>();
        readers.put("startingBalance", () -> "10");
        readers.put("seed", () -> "7");
        Map<String, Boolean> editable = Map.of("startingBalance", true);

        assertEquals(Map.of("startingBalance", "10"),
                SettingsDialog.collect(readers, editable, true));
        assertEquals(Map.of("startingBalance", "10", "seed", "7"),
                SettingsDialog.collect(readers, editable, false),
                "the new-game prefill keeps the disabled fields too");
    }

    @Test
    @DisplayName("the live clock fields validate range and the game-start guard (#769)")
    void should_ValidateClockTargets() {
        GameTime start = GameTime.ofMinuteOfDay(2, 10 * 60); // D2 10:00

        assertEquals("5", SettingsDialog.normalizeDayTarget("5", "12:30", start));
        assertEquals("2", SettingsDialog.normalizeDayTarget(" 2 ", "10:00", start),
                "the exact start instant is allowed");
        assertNull(SettingsDialog.normalizeDayTarget("1", "12:30", start),
                "day before the start day");
        assertNull(SettingsDialog.normalizeDayTarget("0", "12:30", start), "day must be ≥ 1");
        assertNull(SettingsDialog.normalizeDayTarget("abc", "12:30", start));
        assertNull(SettingsDialog.normalizeDayTarget("2", "09:59", start),
                "same day, before the start time");
        assertEquals("2", SettingsDialog.normalizeDayTarget("2", "bad", start),
                "an invalid sibling does not block the day (the time field reports it)");

        assertEquals("12:30", SettingsDialog.normalizeTimeTarget("5", "12:30", start));
        assertEquals("10:00", SettingsDialog.normalizeTimeTarget(" 2 ", "10:00", start));
        assertEquals("07:05", SettingsDialog.normalizeTimeTarget("5", "7:05", start),
                "H:MM normalises to HH:MM");
        assertNull(SettingsDialog.normalizeTimeTarget("5", "24:00", start));
        assertNull(SettingsDialog.normalizeTimeTarget("5", "12:60", start));
        assertNull(SettingsDialog.normalizeTimeTarget("5", "abc", start));
        assertNull(SettingsDialog.normalizeTimeTarget("2", "09:00", start),
                "earlier than the game start");
    }

    @Test
    @DisplayName("the clock tooltips name the range or the start guard (#769)")
    void should_TipTheClockFields() {
        GameTime start = GameTime.ofMinuteOfDay(2, 10 * 60);

        assertEquals(SettingsText.CLOCK_DAY_TOOLTIP,
                SettingsDialog.dayTooltip("0", "12:00", start));
        assertEquals(SettingsText.CLOCK_NOT_BEFORE_START,
                SettingsDialog.dayTooltip("1", "12:00", start));
        assertEquals(SettingsText.CLOCK_TIME_TOOLTIP,
                SettingsDialog.timeTooltip("5", "25:00", start));
        assertEquals(SettingsText.CLOCK_NOT_BEFORE_START,
                SettingsDialog.timeTooltip("2", "09:00", start));
    }

    @Test
    @DisplayName("the game start comes from the Start day/time the tab shows (#769)")
    void should_ReadTheGameStart() {
        assertEquals(GameTime.ofMinuteOfDay(3, 7 * 60 + 30),
                SettingsDialog.gameStart(Map.of("time.startDay", "3", "time.startTime", "07:30")));
        assertEquals(
                GameTime.ofMinuteOfDay(GameClock.DEFAULT_START_DAY,
                        GameClock.DEFAULT_START_MINUTE_OF_DAY),
                SettingsDialog.gameStart(Map.of()), "the catalogue defaults when the map is empty");
    }

    @Test
    @DisplayName("clockChanged compares the target with the snapshot (#769)")
    void should_DetectClockChanges() {
        GameTime snapshot = GameTime.ofMinuteOfDay(1, 8 * 60);

        assertFalse(SettingsDialog.clockChanged(snapshot, GameTime.ofMinuteOfDay(1, 8 * 60)));
        assertTrue(SettingsDialog.clockChanged(snapshot, GameTime.ofMinuteOfDay(1, 8 * 60 + 1)));
        assertTrue(SettingsDialog.clockChanged(snapshot, GameTime.ofMinuteOfDay(2, 8 * 60)));
    }

    @Test
    @DisplayName("the reapply message warns about the clock jump only when it jumps (#769)")
    void should_WarnAboutTheClockJump() {
        String plain = SettingsText.reapplyMessage(List.of("world.latitude"), false);
        assertFalse(plain.contains("clock will jump"));

        String jump = SettingsText.reapplyMessage(List.of("Current day"), true);
        assertTrue(jump.contains("clock will jump"));
        assertTrue(jump.contains("sun and seasons follow"));
    }

    @Test
    @DisplayName("Current day/time are live clock controls, not catalogue fields (#769)")
    void should_KeepTheClockOutOfTheCatalogue() {
        for (SettingDefinition definition : SettingsCatalog.definitions()) {
            assertNotEquals(SettingsText.CURRENT_DAY, definition.displayName(), definition.key());
            assertNotEquals(SettingsText.CURRENT_TIME, definition.displayName(), definition.key());
        }
    }

    @Test
    @DisplayName("the rejection message lists every key with its reason (#764)")
    void should_RenderRejections() {
        Map<String, String> rejections = new LinkedHashMap<>();
        rejections.put("seed", SettingsApplyResult.REQUIRES_NEW_GAME);
        rejections.put("startingBalance", "Setting 'startingBalance' must be ≥ 0.0, got -1.0");

        assertEquals(
                "seed — requires a new game\n"
                        + "startingBalance — Setting 'startingBalance' must be ≥ 0.0, got -1.0",
                SettingsText.rejectedMessage(rejections));
    }
}
