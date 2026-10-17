package letrain.ground;

import java.util.Arrays;
import letrain.settings.SettingsCatalog;

/**
 * Turns the requested terrain proportions (water %, mountain %) into the raw noise thresholds used
 * by {@link letrain.ground.impl.GroundMap} (concept §4). It samples the same noise grid and mapping
 * as the world generator ({@code scaleAndShift(-0.7..0.7 → 0..255)}, {@code col*0.01},
 * {@code row*0.02}, 5 octaves) and takes the requested tails by bisection on the sample
 * distribution (percentile over the sorted sample), so the requested proportions are what you get
 * statistically. Ported from the calibration demo in {@code letrain-lab/demo/terrain-proportions}.
 *
 * <p>
 * Pure and deterministic: same seed and percentages always yield the same thresholds. Requested
 * percentages are clamped to the valid world (each 0..{@value SettingsCatalog#MAX_TERRAIN_PERCENT}
 * %, water + mountain ≤ {@value SettingsCatalog#MAX_TERRAIN_PERCENT} %) so a hand-edited
 * {@code letrain.cfg} can never produce an impossible world.
 */
public final class TerrainCalibrator {

    /** Side of the calibration sample, in cells (matches the demo's "world statistics" sample). */
    public static final int SAMPLE_SIZE = 260;
    public static final int OCTAVES = 5;
    public static final float SCALE_X = 0.01f;
    public static final float SCALE_Y = 0.02f;
    public static final float NOISE_MIN = -0.7f;
    public static final float NOISE_MAX = 0.7f;
    public static final float VALUE_MIN = 0f;
    public static final float VALUE_MAX = 255f;

    /** Valid, clamped terrain proportions. */
    public record Proportions(int waterPercent, int mountainPercent) {
    }

    /** Raw noise thresholds separating water (below) and rock (above) from ground. */
    public record Thresholds(float water, float rock) {
    }

    /**
     * Result of a calibration: the thresholds plus the proportions actually measured on the sample
     * (integer percentages), so a client can show target vs achieved (concept §7).
     */
    public record Calibration(float waterThreshold, float rockThreshold, int measuredWaterPercent,
            int measuredMountainPercent) {
    }

    private TerrainCalibrator() {}

    /** Thresholds for the requested proportions, calibrated against the sample of {@code seed}. */
    public static Thresholds thresholdFor(int seed, int waterPercent, int mountainPercent) {
        Calibration calibration = calibrate(seed, waterPercent, mountainPercent);
        return new Thresholds(calibration.waterThreshold(), calibration.rockThreshold());
    }

    /**
     * Samples the noise region and computes the thresholds for the requested tails. Tolerance of
     * the achieved proportions against the requested ones is ≤ 2 % on the sample (tests assert it).
     */
    public static Calibration calibrate(int seed, int waterPercent, int mountainPercent) {
        Proportions proportions = clamp(waterPercent, mountainPercent);
        PerlinNoise noise = new PerlinNoise(seed);
        float[] sample = new float[SAMPLE_SIZE * SAMPLE_SIZE];
        int i = 0;
        for (int y = 0; y < SAMPLE_SIZE; y++) {
            for (int x = 0; x < SAMPLE_SIZE; x++) {
                sample[i++] = scaled(noise, x, y);
            }
        }
        float[] sorted = sample.clone();
        Arrays.sort(sorted);
        float waterThreshold = percentile(sorted, proportions.waterPercent() / 100.0);
        float rockThreshold = percentile(sorted, 1.0 - proportions.mountainPercent() / 100.0);
        int measuredWater = countBelow(sample, waterThreshold) * 100 / sample.length;
        int measuredMountain = countAbove(sample, rockThreshold) * 100 / sample.length;
        return new Calibration(waterThreshold, rockThreshold, measuredWater, measuredMountain);
    }

    /** Clamps the requested percentages into the valid world (see class javadoc). */
    public static Proportions clamp(int waterPercent, int mountainPercent) {
        int water = Math.max(0, Math.min(SettingsCatalog.MAX_TERRAIN_PERCENT, waterPercent));
        int mountain =
                Math.max(0, Math.min(SettingsCatalog.MAX_TERRAIN_PERCENT - water, mountainPercent));
        return new Proportions(water, mountain);
    }

    /** The world generator's mapping: {@code scaleAndShift(-0.7..0.7 → 0..255)} (GroundMap). */
    static float scaled(PerlinNoise noise, int x, int y) {
        float value = noise.smoothNoise(x * SCALE_X, y * SCALE_Y, 0, OCTAVES);
        return ((value - NOISE_MIN) / (NOISE_MAX - NOISE_MIN)) * (VALUE_MAX - VALUE_MIN)
                + VALUE_MIN;
    }

    /** Value at percentile {@code p} (0..1) of an ascending-sorted sample. */
    static float percentile(float[] sorted, double p) {
        int idx = (int) Math.round(p * (sorted.length - 1));
        idx = Math.max(0, Math.min(sorted.length - 1, idx));
        return sorted[idx];
    }

    static int countBelow(float[] values, float threshold) {
        int count = 0;
        for (float value : values) {
            if (value < threshold) {
                count++;
            }
        }
        return count;
    }

    static int countAbove(float[] values, float threshold) {
        int count = 0;
        for (float value : values) {
            if (value > threshold) {
                count++;
            }
        }
        return count;
    }
}
