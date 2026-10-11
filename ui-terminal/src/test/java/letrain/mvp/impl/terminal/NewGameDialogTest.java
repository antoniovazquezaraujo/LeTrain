package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import letrain.settings.SettingsCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #746: the pure layout pieces of the 2D New Game dialog. The adaptive preview, the 100 %
 * stacked bar and the catalogue field labels are functions of the terminal size and the
 * proportions, so they are pinned here without opening a terminal.
 */
@DisplayName("New Game dialog layout (#746)")
class NewGameDialogTest {

    private static final List<String> FIELD_KEYS =
            List.of("terrain.waterPercent", "world.latitude", "map.VIEW_RADIUS", "startingBalance",
                    "delay.NORMAL_TRACK", "ui.highlightBlockedTracks");

    @Test
    @DisplayName("120x30 gets the large preview: full width and every free row, half blocks")
    void should_PlanLargePreviewAt120x30() {
        NewGameDialog.PreviewPlan plan = NewGameDialog.previewPlan(120, 30);

        assertNotNull(plan);
        assertTrue(plan.large(), "120x30 is the large threshold");
        assertEquals(116, plan.columns());
        // 30 rows - header (6) - footer (1) = 23 text rows, two sample rows each.
        assertEquals(46, plan.sampleRows());
        assertEquals(23, plan.textRows());
    }

    @Test
    @DisplayName("80x24 gets the compact preview: 76 columns and 17 text rows (~76x34 samples)")
    void should_PlanCompactPreviewAt80x24() {
        NewGameDialog.PreviewPlan plan = NewGameDialog.previewPlan(80, 24);

        assertNotNull(plan);
        assertFalse(plan.large());
        assertEquals(76, plan.columns());
        assertEquals(34, plan.sampleRows());
        assertEquals(17, plan.textRows());
    }

    @Test
    @DisplayName("below 80x24 there is no preview (target/measured still shown)")
    void should_PlanNoPreviewBelowThreshold() {
        int[][] tooSmall = {{79, 24}, {80, 23}, {40, 12}, {0, 0}};
        for (int[] size : tooSmall) {
            assertNull(NewGameDialog.previewPlan(size[0], size[1]),
                    size[0] + "x" + size[1] + " must not plan a preview");
        }
    }

    @Test
    @DisplayName("large needs both dimensions: 120x29 and 119x30 stay compact")
    void should_KeepCompactJustBelowLarge() {
        assertFalse(NewGameDialog.previewPlan(120, 29).large());
        assertFalse(NewGameDialog.previewPlan(119, 30).large());
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
    @DisplayName("catalogue fields read with their range and unit (no frills)")
    void should_LabelCatalogueFields() {
        assertEquals("Water (0..95 %)", label("terrain.waterPercent"));
        assertEquals("Latitude (-90..90 °)", label("world.latitude"));
        assertEquals("View radius (5+ cells)", label("map.VIEW_RADIUS"));
        assertEquals("Starting balance (0+)", label("startingBalance"));
        assertEquals("Normal track delay (0+ t)", label("delay.NORMAL_TRACK"));
        assertEquals("Highlight blocked tracks", label("ui.highlightBlockedTracks"));
    }

    @Test
    @DisplayName("every catalogue field has a non-blank label")
    void should_LabelEveryField() {
        SettingsCatalog.definitions()
                .forEach(definition -> assertFalse(NewGameDialog.fieldLabel(definition).isBlank(),
                        definition.key()));
        assertTrue(
                FIELD_KEYS.stream().allMatch(key -> SettingsCatalog.definition(key).isPresent()));
    }

    private static String label(String key) {
        return NewGameDialog.fieldLabel(SettingsCatalog.definition(key).orElseThrow());
    }

    private static void assertArrayEqualsInt(int[] expected, int[] actual) {
        org.junit.jupiter.api.Assertions.assertArrayEquals(expected, actual);
    }
}
