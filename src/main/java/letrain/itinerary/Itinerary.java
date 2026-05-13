package letrain.itinerary;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface Itinerary {
    enum State { CREATED, ACTIVE, PAUSED, DONE }

    void addWaypoint(Waypoint wp);
    List<Waypoint> waypoints();
    State state();
    boolean isValid();
    int currentIndex();
    void advance();
    Optional<Waypoint> currentWaypoint();
    void assignTrain(int trainId);
    void unassignTrain(int trainId);
    Set<Integer> assignedTrains();
}
