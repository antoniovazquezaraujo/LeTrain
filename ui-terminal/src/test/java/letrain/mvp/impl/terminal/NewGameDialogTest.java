package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.PropertyTheme;
import com.googlecode.lanterna.graphics.Theme;
import com.googlecode.lanterna.graphics.ThemeDefinition;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.CheckBox;
import com.googlecode.lanterna.gui2.Direction;
import com.googlecode.lanterna.gui2.Interactable;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.LinearLayout;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.TextBox;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import java.util.Comparator;
import java.util.List;
import java.util.Properties;
import letrain.ground.TerrainPreview;
import letrain.mvp.NewGameText;
import letrain.settings.SettingApplyState;
import letrain.settings.SettingCategory;
import letrain.settings.SettingDefinition;
import letrain.settings.SettingValueType;
import letrain.settings.SettingsCatalog;
import letrain.visitor.terminal.TerminalPalette;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #746: the pure layout pieces of the 2D New Game dialog. The adaptive preview, the 100 %
 * stacked bar, the scrolling column, the catalogue field labels and the preview colours are
 * functions of the terminal size and the proportions, so they are pinned here without opening a
 * terminal. Issue #749 polishes them: square preview, single scrolling column, hotkeys and the
 * brown mountain. Issue #751 drops every range hint, aligns the whole dialog on one value column
 * and rejects invalid commits with a range tooltip. Issue #754 halves the preview, adds the blank
 * row under the tabs and removes the seed button (and Alt+N). Issue #755 gives every page its own
 * value column, flushes the preview left, removes the target/measured line, stretches the stacked
 * bar to the slider row and switches the tabs to letter hotkeys. Issue #757 integrates the tab
 * mnemonics, renames Construction to Build, adds the left margin/map gap, replaces the value on the
 * first keystroke after focus and paints the dialog with a black theme.
 */
@DisplayName("New Game dialog layout (#746, polish #749/#754/#755/#757, field format #751)")
class NewGameDialogTest {

    private static final List<String> FIELD_KEYS =
            List.of("terrain.waterPercent", "world.latitude", "map.VIEW_RADIUS", "startingBalance",
                    "delay.NORMAL_TRACK", "ui.highlightBlockedTracks");
    private static final List<String> TERRAIN_NAMES =
            List.of(NewGameText.SEED_LABEL, NewGameText.WATER_LABEL, NewGameText.MOUNTAIN_LABEL);
    private static final int TERRAIN_COLUMN = NewGameDialog.valueColumn(TERRAIN_NAMES);

    @Test
    @DisplayName("120x30: the preview samples a 22x22 square (11 text rows), a quarter of the area")
    void should_PlanSquarePreviewAt120x30() {
        NewGameDialog.PreviewPlan plan = NewGameDialog.previewPlan(120, 30);

        assertNotNull(plan);
        assertEquals(22, plan.columns());
        assertEquals(22, plan.sampleRows());
        assertEquals(11, plan.textRows());
    }

    @Test
    @DisplayName("80x24: the preview samples a 16x16 square (8 text rows)")
    void should_PlanSquarePreviewAt80x24() {
        NewGameDialog.PreviewPlan plan = NewGameDialog.previewPlan(80, 24);

        assertNotNull(plan);
        assertEquals(16, plan.columns());
        assertEquals(16, plan.sampleRows());
        assertEquals(8, plan.textRows());
    }

    @Test
    @DisplayName("the sample grid is square at every size and never takes over the dialog (#749, #754)")
    void should_KeepTheWorldSquare() {
        int[][] sizes = {{80, 24}, {100, 30}, {120, 30}, {200, 60}};
        for (int[] size : sizes) {
            NewGameDialog.PreviewPlan plan = NewGameDialog.previewPlan(size[0], size[1]);
            assertNotNull(plan, size[0] + "x" + size[1]);
            assertEquals(plan.columns(), plan.sampleRows(),
                    size[0] + "x" + size[1] + " must sample as many rows as columns");
            assertTrue(plan.sampleRows() <= 2 * plan.textRows(),
                    size[0] + "x" + size[1] + " must fit the half-block rows");
            assertTrue(plan.sampleRows() <= (size[0] - 4) / 2,
                    size[0] + "x" + size[1] + " must stay at half the width at most");
            assertTrue(
                    plan.textRows() <= NewGameDialog.sectionRows(size[1])
                            - NewGameDialog.TERRAIN_CHROME_ROWS,
                    size[0] + "x" + size[1] + " must not overflow the content area");
        }
    }

