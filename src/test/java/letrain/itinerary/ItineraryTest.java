package letrain.itinerary;

import letrain.itinerary.impl.ItineraryImpl;
import letrain.itinerary.impl.WaypointImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

@DisplayName("Itinerary")
class ItineraryTest {

    @Test
    @DisplayName("should create empty itinerary")
    void emptyItinerary() {
        Itinerary it = new ItineraryImpl();
        assertTrue(it.waypoints().isEmpty());
        assertEquals(Itinerary.State.CREATED, it.state());
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

    @Test
    @DisplayName("should track current waypoint index")
    void currentIndex() {
        Itinerary it = new ItineraryImpl();
        it.addWaypoint(new WaypointImpl(Waypoint.Type.STATION, 1, List.of()));
        it.addWaypoint(new WaypointImpl(Waypoint.Type.STATION, 2, List.of()));

        assertEquals(0, it.currentIndex());
        it.advance();
        assertEquals(1, it.currentIndex());
        it.advance();
        assertEquals(2, it.currentIndex());
        assertEquals(Itinerary.State.DONE, it.state());
    }

    @Test
    @DisplayName("should be done when all waypoints visited")
    void doneWhenAllVisited() {
        Itinerary it = new ItineraryImpl();
        it.addWaypoint(new WaypointImpl(Waypoint.Type.STATION, 1, List.of()));
        it.addWaypoint(new WaypointImpl(Waypoint.Type.STATION, 2, List.of()));

        it.advance();
        it.advance();
        assertEquals(Itinerary.State.DONE, it.state());
    }

    @Test
    @DisplayName("should return current waypoint")
    void currentWaypoint() {
        Itinerary it = new ItineraryImpl();
        Waypoint wp1 = new WaypointImpl(Waypoint.Type.STATION, 1, List.of());
        Waypoint wp2 = new WaypointImpl(Waypoint.Type.STATION, 2, List.of());
        it.addWaypoint(wp1);
        it.addWaypoint(wp2);

        assertEquals(wp1, it.currentWaypoint().orElse(null));
        it.advance();
        assertEquals(wp2, it.currentWaypoint().orElse(null));
    }

    @Test
    @DisplayName("should allow assigning to multiple trains")
    void multipleTrains() {
        Itinerary it = new ItineraryImpl();
        it.assignTrain(1);
        it.assignTrain(2);
        it.assignTrain(3);

        assertTrue(it.assignedTrains().contains(1));
        assertTrue(it.assignedTrains().contains(2));
        assertTrue(it.assignedTrains().contains(3));
        assertEquals(3, it.assignedTrains().size());
    }

    @Test
    @DisplayName("should allow unassigning trains")
    void unassignTrain() {
        Itinerary it = new ItineraryImpl();
        it.assignTrain(1);
        it.assignTrain(2);
        it.unassignTrain(1);

        assertFalse(it.assignedTrains().contains(1));
        assertTrue(it.assignedTrains().contains(2));
    }
}
