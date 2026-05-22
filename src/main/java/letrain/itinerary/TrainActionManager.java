package letrain.itinerary;

/**
 * Interface to execute physical commands on a train when a waypoint is reached.
 * Decouples AutoPilot from direct Train/Locomotive manipulation.
 */
public interface TrainActionManager {

    /**
     * Executes the given waypoint command on the train/world.
     *
     * @param command the command to execute
     */
    void executeCommand(WaypointCommand command);

    /** Ensure the fork between current and next segment is set correctly. */
    void ensureForkRoute(letrain.segments.Segment from, letrain.segments.Segment to);

    /** Notify that a segment is occupied. */
    void notifySegmentOccupied(letrain.segments.Segment segment);

    /** Force a segment entry reset in safety manager. */
    void forceSegmentReset();
}
