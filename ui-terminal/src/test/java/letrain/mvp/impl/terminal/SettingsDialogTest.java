package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #764 (S3b): the pure pieces of the in-game Settings dialog. The field states, the footer
 * wrapping at 70 columns, the REAPPLY confirmation set, the state-mark column and the rejection
 * message are functions of the catalogue and the terminal width, so they are pinned here without
 * opening a terminal.
 */
@DisplayName("In-game Settings dialog (#764)")
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
    @DisplayName("the scenario lock wins over the catalogue state and brings its reason (#764)")
    void should_LockScenarioKeys() {
        Set<String> scenario = Set.of("startingBalance");
        assertEquals(SettingsDialog.FieldState.SCENARIO,
                SettingsDialog.fieldState(
                        SettingsCatalog.definition("startingBalance").orElseThrow(), scenario),
                "a scenario-fixed key is grey even if the catalogue says LIVE");
        assertEquals(SettingsApplyResult.DEFINED_BY_SCENARIO,
                SettingsDialog.FieldState.SCENARIO.reason());
        assertEquals(SettingsApplyResult.REQUIRES_NEW_GAME,
                SettingsDialog.FieldState.NEW_GAME.reason());
        assertEquals("", SettingsDialog.FieldState.LIVE.reason());
        assertEquals("", SettingsDialog.FieldState.REAPPLY.reason());
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
        List<List<SettingsDialog.ActionButton>> narrow = SettingsDialog.footerRows(70);
        assertEquals(2, narrow.size());
        assertEquals(
                List.of(SettingsDialog.APPLY_ID, SettingsDialog.RESTORE_ID,
                        SettingsDialog.NEW_GAME_ID),
                narrow.get(0).stream().map(SettingsDialog.ActionButton::id).toList());
        assertEquals(List.of(SettingsDialog.SAVE_DEFAULTS_ID, SettingsDialog.CLOSE_ID),
                narrow.get(1).stream().map(SettingsDialog.ActionButton::id).toList());
        for (List<SettingsDialog.ActionButton> row : narrow) {
            int width = row.stream().mapToInt(button -> button.label().length() + 4).sum()
                    + row.size() - 1;
            assertTrue(width <= 70, "row too wide for 70 columns: " + width);
        }

        List<List<SettingsDialog.ActionButton>> wide = SettingsDialog.footerRows(120);
        assertEquals(1, wide.size());
        assertEquals(SettingsDialog.ACTIONS, wide.get(0));
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
