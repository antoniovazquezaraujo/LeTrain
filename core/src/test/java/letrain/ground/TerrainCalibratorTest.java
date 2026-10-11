package letrain.ground;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import letrain.ground.TerrainCalibrator.Calibration;
import letrain.ground.TerrainCalibrator.Proportions;
import letrain.settings.SettingsCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("TerrainCalibrator: deterministic per-seed calibration")
class TerrainCalibratorTest {

    @Test
    @DisplayName("same seed and proportions always produce the same thresholds (deterministic)")
    void calibrate_isDeterministic() {
        Calibration first = TerrainCalibrator.calibrate(123, 25, 10);
        Calibration second = TerrainCalibrator.calibrate(123, 25, 10);

        assertEquals(first, second);
        assertEquals(TerrainCalibrator.thresholdFor(123, 25, 10),
                TerrainCalibrator.thresholdFor(123, 25, 10));
    }

    @Test
    @DisplayName("different seeds get different thresholds (per-seed calibration)")
    void calibrate_isSeedDependent() {
        Calibration seed123 = TerrainCalibrator.calibrate(123, 25, 10);
        Calibration seed42 = TerrainCalibrator.calibrate(42, 25, 10);

        assertTrue(
                seed123.waterThreshold() != seed42.waterThreshold()
                        || seed123.rockThreshold() != seed42.rockThreshold(),
                "two seeds should not calibrate to the exact same thresholds");
    }

    @ParameterizedTest
    @CsvSource({"123, 25, 10", "42, 25, 10", "7, 25, 10", "123, 55, 5", "123, 10, 40",
            "42, 20, 15"})
    @DisplayName("the sample achieves the requested proportions within the 2 % tolerance")
    void calibrate_hitsRequestedProportions(int seed, int waterPercent, int mountainPercent) {
        Calibration calibration = TerrainCalibrator.calibrate(seed, waterPercent, mountainPercent);

        assertTrue(Math.abs(calibration.measuredWaterPercent() - waterPercent) <= 2,
                "water: requested " + waterPercent + ", measured "
                        + calibration.measuredWaterPercent() + " (seed " + seed + ")");
        assertTrue(Math.abs(calibration.measuredMountainPercent() - mountainPercent) <= 2,
                "mountain: requested " + mountainPercent + ", measured "
                        + calibration.measuredMountainPercent() + " (seed " + seed + ")");
    }

    @Test
    @DisplayName("matches the calibration demo numbers (seed 123, 260x260 sample)")
    void calibrate_matchesDemoNumbers() {
        // Expected values read from letrain-lab/demo/terrain-proportions --check, which uses the
        // same mapping and sample: measured integers are the floor of the achieved percentage.
        int[][] cases = {
                // water, mountain, expected water, expected mountain
                {20, 15, 20, 15}, {55, 5, 54, 5}, {10, 40, 10, 40}, {30, 30, 30, 30}, {0, 0, 0, 0},
                {90, 5, 89, 5}};

        for (int[] c : cases) {
            Calibration calibration = TerrainCalibrator.calibrate(123, c[0], c[1]);
            assertEquals(c[2], calibration.measuredWaterPercent(),
                    "demo water for target " + c[0] + "/" + c[1]);
            assertEquals(c[3], calibration.measuredMountainPercent(),
                    "demo mountain for target " + c[0] + "/" + c[1]);
        }
    }

    @Test
    @DisplayName("the D2 defaults give a world close to 25 % water and 10 % mountain")
    void defaults_hitD2() {
        Calibration calibration = TerrainCalibrator.calibrate(42,
                SettingsCatalog.DEFAULT_WATER_PERCENT, SettingsCatalog.DEFAULT_MOUNTAIN_PERCENT);

        assertEquals(25, SettingsCatalog.DEFAULT_WATER_PERCENT);
        assertEquals(10, SettingsCatalog.DEFAULT_MOUNTAIN_PERCENT);
        assertTrue(Math.abs(calibration.measuredWaterPercent() - 25) <= 2,
                "measured water " + calibration.measuredWaterPercent());
        assertTrue(Math.abs(calibration.measuredMountainPercent() - 10) <= 2,
                "measured mountain " + calibration.measuredMountainPercent());
    }

    @Test
    @DisplayName("out-of-range percentages are clamped to the valid world")
    void clamp_enforcesWorldLimits() {
        assertEquals(new Proportions(95, 0), TerrainCalibrator.clamp(120, 10));
        assertEquals(new Proportions(90, 5), TerrainCalibrator.clamp(90, 40));
        assertEquals(new Proportions(0, 10), TerrainCalibrator.clamp(-3, 10));
        assertEquals(new Proportions(0, 0), TerrainCalibrator.clamp(-3, -4));

        assertEquals(TerrainCalibrator.calibrate(123, 90, 5),
                TerrainCalibrator.calibrate(123, 90, 40),
                "a mountain request beyond the remaining field is clamped to the field minimum");
    }

    @Test
    @DisplayName("thresholds are ordered and inside the mapped 0..255 range")
    void thresholds_areSane() {
        Calibration calibration = TerrainCalibrator.calibrate(123, 25, 10);

        assertTrue(calibration.waterThreshold() >= 0f && calibration.waterThreshold() <= 255f);
        assertTrue(calibration.rockThreshold() >= 0f && calibration.rockThreshold() <= 255f);
        assertTrue(calibration.waterThreshold() < calibration.rockThreshold(),
                "water threshold must stay below rock threshold");
    }
}
