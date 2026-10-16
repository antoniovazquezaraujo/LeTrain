package letrain.mvp.impl.graphic;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.badlogic.gdx.graphics.Color;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import letrain.mvp.SettingsText;
import letrain.settings.SettingCategory;
import letrain.settings.SettingDefinition;
import letrain.settings.SettingFieldState;
import letrain.settings.SettingsCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * S4b (#778): the pure pieces of the 3D in-game Settings dialog. The category order, the field
 * states (scenario lock wins, mirror of 2D), the labels, the glyph colours, the legend and the
 * widget collection are functions of the catalogue, so they are pinned here without a GL context.
 */
@DisplayName("3D Settings dialog helpers (#778)")
class Gdx3DHudSettingsTest {

    @Test
    @DisplayName("the five catalogue categories show in the shared order")
    void should_ListTheCategories() {
        assertEquals(
                List.of(SettingCategory.TERRAIN, SettingCategory.WORLD, SettingCategory.ECONOMY,
                        SettingCategory.CONSTRUCTION, SettingCategory.UI),
                Gdx3DHud.settingsCategories());
    }

    @Test
    @DisplayName("the 3D field states mirror 2D: LIVE/REAPPLY editable, scenario lock wins")
    void should_MapFieldStates() {
        assertEquals(SettingFieldState.NEW_GAME,
                Gdx3DHud.settingsFieldState(definition("seed"), Set.of()));
        assertEquals(SettingFieldState.LIVE,
                Gdx3DHud.settingsFieldState(definition("startingBalance"), Set.of()));
        assertEquals(SettingFieldState.REAPPLY,
                Gdx3DHud.settingsFieldState(definition("world.latitude"), Set.of()));
        assertEquals(SettingFieldState.SCENARIO, Gdx3DHud
                .settingsFieldState(definition("startingBalance"), Set.of("startingBalance")));

        assertTrue(Gdx3DHud.settingsFieldState(definition("startingBalance"), Set.of()).editable());
        assertFalse(Gdx3DHud.settingsFieldState(definition("seed"), Set.of()).editable());
    }

    @Test
    @DisplayName("the label shows the display name plus its unit, when the catalogue has one")
    void should_LabelFieldsWithTheirUnit() {
        assertEquals("Water (%)", Gdx3DHud.settingsFieldLabel(definition("terrain.waterPercent")));
        assertEquals("View radius (cells)",
                Gdx3DHud.settingsFieldLabel(definition("map.VIEW_RADIUS")));
        assertEquals("Starting balance", Gdx3DHud.settingsFieldLabel(definition("startingBalance")),
                "a unit-less field keeps its bare name");
    }

    @Test
    @DisplayName("every state gets a distinct glyph colour, matching the 2D palette")
    void should_ColourTheGlyphs() {
        assertEquals(Color.GREEN, Gdx3DHud.settingsGlyphColor(SettingFieldState.LIVE));
        assertEquals(Color.YELLOW, Gdx3DHud.settingsGlyphColor(SettingFieldState.REAPPLY));
        assertEquals(Color.GRAY, Gdx3DHud.settingsGlyphColor(SettingFieldState.SCENARIO));
        Color newGame = Gdx3DHud.settingsGlyphColor(SettingFieldState.NEW_GAME);
        assertNotNull(newGame);
        assertTrue(newGame.r > newGame.g && newGame.r > newGame.b, "NEW_GAME reads red");
    }

    @Test
    @DisplayName("the legend lists the three shared entries and no scenario colour (#767)")
    void should_RenderTheLegend() {
        String legend = Gdx3DHud.settingsLegendMarkup();

        assertTrue(legend.contains(SettingsText.LEGEND_LIVE), legend);
        assertTrue(legend.contains(SettingsText.LEGEND_REAPPLY), legend);
        assertTrue(legend.contains(SettingsText.LEGEND_NEW_GAME), legend);
        assertEquals(3, legend.split(Gdx3DHud.SETTINGS_GLYPH, -1).length - 1,
                "exactly one glyph per documented state, no scenario entry: " + legend);
    }

    @Test
    @DisplayName("editableValues reads only the LIVE/REAPPLY fields of the form")
    void should_CollectEditableValues() {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("startingBalance", "10");
        form.put("seed", "7");
        form.put("world.latitude", "41.5");

        assertEquals(Map.of("startingBalance", "10", "world.latitude", "41.5"),
                Gdx3DHud.editableValues(form, Set.of()),
                "NEW_GAME fields are left out of the Apply payload");
        assertEquals(Map.of("startingBalance", "10"),
                Gdx3DHud.editableValues(form, Set.of("world.latitude")),
                "a scenario-locked key is not applied either");
    }

    @Test
    @DisplayName("editableValues ignores unknown keys, never injects absent ones and does not mutate")
    void should_NotMutateOrInventEditableValues() {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("startingBalance", "10");
        form.put("totally.unknown.key", "x");
        Map<String, String> snapshot = new LinkedHashMap<>(form);

        Map<String, String> values = Gdx3DHud.editableValues(form, Set.of());

        assertEquals(Map.of("startingBalance", "10"), values,
                "a key outside the catalogue never becomes editable");
        assertFalse(values.containsKey("time.dayDurationSeconds"),
                "fields absent from the form are not invented");
        assertEquals(snapshot, form, "the working form is left untouched");
    }

    @Test
    @DisplayName("the terrain bar always adds up to the requested width, field is the remainder")
    void should_StackTheTerrainBarToTheFullWidth() {
        // Draw order: water, field, mountain (mirrors the 2D NewGameDialog cases).
        assertArrayEquals(new int[] {10, 26, 4}, Gdx3DHud.stackedBarWidths(25, 10, 40));
        assertArrayEquals(new int[] {0, 40, 0}, Gdx3DHud.stackedBarWidths(0, 0, 40));
        assertArrayEquals(new int[] {38, 2, 0}, Gdx3DHud.stackedBarWidths(95, 0, 40));
        // Largest remainder keeps every proportion visible: 33/34/33 over 10 cells.
        assertArrayEquals(new int[] {3, 4, 3}, Gdx3DHud.stackedBarWidths(33, 33, 10));
    }

    @Test
    @DisplayName("the terrain bar segments always total the width and keep a zero segment empty")
    void should_SumTheTerrainBarSegmentsAndKeepZeroEmpty() {
        int width = Gdx3DHud.TERRAIN_BAR_WIDTH;
        int[][] cases = {{25, 10}, {0, 0}, {0, 100}, {50, 0}, {95, 0}, {100, 0}, {33, 33}};
        for (int[] percent : cases) {
            int[] cells = Gdx3DHud.stackedBarWidths(percent[0], percent[1], width);
            assertEquals(3, cells.length, "water/field/mountain");
            assertEquals(width, cells[0] + cells[1] + cells[2],
                    "water " + percent[0] + " mountain " + percent[1] + " must fill " + width);
        }
        // A zero proportion must claim no cells when the bar still has room elsewhere.
        assertArrayEquals(new int[] {0, width, 0}, Gdx3DHud.stackedBarWidths(0, 0, width));
    }

    private static SettingDefinition definition(String key) {
        return SettingsCatalog.definition(key).orElseThrow();
    }
}
