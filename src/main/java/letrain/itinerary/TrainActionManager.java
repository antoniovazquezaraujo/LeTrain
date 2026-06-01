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

    /** Schedule the autopilot to resume after ticks. */
    void scheduleResume(int ticks);

    /** Acquire initial locks when starting or resuming movement. */
    void acquireInitialLocks();

    int getSavedSpeedBeforeReverse();

    void setSavedSpeedBeforeReverse(int savedSpeedBeforeReverse);

    void resumeWaiting();

    void checkWaypointArrival();

    void runPendingCommands();
}
