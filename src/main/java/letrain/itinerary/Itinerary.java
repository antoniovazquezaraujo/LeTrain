package letrain.itinerary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * A sequence of waypoints that a train can follow automatically.
 * Supports N:1 train assignment (one itinerary → many trains).
 */
public class Itinerary {
    public enum State { CREATED, ACTIVE, PAUSED, DONE }

    private final List<Waypoint> waypoints = new ArrayList<>();
    private final Set<Integer> assignedTrains = new HashSet<>();
    private State state = State.CREATED;
    private int currentIndex = 0;

    public void addWaypoint(Waypoint wp) {
        waypoints.add(wp);
    }

    public List<Waypoint> waypoints() {
        return Collections.unmodifiableList(waypoints);
    }

    public State state() { return state; }

    public boolean isValid() {
        return waypoints.size() >= 2;
    }

    public int currentIndex() { return currentIndex; }

    public void advance() {
        currentIndex++;
        if (currentIndex >= waypoints.size()) {
            state = State.DONE;
            currentIndex = waypoints.size();
        }
    }

    public Optional<Waypoint> currentWaypoint() {
        if (currentIndex < waypoints.size()) {
            return Optional.of(waypoints.get(currentIndex));
        }
        return Optional.empty();
    }

    public void assignTrain(int trainId) {
        assignedTrains.add(trainId);
    }

    public void unassignTrain(int trainId) {
        assignedTrains.remove(trainId);
    }

    public Set<Integer> assignedTrains() {
        return Collections.unmodifiableSet(assignedTrains);
    }
}
