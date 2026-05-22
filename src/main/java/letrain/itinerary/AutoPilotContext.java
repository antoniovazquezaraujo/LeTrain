package letrain.itinerary;

import letrain.core.segments.Segment;

/**
 * Bridge between AutoPilot and the real train/locomotive/world.
 * AutoPilot only knows about this interface, not about Train or Locomotive directly.
 */
public interface AutoPilotContext {

    /** Current speed of the train (0-10). */
    int currentSpeed();

    /** Current segment where the train's first linker is. */
    Segment currentSegment();

    /** Target segment where the given waypoint is. */
    Segment targetSegment(Waypoint wp);

    /** Is the given segment free (not occupied by another train)? */
    boolean isSegmentFree(Segment seg);

    /** Ensure the fork between current and next segment is set correctly. */
    void ensureForkRoute(Segment from, Segment to);

    /** Is the train at the target waypoint? */
    boolean isAtTarget(Waypoint wp);

    /** Notify that a segment is occupied. */
    void notifySegmentOccupied(Segment segment);

    /** Force a segment entry reset in safety manager. */
    void forceSegmentReset();
}
