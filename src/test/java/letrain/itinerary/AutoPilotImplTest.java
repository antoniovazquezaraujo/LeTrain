package letrain.itinerary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import letrain.itinerary.impl.AutoPilotImpl;
import letrain.itinerary.impl.ItineraryImpl;
import letrain.itinerary.impl.WaypointImpl;
import letrain.segments.Segment;
import letrain.vehicle.rail.impl.Train;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AutoPilotImpl")
class AutoPilotImplTest {

    private AutoPilot autopilot;
    private Train train;
    private TrainActionManager actionManager;
    private SegmentPathfinder pathfinder;
    private Itinerary itinerary;
    private Segment segA, segB;

    @BeforeEach
    void setUp() {
        train = mock(Train.class);
        actionManager = mock(TrainActionManager.class);
        pathfinder = mock(SegmentPathfinder.class);
        autopilot = new AutoPilotImpl(train, actionManager);
        autopilot.setPathfinder(pathfinder);

        segA = mock(Segment.class);
        segB = mock(Segment.class);

        itinerary = new ItineraryImpl();
        itinerary.addWaypoint(new WaypointImpl(Waypoint.Type.STATION, 1, List.of()));
        itinerary.addWaypoint(new WaypointImpl(Waypoint.Type.STATION, 2, List.of()));
    }

    @Test
    @DisplayName("should activate even if train is moving")
    void activatesWhenMoving() {
        when(train.getSpeed()).thenReturn(5);
        autopilot.setItinerary(itinerary);
        assertTrue(autopilot.activate());
        assertEquals(AutoPilot.Mode.FOLLOWING, autopilot.mode());
    }

    @Test
    @DisplayName("should activate when stopped with valid itinerary")
    void activatesWhenStopped() {
        when(train.getSpeed()).thenReturn(0);
        autopilot.setItinerary(itinerary);
        assertTrue(autopilot.activate());
        assertEquals(AutoPilot.Mode.FOLLOWING, autopilot.mode());
    }

    @Test
    @DisplayName("should deactivate cleanly")
    void deactivatesCleanly() {
        autopilot.deactivate();
        assertEquals(AutoPilot.Mode.IDLE, autopilot.mode());
    }
}