    @Test
    @DisplayName("a field row puts its widget exactly on the page column, with no hidden gap (#757)")
    void should_AlignTheWidgetOnTheColumn() {
        assertEquals(0, NewGameDialog.rowLayout().getSpacing());
        Panel row = new Panel(NewGameDialog.rowLayout());
        row.addComponent(
                new Label(NewGameDialog.alignedLabel(NewGameText.SEED_LABEL, TERRAIN_COLUMN)));
        row.addComponent(new TextBox(new TerminalSize(10, 1), "7"));
        row.getLayoutManager().doLayout(new TerminalSize(40, 1), row.getChildrenList());

        assertEquals(TERRAIN_COLUMN, row.getChildrenList().get(1).getPosition().getColumn(),
                "Lanterna's default 1-column gap must not shift the values");
    }

    @Test
    @DisplayName("every section starts one blank row and one empty column inside (#754/#757)")
    void should_ReserveABlankRowUnderTheTabs() {
        assertEquals(1, NewGameDialog.SECTION_TOP_ROWS);
        assertEquals(1, NewGameDialog.LEFT_MARGIN_COLUMNS, "content floats one column right");
        assertEquals(24 - NewGameDialog.TAB_ROWS - NewGameDialog.FOOTER_ROWS - 1,
                NewGameDialog.sectionRows(24));
        assertEquals(30 - NewGameDialog.TAB_ROWS - NewGameDialog.FOOTER_ROWS - 1,
                NewGameDialog.sectionRows(30));
    }

    @Test
    @DisplayName("below 80x24 there is no preview (sliders and the bar stay)")
    void should_PlanNoPreviewBelowThreshold() {
        int[][] tooSmall = {{79, 24}, {80, 23}, {40, 12}, {0, 0}, {70, 20}};
        for (int[] size : tooSmall) {
            assertNull(NewGameDialog.previewPlan(size[0], size[1]),
                    size[0] + "x" + size[1] + " must not plan a preview");
        }
    }

    @Test
    @DisplayName("the 100 % bar always adds up to the requested width, field is the remainder")
    void should_StackTheBarToTheFullWidth() {
        // Draw order: water, field, mountain.
        assertArrayEqualsInt(new int[] {10, 26, 4}, NewGameDialog.stackedBarWidths(25, 10, 40));
        assertArrayEqualsInt(new int[] {0, 40, 0}, NewGameDialog.stackedBarWidths(0, 0, 40));
        assertArrayEqualsInt(new int[] {38, 2, 0}, NewGameDialog.stackedBarWidths(95, 0, 40));
        // Largest remainder keeps every proportion visible: 33/34/33 over 10 cells.
        assertArrayEqualsInt(new int[] {3, 4, 3}, NewGameDialog.stackedBarWidths(33, 33, 10));
    }

    @Test
    @DisplayName("labels are just the field names: no range hint survives (#751)")
    void should_LabelCatalogueFields() {
        assertEquals("Water", label("terrain.waterPercent"));
        assertEquals("%", unit("terrain.waterPercent"));
        assertEquals("Latitude", label("world.latitude"));
        assertEquals("°", unit("world.latitude"));
        assertEquals("Start day", label("time.startDay"));
        assertEquals("", unit("time.startDay"));
        assertEquals("Start time", label("time.startTime"));
        assertEquals("", unit("time.startTime"));
        assertEquals("View radius", label("map.VIEW_RADIUS"));
        assertEquals("cells", unit("map.VIEW_RADIUS"));
        assertEquals("Starting balance", label("startingBalance"));
        assertEquals("", unit("startingBalance"));
        // "ticks" reads in full: the single column has room for it.
        assertEquals("Normal track delay", label("delay.NORMAL_TRACK"));
        assertEquals("ticks", unit("delay.NORMAL_TRACK"));
        assertEquals("Highlight blocked tracks", label("ui.highlightBlockedTracks"));
        assertEquals("", unit("ui.highlightBlockedTracks"));
        SettingsCatalog.definitions()
                .forEach(definition -> assertFalse(
                        NewGameDialog.fieldLabel(definition).contains("("),
                        definition.key() + " must not carry a range hint"));
    }

