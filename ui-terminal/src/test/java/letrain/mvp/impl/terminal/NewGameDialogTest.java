package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.Direction;
import com.googlecode.lanterna.gui2.Interactable;
import com.googlecode.lanterna.gui2.LinearLayout;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;
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
 * row under the tabs and removes the seed button (and Alt+N).
 */
@DisplayName("New Game dialog layout (#746, polish #749/#754, field format #751)")
class NewGameDialogTest {

    private static final List<String> FIELD_KEYS =
            List.of("terrain.waterPercent", "world.latitude", "map.VIEW_RADIUS", "startingBalance",
                    "delay.NORMAL_TRACK", "ui.highlightBlockedTracks");

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
    @DisplayName("every section starts one blank row below the tab bar (#754)")
    void should_ReserveABlankRowUnderTheTabs() {
        assertEquals(1, NewGameDialog.SECTION_TOP_ROWS);
        assertEquals(24 - NewGameDialog.TAB_ROWS - NewGameDialog.FOOTER_ROWS - 1,
                NewGameDialog.sectionRows(24));
        assertEquals(30 - NewGameDialog.TAB_ROWS - NewGameDialog.FOOTER_ROWS - 1,
                NewGameDialog.sectionRows(30));
    }

    @Test
    @DisplayName("below 80x24 there is no preview (target/measured still shown)")
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
    @DisplayName("every value starts after the widest field name of the whole dialog (#751)")
    void should_AlignTheValueColumn() {
        int column = NewGameDialog.fieldValueColumn();
        String widest = Stream
                .concat(SettingsCatalog.definitions().stream().map(SettingDefinition::displayName),
                        Stream.of(NewGameText.SEED_LABEL, NewGameText.WATER_LABEL,
                                NewGameText.MOUNTAIN_LABEL))
                .max(Comparator.comparingInt(String::length)).orElseThrow();

        assertEquals(widest.length() + 2, column, "widest name + ':' + one space");
        assertEquals(widest + ": ", NewGameDialog.alignedLabel(widest, column),
                "the widest name keeps a single space before the value");
        for (String name : List.of(NewGameText.SEED_LABEL, NewGameText.WATER_LABEL,
                NewGameText.MOUNTAIN_LABEL, "Starting balance", widest)) {
            String aligned = NewGameDialog.alignedLabel(name, column);
            assertEquals(column, aligned.length(), name + " must end at the value column");
            assertTrue(aligned.startsWith(name + ":"), name + " keeps the colon glued");
            assertTrue(aligned.endsWith(" "), name + " pads between colon and value");
        }
        assertEquals("Starting balance:" + " ".repeat(column - 17),
                NewGameDialog.alignedLabel("Starting balance", column));
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
    }

    @Test
    @DisplayName("an invalid commit reverts, keeps the focus and opens the tooltip (#751)")
    void should_RevertInvalidCommitsWithATooltip() {
        int column = NewGameDialog.fieldValueColumn();
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
        int column = NewGameDialog.fieldValueColumn();
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
    @DisplayName("Alt+1..5 pick the tabs in order; other keys do not (#749)")
    void should_MapTabHotkeys() {
        List<SettingCategory> tabs = List.of(SettingCategory.TERRAIN, SettingCategory.WORLD,
                SettingCategory.ECONOMY, SettingCategory.CONSTRUCTION, SettingCategory.UI);

        assertEquals(SettingCategory.TERRAIN, NewGameDialog.tabHotkey('1', tabs).orElseThrow());
        assertEquals(SettingCategory.WORLD, NewGameDialog.tabHotkey('2', tabs).orElseThrow());
        assertEquals(SettingCategory.ECONOMY, NewGameDialog.tabHotkey('3', tabs).orElseThrow());
        assertEquals(SettingCategory.CONSTRUCTION,
                NewGameDialog.tabHotkey('4', tabs).orElseThrow());
        assertEquals(SettingCategory.UI, NewGameDialog.tabHotkey('5', tabs).orElseThrow());
        assertTrue(NewGameDialog.tabHotkey('6', tabs).isEmpty(), "past the last tab");
        assertTrue(NewGameDialog.tabHotkey('0', tabs).isEmpty(), "no tab for 0");
        assertTrue(NewGameDialog.tabHotkey('t', tabs).isEmpty(),
                "tabs are numeric: letters stay free for the actions");
    }

    @Test
    @DisplayName("the footer hint lists the Alt keys and no Alt+N anymore (#754)")
    void should_AdvertiseTheAltKeys() {
        assertEquals("Alt+1-5 · Alt+S · Alt+C", NewGameDialog.HOTKEY_HINT);
        assertTrue(NewGameDialog.HOTKEY_HINT.contains("Alt+S"), "Start stays advertised");
        assertTrue(NewGameDialog.HOTKEY_HINT.contains("Alt+C"), "Cancel stays advertised");
        assertFalse(NewGameDialog.HOTKEY_HINT.contains("Alt+N"), "the seed button is gone");
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
