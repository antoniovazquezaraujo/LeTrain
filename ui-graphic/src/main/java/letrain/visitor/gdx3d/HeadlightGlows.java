package letrain.visitor.gdx3d;

import java.util.List;
import letrain.palette.VisualPalette;

/**
 * Placement and strength of the soft ground pools that stand in for the locomotive headlights in 3D
 * (ADR-022 phase 1e, issue #690). The default LibGDX shader lights per vertex and the ground is one
 * 1x1 quad per cell, so the old {@code PointLight}s showed up as square patches that traced the
 * cell grid. Each pool is now a flat quad with an <em>analytic</em> radial falloff computed per
 * fragment ({@link HeadlightGlowRenderer}) and normal alpha blending: smooth at any distance or
 * grazing angle (no texture to minify) and colour-capped, so overlapping pools can never blow out
 * to white.
 *
 * <p>
 * The pool is painted twice per locomotive ({@link #HALO} + {@link #CORE}) to get a diffuse core
 * with a wide, faint wash around it; the falloff reaches zero before the quad edge
 * ({@link #FALLOFF_CUTOFF}) so no silhouette is ever visible.
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

    /**
     * The falloff reaches zero at this fraction of the quad half-size, so the outer margin of the
     * quad is fully transparent and no edge or corner is ever visible.
     */
    public static final float FALLOFF_CUTOFF = 0.9f;

    /**
     * Sharpness of the gaussian falloff (higher = tighter core). Around 4.5 gives a fat, diffuse
     * profile: still 30% of the peak at half the cutoff radius, which is what makes the pool read
     * as a soft wash instead of a disc.
     */
    public static final float FALLOFF_SHARPNESS = 4.5f;

    /**
     * One elliptical layer of the pool: quad size, peak alpha at full night and how far ahead of
     * the rendered locomotive its centre sits.
     */
    public record Layer(float width, float length, float opacity, float ahead) {
    }

    /** Wide, faint wash that diffuses the light far beyond the core. */
    public static final Layer HALO = new Layer(8.0f, 14.0f, 0.18f, 3.2f);

    /** Brighter core right in front of the locomotive. */
    public static final Layer CORE = new Layer(3.6f, 7.0f, 0.28f, 1.8f);

    /** Layers painted back to front. */
    public static final List<Layer> LAYERS = List.of(HALO, CORE);

    private HeadlightGlows() {}

    /** World X of the centre of {@code layer}, in front of the locomotive. */
    public static float centerX(Headlights.Source source, Layer layer) {
        return source.x() + source.dirX() * layer.ahead();
    }

    /** World Z of the centre of {@code layer}, in front of the locomotive. */
    public static float centerZ(Headlights.Source source, Layer layer) {
        return source.z() + source.dirZ() * layer.ahead();
    }

    /**
     * Yaw in degrees that aligns the pool's long axis (+Z) with the locomotive heading, so the
     * stretched falloff points the beam where the locomotive looks.
     */
    public static float yawDegrees(Headlights.Source source) {
        return (float) Math.toDegrees(Math.atan2(source.dirX(), source.dirZ()));
    }

    /** Peak alpha of a layer at a day/night ratio: 0 in daylight, the layer peak at night. */
    public static float opacity(Layer layer, double dayNightRatio) {
        return layer.opacity() * VisualPalette.lightsOnFactor(dayNightRatio);
    }
}
