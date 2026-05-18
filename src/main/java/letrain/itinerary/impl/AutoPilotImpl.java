package letrain.itinerary.impl;

import letrain.core.segments.Segment;
import letrain.itinerary.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Real AutoPilot implementation. Controls a train automatically along an itinerary.
 */
public class AutoPilotImpl implements AutoPilot {

    private static final Logger log = LoggerFactory.getLogger(AutoPilotImpl.class);

    private Itinerary itinerary;
    private Mode mode = Mode.IDLE;
    private SegmentPathfinder pathfinder;
    private List<Segment> currentRoute = List.of();
    private int waypointIndex = 0;
    private int waitTicks = 0;
    private int cruiseSpeed = 3;

    private final AutoPilotContext ctx;

    public AutoPilotImpl(AutoPilotContext ctx) {
        this.ctx = ctx;
        log.info("[AP] created");
    }

    @Override public Optional<Itinerary> itinerary() { return Optional.ofNullable(itinerary); }
    @Override public Mode mode() { return mode; }
    @Override public List<Segment> currentRoute() { return currentRoute; }
    @Override public int currentWaypointIndex() { return waypointIndex; }
    @Override public void setPathfinder(SegmentPathfinder pf) { this.pathfinder = pf; }

    @Override
    public void setItinerary(Itinerary it) {
        log.info("[AP] setItinerary waypoints={}", it.waypoints().size());
        this.itinerary = it;
        this.mode = Mode.IDLE;
        this.waypointIndex = 0;
        this.currentRoute = List.of();
        this.cruiseSpeed = 3;
    }

    @Override
    public boolean activate() {
        log.info("[AP] activate() speed=" + ctx.currentSpeed()
            + " itin=" + (itinerary != null && itinerary.isValid())
            + " pf=" + (pathfinder != null));
        if (itinerary == null || !itinerary.isValid()) return false;
        if (pathfinder == null) return false;
        if (ctx.currentSpeed() != 0) return false;
        mode = Mode.FOLLOWING;
        currentRoute = List.of();
        waypointIndex = 0;
        cruiseSpeed = 3;
        itinerary.reset();
        log.info("[AP] activate → FOLLOWING");
        return true;
    }

    @Override
    public void deactivate() {
        log.info("[AP] deactivate → IDLE");
        mode = Mode.IDLE;
        ctx.setTargetSpeed(0);
    }

    @Override
    public boolean tick() {
        if (mode == Mode.IDLE || mode == Mode.ERROR) return false;
        if (itinerary == null) { mode = Mode.ERROR; return false; }

        if (mode == Mode.WAITING) {
            waitTicks--;
            log.info("[AP] WAITING " + (waitTicks+1) + "→" + waitTicks);
            if (waitTicks <= 0) {
                mode = Mode.FOLLOWING;
                itinerary.advance();
                currentRoute = List.of();
                log.info("[AP] WAITING done → FOLLOWING, advanced wp");
            }
            return false;
        }

        if (mode == Mode.REVERSING) {
            if (ctx.currentSpeed() != 0) {
                log.info("[AP] REVERSING braking...");
                ctx.setTargetSpeed(0);
                return false;
            }
            log.info("[AP] REVERSING → reverse!");
            ctx.reverse();
            mode = Mode.FOLLOWING;
        itinerary.advance();
        currentRoute = List.of();
        if (itinerary.state() == Itinerary.State.DONE) {
            log.info("[AP] itinerary DONE, coasting to stop...");
        }
        return false;
        }

        // FOLLOWING
        Optional<Waypoint> currentWp = itinerary.currentWaypoint();
        if (currentWp.isEmpty()) {
            log.info("[AP] tick → no waypoints (DONE), speed={}", cruiseSpeed);
            ctx.setTargetSpeed(cruiseSpeed);
            return true;
        }
        Waypoint wp = currentWp.get();
        log.info("[AP] target wp=" + wp.targetId() + " type=" + wp.type()
            + " cmds=" + wp.commands().size());

        if (hasArrived(wp)) {
            log.info("[AP] ARRIVED at wp " + wp.targetId());
            return executeWaypoint(wp);
        }

        if (currentRoute.isEmpty()) {
            log.info("[AP] calculating route...");
            if (!calculateRoute()) {
                log.info("[AP] route FAILED → ERROR");
                mode = Mode.ERROR;
                ctx.setTargetSpeed(0);
                return false;
            }
            log.info("[AP] route calculated: " + currentRoute.size() + " segments");
        }

        return followRoute();
    }

