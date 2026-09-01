package letrain.itinerary;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import letrain.itinerary.impl.ItineraryImpl;
import letrain.itinerary.impl.WaypointImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Itinerary")
class ItineraryTest {

    @Test
    @DisplayName("should create empty itinerary")
    void emptyItinerary() {
        Itinerary it = new ItineraryImpl();
        assertTrue(it.waypoints().isEmpty());
    }

    @Test
    @DisplayName("should add waypoints")
    void addWaypoints() {
        Itinerary it = new ItineraryImpl();
        it.addWaypoint(new WaypointImpl(Waypoint.Type.STATION, 1, List.of(WaypointCommand.LOAD)));
        it.addWaypoint(new WaypointImpl(Waypoint.Type.SENSOR, 7, List.of()));
        it.addWaypoint(new WaypointImpl(Waypoint.Type.STATION, 5, List.of(WaypointCommand.UNLOAD)));

        assertEquals(3, it.waypoints().size());
        assertEquals(1, it.waypoints().get(0).targetId());
        assertEquals(7, it.waypoints().get(1).targetId());
        assertEquals(5, it.waypoints().get(2).targetId());
    }

    @Test
    @DisplayName("should not be valid with less than 2 waypoints")
    void needsTwoWaypoints() {
        Itinerary it = new ItineraryImpl();
        it.addWaypoint(new WaypointImpl(Waypoint.Type.STATION, 1, List.of()));
        assertFalse(it.isValid());
    }

    @Test
    @DisplayName("should be valid with 2 or more waypoints")
    void validWithTwoWaypoints() {
        Itinerary it = new ItineraryImpl();
        it.addWaypoint(new WaypointImpl(Waypoint.Type.STATION, 1, List.of()));
        it.addWaypoint(new WaypointImpl(Waypoint.Type.STATION, 2, List.of()));
        assertTrue(it.isValid());
    }
}
