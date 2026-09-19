package letrain.ground;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.function.IntBinaryOperator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Zone sensor")
class ZoneSensorTest {

    private static IntBinaryOperator grid(int[][] cells) {
        return (x, y) -> {
            if (y < 0 || y >= cells.length || x < 0 || x >= cells[0].length) {
                return -1;
            }
            return cells[y][x];
        };
    }

    private static int[][] filled(int width, int height, int value) {
        int[][] cells = new int[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                cells[y][x] = value;
            }
        }
        return cells;
    }

    @Test
    @DisplayName("fields everywhere gives a single primary zone")
    void should_SenseFields() {
        ZoneSensor sensor = new ZoneSensor(10, 2);

        ZoneSensor.Result result = sensor.sense(grid(filled(64, 64, GroundMap.GROUND)), 32, 32);

        assertEquals("fields", result.primary());
        assertEquals(1f, result.weightOf("fields"), 1e-6);
        assertEquals(1, result.weights().size());
        assertEquals(1f, result.influence().get("fields"), 1e-6);
    }

    @Test
    @DisplayName("a coast gives sea as secondary, below the primary")
    void should_SenseCoast() {
        int[][] cells = filled(64, 64, GroundMap.GROUND);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 32; x++) {
                cells[y][x] = GroundMap.WATER;
            }
        }
        ZoneSensor sensor = new ZoneSensor(12, 1);

        ZoneSensor.Result result = sensor.sense(grid(cells), 33, 32);

        assertEquals("fields", result.primary());
        assertEquals("sea", result.weights().keySet().stream()
                .filter(zone -> !zone.equals("fields")).findFirst().orElse(null));
        float seaWeight = result.weightOf("sea");
        assertTrue(seaWeight > 0.5f && seaWeight <= ZoneSensor.SECONDARY_MAX + 1e-4f);
        assertTrue(result.weightOf("fields") < 0.5f);
        assertTrue(result.influence().get("sea") > 0.3f);
    }

    @Test
    @DisplayName("a bridge over water has sea as primary and land as secondary")
    void should_SenseBridge() {
        int[][] cells = filled(64, 64, GroundMap.WATER);
        for (int y = 0; y < 64; y++) {
            for (int x = 0; x < 8; x++) {
                cells[y][x] = GroundMap.GROUND;
            }
        }
        ZoneSensor sensor = new ZoneSensor(12, 1);

        ZoneSensor.Result result = sensor.sense(grid(cells), 10, 32);

        assertEquals("sea", result.primary());
        assertTrue(result.weightOf("fields") > 0f);
    }

    @Test
    @DisplayName("industry tiles map to mine and factory zones")
    void should_SenseIndustry() {
        int[][] cells = filled(64, 64, GroundMap.GROUND);
        cells[32][32] = GroundMap.GOLD_MINE;
        cells[30][32] = GroundMap.JEWELRY_STORE;
        ZoneSensor sensor = new ZoneSensor(12, 1);

        ZoneSensor.Result mine = sensor.sense(grid(cells), 32, 32);
        ZoneSensor.Result factory = sensor.sense(grid(cells), 32, 30);

        assertEquals("gold-mine", mine.primary());
        assertEquals("gold-factory", factory.primary());
    }

    @Test
    @DisplayName("a sparse feature (a single factory tile) is heard by proximity")
    void should_HearSparseFeature() {
        int[][] cells = filled(64, 64, GroundMap.GROUND);
        cells[32][34] = GroundMap.JEWELRY_STORE;
        ZoneSensor sensor = new ZoneSensor(8, 1);

        ZoneSensor.Result result = sensor.sense(grid(cells), 32, 32);

        assertEquals("fields", result.primary());
        assertTrue(result.weightOf("gold-factory") > 0.5f);
        assertTrue(result.weightOf("fields") < 0.5f);
    }

    @Test
    @DisplayName("a feature four tiles away is still audible")
    void should_HearFeature_AtFourTiles() {
        int[][] cells = filled(64, 64, GroundMap.GROUND);
        cells[32][36] = GroundMap.JEWELRY_STORE;
        ZoneSensor sensor = new ZoneSensor(8, 1);

        ZoneSensor.Result result = sensor.sense(grid(cells), 32, 32);

        assertTrue(result.weightOf("gold-factory") > 0.8f,
                "weight " + result.weightOf("gold-factory"));
        assertTrue(result.weightOf("fields") < 0.2f);
    }

    @Test
    @DisplayName("ties keep the first zone found in scan order")
    void should_BreakTies_ByScanOrder() {
        int[][] cells = filled(64, 64, GroundMap.GROUND);
        cells[32][30] = GroundMap.WATER;
        cells[32][34] = GroundMap.JEWELRY_STORE;
        ZoneSensor sensor = new ZoneSensor(8, 1);

        ZoneSensor.Result result = sensor.sense(grid(cells), 32, 32);

        assertEquals("sea", result.weights().keySet().stream()
                .filter(zone -> !zone.equals("fields")).findFirst().orElse(null));
    }

    @Test
    @DisplayName("a zone beyond the radius does not sound")
    void should_IgnoreZonesBeyondRadius() {
        int[][] cells = filled(64, 64, GroundMap.GROUND);
        for (int y = 0; y < 64; y++) {
            cells[y][5] = GroundMap.WATER;
        }
        ZoneSensor sensor = new ZoneSensor(4, 1);

        ZoneSensor.Result result = sensor.sense(grid(cells), 32, 32);

        assertEquals("fields", result.primary());
        assertEquals(1, result.weights().size());
    }

    @Test
    @DisplayName("void terrain yields no zones")
    void should_ReturnEmpty_When_Void() {
        ZoneSensor sensor = new ZoneSensor(8, 2);

        ZoneSensor.Result result = sensor.sense(grid(filled(16, 16, -1)), 8, 8);

        assertNull(result.primary());
        assertTrue(result.weights().isEmpty());
    }
}
