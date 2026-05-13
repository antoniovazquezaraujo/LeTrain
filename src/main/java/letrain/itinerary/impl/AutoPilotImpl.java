package letrain.itinerary.impl;

import letrain.core.segments.Segment;
import letrain.itinerary.*;
import java.util.*;

/**
 * Real AutoPilot implementation. Controls a train automatically along an itinerary.
 */
public class AutoPilotImpl implements AutoPilot {

    private Itinerary itinerary;
    private Mode mode = Mode.IDLE;
    private SegmentPathfinder pathfinder;
    private List<Segment> currentRoute = List.of();
    private int waypointIndex = 0;
    private int waitTicks = 0;

    // Dependency on the real world — injected via constructor or setter
    private final AutoPilotContext ctx;

    public AutoPilotImpl(AutoPilotContext ctx) {
        this.ctx = ctx;
    }

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
        this.currentRoute = List.of();
    }

    @Override
    public boolean activate() {
        if (itinerary == null || !itinerary.isValid()) return false;
        if (pathfinder == null) return false;
        if (ctx.currentSpeed() != 0) return false; // must be stopped
        mode = Mode.FOLLOWING;
        currentRoute = List.of();
        waypointIndex = 0;
        return true;
    }

    @Override
    public void deactivate() {
        mode = Mode.IDLE;
        ctx.setTargetSpeed(0);
    }

    @Override
    public boolean tick() {
        if (mode == Mode.IDLE || mode == Mode.ERROR) return false;
        if (itinerary == null) { mode = Mode.ERROR; return false; }

        System.out.println("[AP] tick mode=" + mode + " wpIdx=" + waypointIndex + " routeSize=" + currentRoute.size());

        // Handle WAIT command
        if (mode == Mode.WAITING) {
            waitTicks--;
            if (waitTicks <= 0) {
                mode = Mode.FOLLOWING;
                itinerary.advance();
                currentRoute = List.of(); // force recalculation
            }
            return false;
        }

        // Handle REVERSE
        if (mode == Mode.REVERSING) {
            if (ctx.currentSpeed() != 0) {
                ctx.setTargetSpeed(0);
                return false; // still braking
            }
            ctx.reverse();
            mode = Mode.FOLLOWING;
            itinerary.advance();
            currentRoute = List.of();
            return false;
        }

        // FOLLOWING mode
        Optional<Waypoint> currentWp = itinerary.currentWaypoint();
        if (currentWp.isEmpty()) {
            mode = Mode.IDLE;
            return false;
        }

        // Check if we've arrived at the current waypoint
        if (hasArrived(currentWp.get())) {
            return executeWaypoint(currentWp.get());
        }

        // Calculate route if needed
        if (currentRoute.isEmpty()) {
            if (!calculateRoute()) {
                mode = Mode.ERROR;
                ctx.setTargetSpeed(0);
                return false;
            }
        }

        // Follow the route
        return followRoute();
    }

    private boolean calculateRoute() {
        if (pathfinder == null || itinerary == null) {
            System.out.println("[AP] calculateRoute: no pathfinder or itinerary");
            return false;
        }
        Optional<Waypoint> wp = itinerary.currentWaypoint();
        if (wp.isEmpty()) {
            System.out.println("[AP] calculateRoute: no current waypoint");
            return false;
        }

        Segment currentSeg = ctx.currentSegment();
        Segment targetSeg = ctx.targetSegment(wp.get());
        System.out.println("[AP] calculateRoute: from " + currentSeg + " to " + targetSeg);
        if (currentSeg == null || targetSeg == null) {
            System.out.println("[AP] calculateRoute: null segment");
            return false;
        }

        currentRoute = pathfinder.find(currentSeg, targetSeg, wp.get().entryDir());
        System.out.println("[AP] calculateRoute: result size=" + currentRoute.size());
        return !currentRoute.isEmpty();
    }

    private boolean followRoute() {
        if (currentRoute.isEmpty()) return false;

        // Same segment — just move forward until arrival
        if (currentRoute.size() <= 1) {
            ctx.setTargetSpeed(3);
            return true;
        }
        if (nextSeg == null) return false;

        // Check if next segment is free
        if (!ctx.isSegmentFree(nextSeg)) {
            ctx.setTargetSpeed(0);
            return false;
        }

        // Change fork if needed
        ctx.ensureForkRoute(currentRoute.get(0), nextSeg);

        // Control speed: slow down when approaching destination
        int remaining = currentRoute.size() - 1;
        if (remaining <= 2) {
            ctx.setTargetSpeed(Math.min(2, ctx.targetSpeed()));
        } else {
            ctx.setTargetSpeed(Math.min(5, ctx.targetSpeed()));
        }

        return true; // train movement happens via the normal Locomotive.update()
    }

    private boolean hasArrived(Waypoint wp) {
        return ctx.isAtTarget(wp);
    }

    private boolean executeWaypoint(Waypoint wp) {
        for (WaypointCommand cmd : wp.commands()) {
            switch (cmd.kind()) {
                case LOAD -> ctx.load();
                case UNLOAD -> ctx.unload();
                case REVERSE -> {
                    mode = Mode.REVERSING;
                    ctx.setTargetSpeed(0);
                    return false;
                }
                case WAIT -> {
                    mode = Mode.WAITING;
                    waitTicks = cmd.ticks();
                    ctx.setTargetSpeed(0);
                    return false;
                }
                case SPEED -> ctx.setTargetSpeed(cmd.targetSpeed());
                case NONE -> {}
            }
        }
        // Advance to next waypoint
        itinerary.advance();
        currentRoute = List.of();
        if (itinerary.state() == Itinerary.State.DONE) {
            mode = Mode.IDLE;
            ctx.setTargetSpeed(0);
        }
        return false;
    }
}
