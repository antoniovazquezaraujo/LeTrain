package letrain.ground;

import java.util.Objects;

/**
 * Sample of the terrain the new game will generate, used by the New Game preview (S2, issue #746).
 * It reuses the exact world mapping ({@link TerrainCalibrator#scaled}: {@code smoothNoise} with
 * {@code col*0.01}, {@code row*0.02}, 5 octaves, mapped to 0..255) and the calibrated thresholds
 * for the requested proportions, so the preview shows the same terrain the world generator will
 * produce.
 *
 * <p>
 * The sample covers the calibration window (0..{@value TerrainCalibrator#SAMPLE_SIZE} cells, the
 * region {@link TerrainCalibrator} measures), scaled to the requested grid. That way the preview,
 * the target proportions and the measured proportions all describe the same region: a compact
 * preview is the same picture at a lower resolution. Pure and deterministic: same seed and
 * proportions always yield the same grid.
 */
public final class TerrainPreview {

    /** Terrain value of a preview cell, mirroring {@link GroundMap} constants. */
    public static final int GROUND = letrain.ground.GroundMap.GROUND;
    public static final int WATER = letrain.ground.GroundMap.WATER;
    public static final int ROCK = letrain.ground.GroundMap.ROCK;

    /** One sampled grid: {@code columns x rows} terrain values, row-major. */
    public record Grid(int columns, int rows, int[] cells) {
        public Grid {
            Objects.requireNonNull(cells, "cells must not be null");
            if (columns < 1 || rows < 1) {
                throw new IllegalArgumentException(
                        "grid must be at least 1x1, got " + columns + "x" + rows);
            }
            if (cells.length != columns * rows) {
                throw new IllegalArgumentException(
                        "cells length " + cells.length + " does not match " + columns + "x" + rows);
            }
        }

        public int cell(int column, int row) {
            return cells[row * columns + column];
        }

        /** Number of cells with the given terrain value. */
        public int count(int terrain) {
            int total = 0;
            for (int value : cells) {
                if (value == terrain) {
                    total++;
                }
            }
            return total;
        }

        /** Percentage of the grid with the given terrain value (integer truncation). */
        public int percent(int terrain) {
            return count(terrain) * 100 / cells.length;
        }
    }

    private TerrainPreview() {}

    /**
     * Samples {@code columns x rows} terrain values for the requested proportions, calibrated
     * against {@code seed}. Each grid cell maps to its own spot of the calibration window: the
     * whole window is covered whatever the grid size.
     */
    public static Grid sample(int seed, int waterPercent, int mountainPercent, int columns,
            int rows) {
        TerrainCalibrator.Calibration calibration =
                TerrainCalibrator.calibrate(seed, waterPercent, mountainPercent);
        float waterThreshold = calibration.waterThreshold();
        float rockThreshold = calibration.rockThreshold();
        PerlinNoise noise = new PerlinNoise(seed);
        int[] cells = new int[columns * rows];
        int i = 0;
        for (int row = 0; row < rows; row++) {
            int worldY = worldCoordinate(row, rows);
            for (int column = 0; column < columns; column++) {
                int worldX = worldCoordinate(column, columns);
                float value = TerrainCalibrator.scaled(noise, worldX, worldY);
                if (value < waterThreshold) {
                    cells[i++] = WATER;
                } else if (value > rockThreshold) {
                    cells[i++] = ROCK;
                } else {
                    cells[i++] = GROUND;
                }
            }
        }
        return new Grid(columns, rows, cells);
    }

    /** Spreads {@code index} over the calibration window (0..SAMPLE_SIZE-1). */
    static int worldCoordinate(int index, int size) {
        if (size <= 1) {
            return 0;
        }
        return Math.round(index * (float) (TerrainCalibrator.SAMPLE_SIZE - 1) / (size - 1));
    }
}
