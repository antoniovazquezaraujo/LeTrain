package letrain.visitor.gdx3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import letrain.palette.VisualPalette;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Headlight ground pools")
class HeadlightGlowsTest {

    private static final float EPSILON = 1e-4f;

    @Test
    @DisplayName("each layer centre sits ahead of the locomotive")
    void should_PlaceLayerCentres_Ahead() {
        Headlights.Source source = new Headlights.Source(3f, 5f, 1f, 0f);

        assertEquals(3f + HeadlightGlows.CORE.ahead(),
                HeadlightGlows.centerX(source, HeadlightGlows.CORE), EPSILON);
        assertEquals(5f, HeadlightGlows.centerZ(source, HeadlightGlows.CORE), EPSILON);
        assertEquals(3f + HeadlightGlows.HALO.ahead(),
                HeadlightGlows.centerX(source, HeadlightGlows.HALO), EPSILON);
        assertEquals(5f, HeadlightGlows.centerZ(source, HeadlightGlows.HALO), EPSILON);
    }

    @Test
    @DisplayName("the pool long axis follows the heading")
    void should_YawFollowHeading() {
        assertEquals(90f, HeadlightGlows.yawDegrees(new Headlights.Source(0, 0, 1f, 0f)), EPSILON);
        assertEquals(-90f, HeadlightGlows.yawDegrees(new Headlights.Source(0, 0, -1f, 0f)),
                EPSILON);
        assertEquals(0f, HeadlightGlows.yawDegrees(new Headlights.Source(0, 0, 0f, 1f)), EPSILON);
        assertEquals(180f, HeadlightGlows.yawDegrees(new Headlights.Source(0, 0, 0f, -1f)),
                EPSILON);
    }

    @Test
    @DisplayName("layer opacity is off by day and peaks at full night")
    void should_FadeWithDayNightRatio() {
        assertEquals(0f, HeadlightGlows.opacity(HeadlightGlows.CORE, 0.0), EPSILON);
        assertEquals(0f, HeadlightGlows.opacity(HeadlightGlows.CORE, VisualPalette.LIGHTS_ON_RATIO),
                EPSILON);
        assertEquals(HeadlightGlows.CORE.opacity(),
                HeadlightGlows.opacity(HeadlightGlows.CORE, 1.0), EPSILON);
        assertTrue(HeadlightGlows.opacity(HeadlightGlows.CORE, 0.9) > HeadlightGlows
                .opacity(HeadlightGlows.CORE, 0.4));
    }

    @Test
    @DisplayName("opacity never exceeds the peak when the ratio is out of range")
    void should_ClampOpacity() {
        assertEquals(HeadlightGlows.CORE.opacity(),
                HeadlightGlows.opacity(HeadlightGlows.CORE, 2.0), EPSILON);
        assertEquals(0f, HeadlightGlows.opacity(HeadlightGlows.CORE, -1.0), EPSILON);
    }

    @Test
    @DisplayName("the glow stays diffuse, elongated and far from opaque")
    void should_StaySoftAndElongated() {
        // The falloff reaches zero at the cutoff and the gaussian is fat: no silhouette, no disc.
        assertTrue(HeadlightGlows.FALLOFF_CUTOFF < 1f,
                "the falloff must reach zero before the quad edge");
        assertTrue(HeadlightGlows.FALLOFF_SHARPNESS > 1f,
                "the gaussian must stay diffuse instead of collapsing into a disc");
        // Even with both layers stacked the pool stays translucent: no white blowout.
        float combined =
                1f - (1f - HeadlightGlows.HALO.opacity()) * (1f - HeadlightGlows.CORE.opacity());
        assertTrue(combined < 0.5f, "combined peak alpha must stay low, was " + combined);
        // The halo diffuses far beyond the core and both read as beams, not as blobs.
        assertTrue(HeadlightGlows.HALO.width() > HeadlightGlows.CORE.width());
        assertTrue(HeadlightGlows.HALO.length() > HeadlightGlows.CORE.length());
        assertTrue(HeadlightGlows.CORE.length() > HeadlightGlows.CORE.width());
        assertTrue(HeadlightGlows.HALO.length() > HeadlightGlows.HALO.width());
    }

    @Test
    @DisplayName("the pool plane clears the per-cell ballast boxes and stays under the rails")
    void should_ClearTheBallast() {
        // Ballast: a 0.1-high box centred at y=0.03 -> top 0.08. If the plane sat lower, each
        // per-cell ballast box would punch a grid-shaped hole in the glow.
        assertTrue(HeadlightGlows.GROUND_Y > 0.08f,
                "the pool plane must sit above the ballast top");
        // Rails: a 0.2-high box centred at y=0.08 -> top 0.18. Below it, the rails keep occluding
        // the pool instead of being painted by it.
        assertTrue(HeadlightGlows.GROUND_Y < 0.18f, "the pool plane must stay below the rails");
    }
}