    @Test
    @DisplayName("each page aligns its values after its own widest name (#755)")
    void should_AlignTheValueColumnPerPage() {
        for (SettingCategory category : SettingCategory.values()) {
            if (category == SettingCategory.TERRAIN) {
                continue; // the Terrain page shows its own labels (Water %, Mountain %)
            }
            List<String> names = NewGameDialog.fieldNames(category);
            int column = NewGameDialog.valueColumn(names);
            String widest =
                    names.stream().max(Comparator.comparingInt(String::length)).orElseThrow();

            assertEquals(widest.length() + 2, column, category + ": widest name + ':' + space");
            assertEquals(widest + ": ", NewGameDialog.alignedLabel(widest, column),
                    "the widest name keeps a single space before the value");
            for (String name : names) {
                String aligned = NewGameDialog.alignedLabel(name, column);
                assertEquals(column, aligned.length(), name + " must end at the value column");
                assertTrue(aligned.startsWith(name + ":"), name + " keeps the colon glued");
                assertTrue(aligned.endsWith(" "), name + " pads between colon and value");
            }
        }

        // Terrain keeps its own, much closer column: Seed, Water % and Mountain % at 12 (#755).
        assertEquals(12, TERRAIN_COLUMN);
        assertEquals("Seed:" + " ".repeat(7),
                NewGameDialog.alignedLabel(NewGameText.SEED_LABEL, TERRAIN_COLUMN));
        assertTrue(
                TERRAIN_COLUMN < NewGameDialog
                        .valueColumn(NewGameDialog.fieldNames(SettingCategory.UI)),
                "the old global 26-cell column is gone");
    }

    @Test
    @DisplayName("the stacked bar spans the full slider row: 59 cells at 70 columns (#755)")
    void should_SpanTheSliderRow() {
        assertEquals(40, NewGameDialog.sliderBarWidth(70, TERRAIN_COLUMN));
        assertEquals(59, NewGameDialog.stackedBarWidth(70, TERRAIN_COLUMN));
        assertEquals(59, NewGameDialog.stackedBarWidth(120, TERRAIN_COLUMN));
        assertEquals(59, NewGameDialog.stackedBarWidth(200, TERRAIN_COLUMN));
        // Narrow terminals shrink the slider bar and the stacked bar follows it exactly.
        assertEquals(30, NewGameDialog.sliderBarWidth(49, TERRAIN_COLUMN));
        assertEquals(49, NewGameDialog.stackedBarWidth(49, TERRAIN_COLUMN));
    }

    @Test
    @DisplayName("commit validation rejects out-of-range and malformed values (#751)")
    void should_ValidateCommits() {
        SettingDefinition water = SettingsCatalog.definition("terrain.waterPercent").orElseThrow();
        assertEquals("95", NewGameDialog.normalizeCommit(water, " 95 "));
        assertNull(NewGameDialog.normalizeCommit(water, "96"), "above the maximum");
        assertNull(NewGameDialog.normalizeCommit(water, "-1"), "below the minimum");
        assertNull(NewGameDialog.normalizeCommit(water, "abc"), "not a number");

        SettingDefinition latitude = SettingsCatalog.definition("world.latitude").orElseThrow();
        assertEquals("90.0", NewGameDialog.normalizeCommit(latitude, " 90.0 "));
        assertNull(NewGameDialog.normalizeCommit(latitude, "90.5"));

        SettingDefinition seed = SettingsCatalog.definition("seed").orElseThrow();
        assertEquals("777", NewGameDialog.normalizeCommit(seed, " 777 "));
        assertNull(NewGameDialog.normalizeCommit(seed, "random"),
                "the seed box only takes integers once the dialog is open");
        assertNull(NewGameDialog.normalizeCommit(seed, "7.7"));

        SettingDefinition startTime = SettingsCatalog.definition("time.startTime").orElseThrow();
        assertEquals("06:30", NewGameDialog.normalizeCommit(startTime, "6:30"),
                "the commit is zero-padded to HH:MM");
        assertEquals("23:59", NewGameDialog.normalizeCommit(startTime, "23:59"));
        assertNull(NewGameDialog.normalizeCommit(startTime, "24:00"));
        assertNull(NewGameDialog.normalizeCommit(startTime, "12:5"));
    }

