package letrain.itinerary.impl;

import letrain.itinerary.Itinerary;
import letrain.itinerary.Waypoint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public class ItineraryImpl implements Itinerary {
    private final List<Waypoint> waypoints = new ArrayList<>();
    private final Set<Integer> assignedTrains = new HashSet<>();
    private State state = State.CREATED;
    private int currentIndex = 0;

    @Override
    public void addWaypoint(Waypoint wp) {
        waypoints.add(wp);
    }

    @Override
    public List<Waypoint> waypoints() {
        return Collections.unmodifiableList(waypoints);
    }

    @Override
    public State state() { return state; }

    @Override
    public boolean isValid() {
        return waypoints.size() >= 2;
    }

    @Override
    public int currentIndex() { return currentIndex; }

    @Override
    public void advance() {
        currentIndex++;
        if (currentIndex >= waypoints.size()) {
            state = State.DONE;
            currentIndex = waypoints.size();
        }
    }

    @Override
    public void reset() {
        state = State.CREATED;
        currentIndex = 0;
    }

    @Override
    public Optional<Waypoint> currentWaypoint() {
        if (currentIndex < waypoints.size()) {
            return Optional.of(waypoints.get(currentIndex));
        }
        return Optional.empty();
    }

    @Override
    public void assignTrain(int trainId) {
        assignedTrains.add(trainId);
    }

    @Override
    public void unassignTrain(int trainId) {
        assignedTrains.remove(trainId);
    }

    @Override
    public Set<Integer> assignedTrains() {
        return Collections.unmodifiableSet(assignedTrains);
    }
}
