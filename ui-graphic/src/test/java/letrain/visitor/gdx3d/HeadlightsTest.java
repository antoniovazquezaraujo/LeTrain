package letrain.visitor.gdx3d;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.badlogic.gdx.math.Vector3;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Headlight light-pool selection")
class HeadlightsTest {

    @Test
    @DisplayName("picks the nearest rendered sources to the camera, nearest first")
    void should_PickNearest() {
        Headlights.Source near = new Headlights.Source(2.5f, 2.5f, 1f, 0f);
        Headlights.Source mid = new Headlights.Source(10.5f, 10.5f, 1f, 0f);
        Headlights.Source far = new Headlights.Source(40.5f, 40.5f, 1f, 0f);

        List<Headlights.Source> picked =
                Headlights.nearestTo(List.of(far, near, mid), new Vector3(), 2);

        assertEquals(List.of(near, mid), picked);
    }

    @Test
    @DisplayName("keeps every source when the pool is big enough")
    void should_KeepAll_WhenTheyFit() {
        Headlights.Source a = new Headlights.Source(1.5f, 0.5f, 1f, 0f);
        Headlights.Source b = new Headlights.Source(0.5f, 3.5f, 0f, 1f);

        assertEquals(List.of(a, b), Headlights.nearestTo(List.of(a, b), new Vector3(), 4));
    }

    @Test
    @DisplayName("no sources means no lights")
    void should_ReturnEmpty_When_NoSources() {
        assertEquals(List.of(), Headlights.nearestTo(null, new Vector3(), 4));
    }
}
