package letrain.itinerary;

import letrain.vehicle.rail.CoreTrainEventListener;

/**
 * Interface to execute physical commands on a train when a waypoint is reached. Decouples AutoPilot
 * from direct Train/Locomotive manipulation.
 */
public interface TrainActionManager extends CoreTrainEventListener {

    /**
     * Drops the pending waypoint actions and the mission/deferred command a plan change may have
     * left behind. The program text is the source of truth (ADR-009): when an itinerary is replaced
     * nothing from the old plan may resume against the new one (hot reprogram, #653).
     */
    default void resetPendingActions() {}

    /**
     * Resumes waypoint processing after a schedule hold (ADR-022 phase 2b). Called by the autopilot
     * when the departure time is reached; it starts a parked engine if needed and completes the
     * waypoint.
     */
    default void onRetentionReleased() {}
}
