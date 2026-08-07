package letrain.itinerary;

import letrain.itinerary.impl.AutoPilotImpl;
import letrain.itinerary.impl.ItineraryImpl;
import letrain.itinerary.impl.WaypointImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import letrain.vehicle.rail.impl.Train;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("AutoPilot")
class AutoPilotTest {

    private AutoPilot autopilot;
    private Train train;
    private Itinerary itinerary;

    @BeforeEach
    void setUp() {
        train = mock(Train.class);
        autopilot = new AutoPilotImpl(train);
        itinerary = new ItineraryImpl();
        itinerary.addWaypoint(new WaypointImpl(Waypoint.Type.STATION, 1, List.of()));
        itinerary.addWaypoint(new WaypointImpl(Waypoint.Type.STATION, 2, List.of()));
    }

    @Test
    @DisplayName("should start in IDLE mode")
    void startsIdle() {
        assertEquals(AutoPilot.Mode.IDLE, autopilot.mode());
    }

    @Test
    @DisplayName("should not activate without itinerary")
    void needsItinerary() {
        assertFalse(autopilot.activate());
    }

    @Test
    @DisplayName("should not activate without pathfinder")
    void needsPathfinder() {
        autopilot.setItinerary(itinerary);
        assertFalse(autopilot.activate());
    }

    @Test
    @DisplayName("should activate with itinerary and pathfinder")
    void activates() {
        when(train.getSpeed()).thenReturn(0);
        autopilot.setItinerary(itinerary);
        autopilot.setPathfinder(new AStarPathfinder(null));
        assertTrue(autopilot.activate());
        assertEquals(AutoPilot.Mode.FOLLOWING, autopilot.mode());
    }

    @Test
    @DisplayName("should deactivate back to IDLE")
    void deactivates() {
        autopilot.deactivate();
        assertEquals(AutoPilot.Mode.IDLE, autopilot.mode());
    }

    @Test
    @DisplayName("should track assigned itinerary")
    void holdsItinerary() {
        autopilot.setItinerary(itinerary);
        assertTrue(autopilot.itinerary().isPresent());
        assertEquals(itinerary, autopilot.itinerary().get());
    }

    @Test
    @DisplayName("should start with waypoint index 0")
    void startsAtFirstWaypoint() {
        assertEquals(0, autopilot.currentWaypointIndex());
    }

    @Test
    @DisplayName("should loop waypoint index cyclically")
    void advanceWaypointLoopsCyclically() {
        autopilot.setItinerary(itinerary);
        assertEquals(0, autopilot.currentWaypointIndex());
        autopilot.advanceWaypoint();
        assertEquals(1, autopilot.currentWaypointIndex());
        autopilot.advanceWaypoint();
        assertEquals(0, autopilot.currentWaypointIndex());
    }
}
