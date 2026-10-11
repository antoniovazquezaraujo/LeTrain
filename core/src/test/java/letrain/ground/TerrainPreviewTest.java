package letrain.ground;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Issue #746: the terrain preview samples the same noise and calibrated thresholds as the world
 * generator, so target, measured and preview describe the same terrain.
 */
@DisplayName("Terrain preview sampling (#746)")
class TerrainPreviewTest {

    @Test
    @DisplayName("the grid has the requested size and only world terrain values")
    void should_SampleTheRequestedGrid() {
        TerrainPreview.Grid grid = TerrainPreview.sample(123, 25, 10, 40, 20);

        assertEquals(40, grid.columns());
        assertEquals(20, grid.rows());
        assertEquals(800, grid.cells().length);
        for (int value : grid.cells()) {
            assertTrue(value == TerrainPreview.GROUND || value == TerrainPreview.WATER
                    || value == TerrainPreview.ROCK, "unexpected terrain " + value);
        }
    }

    @Test
    @DisplayName("0 % water / 0 % mountain leaves a pure field, 95 / 0 only water and field")
    void should_RespectTheProportions() {
        TerrainPreview.Grid noWater = TerrainPreview.sample(123, 0, 0, 40, 20);
        assertEquals(0, noWater.count(TerrainPreview.WATER));
        assertEquals(0, noWater.count(TerrainPreview.ROCK));

        TerrainPreview.Grid waterOnly = TerrainPreview.sample(123, 95, 0, 40, 20);
        assertEquals(0, waterOnly.count(TerrainPreview.ROCK));
        assertTrue(waterOnly.count(TerrainPreview.WATER) > 0);
    }

    @ParameterizedTest
    @ValueSource(ints = {123, 42, 7})
    @DisplayName("the preview proportions stay close to the requested ones (same calibrated tails)")
    void should_TrackTheTarget(int seed) {
        TerrainPreview.Grid grid = TerrainPreview.sample(seed, 30, 15, 80, 40);

        // The grid is a downsampling of the calibration window, so it tracks the measured
        // proportions within a few points (exact equality is asserted by the calibrator tests).
        assertTrue(Math.abs(grid.percent(TerrainPreview.WATER) - 30) <= 6,
                "seed " + seed + ": water " + grid.percent(TerrainPreview.WATER));
        assertTrue(Math.abs(grid.percent(TerrainPreview.ROCK) - 15) <= 6,
                "seed " + seed + ": mountain " + grid.percent(TerrainPreview.ROCK));
    }

    @Test
    @DisplayName("sampling is deterministic for the same seed and proportions")
    void should_BeDeterministic() {
        TerrainPreview.Grid first = TerrainPreview.sample(99, 20, 20, 20, 10);

        org.junit.jupiter.api.Assertions.assertArrayEquals(first.cells(),
                TerrainPreview.sample(99, 20, 20, 20, 10).cells());
    }

    @Test
    @DisplayName("the grid spans the whole calibration window whatever the size")
    void should_SpanTheCalibrationWindow() {
        assertEquals(0, TerrainPreview.worldCoordinate(0, 10));
        assertEquals(TerrainCalibrator.SAMPLE_SIZE - 1, TerrainPreview.worldCoordinate(9, 10));
        assertEquals(0, TerrainPreview.worldCoordinate(0, 1),
                "a 1-cell axis collapses to the window origin instead of dividing by zero");
    }
}
