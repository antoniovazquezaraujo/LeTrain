package letrain.itinerary;

import java.util.List;

public interface Itinerary {
    void addWaypoint(Waypoint wp);
    List<Waypoint> waypoints();
    boolean isValid();
}
