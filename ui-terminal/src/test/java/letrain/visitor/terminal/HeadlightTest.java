package letrain.visitor.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import letrain.map.Dir;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Locomotive headlight cone")
class HeadlightTest {

    @Test
    @DisplayName("the tile in front is lit, behind and out of range are not")
    void should_LightTheCone() {
        assertTrue(Headlight.factor(1, 0, Dir.E) > 0.5f);
        assertTrue(Headlight.factor(2, 0, Dir.E) > 0f);
        assertEquals(0f, Headlight.factor(-1, 0, Dir.E));
        assertEquals(0f, Headlight.factor(-5, 0, Dir.E));
        assertEquals(0f, Headlight.factor(Headlight.RADIUS + 1, 0, Dir.E));
    }

    @Test
    @DisplayName("the beam narrows with the angle and fades with the distance")
    void should_FadeWithAngleAndDistance() {
        float near = Headlight.factor(1, 0, Dir.E);
        float far = Headlight.factor(2, 0, Dir.E);
        assertTrue(near > far, "near=" + near + " far=" + far);

        assertTrue(Headlight.factor(2, 1, Dir.E) > 0f);
        assertEquals(0f, Headlight.factor(2, 2, Dir.E),
                "40 degrees off the axis is outside the cone");
    }

    @Test
    @DisplayName("the locomotive's own tile is fully lit")
    void should_LightOwnTile() {
        assertEquals(1f, Headlight.factor(0, 0, Dir.E));
    }

    @Test
    @DisplayName("map directions are honored: the beam follows where the locomotive points")
    void should_FollowMapDirection() {
        assertTrue(Headlight.factor(0, -2, Dir.N) > 0f, "north is y-");
        assertTrue(Headlight.factor(2, 0, Dir.E) > 0f);
        assertEquals(0f, Headlight.factor(0, 3, Dir.N), "south is behind a northbound locomotive");
        assertEquals(0f, Headlight.factor(0, 0, (Dir) null), "no direction, no beam");
        assertTrue(Headlight.factor(2, -2, Dir.NE) > 0f, "diagonals are normalized");
    }
}
