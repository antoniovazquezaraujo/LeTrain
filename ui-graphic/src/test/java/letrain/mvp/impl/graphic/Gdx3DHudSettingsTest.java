package letrain.mvp.impl.graphic;

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

    private static SettingDefinition definition(String key) {
        return SettingsCatalog.definition(key).orElseThrow();
    }
}
