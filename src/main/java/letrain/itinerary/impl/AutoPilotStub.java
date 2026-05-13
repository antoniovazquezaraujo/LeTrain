package letrain.itinerary.impl;

import letrain.core.segments.Segment;
import letrain.itinerary.*;
import java.util.*;

public class AutoPilotStub implements AutoPilot {
    private Itinerary itinerary;
    private Mode mode = Mode.IDLE;
    private SegmentPathfinder pathfinder;
    private List<Segment> currentRoute = List.of();
    private int waypointIndex = 0;

    @Override public Optional<Itinerary> itinerary() { return Optional.ofNullable(itinerary); }
    @Override public Mode mode() { return mode; }
    @Override public List<Segment> currentRoute() { return currentRoute; }
    @Override public int currentWaypointIndex() { return waypointIndex; }
    @Override public void setPathfinder(SegmentPathfinder pf) { this.pathfinder = pf; }

    @Override
    public void setItinerary(Itinerary it) {
        this.itinerary = it;
        this.mode = Mode.IDLE;
        this.waypointIndex = 0;
    }

    @Override
    public boolean activate() {
        if (itinerary == null || !itinerary.isValid()) return false;
        if (pathfinder == null) return false;
        mode = Mode.FOLLOWING;
        return true;
    }

    @Override
    public void deactivate() {
        mode = Mode.IDLE;
    }

    @Override
    public boolean tick() {
        return false; // stub
    }
}
