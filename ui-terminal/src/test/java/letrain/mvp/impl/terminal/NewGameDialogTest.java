package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.googlecode.lanterna.TextColor;
import java.util.List;
import letrain.ground.TerrainPreview;
import letrain.settings.SettingCategory;
import letrain.settings.SettingsCatalog;
import letrain.visitor.terminal.TerminalPalette;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #746: the pure layout pieces of the 2D New Game dialog. The adaptive preview, the 100 %
 * stacked bar, the scrolling column, the catalogue field labels and the preview colours are
 * functions of the terminal size and the proportions, so they are pinned here without opening a
 * terminal. Issue #749 polishes them: square preview, single scrolling column, hotkeys and the
 * brown mountain.
 */
@DisplayName("New Game dialog layout (#746, polish #749)")
class NewGameDialogTest {

    private static final List<String> FIELD_KEYS =
            List.of("terrain.waterPercent", "world.latitude", "map.VIEW_RADIUS", "startingBalance",
                    "delay.NORMAL_TRACK", "ui.highlightBlockedTracks");

    @Test
    @DisplayName("120x30: the preview samples a 46x46 square (23 text rows) with half blocks")
    void should_PlanSquarePreviewAt120x30() {
        NewGameDialog.PreviewPlan plan = NewGameDialog.previewPlan(120, 30);

        assertNotNull(plan);
        assertEquals(46, plan.columns());
        assertEquals(46, plan.sampleRows());
        assertEquals(23, plan.textRows());
    }

    @Test
    @DisplayName("80x24: the preview samples a 34x34 square (17 text rows)")
    void should_PlanSquarePreviewAt80x24() {
        NewGameDialog.PreviewPlan plan = NewGameDialog.previewPlan(80, 24);

        assertNotNull(plan);
        assertEquals(34, plan.columns());
        assertEquals(34, plan.sampleRows());
        assertEquals(17, plan.textRows());
    }

    @Test
    @DisplayName("the sample grid is square at every size: the world keeps its 1:1 cells (#749)")
    void should_KeepTheWorldSquare() {
        int[][] sizes = {{80, 24}, {100, 30}, {120, 30}, {200, 60}};
        for (int[] size : sizes) {
            NewGameDialog.PreviewPlan plan = NewGameDialog.previewPlan(size[0], size[1]);
            assertNotNull(plan, size[0] + "x" + size[1]);
            assertEquals(plan.columns(), plan.sampleRows(),
                    size[0] + "x" + size[1] + " must sample as many rows as columns");
            assertTrue(plan.sampleRows() <= 2 * plan.textRows(),
                    size[0] + "x" + size[1] + " must fit the half-block rows");
            assertTrue(plan.sampleRows() <= size[0] - 4,
                    size[0] + "x" + size[1] + " must respect the preview margin");
            assertTrue(plan.textRows() <= size[1] - 1 - 1 - 5,
                    size[0] + "x" + size[1] + " must not overflow the content area");
        }
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
    @DisplayName("catalogue rows read as label + range, value and unit apart (#749)")
    void should_LabelCatalogueFields() {
        assertEquals("Water (0..95)", label("terrain.waterPercent"));
        assertEquals("%", unit("terrain.waterPercent"));
        assertEquals("Latitude (-90..90)", label("world.latitude"));
        assertEquals("°", unit("world.latitude"));
        assertEquals("View radius (5+)", label("map.VIEW_RADIUS"));
        assertEquals("cells", unit("map.VIEW_RADIUS"));
        assertEquals("Starting balance (0+)", label("startingBalance"));
        assertEquals("", unit("startingBalance"));
        // "ticks" reads in full: the single column has room for it.
        assertEquals("Normal track delay (0+)", label("delay.NORMAL_TRACK"));
        assertEquals("ticks", unit("delay.NORMAL_TRACK"));
        assertEquals("Highlight blocked tracks", label("ui.highlightBlockedTracks"));
        assertEquals("", unit("ui.highlightBlockedTracks"));
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
