package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.googlecode.lanterna.TerminalSize;
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
    @DisplayName("70 columns fits the five buttons in one row; 50 wraps in two (order kept) (#764)")
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
    @DisplayName("80x24 gets a 70x20 content box (72x22 framed), one footer row and a preview that fits (#742 polish)")
    void should_SizeTheDialogAt80x24() {
        TerminalSize screen = new TerminalSize(80, 24);
        TerminalSize dialog = DialogWindow.dialogSize(screen);

        assertEquals(new TerminalSize(70, 20), dialog, "content box");
        assertEquals(new TerminalSize(72, 22),
                dialog.withRelative(DialogWindow.DECORATION_COLUMNS * 2,
                        DialogWindow.DECORATION_ROWS * 2),
                "the framed box is 72x22");
        assertTrue(
                dialog.getColumns() + 2 * DialogWindow.DECORATION_COLUMNS <= screen.getColumns()
                        - 2 * DialogWindow.MARGIN_COLUMNS,
                "the framed box keeps at least the horizontal margins");
        assertEquals(screen.getRows() - 2 * DialogWindow.MARGIN_ROWS,
                dialog.getRows() + 2 * DialogWindow.DECORATION_ROWS,
                "the framed box keeps the vertical margins");
        assertEquals(1, SettingsDialog.footerRows(dialog.getColumns()).size(),
                "the five buttons still fit in one row at 70 columns");

        TerminalSize terrain = SettingsDialog.terrainSize(dialog,
                SettingsDialog.footerRows(dialog.getColumns()).size());
        assertEquals(new TerminalSize(70, 18), terrain);
        // Settings lifts New Game's whole-terminal minimums: the plan is computed for the box.
        NewGameDialog.PreviewPlan plan =
                NewGameDialog.previewPlan(terrain.getColumns(), terrain.getRows(), 0, 0);
        assertNotNull(plan, "the preview still has room at the common terminal size");
        assertEquals(10, plan.sampleRows(), "a 10x10 sample (5 text rows) at 80x24");
        int sectionBody =
                dialog.getRows() - SettingsDialog.TAB_ROWS - SettingsDialog.SECTION_TOP_ROWS - 1
                        - SettingsDialog.LEGEND_ROWS - SettingsDialog.FEEDBACK_ROWS;
        assertEquals(15, sectionBody, "tabs + blank + three footer rows leave 15 body rows");
        assertTrue(NewGameDialog.TERRAIN_CHROME_ROWS + plan.textRows() <= sectionBody,
                "no preview row is hidden behind the framed box");
    }

    @Test
    @DisplayName("the box fits the screen and keeps margins with room, never collapsing (#742 polish)")
    void should_KeepTheDialogInsideTheScreen() {
        int[][] screens = {{1, 1}, {56, 20}, {69, 21}, {70, 22}, {80, 24}, {100, 30}, {200, 60}};
        TerminalSize[] expected = {new TerminalSize(1, 1), new TerminalSize(50, 16),
                new TerminalSize(63, 17), new TerminalSize(64, 18), new TerminalSize(70, 20),
                new TerminalSize(70, 26), new TerminalSize(100, 30)};

        for (int i = 0; i < screens.length; i++) {
            TerminalSize screen = new TerminalSize(screens[i][0], screens[i][1]);
            TerminalSize dialog = DialogWindow.dialogSize(screen);
            String label = screen.getColumns() + "x" + screen.getRows();

            assertEquals(expected[i], dialog, label);
            assertTrue(dialog.getColumns() > 0 && dialog.getRows() > 0,
                    label + " must never get a non-positive dimension");
            if (screen.getColumns() > 2 * DialogWindow.DECORATION_COLUMNS) {
                assertTrue(dialog.getColumns() + 2 * DialogWindow.DECORATION_COLUMNS <= screen
                        .getColumns(), label + " must not be wider than the terminal");
            }
            if (screen.getRows() > 2 * DialogWindow.DECORATION_ROWS) {
                assertTrue(dialog.getRows() + 2 * DialogWindow.DECORATION_ROWS <= screen.getRows(),
                        label + " must not be taller than the terminal");
            }
            if (screen.getColumns() >= DialogWindow.MIN_COLUMNS + 2 * DialogWindow.MARGIN_COLUMNS) {
                assertTrue(
                        dialog.getColumns() + 2 * DialogWindow.DECORATION_COLUMNS <= screen
                                .getColumns() - 2 * DialogWindow.MARGIN_COLUMNS,
                        label + " must keep the horizontal margins");
            }
            if (screen.getRows() >= DialogWindow.MIN_ROWS + 2 * DialogWindow.MARGIN_ROWS) {
                assertTrue(
                        dialog.getRows() + 2 * DialogWindow.DECORATION_ROWS <= screen.getRows()
                                - 2 * DialogWindow.MARGIN_ROWS,
                        label + " must keep the vertical margins");
            }

            int footerButtonRows = SettingsDialog.footerRows(dialog.getColumns()).size();
            TerminalSize terrain = SettingsDialog.terrainSize(dialog, footerButtonRows);
            NewGameDialog.PreviewPlan plan =
                    NewGameDialog.previewPlan(terrain.getColumns(), terrain.getRows(), 0, 0);
            int sectionBody = dialog.getRows() - SettingsDialog.TAB_ROWS
                    - SettingsDialog.SECTION_TOP_ROWS - footerButtonRows
                    - SettingsDialog.LEGEND_ROWS - SettingsDialog.FEEDBACK_ROWS;
            if (plan != null) {
                assertTrue(NewGameDialog.TERRAIN_CHROME_ROWS + plan.textRows() <= sectionBody,
                        label + " would hide preview rows behind the frame");
            }
        }

        // TerminalSize accepts 0x0 although a real screen never reports it: the geometry still
        // falls back to a positive 1x1 box instead of zero or negative dimensions.
        assertEquals(new TerminalSize(1, 1), DialogWindow.dialogSize(new TerminalSize(0, 0)));
    }

    @Test
    @DisplayName("large and tall terminals keep a bounded, centered box (#742 polish)")
    void should_CapTheDialogOnLargeTerminals() {
        TerminalSize large = DialogWindow.dialogSize(new TerminalSize(200, 60));
        assertEquals(new TerminalSize(100, 30), large);
        assertTrue(large.getColumns() <= DialogWindow.MAX_COLUMNS,
                "the width stays capped instead of reaching the screen edges");
        assertTrue(large.getRows() <= DialogWindow.MAX_ROWS,
                "the height stays capped on tall terminals");

        // 120x30: the minimum width and the available height minus the decoration and margins.
        assertEquals(new TerminalSize(70, 26), DialogWindow.dialogSize(new TerminalSize(120, 30)));
        // Half-width caps the box between the minimum and MAX_COLUMNS.
        assertEquals(80, DialogWindow.dialogSize(new TerminalSize(160, 30)).getColumns());

        // A terminal below the minimum still gets a box no larger than the screen.
        TerminalSize tiny = DialogWindow.dialogSize(new TerminalSize(60, 20));
        assertEquals(new TerminalSize(54, 16), tiny);
        assertTrue(SettingsDialog.footerRows(tiny.getColumns()).size() > 1,
                "narrow dialogs wrap the footer instead of shrinking the frame");
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
