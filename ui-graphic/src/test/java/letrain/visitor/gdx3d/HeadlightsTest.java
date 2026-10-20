package letrain.visitor.gdx3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.badlogic.gdx.math.Vector3;
import java.util.ArrayList;
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

    @Test
    @DisplayName("#820: the per-frame overload reuses and fills the caller's list")
    void should_ReuseTheOutputList() {
        Headlights.Source near = new Headlights.Source(2.5f, 2.5f, 1f, 0f);
        Headlights.Source mid = new Headlights.Source(10.5f, 10.5f, 1f, 0f);
        Headlights.Source far = new Headlights.Source(40.5f, 40.5f, 1f, 0f);
        List<Headlights.Source> out = new ArrayList<>(List.of(far));

        List<Headlights.Source> picked =
                Headlights.nearestTo(List.of(far, near, mid), new Vector3(), 2, out);

        assertSame(out, picked, "no new list is allocated");
        assertEquals(List.of(near, mid), picked);
    }

    @Test
    @DisplayName("with more sources than max it keeps the nearest ones, nearest first")
    void should_PickTheNearestTopN() {
        Headlights.Source d5 = new Headlights.Source(50f, 0f, 1f, 0f);
        Headlights.Source d1 = new Headlights.Source(10f, 0f, 1f, 0f);
        Headlights.Source d4 = new Headlights.Source(40f, 0f, 1f, 0f);
        Headlights.Source d2 = new Headlights.Source(20f, 0f, 1f, 0f);
        Headlights.Source d3 = new Headlights.Source(30f, 0f, 1f, 0f);

        List<Headlights.Source> picked =
                Headlights.nearestTo(List.of(d5, d1, d4, d2, d3), new Vector3(), 3);

        assertEquals(List.of(d1, d2, d3), picked);
    }

    @Test
    @DisplayName("with everything fitting, the input order is copied as-is (no distance sort)")
    void should_CopyTheInputOrderWhenEverythingFits() {
        Headlights.Source far = new Headlights.Source(40f, 0f, 1f, 0f);
        Headlights.Source near = new Headlights.Source(1f, 0f, 1f, 0f);

        // Contract of the top-N overload: the sort only runs when some source has to be dropped.
        assertEquals(List.of(far, near),
                Headlights.nearestTo(List.of(far, near), new Vector3(), 2));
    }

    @Test
    @DisplayName("a non-positive max yields an empty list and clears the caller's list")
    void should_ReturnEmptyForNonPositiveMax() {
        Headlights.Source near = new Headlights.Source(1f, 0f, 1f, 0f);
        List<Headlights.Source> out = new ArrayList<>(List.of(near));

        assertEquals(List.of(), Headlights.nearestTo(List.of(near), new Vector3(), 0));
        assertSame(out, Headlights.nearestTo(List.of(near), new Vector3(), 0, out));
        assertEquals(List.of(), out, "the reusable list is cleared first");

        out.add(near);
        Headlights.nearestTo(List.of(near), new Vector3(), -1, out);
        assertEquals(List.of(), out);
    }

    @Test
    @DisplayName("no sources (or a null list) clears the caller's reusable list")
    void should_ClearTheOutputWhenThereAreNoSources() {
        List<Headlights.Source> out =
                new ArrayList<>(List.of(new Headlights.Source(1f, 1f, 1f, 0f)));

        assertSame(out, Headlights.nearestTo(null, new Vector3(), 4, out));
        assertEquals(List.of(), out);

        out.add(new Headlights.Source(2f, 2f, 1f, 0f));
        Headlights.nearestTo(List.of(), new Vector3(), 4, out);
        assertEquals(List.of(), out);
    }

    @Test
    @DisplayName("an equal-distance tie keeps the source inserted first (stable top-N)")
    void should_KeepTheEarlierSourceOnADistanceTie() {
        Headlights.Source first = new Headlights.Source(10f, 0f, 1f, 0f);
        Headlights.Source second = new Headlights.Source(-10f, 0f, 1f, 0f);

        assertEquals(List.of(first),
                Headlights.nearestTo(List.of(first, second), new Vector3(), 1));
    }
}
