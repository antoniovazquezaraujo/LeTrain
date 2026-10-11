package letrain.ground.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import letrain.economy.impl.EconomyManager;
import letrain.ground.TerrainCalibrator;
import letrain.mvp.impl.EventLogManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("GroundMap: generated world proportions (percent mode vs legacy thresholds)")
class GroundMapTerrainPercentTest {

    private static final int SAMPLE = TerrainCalibrator.SAMPLE_SIZE;

    private record Measured(int waterPercent, int mountainPercent) {
    }

    /** Materializes the calibration sample region and counts water/rock cells. */
    private static Measured measure(GroundMap map) {
        map.renderBlock(0, 0, SAMPLE, SAMPLE);
        int water = 0;
        int mountain = 0;
        for (int y = 0; y < SAMPLE; y++) {
            for (int x = 0; x < SAMPLE; x++) {
                Integer value = map.getValueAt(x, y);
                if (value != null && value == letrain.ground.GroundMap.WATER) {
                    water++;
                } else if (value != null && value == letrain.ground.GroundMap.ROCK) {
                    mountain++;
                }
            }
        }
        int total = SAMPLE * SAMPLE;
        return new Measured(water * 100 / total, mountain * 100 / total);
    }

    @ParameterizedTest
    @ValueSource(ints = {123, 42, 7})
    @DisplayName("with terrain.waterPercent=25 / mountainPercent=10 the world measures ~25 % / ~10 %")
    void percentSettings_achieveRequestedProportions(int seed) {
        EconomyManager economy = new EconomyManager(new EventLogManager());
        economy.applyConfig(Map.of("terrain.waterPercent", "25", "terrain.mountainPercent", "10"));

        Measured measured = measure(new GroundMap(seed, economy));

        assertTrue(Math.abs(measured.waterPercent() - 25) <= 2,
                "seed " + seed + ": water " + measured.waterPercent());
        assertTrue(Math.abs(measured.mountainPercent() - 10) <= 2,
                "seed " + seed + ": mountain " + measured.mountainPercent());
    }

    @Test
    @DisplayName("the generated world matches the calibration sample exactly")
    void generatedWorld_matchesCalibrationSample() {
        EconomyManager economy = new EconomyManager(new EventLogManager());
        economy.applyConfig(Map.of("terrain.waterPercent", "30", "terrain.mountainPercent", "20"));

        Measured measured = measure(new GroundMap(123, economy));
        TerrainCalibrator.Calibration calibration = TerrainCalibrator.calibrate(123, 30, 20);

        assertTrue(measured.waterPercent() == calibration.measuredWaterPercent(), "water: world "
                + measured.waterPercent() + " vs sample " + calibration.measuredWaterPercent());
        assertTrue(measured.mountainPercent() == calibration.measuredMountainPercent(),
                "mountain: world " + measured.mountainPercent() + " vs sample "
                        + calibration.measuredMountainPercent());
    }

    @Test
    @DisplayName("a fresh game with no terrain keys anywhere uses the D2 defaults (25 / 10)")
    void freshDefaults_useD2() {
        Measured measured = measure(new GroundMap(123, new EconomyManager(new EventLogManager())));

        assertTrue(Math.abs(measured.waterPercent() - 25) <= 2, "water " + measured.waterPercent());
        assertTrue(Math.abs(measured.mountainPercent() - 10) <= 2,
                "mountain " + measured.mountainPercent());
    }

    @Test
    @DisplayName("legacy threshold.WATER/ROCK keep the historical look (concept §4 numbers)")
    void legacyThresholds_keepHistoricalLook() {
        EconomyManager economy = new EconomyManager(new EventLogManager());
        economy.applyConfig(Map.of("threshold.WATER", "130", "threshold.ROCK", "180"));

        Measured measured = measure(new GroundMap(123, economy));

        // Concept §4: seed 123 with the current thresholds is 52.2 % water and 3.1 % mountain.
        assertTrue(Math.abs(measured.waterPercent() - 52) <= 2,
                "legacy water " + measured.waterPercent());
        assertTrue(Math.abs(measured.mountainPercent() - 3) <= 2,
                "legacy mountain " + measured.mountainPercent());
    }
}
