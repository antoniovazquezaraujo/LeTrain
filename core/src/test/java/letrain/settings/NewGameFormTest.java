package letrain.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #746: the New Game form. Presentation-neutral state: prefill from the effective
 * configuration, terrain slider rule (water + mountain ≤ 95 %), seed handling and catalogue
 * validation on Start.
 */
@DisplayName("New Game form (#746)")
class NewGameFormTest {

    @Test
    @DisplayName("prefills every catalogue key and draws a fresh numeric seed (D3)")
    void should_PrefillAndDrawASeed() {
        NewGameForm form = NewGameForm.forNewGame(Map.of(), new Random(1));

        for (SettingDefinition definition : SettingsCatalog.definitions()) {
            assertTrue(form.value(definition.key()) != null, definition.key());
        }
        assertTrue(form.seed() >= 1 && form.seed() <= NewGameForm.MAX_SEED,
                "drawn seed: " + form.seed());
        assertEquals(String.valueOf(form.seed()), form.value(NewGameForm.SEED_KEY));
    }

    @Test
    @DisplayName("terrain defaults are the catalogue D2 values when the config has no percent keys")
    void should_UseD2TerrainDefaults() {
        NewGameForm form = NewGameForm.forNewGame(Map.of("threshold.WATER", "130"));

        assertEquals(SettingsCatalog.DEFAULT_WATER_PERCENT, form.waterPercent());
        assertEquals(SettingsCatalog.DEFAULT_MOUNTAIN_PERCENT, form.mountainPercent());
        assertEquals(65, form.fieldPercent());
    }

    @Test
    @DisplayName("effective configuration values win over catalogue defaults")
    void should_PreferEffectiveConfiguration() {
        NewGameForm form = NewGameForm.forNewGame(Map.of("startingBalance", "999",
                "terrain.waterPercent", "40", "terrain.mountainPercent", "20"));

        assertEquals("999", form.value("startingBalance"));
        assertEquals(40, form.waterPercent());
        assertEquals(20, form.mountainPercent());
    }

    @Test
    @DisplayName("raising water clamps mountain so the pair never exceeds 95 %")
    void should_ClampMountainWhenWaterRises() {
        NewGameForm form = NewGameForm.forNewGame(Map.of(), new Random(1));
        form.setWaterPercent(90);

        assertEquals(90, form.waterPercent());
        assertEquals(5, form.mountainPercent(),
                "the other slider makes room for the minimum field");
        assertEquals(5, form.fieldPercent());
    }

    @Test
    @DisplayName("raising mountain clamps water symmetrically")
    void should_ClampWaterWhenMountainRises() {
        NewGameForm form = NewGameForm.forNewGame(Map.of(), new Random(1));
        form.setMountainPercent(80);

        assertEquals(15, form.waterPercent());
        assertEquals(80, form.mountainPercent());
        assertEquals(5, form.fieldPercent());
    }

    @Test
    @DisplayName("single sliders are clamped to 0..95")
    void should_ClampSingleSlider() {
        NewGameForm form = NewGameForm.forNewGame(Map.of(), new Random(1));

        form.setWaterPercent(-10);
        assertEquals(0, form.waterPercent());

        form.setWaterPercent(200);
        assertEquals(SettingsCatalog.MAX_TERRAIN_PERCENT, form.waterPercent());
        assertEquals(0, form.mountainPercent());
    }

    @Test
    @DisplayName("toSettings validates every field and normalises the terrain values")
    void should_ValidateOnStart() {
        NewGameForm form = NewGameForm.forNewGame(Map.of(), new Random(1));
        form.setWaterPercent(30);
        form.setMountainPercent(5);

        Map<String, String> settings = form.toSettings();

        assertEquals("30", settings.get(NewGameForm.WATER_KEY));
        assertEquals("5", settings.get(NewGameForm.MOUNTAIN_KEY));
        assertEquals(String.valueOf(form.seed()), settings.get(NewGameForm.SEED_KEY));
        assertEquals("40.0", settings.get("world.latitude"));
        assertEquals("true", settings.get("ui.highlightBlockedTracks"));
    }

    @Test
    @DisplayName("an invalid widget value makes Start fail with the catalogue message")
    void should_RejectInvalidWidgetValue() {
        NewGameForm form = NewGameForm.forNewGame(Map.of(), new Random(1));
        form.setValue("startingBalance", "lots");

        IllegalArgumentException error =
                assertThrows(IllegalArgumentException.class, form::toSettings);

        assertTrue(error.getMessage().contains("startingBalance"), error.getMessage());
    }

    @Test
    @DisplayName("the seed field accepts a copyable/pasteable number and ignores garbage")
    void should_BindTheSeedField() {
        NewGameForm form = NewGameForm.forNewGame(Map.of(), new Random(1));

        form.setSeed("12345");
        assertEquals(12345, form.seed());
        assertEquals("12345", form.toSettings().get(NewGameForm.SEED_KEY));

        form.setSeed("nope");
        assertEquals(12345, form.seed(), "the last valid seed survives while the field is invalid");
    }

    @Test
    @DisplayName("the sections map world to Game and keep the catalogue grouping")
    void should_ExposeSectionNames() {
        assertEquals(List.of("Terrain", "Game", "Economy", "Construction", "UI"), java.util.Arrays
                .stream(SettingCategory.values()).map(SettingCategory::displayName).toList());
        assertEquals(SettingCategory.WORLD,
                SettingsCatalog.definition("world.latitude").orElseThrow().category());
    }
}
