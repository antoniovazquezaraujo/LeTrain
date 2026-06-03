package letrain.itinerary;

/**
 * Interface to execute physical commands on a train when a waypoint is reached.
 * Decouples AutoPilot from direct Train/Locomotive manipulation.
 */
public interface TrainActionManager {

    void checkWaypointArrival();
}
