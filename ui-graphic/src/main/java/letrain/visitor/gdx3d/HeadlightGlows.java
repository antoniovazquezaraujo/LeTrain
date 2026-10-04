package letrain.visitor.gdx3d;

import letrain.palette.VisualPalette;

/**
 * Placement and strength of the soft ground pools that stand in for the locomotive headlights in 3D
 * (ADR-022 phase 1e, issue #690). The default LibGDX shader lights per vertex and the ground is one
 * 1x1 quad per cell, so the old {@code PointLight}s showed up as square patches that traced the
 * cell grid. Each pool is now a flat additive quad with a radial-gradient texture that follows the
 * rendered locomotive: smooth at any distance and no per-cell geometry involved.
 *
 * <p>
 * Pure maths on the GDX-free {@link Headlights.Source}, so placement and opacity are unit-testable.
 */
public final class HeadlightGlows {

    /** At most this many pools, for the locomotives nearest to the camera. */
    public static final int MAX_SOURCES = 4;

    /**
     * Height of the pool above the ground top (0.005): low enough to read as paint on the board.
     */
    public static final float GROUND_Y = 0.02f;

    /** Distance from the rendered locomotive to the pool centre (its brightest point). */
    public static final float CENTER_AHEAD = 1.6f;

    /** Pool size across the beam and along it; the radial gradient reaches zero at the edges. */
    public static final float WIDTH = 4.0f;
    public static final float LENGTH = 6.0f;

    /** Pool strength at full night; additive, so it builds up gradually on the dark ground. */
    public static final float PEAK_OPACITY = 0.6f;

    private HeadlightGlows() {}

    /** World X of the pool centre, in front of the locomotive. */
    public static float centerX(Headlights.Source source) {
        return source.x() + source.dirX() * CENTER_AHEAD;
    }

    /** World Z of the pool centre, in front of the locomotive. */
    public static float centerZ(Headlights.Source source) {
        return source.z() + source.dirZ() * CENTER_AHEAD;
    }

    /**
     * Yaw in degrees that aligns the pool's long axis (+Z) with the locomotive heading, so the
     * stretched gradient points the beam where the locomotive looks.
     */
    public static float yawDegrees(Headlights.Source source) {
        return (float) Math.toDegrees(Math.atan2(source.dirX(), source.dirZ()));
    }

    /** Additive strength of a pool at a day/night ratio: 0 in daylight, full at night. */
    public static float opacity(double dayNightRatio) {
        return PEAK_OPACITY * VisualPalette.lightsOnFactor(dayNightRatio);
    }
}
