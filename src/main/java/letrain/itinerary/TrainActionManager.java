package letrain.itinerary;

import letrain.vehicle.rail.CoreTrainEventListener;

/**
 * Interface to execute physical commands on a train when a waypoint is reached.
 * Decouples AutoPilot from direct Train/Locomotive manipulation.
 */
public interface TrainActionManager extends CoreTrainEventListener {
}
