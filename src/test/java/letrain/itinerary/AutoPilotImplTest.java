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
    private SegmentPathfinder pathfinder;
    private Itinerary itinerary;
    private Segment segA, segB;

    @BeforeEach
    void setUp() {
        ctx = mock(AutoPilotContext.class);
        pathfinder = mock(SegmentPathfinder.class);
        autopilot = new AutoPilotImpl(ctx);
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
    @DisplayName("should calculate route on first tick and orient fork if segment changed")
    void calculatesRouteAndOrientsFork() {
        when(ctx.currentSpeed()).thenReturn(0);
        when(ctx.currentSegment()).thenReturn(segA);
        when(ctx.targetSegment(any())).thenReturn(segB);
        when(ctx.isAtTarget(any())).thenReturn(false);
        when(ctx.isSegmentFree(any())).thenReturn(true);
        when(pathfinder.find(any(), any(), any())).thenReturn(List.of(segA, segB));

        autopilot.setItinerary(itinerary);
        autopilot.activate();
        autopilot.tick();

        verify(pathfinder).find(eq(segA), eq(segB), any());
        verify(ctx).ensureForkRoute(segA, segB);
    }

    @Test
    @DisplayName("should notify when next segment is occupied")
    void firesSegmentOccupied() {
        when(ctx.currentSpeed()).thenReturn(0);
        when(ctx.currentSegment()).thenReturn(segA);
        when(ctx.targetSegment(any())).thenReturn(segB);
        when(ctx.isAtTarget(any())).thenReturn(false);
        when(pathfinder.find(any(), any(), any())).thenReturn(List.of(segA, segB));
        when(ctx.isSegmentFree(segB)).thenReturn(false);

        autopilot.setItinerary(itinerary);
        autopilot.activate();
        autopilot.tick();

        verify(ctx).ensureForkRoute(segA, segB);
        verify(ctx).notifySegmentOccupied(segB);
    }

    @Test
    @DisplayName("should keep mode FOLLOWING but return false if route calculation fails")
    void failsRoutingGracefully() {
        when(ctx.currentSpeed()).thenReturn(0);
        when(ctx.currentSegment()).thenReturn(segA);
        when(ctx.targetSegment(any())).thenReturn(segB);
        when(ctx.isAtTarget(any())).thenReturn(false);
        when(pathfinder.find(any(), any(), any())).thenReturn(List.of());

        autopilot.setItinerary(itinerary);
        autopilot.activate();
        assertFalse(autopilot.tick());
        assertEquals(AutoPilot.Mode.FOLLOWING, autopilot.mode());
    }

    @Test
    @DisplayName("should calculate route immediately after executing waypoint if itinerary not done")
    void calculatesRouteAfterWaypoint() {
        when(ctx.currentSpeed()).thenReturn(0);
        when(ctx.currentSegment()).thenReturn(segA);
        when(ctx.targetSegment(any())).thenReturn(segB);
        when(ctx.isAtTarget(any())).thenReturn(true);
        when(pathfinder.find(any(), any(), any())).thenReturn(List.of(segA, segB));

        autopilot.setItinerary(itinerary);
        autopilot.activate();
        autopilot.tick();

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
    @DisplayName("should not call pathfinder on every tick after a route failure (cooldown)")
    void routeFailureCooldownSuppressesRetries() {
        when(ctx.currentSpeed()).thenReturn(0);
        when(ctx.currentSegment()).thenReturn(segA);
        when(ctx.targetSegment(any())).thenReturn(segB);
        when(ctx.isAtTarget(any())).thenReturn(false);
        when(pathfinder.find(any(), any(), any())).thenReturn(List.of());

        autopilot.setItinerary(itinerary);
        autopilot.activate();

        // First tick: route fails, cooldown starts
        assertFalse(autopilot.tick());

        // Next ticks within cooldown window: pathfinder must NOT be called again
        autopilot.tick();
        autopilot.tick();

        // pathfinder was called exactly once (on the first tick)
        org.mockito.Mockito.verify(pathfinder, org.mockito.Mockito.times(1))
                .find(any(), any(), any());
    }
}
