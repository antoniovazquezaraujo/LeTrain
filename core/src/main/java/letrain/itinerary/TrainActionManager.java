package letrain.itinerary;

import letrain.vehicle.rail.CoreTrainEventListener;

/**
 * Interface to execute physical commands on a train when a waypoint is reached. Decouples AutoPilot
 * from direct Train/Locomotive manipulation.
 */
public interface TrainActionManager extends CoreTrainEventListener {

    /**
     * Resumes waypoint processing after a schedule hold (ADR-022 phase 2b). Called by the autopilot
     * when the departure time is reached; it starts a parked engine if needed and completes the
     * waypoint.
     */
    default void onRetentionReleased() {}
}
