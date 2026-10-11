package letrain.mvp.impl;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import letrain.ground.TerrainCalibrator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #746: the core hook for "a new world with settings". {@code Model.newGame} reuses the
 * scenario/load machinery (seeded model + {@code applyConfig}) and applies the validated settings
 * before any terrain is generated, so the calibrated world matches the New Game dialog.
 */
@DisplayName("Model.newGame: fresh world with catalogue settings (#746)")
class ModelNewGameTest {

    private static final int GRID = TerrainCalibrator.SAMPLE_SIZE;
    private static final int STRIDE = 4;

    /** Deterministic terrain sampling over the calibration region (no cells materialized). */
    private static int[] terrainSignature(Model model) {
        int side = (GRID + STRIDE - 1) / STRIDE;
        int[] signature = new int[side * side];
        int i = 0;
        for (int y = 0; y < GRID; y += STRIDE) {
            for (int x = 0; x < GRID; x += STRIDE) {
                signature[i++] = model.getGroundMap().getBackgroundTerrain(x, y);
            }
        }
        return signature;
    }

    @Test
    @DisplayName("the world uses the requested seed and terrain proportions")
    void newGame_appliesSeedAndTerrain() {
        Model model = Model.newGame(123,
                Map.of("terrain.waterPercent", "60", "terrain.mountainPercent", "5"));

        assertEquals(123, model.getSeed());
        assertTrue(model.getEconomyManager().isTerrainPercentEnabled());
        assertEquals(60, model.getEconomyManager().getWaterPercent());
        assertEquals(5, model.getEconomyManager().getMountainPercent());

        // Same terrain as applying the settings to a seeded model by hand (scenario/load path):
        // the hook applies them before any generation.
        Model reference = new Model(123);
        reference.getEconomyManager()
                .applyConfig(Map.of("terrain.waterPercent", "60", "terrain.mountainPercent", "5"));
        assertArrayEquals(terrainSignature(reference), terrainSignature(model));
    }

    @Test
    @DisplayName("economy values reach the model and the day clock is re-wired")
    void newGame_appliesEconomyAndClock() {
        Model model = Model.newGame(7, Map.of("startingBalance", "500", "time.dayDurationSeconds",
                "3600", "world.latitude", "10.0"));

        assertEquals(500f, model.getEconomyManager().getBalance(), 0.001f);
        assertEquals(3600, model.getEconomyManager().getDayDurationSeconds());
        assertEquals(10.0, model.getGameClock().getLatitude(), 0.001);
        assertEquals("500.0", model.getEconomyManager().effectiveConfig().get("startingBalance"));
    }

    @Test
    @DisplayName("missing keys keep the code defaults (D2 terrain proportions included)")
    void newGame_usesDefaultsForMissingKeys() {
        Model model = Model.newGame(42, Map.of());

        assertEquals(25, model.getEconomyManager().getWaterPercent());
        assertEquals(10, model.getEconomyManager().getMountainPercent());
    }

    @Test
    @DisplayName("invalid settings are rejected before any world is built")
    void newGame_rejectsInvalidSettings() {
        assertThrows(IllegalArgumentException.class, () -> Model.newGame(1,
                Map.of("terrain.waterPercent", "80", "terrain.mountainPercent", "30")));
        assertThrows(IllegalArgumentException.class,
                () -> Model.newGame(1, Map.of("startingBalance", "not-a-number")));
    }
}
