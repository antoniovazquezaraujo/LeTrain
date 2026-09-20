package letrain.palette;

/**
 * Shared day/night visual palette (ADR-022 phase 1, see
 * {@code docs/developer/systems/DayNight_Colors.md}). Pure data and maths: no UI dependencies, so
 * the same token values feed the 3D renderer and, later, the 2D terminal.
 *
 * <p>
 * Every token has day, dusk and night keys; {@link #color(Token, double)} interpolates them in
 * linear light space (perceptually even) with the clock's day/night ratio.
 */
public final class VisualPalette {

    /** Colour tokens wired so far; the rest of the map joins as the phases land. */
    public enum Token {
        AMBIENT_LIGHT, SUN_LIGHT, SKY, TABLE_BOARD
    }

    private static final int DAY = 0;
    private static final int DUSK = 1;
    private static final int NIGHT = 2;

    /** Keys are {@code [token][phase]} as 0xRRGGBB. */
    private static final int[][] KEYS = {
            // AMBIENT_LIGHT: neutral day, warm dusk, blue night
            {0x8C8C85, 0x594738, 0x1F2438},
            // SUN_LIGHT
            {0xCCCCCC, 0xB3804D, 0x404D66},
            // SKY (clear colour)
            {0x000000, 0x402E1F, 0x05080F},
            // TABLE_BOARD
            {0x664D1A, 0x473314, 0x1A140D},};

    /** Interpolated 0xRRGGBB for a token at a day/night ratio (0 = day, 1 = night). */
    public int color(Token token, double dayNightRatio) {
        double ratio = Math.max(0.0, Math.min(1.0, dayNightRatio));
        int[] keys = KEYS[token.ordinal()];
        if (ratio <= 0.5) {
            return mix(keys[DAY], keys[DUSK], (float) (ratio * 2.0));
        }
        return mix(keys[DUSK], keys[NIGHT], (float) ((ratio - 0.5) * 2.0));
    }

    /** Perceptual (linear light) interpolation between two 0xRRGGBB colours. */
    public static int mix(int a, int b, float t) {
        int r = toSrgb(lerp(toLinear((a >> 16) & 0xFF), toLinear((b >> 16) & 0xFF), t));
        int g = toSrgb(lerp(toLinear((a >> 8) & 0xFF), toLinear((b >> 8) & 0xFF), t));
        int bl = toSrgb(lerp(toLinear(a & 0xFF), toLinear(b & 0xFF), t));
        return (r << 16) | (g << 8) | bl;
    }

    private static double lerp(double a, double b, float t) {
        return a + (b - a) * t;
    }

    private static double toLinear(int channel) {
        double v = channel / 255.0;
        return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
    }

    private static int toSrgb(double linear) {
        double v = linear <= 0.0031308 ? 12.92 * linear : 1.055 * Math.pow(linear, 1 / 2.4) - 0.055;
        return (int) Math.round(Math.max(0.0, Math.min(1.0, v)) * 255);
    }
}
