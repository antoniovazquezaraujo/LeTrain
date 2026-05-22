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
}