    @Test
    @DisplayName("the tooltip names the allowed range: bounded, minimum, maximum (#751)")
    void should_DescribeTheAllowedRange() {
        assertEquals("Allowed: 0..95", NewGameDialog
                .allowedRange(SettingsCatalog.definition("terrain.waterPercent").orElseThrow()));
        assertEquals("Minimum: 5", NewGameDialog
                .allowedRange(SettingsCatalog.definition("map.VIEW_RADIUS").orElseThrow()));
        assertEquals("Maximum: 12",
                NewGameDialog.allowedRange(new SettingDefinition("test.maxOnly",
                        SettingCategory.CONSTRUCTION, "Max only", SettingValueType.INTEGER, null,
                        null, 12.0, "12", SettingApplyState.LIVE)));
        assertEquals("Allowed: whole number",
                NewGameDialog.allowedRange(SettingsCatalog.definition("seed").orElseThrow()));
        assertEquals("Allowed: 00:00..23:59", NewGameDialog
                .allowedRange(SettingsCatalog.definition("time.startTime").orElseThrow()));
        assertEquals("Allowed: 1..366", NewGameDialog
                .allowedRange(SettingsCatalog.definition("time.startDay").orElseThrow()));
    }

    @Test
    @DisplayName("an invalid commit reverts, keeps the focus and opens the tooltip (#751)")
    void should_RevertInvalidCommitsWithATooltip() {
        int column = TERRAIN_COLUMN;
        NewGameDialog.ValidatingTextBox box = new NewGameDialog.ValidatingTextBox(
                SettingsCatalog.definition("terrain.waterPercent").orElseThrow(), "25", column);
        Panel host = new Panel(new LinearLayout(Direction.VERTICAL));
        box.setTooltipHost(host);

        // Typing alone never validates: no tooltip until the commit.
        box.setText("96");
        assertTrue(host.getChildrenList().isEmpty(), "no tooltip while typing");

        assertEquals(Interactable.Result.HANDLED, box.handleKeyStroke(new KeyStroke(KeyType.Tab)),
                "the rejected commit is consumed so the focus stays");
        assertEquals("25", box.getText(), "the previous value comes back");
        Panel tooltipLine = (Panel) host.getChildrenList().get(0);
        assertEquals("Allowed: 0..95",
                ((NewGameDialog.TooltipBox) tooltipLine.getChildrenList().get(1)).text());

        // A valid commit applies, normalises and closes the tooltip.
        box.setText("40");
        assertEquals(Interactable.Result.MOVE_FOCUS_NEXT,
                box.handleKeyStroke(new KeyStroke(KeyType.Enter)),
                "a valid commit follows the normal focus flow");
        assertEquals("40", box.getText());
        assertTrue(host.getChildrenList().isEmpty(), "the tooltip closes on a valid commit");
    }

    @Test
    @DisplayName("the seed box only commits whole numbers; its tooltip says so (#751)")
    void should_RejectNonIntegerSeeds() {
        int column = TERRAIN_COLUMN;
        NewGameDialog.ValidatingTextBox box = new NewGameDialog.ValidatingTextBox(
                SettingsCatalog.definition("seed").orElseThrow(), "777", column);
        Panel host = new Panel(new LinearLayout(Direction.VERTICAL));
        box.setTooltipHost(host);

        box.setText("random");
        assertEquals(Interactable.Result.HANDLED,
                box.handleKeyStroke(new KeyStroke(KeyType.Enter)));
        assertEquals("777", box.getText(), "the previous seed comes back");
        Panel tooltipLine = (Panel) host.getChildrenList().get(0);
        assertEquals("Allowed: whole number",
                ((NewGameDialog.TooltipBox) tooltipLine.getChildrenList().get(1)).text());

        box.setText("  123  ");
        assertEquals(Interactable.Result.MOVE_FOCUS_NEXT,
                box.handleKeyStroke(new KeyStroke(KeyType.Enter)));
        assertEquals("123", box.getText(), "valid seeds are normalised");
        assertTrue(host.getChildrenList().isEmpty());
    }

    @Test
    @DisplayName("every catalogue field has a non-blank label and a real unit or none")
    void should_LabelEveryField() {
        SettingsCatalog.definitions()
                .forEach(definition -> assertFalse(NewGameDialog.fieldLabel(definition).isBlank(),
                        definition.key()));
        assertTrue(
                FIELD_KEYS.stream().allMatch(key -> SettingsCatalog.definition(key).isPresent()));
        assertEquals("%", unit("terrain.mountainPercent"));
    }

