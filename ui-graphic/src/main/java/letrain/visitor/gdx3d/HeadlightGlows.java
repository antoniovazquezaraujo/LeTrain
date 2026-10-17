package letrain.visitor.gdx3d;

import letrain.palette.VisualPalette;

/**
 * Placement and strength of the soft ground pools that stand in for the locomotive headlights in 3D
 * (ADR-022 phase 1e, issue #690). The default LibGDX shader lights per vertex and the ground is one
 * 1x1 quad per cell, so the old {@code PointLight}s showed up as square patches that traced the
 * cell grid. Each pool is now a flat quad with an analytic radial falloff computed per fragment
 * ({@link HeadlightGlowRenderer}) and normal alpha blending: smooth at any distance or grazing
 * angle (no texture to minify) and colour-capped, so overlapping pools can never blow out to white.
 *
 * <p>
 * Pure maths on the GDX-free {@link Headlights.Source}, so placement and opacity are unit-testable.
 */
public final class HeadlightGlows {

    /** At most this many pools, for the locomotives nearest to the camera. */
    public static final int MAX_SOURCES = 4;

    /**
     * Height of the pool above the ground top (0.005). Above the ballast top (0.08: a 0.1-high box
     * centred at 0.03) so its per-cell boxes cannot punch grid-shaped holes in the glow; below the
     * rail top (0.18) and the locomotive body (bottom 0.21), so the track and the train still
     * occlude the light as they should.
     */
    public static final float GROUND_Y = 0.12f;

    /** Distance from the rendered locomotive to the pool centre (its brightest point). */
    public static final float CENTER_AHEAD = 1.8f;

    /** Pool size across the beam and along it; the falloff reaches zero at the quad edge. */
    public static final float WIDTH = 3.6f;
    public static final float LENGTH = 6.5f;

    /**
     * Peak alpha of the pool at full night. Below 1 so the ground keeps its texture through the
     * light and several overlapping pools composite instead of painting it flat.
     */
    public static final float PEAK_OPACITY = 0.5f;

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
     * stretched falloff points the beam where the locomotive looks.
     */
    public static float yawDegrees(Headlights.Source source) {
        return (float) Math.toDegrees(Math.atan2(source.dirX(), source.dirZ()));
    }

    /** Peak alpha of a pool at a day/night ratio: 0 in daylight, {@link #PEAK_OPACITY} at night. */
    public static float opacity(double dayNightRatio) {
        return PEAK_OPACITY * VisualPalette.lightsOnFactor(dayNightRatio);
    }
}
