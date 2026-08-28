package letrain.itinerary;

import java.util.List;
import java.util.Optional;
import letrain.segments.Segment;

/**
 * Drives a train automatically along an itinerary.
 * Called each tick by the locomotive.
 */
public interface AutoPilot {

    enum Mode {
        IDLE,
        FOLLOWING,
        WAITING,
        REVERSING,
        ERROR
    }

    /** The itinerary this autopilot is following. */
    Optional<Itinerary> itinerary();

    /** Current operational mode. */
    Mode mode();

    /** Assign an itinerary to follow. */
    void setItinerary(Itinerary itinerary);

    /** Start/activate the autopilot. Only works if a valid route exists. */
    boolean activate();

    /** Stop and return to manual control. */
    void deactivate();

    /** The currently targeted waypoint. */
    Optional<Waypoint> currentWaypoint();

    /** Advances to the next waypoint in the itinerary. */
    void advanceWaypoint();

    /** The currently calculated route (segments from current position to next waypoint). */
    List<Segment> currentRoute();

    /** Current waypoint index within the itinerary. */
    int currentWaypointIndex();

    /** Set the pathfinder to use for route calculation. */
    void setPathfinder(SegmentPathfinder pathfinder);

    default void onSegmentEntered(Segment newSegment) {}

    void resumeWaiting();

    void clearRoute();

    /** Replaces a segment in the current route (e.g. when bypassing a blocked segment with an alternative). */
    void replaceRouteSegment(Segment oldSeg, Segment newSeg);

    /** Ensure the fork between 'from' and 'to' segments is oriented correctly. */
    default void ensureForkRoute(letrain.segments.Segment from, letrain.segments.Segment to) {}
}