    @Test
    @DisplayName("the preview paints the mountain with the game brown at every depth, never white")
    void should_PaintTheMountainBrown() {
        TerminalPalette truecolor = new TerminalPalette(TerminalPalette.Depth.TRUECOLOR);
        assertEquals(new TextColor.RGB(0x80, 0x66, 0x4D),
                NewGameDialog.terrainColor(truecolor, TerrainPreview.ROCK));
        assertEquals(new TextColor.RGB(0x33, 0x66, 0xCC),
                NewGameDialog.terrainColor(truecolor, TerrainPreview.WATER));
        assertEquals(new TextColor.RGB(0x4C, 0xA3, 0x31),
                NewGameDialog.terrainColor(truecolor, TerrainPreview.GROUND));

        assertEquals(new TextColor.Indexed(95),
                NewGameDialog.terrainColor(new TerminalPalette(TerminalPalette.Depth.INDEXED_256),
                        TerrainPreview.ROCK),
                "256 colours take the nearest brown of the cube");
        assertEquals(TextColor.ANSI.RED,
                NewGameDialog.terrainColor(new TerminalPalette(TerminalPalette.Depth.ANSI_16),
                        TerrainPreview.ROCK),
                "16 colours have no brown slot: the 2D map degrades rock to dark red");

        for (TerminalPalette.Depth depth : TerminalPalette.Depth.values()) {
            TextColor rock =
                    NewGameDialog.terrainColor(new TerminalPalette(depth), TerrainPreview.ROCK);
            assertNotEquals(TextColor.ANSI.WHITE, rock, depth + " must never paint white");
            assertNotEquals(TextColor.ANSI.WHITE_BRIGHT, rock, depth + " must never paint white");
            assertNotEquals(new TextColor.RGB(0xFF, 0xFF, 0xFF), rock, depth + " never white");
        }
    }

    @Test
    @DisplayName("scrolling keeps the focused field visible with the smallest move (#749)")
    void should_RevealTheFocusedField() {
        // 20-row viewport over 40 rows: a row below the view scrolls just enough...
        assertEquals(11, NewGameDialog.revealOffset(0, 30, 31, 20, 40));
        // ... and a row above it comes back to the top.
        assertEquals(0, NewGameDialog.revealOffset(11, 0, 1, 20, 40));
        // Already visible: the offset stays put.
        assertEquals(5, NewGameDialog.revealOffset(5, 6, 7, 20, 40));
        // Never past the end.
        assertEquals(20, NewGameDialog.revealOffset(19, 39, 40, 20, 40));
        // Content shorter than the viewport: always at the top.
        assertEquals(0, NewGameDialog.revealOffset(3, 4, 5, 20, 10));
    }

    @Test
    @DisplayName("Alt+T/G/E/B/U pick the tabs; C is Cancel again (#755, Build #757)")
    void should_MapTabHotkeys() {
        List<SettingCategory> tabs = List.of(SettingCategory.TERRAIN, SettingCategory.WORLD,
                SettingCategory.ECONOMY, SettingCategory.CONSTRUCTION, SettingCategory.UI);

        assertEquals(SettingCategory.TERRAIN, NewGameDialog.tabHotkey('t', tabs).orElseThrow());
        assertEquals(SettingCategory.WORLD, NewGameDialog.tabHotkey('g', tabs).orElseThrow());
        assertEquals(SettingCategory.ECONOMY, NewGameDialog.tabHotkey('e', tabs).orElseThrow());
        assertEquals(SettingCategory.CONSTRUCTION,
                NewGameDialog.tabHotkey('b', tabs).orElseThrow());
        assertEquals(SettingCategory.UI, NewGameDialog.tabHotkey('u', tabs).orElseThrow());
        assertEquals(SettingCategory.TERRAIN, NewGameDialog.tabHotkey('T', tabs).orElseThrow(),
                "letters are case-insensitive");
        assertTrue(NewGameDialog.tabHotkey('1', tabs).isEmpty(), "the number shortcuts are gone");
        assertTrue(NewGameDialog.tabHotkey('c', tabs).isEmpty(),
                "C cancels the dialog: Build took B");
        assertTrue(NewGameDialog.tabHotkey('s', tabs).isEmpty(),
                "S starts the game: it is not a tab");

        assertEquals('T', NewGameDialog.tabLetter(SettingCategory.TERRAIN));
        assertEquals('G', NewGameDialog.tabLetter(SettingCategory.WORLD));
        assertEquals('B', NewGameDialog.tabLetter(SettingCategory.CONSTRUCTION));
        assertEquals('U', NewGameDialog.tabLetter(SettingCategory.UI));
        assertEquals("Build", SettingCategory.CONSTRUCTION.displayName());
    }

