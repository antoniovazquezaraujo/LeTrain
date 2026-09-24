package letrain.itinerary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import letrain.itinerary.impl.AutoPilotImpl;
import letrain.itinerary.impl.ItineraryImpl;
import letrain.itinerary.impl.WaypointImpl;
import letrain.mvp.impl.Model;
import letrain.segments.Segment;
import letrain.time.GameTime;
import letrain.time.impl.SimpleGameClock;
import letrain.utils.impl.SimulationScheduler;
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

    @Test
    @DisplayName("a due departure is recorded once even if retention is re-checked (reload guard)")
    void retainUntilDeparture_recordsDueDepartureOnce() {
        SimpleGameClock clock = new SimpleGameClock();
        clock.setTime(new GameTime(1, 9, 0));
        Model model = mock(Model.class);
        when(model.getGameClock()).thenReturn(clock);
        when(model.getScheduler()).thenReturn(new SimulationScheduler());
        when(train.getModel()).thenReturn(model);

        Itinerary withDeparture = new ItineraryImpl();
        withDeparture.addWaypoint(new WaypointImpl(Waypoint.Type.STATION, 1, Optional.empty(),
                List.of(), Optional.empty(), Optional.of(LocalTime.of(8, 55))));
        withDeparture.addWaypoint(new WaypointImpl(Waypoint.Type.STATION, 2, List.of()));
        autopilot.setItinerary(withDeparture);
        assertTrue(autopilot.activate());

        assertFalse(autopilot.retainUntilDeparture(), "already late: it must release");
        assertFalse(autopilot.retainUntilDeparture(), "a second check must not duplicate");

        Punctuality punctuality = autopilot.punctuality().orElseThrow();
        assertEquals(1, punctuality.stops().size(), punctuality.describe());
        assertEquals(5, punctuality.stops().get(0).departure().orElseThrow(),
                "09:00 vs 08:55 = five minutes late");
    }
}
