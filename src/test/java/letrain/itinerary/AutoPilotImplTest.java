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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AutoPilotImpl")
class AutoPilotImplTest {

    private AutoPilot autopilot;
    private AutoPilotContext ctx;
    private TrainActionManager actionManager;
    private SegmentPathfinder pathfinder;
    private Itinerary itinerary;
    private Segment segA, segB;

    @BeforeEach
    void setUp() {
        ctx = mock(AutoPilotContext.class);
        actionManager = mock(TrainActionManager.class);
        pathfinder = mock(SegmentPathfinder.class);
        autopilot = new AutoPilotImpl(ctx, actionManager);
        autopilot.setPathfinder(pathfinder);

        segA = mock(Segment.class);
        segB = mock(Segment.class);

        itinerary = new ItineraryImpl();
        itinerary.addWaypoint(new WaypointImpl(Waypoint.Type.STATION, 1, List.of()));
        itinerary.addWaypoint(new WaypointImpl(Waypoint.Type.STATION, 2, List.of()));
    }

    @Test
    @DisplayName("should not activate if train is moving")
    void needsStopToActivate() {
        when(ctx.currentSpeed()).thenReturn(5);
        autopilot.setItinerary(itinerary);
        assertFalse(autopilot.activate());
    }

    @Test
    @DisplayName("should activate when stopped with valid itinerary")
    void activatesWhenStopped() {
        when(ctx.currentSpeed()).thenReturn(0);
        autopilot.setItinerary(itinerary);
        assertTrue(autopilot.activate());
        assertEquals(AutoPilot.Mode.FOLLOWING, autopilot.mode());
    }

    @Test
    @DisplayName("should calculate route on activation and orient fork if segment changed")
    void calculatesRouteAndOrientsFork() {
        when(ctx.currentSpeed()).thenReturn(0);
        when(ctx.currentSegment()).thenReturn(segA);
        when(ctx.targetSegment(any())).thenReturn(segB);
        when(ctx.isAtTarget(any())).thenReturn(false);
        when(ctx.isSegmentFree(any())).thenReturn(true);
        when(pathfinder.find(any(), any(), any())).thenReturn(List.of(segA, segB));

        autopilot.setItinerary(itinerary);
        autopilot.activate();

        verify(pathfinder).find(eq(segA), eq(segB), any());
        verify(actionManager).ensureForkRoute(segA, segB);
    }

    @Test
    @DisplayName("should keep mode FOLLOWING if route calculation fails")
    void failsRoutingGracefully() {
        when(ctx.currentSpeed()).thenReturn(0);
        when(ctx.currentSegment()).thenReturn(segA);
        when(ctx.targetSegment(any())).thenReturn(segB);
        when(ctx.isAtTarget(any())).thenReturn(false);
        when(pathfinder.find(any(), any(), any())).thenReturn(List.of());

        autopilot.setItinerary(itinerary);
        autopilot.activate();
        assertEquals(AutoPilot.Mode.FOLLOWING, autopilot.mode());
    }

    @Test
    @DisplayName("should calculate route immediately after executing waypoint if itinerary not done")
    void calculatesRouteAfterWaypoint() {
        when(ctx.currentSpeed()).thenReturn(0);
        when(ctx.currentSegment()).thenReturn(segA);
        when(ctx.targetSegment(any())).thenReturn(segB);
        Waypoint wp1 = itinerary.waypoints().get(0);
        Waypoint wp2 = itinerary.waypoints().get(1);
        when(ctx.isAtTarget(wp1)).thenReturn(true);
        when(ctx.isAtTarget(wp2)).thenReturn(false);
        when(pathfinder.find(any(), any(), any())).thenReturn(List.of(segA, segB));

        autopilot.setItinerary(itinerary);
        autopilot.activate();

        verify(pathfinder).find(eq(segA), eq(segB), any());
        assertEquals(List.of(segA, segB), autopilot.currentRoute());
    }

    @Test
    @DisplayName("should deactivate cleanly")
    void deactivatesCleanly() {
        autopilot.deactivate();
        assertEquals(AutoPilot.Mode.IDLE, autopilot.mode());
    }

    @Test
    @DisplayName("should execute waypoint commands when target is reached")
    void should_ExecuteCommands_When_WaypointReached() {
        // Arrange
        TrainActionManager actionManager = mock(TrainActionManager.class);
        autopilot = new AutoPilotImpl(ctx, actionManager);
        autopilot.setPathfinder(pathfinder);

        WaypointCommand cmdSpeed = WaypointCommand.speed(5);
        WaypointCommand cmdLoad = WaypointCommand.LOAD;
        Waypoint wp = new letrain.itinerary.impl.WaypointImpl(
                Waypoint.Type.STATION, 1, List.of(cmdSpeed, cmdLoad));
        Waypoint wp2 = new letrain.itinerary.impl.WaypointImpl(
                Waypoint.Type.STATION, 2, List.of());

        Itinerary multiWpItin = new letrain.itinerary.impl.ItineraryImpl();
        multiWpItin.addWaypoint(wp);
        multiWpItin.addWaypoint(wp2);

        when(ctx.currentSpeed()).thenReturn(0);
        when(ctx.currentSegment()).thenReturn(segA);
        when(ctx.isAtTarget(wp)).thenReturn(true);

        autopilot.setItinerary(multiWpItin);
        assertTrue(autopilot.activate());

        // Assert
        verify(actionManager).forceSegmentReset();
        verify(actionManager).executeCommand(cmdSpeed);
        verify(actionManager).executeCommand(cmdLoad);
        assertEquals(AutoPilot.Mode.FOLLOWING, autopilot.mode()); // Advanced to the next waypoint, still following
    }

    @Test
    @DisplayName("should pause and wait when WAIT command is present, then execute remaining commands")
    void should_HandleWaitCommand_When_WaypointReached() {
        // Arrange
        TrainActionManager actionManager = mock(TrainActionManager.class);
        autopilot = new AutoPilotImpl(ctx, actionManager);
        autopilot.setPathfinder(pathfinder);

        WaypointCommand cmdStop = WaypointCommand.speed(0);
        WaypointCommand cmdWait = WaypointCommand.waitSeconds(2);
        WaypointCommand cmdSpeed = WaypointCommand.speed(3);
        Waypoint wp = new letrain.itinerary.impl.WaypointImpl(
                Waypoint.Type.STATION, 1, List.of(cmdStop, cmdWait, cmdSpeed));
        Waypoint wp2 = new letrain.itinerary.impl.WaypointImpl(
                Waypoint.Type.STATION, 2, List.of());

        Itinerary multiWpItin = new letrain.itinerary.impl.ItineraryImpl();
        multiWpItin.addWaypoint(wp);
        multiWpItin.addWaypoint(wp2);

        when(ctx.currentSpeed()).thenReturn(0);
        when(ctx.currentSegment()).thenReturn(segA);
        when(ctx.isAtTarget(wp)).thenReturn(true);

        autopilot.setItinerary(multiWpItin);
        assertTrue(autopilot.activate());

        verify(actionManager).forceSegmentReset();
        verify(actionManager).executeCommand(cmdStop);
        verify(actionManager).scheduleResume(2 * WaypointCommand.TICKS_PER_SECOND);
        assertEquals(AutoPilot.Mode.WAITING, autopilot.mode());

        // When the timer fires, resumeWaiting is called
        autopilot.resumeWaiting();

        verify(actionManager).executeCommand(cmdSpeed);
        assertEquals(AutoPilot.Mode.FOLLOWING, autopilot.mode()); // Advanced to wp2, back to FOLLOWING
    }
}