    @Test
    @DisplayName("Ctrl+Tab cycles the tabs forward and backward, wrapping around (#757)")
    void should_CycleTabs() {
        List<SettingCategory> tabs = List.of(SettingCategory.TERRAIN, SettingCategory.WORLD,
                SettingCategory.ECONOMY, SettingCategory.CONSTRUCTION, SettingCategory.UI);

        assertEquals(SettingCategory.WORLD,
                NewGameDialog.cycleTab(SettingCategory.TERRAIN, tabs, false));
        assertEquals(SettingCategory.UI,
                NewGameDialog.cycleTab(SettingCategory.TERRAIN, tabs, true),
                "reverse wraps to the last tab");
        assertEquals(SettingCategory.TERRAIN,
                NewGameDialog.cycleTab(SettingCategory.UI, tabs, false), "forward wraps");
        assertEquals(SettingCategory.CONSTRUCTION,
                NewGameDialog.cycleTab(SettingCategory.UI, tabs, true));
    }

    @Test
    @DisplayName("the first keystroke after taking focus replaces the whole value (#757)")
    void should_ReplaceTheValueOnFirstKeystroke() {
        NewGameDialog.ValidatingTextBox box = new NewGameDialog.ValidatingTextBox(
                SettingsCatalog.definition("terrain.waterPercent").orElseThrow(), "25",
                TERRAIN_COLUMN);

        box.afterEnterFocus(Interactable.FocusChangeDirection.TELEPORT, null);
        assertEquals(Interactable.Result.HANDLED,
                box.handleKeyStroke(new KeyStroke('7', false, false)));
        assertEquals("7", box.getText(), "the first printable character replaces the value");
        box.handleKeyStroke(new KeyStroke('4', false, false));
        assertEquals("74", box.getText(), "the next characters append normally");

        // A caret movement cancels the emulated selection without erasing anything.
        box.afterEnterFocus(Interactable.FocusChangeDirection.TELEPORT, null);
        box.handleKeyStroke(new KeyStroke(KeyType.End));
        box.handleKeyStroke(new KeyStroke('9', false, false));
        assertEquals("749", box.getText(), "End keeps the value and the digit appends");

        // Backspace while armed clears the field.
        box.afterEnterFocus(Interactable.FocusChangeDirection.TELEPORT, null);
        box.handleKeyStroke(new KeyStroke(KeyType.Backspace));
        assertEquals("", box.getText());
    }

    @Test
    @DisplayName("the dialog theme is white-on-black in every definition (#757)")
    void should_BuildADarkTheme() {
        Theme dark = NewGameDialog.darkTheme(new PropertyTheme(new Properties()));
        for (Class<?> clazz : List.of(Label.class, TextBox.class, CheckBox.class, Button.class,
                Panel.class)) {
            ThemeDefinition definition = dark.getDefinition(clazz);
            assertEquals(TerminalView.NORMAL_MENU_FG_COLOR, definition.getNormal().getForeground(),
                    clazz.getSimpleName() + " normal foreground");
            assertEquals(TerminalView.NORMAL_MENU_BG_COLOR, definition.getNormal().getBackground(),
                    clazz.getSimpleName() + " normal background");
            assertEquals(TerminalView.SELECTED_BG_COLOR, definition.getActive().getBackground(),
                    clazz.getSimpleName() + " focus background");
            assertEquals(TerminalView.MENU_DISABLED_FG_COLOR,
                    definition.getInsensitive().getForeground(),
                    clazz.getSimpleName() + " disabled foreground");
        }
        assertEquals(TerminalView.NORMAL_MENU_BG_COLOR,
                dark.getDefaultDefinition().getNormal().getBackground(), "window backdrop");
    }

    private static String label(String key) {
        return NewGameDialog.fieldLabel(SettingsCatalog.definition(key).orElseThrow());
    }

    private static String unit(String key) {
        return NewGameDialog.fieldUnit(SettingsCatalog.definition(key).orElseThrow());
    }

    private static void assertArrayEqualsInt(int[] expected, int[] actual) {
        org.junit.jupiter.api.Assertions.assertArrayEquals(expected, actual);
    }
}
