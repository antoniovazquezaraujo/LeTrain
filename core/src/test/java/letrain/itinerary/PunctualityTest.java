package letrain.itinerary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * ADR-022 phase 2b: punctuality deltas in game minutes. Convention: positive = late, negative =
 * early; current = most recent measurement; average and max over all measured deltas.
 */
@DisplayName("Punctuality history (ADR-022 phase 2b)")
class PunctualityTest {

    @Test
    @DisplayName("empty history has no deltas and no text")
    void emptyHistory() {
        Punctuality punctuality = new Punctuality();

        assertTrue(punctuality.isEmpty());
        assertTrue(punctuality.currentDelta().isEmpty());
        assertTrue(punctuality.averageDelta().isEmpty());
        assertTrue(punctuality.maxDelta().isEmpty());
        assertEquals("", punctuality.describe());
    }

    @Test
    @DisplayName("departure completes the last arrival of the same stop")
    void departureCompletesSameStop() {
        Punctuality punctuality = new Punctuality();

        punctuality.recordArrival(Waypoint.Type.STATION, 2, 2);
        punctuality.recordDeparture(Waypoint.Type.STATION, 2, 5);

        List<Punctuality.Stop> stops = punctuality.stops();
        assertEquals(1, stops.size(), "arrival and departure must share one stop entry");
        assertEquals(2, stops.get(0).arrival().orElseThrow());
        assertEquals(5, stops.get(0).departure().orElseThrow());
    }

    @Test
    @DisplayName("a departure without arrival opens its own stop")
    void departureWithoutArrival() {
        Punctuality punctuality = new Punctuality();

        punctuality.recordDeparture(Waypoint.Type.SENSOR, 5, 1);

        assertEquals(1, punctuality.stops().size());
        assertTrue(punctuality.stops().get(0).arrival().isEmpty());
        assertEquals(1, punctuality.stops().get(0).departure().orElseThrow());
    }

    @Test
    @DisplayName("current is the latest measurement; average and max cover all deltas")
    void aggregates() {
        Punctuality punctuality = new Punctuality();
        punctuality.recordArrival(Waypoint.Type.STATION, 1, 2);
        punctuality.recordDeparture(Waypoint.Type.STATION, 1, 5);

        assertEquals(5, punctuality.currentDelta().orElseThrow(), "latest event is the departure");
        assertEquals((2 + 5) / 2.0, punctuality.averageDelta().orElseThrow(), 1e-9);
        assertEquals(5, punctuality.maxDelta().orElseThrow());

        punctuality.recordArrival(Waypoint.Type.STATION, 2, -1);
        assertEquals(-1, punctuality.currentDelta().orElseThrow(), "latest event is the arrival");
        assertEquals((2 + 5 - 1) / 3.0, punctuality.averageDelta().orElseThrow(), 1e-9);
        assertEquals(5, punctuality.maxDelta().orElseThrow());
    }

    @Test
    @DisplayName("describe prints signed deltas per stop plus current, average and max")
    void describeFormat() {
        Punctuality punctuality = new Punctuality();
        punctuality.recordArrival(Waypoint.Type.STATION, 2, 2);
        punctuality.recordDeparture(Waypoint.Type.STATION, 2, 5);
        punctuality.recordArrival(Waypoint.Type.SENSOR, 3, -1);

        String text = punctuality.describe();

        List<String> lines = text.lines().toList();
        assertEquals("Punctuality:", lines.get(0));
        assertEquals("  Station 2: arrival +2 min, departure +5 min", lines.get(1));
        assertEquals("  Sensor 3: arrival -1 min", lines.get(2));
        assertEquals("  Current: -1 min", lines.get(3));
        assertEquals("  Average: +2.0 min", lines.get(4));
        assertEquals("  Max: +5 min", lines.get(5));
        assertTrue(text.endsWith("\n"));
    }

    @Test
    @DisplayName("clear forgets the history")
    void clearResets() {
        Punctuality punctuality = new Punctuality();
        punctuality.recordArrival(Waypoint.Type.STATION, 1, 3);

        punctuality.clear();

        assertTrue(punctuality.isEmpty());
        assertFalse(punctuality.stops().iterator().hasNext());
    }
}
