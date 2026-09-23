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
        AMBIENT_LIGHT, SUN_LIGHT, SKY, TABLE_BOARD,
        // Terrain (phase 1b)
        TERRAIN_FIELDS, TERRAIN_WATER, TERRAIN_MOUNTAIN, TERRAIN_BALLAST, STRUCTURE_BRIDGE_PILLAR, STRUCTURE_TUNNEL_PORTAL, STRUCTURE_TERRAIN_WALL, TABLE_GRID, DECOR_BOX, VOID,
        // Emissive (phase 1e): lights, constant so they never dim at night
        EMISSIVE_HEADLIGHT
    }

    private static final int DAY = 0;
    private static final int DUSK = 1;
    private static final int NIGHT = 2;

    /**
     * Day/night ratio at which the emissive lights come on. Emissive tokens never dim, but they
     * should not glow in broad daylight either; one shared threshold keeps the lamp and its glow
     * switching on together in both clients.
     */
    public static final double LIGHTS_ON_RATIO = 0.1;

    /** Keys are {@code [token][phase]} as 0xRRGGBB. */
    private static final int[][] KEYS = {
            // AMBIENT_LIGHT: neutral day, warm dusk, blue night
            {0x8C8C85, 0x594738, 0x1F2438},
            // SUN_LIGHT
            {0xCCCCCC, 0xB3804D, 0x404D66},
            // SKY (clear colour): day sky blue, warm dusk, dark night
            {0x87CEEB, 0x6B4632, 0x05080F},
            // TABLE_BOARD
            {0x664D1A, 0x473314, 0x1A140D},
            // TERRAIN_FIELDS (phase 1b)
            {0x66994C, 0x526B38, 0x1F2E24},
            // TERRAIN_WATER
            {0x3366CC, 0x26478C, 0x0F1F4D},
            // TERRAIN_MOUNTAIN
            {0x80664D, 0x614D38, 0x29241F},
            // TERRAIN_BALLAST
            {0x808080, 0x5C574D, 0x242426},
            // STRUCTURE_BRIDGE_PILLAR
            {0x808080, 0x5C574D, 0x242426},
            // STRUCTURE_TUNNEL_PORTAL
            {0x808080, 0x666666, 0x262626},
            // STRUCTURE_TERRAIN_WALL
            {0x808080, 0x666666, 0x262626},
            // TABLE_GRID
            {0xBFBFBF, 0x808080, 0x2E2E33},
            // DECOR_BOX
            {0x218C21, 0x336626, 0x142914},
            // VOID (below the horizon)
            {0x000000, 0x000000, 0x000000},
            // EMISSIVE_HEADLIGHT (phase 1e): warm white, identical at every hour
            {0xFFF2C8, 0xFFF2C8, 0xFFF2C8},};

    /** Interpolated 0xRRGGBB for a token at a day/night ratio (0 = day, 1 = night). */
    public int color(Token token, double dayNightRatio) {
        double ratio = Math.max(0.0, Math.min(1.0, dayNightRatio));
        int[] keys = KEYS[token.ordinal()];
        if (ratio <= 0.5) {
            return mix(keys[DAY], keys[DUSK], (float) (ratio * 2.0));
        }
        return mix(keys[DUSK], keys[NIGHT], (float) ((ratio - 0.5) * 2.0));
    }

    /**
     * How strongly the emissive lights burn at a ratio: 0 below {@link #LIGHTS_ON_RATIO} (day), 1
     * at night.
     */
    public static float lightsOnFactor(double dayNightRatio) {
        double ratio = Math.max(0.0, Math.min(1.0, dayNightRatio));
        if (ratio <= LIGHTS_ON_RATIO) {
            return 0f;
        }
        return (float) ((ratio - LIGHTS_ON_RATIO) / (1.0 - LIGHTS_ON_RATIO));
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
