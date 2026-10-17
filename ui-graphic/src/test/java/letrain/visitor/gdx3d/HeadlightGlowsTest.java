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
    @DisplayName("the pool centre sits in front of the locomotive")
    void should_PlacePoolCentre_Ahead() {
        Headlights.Source source = new Headlights.Source(3f, 5f, 1f, 0f);

        assertEquals(3f + HeadlightGlows.CENTER_AHEAD, HeadlightGlows.centerX(source), EPSILON);
        assertEquals(5f, HeadlightGlows.centerZ(source), EPSILON);
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
    @DisplayName("opacity is off by day and peaks at full night")
    void should_FadeWithDayNightRatio() {
        assertEquals(0f, HeadlightGlows.opacity(0.0), EPSILON);
        assertEquals(0f, HeadlightGlows.opacity(VisualPalette.LIGHTS_ON_RATIO), EPSILON);
        assertEquals(HeadlightGlows.PEAK_OPACITY, HeadlightGlows.opacity(1.0), EPSILON);
        assertTrue(HeadlightGlows.opacity(0.9) > HeadlightGlows.opacity(0.4));
    }

    @Test
    @DisplayName("opacity never exceeds the peak when the ratio is out of range")
    void should_ClampOpacity() {
        assertEquals(HeadlightGlows.PEAK_OPACITY, HeadlightGlows.opacity(2.0), EPSILON);
        assertEquals(0f, HeadlightGlows.opacity(-1.0), EPSILON);
    }

    @Test
    @DisplayName("the pool stays elongated and below full opacity")
    void should_StaySoftAndElongated() {
        assertTrue(HeadlightGlows.LENGTH > HeadlightGlows.WIDTH,
                "the pool must read as a beam, not as a round blob");
        assertTrue(HeadlightGlows.PEAK_OPACITY < 1f,
                "alpha must stay below 1 so overlapping pools never blow out to white");
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
