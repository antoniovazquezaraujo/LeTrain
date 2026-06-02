package letrain.itinerary.impl;

import letrain.itinerary.Itinerary;
import letrain.itinerary.Waypoint;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ItineraryImpl implements Itinerary {
    private final List<Waypoint> waypoints = new ArrayList<>();

    public ItineraryImpl() {
    }

    public ItineraryImpl(List<Waypoint> waypoints) {
        if (waypoints != null) {
            this.waypoints.addAll(waypoints);
        }
    }

    @Override
    public void addWaypoint(Waypoint wp) {
        waypoints.add(wp);
    }

    @Override
    public List<Waypoint> waypoints() {
        return Collections.unmodifiableList(waypoints);
    }

    @Override
    public boolean isValid() {
        return waypoints.size() >= 2;
    }
}
