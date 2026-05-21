package letrain.itinerary;

import letrain.core.segments.Segment;
import java.util.List;
import java.util.Optional;

/**
 * Drives a train automatically along an itinerary.
 * Called each tick by the locomotive.
 */
public interface AutoPilot {

    enum Mode { IDLE, FOLLOWING, WAITING, REVERSING, ERROR }

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

    /** Called each simulation tick. Returns true if the train moved. */
    boolean tick();

    /** The currently calculated route (segments from current position to next waypoint). */
    List<Segment> currentRoute();

    /** Current waypoint index within the itinerary. */
    int currentWaypointIndex();

    /** Set the pathfinder to use for route calculation. */
    void setPathfinder(SegmentPathfinder pathfinder);

    default void onForkEntered(letrain.track.rail.ForkRailTrack fork) {}

    /** Ensure the fork between 'from' and 'to' segments is oriented correctly. */
    default void ensureForkRoute(letrain.core.segments.Segment from, letrain.core.segments.Segment to) {}
}