    private boolean calculateRoute() {
        if (pathfinder == null || itinerary == null) return false;
        Optional<Waypoint> wp = itinerary.currentWaypoint();
        if (wp.isEmpty()) return false;

        Segment currentSeg = ctx.currentSegment();
        Segment targetSeg = ctx.targetSegment(wp.get());
        log.info("[AP] calcRoute currentSeg={} targetSeg={}",
                currentSeg != null ? currentSeg.getId() : "null",
                targetSeg != null ? targetSeg.getId() : "null");
        if (currentSeg == null || targetSeg == null) {
            log.warn("[AP] calcRoute FAILED: current={} target={}",
                    currentSeg == null ? "null" : "ok",
                    targetSeg == null ? "null" : "ok");
            return false;
        }

        currentRoute = pathfinder.find(currentSeg, targetSeg, wp.get().entryDir());
        log.info("[AP] calcRoute result: {} segments{}",
                currentRoute.size(),
                currentRoute.isEmpty() ? " → ROUTE NOT FOUND" : "");
        return !currentRoute.isEmpty();
    }

    private boolean followRoute() {
        if (currentRoute.isEmpty()) return false;

        if (currentRoute.size() == 1) {
            log.info("[AP] followRoute 1 seg → speed=" + cruiseSpeed);
            ctx.setTargetSpeed(cruiseSpeed);
            return true;
        }

        Segment nextSeg = currentRoute.get(1);
        if (nextSeg == null) return false;

        if (!ctx.isSegmentFree(nextSeg)) {
            log.info("[AP] followRoute BLOCKED → speed=0");
            ctx.setTargetSpeed(0);
            return false;
        }

        ctx.ensureForkRoute(currentRoute.get(0), nextSeg);
        log.info("[AP] followRoute " + currentRoute.size() + " segs → speed=" + cruiseSpeed);
        ctx.setTargetSpeed(cruiseSpeed);
        return true;
    }

    private boolean hasArrived(Waypoint wp) {
        boolean arrived = ctx.isAtTarget(wp);
        log.info("[AP] hasArrived wp=" + wp.targetId() + " type=" + wp.type() + " → " + arrived);
        return arrived;
    }

    private boolean executeWaypoint(Waypoint wp) {
        log.info("[AP] executeWaypoint wp=" + wp.targetId());
        for (WaypointCommand cmd : wp.commands()) {
            log.info("[AP]   cmd=" + cmd.kind() + (cmd.kind()==WaypointCommand.Kind.SPEED ? " val="+cmd.targetSpeed() : ""));
            switch (cmd.kind()) {
                case LOAD -> ctx.load();
                case UNLOAD -> ctx.unload();
                case REVERSE -> {
                    log.info("[AP]   → REVERSING");
                    mode = Mode.REVERSING;
                    ctx.setTargetSpeed(0);
                    return false;
                }
                case WAIT -> {
                    log.info("[AP]   → WAITING " + cmd.ticks());
                    mode = Mode.WAITING;
                    waitTicks = cmd.ticks();
                    ctx.setTargetSpeed(0);
                    return false;
                }
                case SPEED -> {
                    cruiseSpeed = cmd.targetSpeed();
                    log.info("[AP]   → cruiseSpeed=" + cruiseSpeed);
                    ctx.setTargetSpeed(cruiseSpeed);
                }
                case NONE -> {}
            }
        }
        itinerary.advance();
        currentRoute = List.of();
        log.info("[AP] advanced to wp " + itinerary.currentWaypoint().map(w -> ""+w.targetId()).orElse("none"));
        if (itinerary.state() == Itinerary.State.DONE) {
            log.info("[AP] itinerary DONE → IDLE");
            mode = Mode.IDLE;
        }
        return false;
    }
}
