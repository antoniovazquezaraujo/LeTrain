package letrain.itinerary.impl;

import java.util.List;
import java.util.Optional;

import letrain.itinerary.AutoPilot;
import letrain.itinerary.AutoPilotContext;
import letrain.itinerary.Itinerary;
import letrain.itinerary.SegmentPathfinder;
import letrain.itinerary.Waypoint;
import letrain.segments.Segment;
import letrain.track.rail.ForkRailTrack;
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
    private Segment lastSegment;

    private final AutoPilotContext ctx;

    public AutoPilotImpl(AutoPilotContext ctx) {
        this.ctx = ctx;
        log.info("[AP] created");
    }

    @Override
    public Optional<Itinerary> itinerary() {
        return Optional.ofNullable(itinerary);
    }

    @Override
    public Mode mode() {
        return mode;
    }

    @Override
    public List<Segment> currentRoute() {
        return currentRoute;
    }

    @Override
    public int currentWaypointIndex() {
        return itinerary != null ? itinerary.currentIndex() : 0;
    }

    @Override
    public void setPathfinder(SegmentPathfinder pf) {
        this.pathfinder = pf;
    }

    @Override
    public void setItinerary(Itinerary it) {
        log.info("[AP] setItinerary waypoints={}", it != null ? it.waypoints().size() : 0);
        this.itinerary = it;
        this.mode = Mode.IDLE;
        this.currentRoute = List.of();
        this.lastSegment = null;
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
        lastSegment = null;
        itinerary.reset();
        ctx.forceSegmentReset();
        log.info("[AP] activate → FOLLOWING");
        return true;
    }

    @Override
    public void onForkEntered(ForkRailTrack fork) {
        log.info("[AP] onForkEntered {}", fork.getId());
        currentRoute = List.of();
        lastSegment = null;
    }

    @Override
    public void ensureForkRoute(Segment from, Segment to) {
        ctx.ensureForkRoute(from, to);
    }

    @Override
    public void deactivate() {
        log.info("[AP] deactivate → IDLE");
        mode = Mode.IDLE;
    }

    @Override
    public boolean tick() {
        if (mode == Mode.IDLE || mode == Mode.ERROR) {
            return false;
        }
        if (itinerary == null) {
            mode = Mode.ERROR;
            return false;
        }

        Segment currentSeg = ctx.currentSegment();
        if (currentSeg == null) {
            return false;
        }

        Optional<Waypoint> currentWpOpt = itinerary.currentWaypoint();
        if (currentWpOpt.isEmpty()) {
            log.info("[AP] tick → no waypoints (DONE)");
            mode = Mode.IDLE;
            return false;
        }

        Waypoint wp = currentWpOpt.get();

        if (ctx.isAtTarget(wp)) {
            log.info("[AP] ARRIVED at wp {}", wp.targetId());
            itinerary.advance();
            currentRoute = List.of();
            lastSegment = null;

            if (itinerary.state() == Itinerary.State.DONE || itinerary.currentWaypoint().isEmpty()) {
                log.info("[AP] itinerary DONE → IDLE");
                mode = Mode.IDLE;
                return false;
            }

            wp = itinerary.currentWaypoint().get();
        }

        if (currentRoute.isEmpty()) {
            log.info("[AP] calculating route to wp {}...", wp.targetId());
            if (!calculateRoute()) {
                log.warn("[AP] calculateRoute failed, retrying next tick");
                return false;
            }
        }

        if (currentSeg != lastSegment) {
            lastSegment = currentSeg;

            int index = currentRoute.indexOf(currentSeg);
            if (index == -1) {
                log.info("[AP] train is off-route. Recalculating...");
                if (!calculateRoute()) {
                    log.warn("[AP] recalculateRoute failed, retrying next tick");
                    return false;
                }
                index = currentRoute.indexOf(currentSeg);
            }

            if (index != -1 && index + 1 < currentRoute.size()) {
                Segment nextSeg = currentRoute.get(index + 1);
                ctx.ensureForkRoute(currentSeg, nextSeg);

                if (!ctx.isSegmentFree(nextSeg)) {
                    log.info("[AP] next segment {} is occupied, firing event", nextSeg.getId());
                    ctx.notifySegmentOccupied(nextSeg);
                }
            }
        }

        return true;
    }

    private boolean calculateRoute() {
        if (pathfinder == null || itinerary == null) return false;
        Waypoint wp = itinerary.currentWaypoint().orElse(null);
        if (wp == null) return false;

        Segment currentSeg = ctx.currentSegment();
        Segment targetSeg = ctx.targetSegment(wp);
        log.info("[AP] calcRoute currentSeg={} targetSeg={}",
                currentSeg != null ? currentSeg.getId() : "null",
                targetSeg != null ? targetSeg.getId() : "null");
        if (currentSeg == null || targetSeg == null) {
            return false;
        }

        currentRoute = pathfinder.find(currentSeg, targetSeg, wp.entryDir());
        log.info("[AP] calcRoute result: {} segments{} route={}",
                currentRoute.size(),
                currentRoute.isEmpty() ? " → ROUTE NOT FOUND" : "",
                currentRoute.stream().map(Segment::getId).toList());
        return !currentRoute.isEmpty();
    }
}
