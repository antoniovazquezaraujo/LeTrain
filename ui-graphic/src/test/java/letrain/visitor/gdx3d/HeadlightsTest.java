package letrain.visitor.gdx3d;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.badlogic.gdx.math.Vector3;
import java.util.List;
import letrain.map.Point;
import letrain.vehicle.rail.impl.Locomotive;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Headlight light-pool selection")
class HeadlightsTest {

    @Test
    @DisplayName("picks the nearest locomotives to the camera, nearest first")
    void should_PickNearest() {
        Locomotive near = locoAt(2, 2);
        Locomotive mid = locoAt(10, 10);
        Locomotive far = locoAt(40, 40);

        List<Locomotive> picked = Headlights.nearestTo(List.of(far, near, mid), new Vector3(), 2);

        assertEquals(List.of(near, mid), picked);
    }

    @Test
    @DisplayName("keeps every locomotive when the pool is big enough")
    void should_KeepAll_WhenTheyFit() {
        Locomotive a = locoAt(1, 0);
        Locomotive b = locoAt(0, 3);

        assertEquals(List.of(a, b), Headlights.nearestTo(List.of(a, b), new Vector3(), 4));
    }

    @Test
    @DisplayName("no locomotives means no lights")
    void should_ReturnEmpty_When_NoLocomotives() {
        assertEquals(List.of(), Headlights.nearestTo(null, new Vector3(), 4));
    }

    private static Locomotive locoAt(int x, int y) {
        Locomotive locomotive = new Locomotive(x, "L" + x, "GREEN_BRIGHT");
        locomotive.setPosition(new Point(x, y));
        return locomotive;
    }
}
