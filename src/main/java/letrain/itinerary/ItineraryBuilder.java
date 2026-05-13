package letrain.itinerary;

import letrain.itinerary.impl.ItineraryImpl;
import letrain.itinerary.impl.WaypointImpl;
import letrain.track.Station;
import letrain.track.Track;

import java.util.*;

/**
 * Simple builder for creating itineraries station by station.
 * Used by the keyboard-driven editor.
 */
public class ItineraryBuilder {

    private final List<Waypoint> waypoints = new ArrayList<>();
    private final RailwayGraphProvider graphProvider;

    public ItineraryBuilder(RailwayGraphProvider graphProvider) {
        this.graphProvider = graphProvider;
    }

    /** Add a station to the itinerary. */
    public boolean addStation(Station station) {
        Waypoint wp = new WaypointImpl(Waypoint.Type.STATION, station.getId(), List.of());
        waypoints.add(wp);
        return true;
    }

    /** Remove the last added waypoint. */
    public void removeLast() {
        if (!waypoints.isEmpty()) waypoints.remove(waypoints.size() - 1);
    }

    /** Remove a specific waypoint by index. */
    public void removeAt(int index) {
        if (index >= 0 && index < waypoints.size()) waypoints.remove(index);
    }

    /** Set commands for the last waypoint. */
    public void setLastCommands(List<WaypointCommand> commands) {
        if (waypoints.isEmpty()) return;
        Waypoint last = waypoints.get(waypoints.size() - 1);
        waypoints.set(waypoints.size() - 1,
            new WaypointImpl(last.type(), last.targetId(), last.entryDir().orElse(null), commands));
    }

    /** Build the itinerary. */
    public Itinerary build() {
        ItineraryImpl it = new ItineraryImpl();
        for (Waypoint wp : waypoints) it.addWaypoint(wp);
        return it;
    }

    /** Get current waypoints being built. */
    public List<Waypoint> waypoints() {
        return Collections.unmodifiableList(waypoints);
    }

    /** Clear the builder. */
    public void clear() {
        waypoints.clear();
    }

    /** Check if the itinerary is valid (at least 2 stations). */
    public boolean isValid() {
        return waypoints.size() >= 2;
    }

    /** Get a human-readable name for the itinerary. */
    public String getName() {
        if (waypoints.size() < 2) return "(incomplete)";
        String first = stationName(waypoints.get(0));
        String last = stationName(waypoints.get(waypoints.size() - 1));
        return first + " → " + last;
    }

    private String stationName(Waypoint wp) {
        return "Station " + wp.targetId();
    }

    /** Provider interface to avoid coupling to Model. */
    public interface RailwayGraphProvider {
        Optional<letrain.core.segments.RailwayGraph> getGraph();
    }
}
