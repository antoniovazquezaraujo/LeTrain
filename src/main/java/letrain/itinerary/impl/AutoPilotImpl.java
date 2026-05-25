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
import letrain.itinerary.TrainActionManager;
import letrain.itinerary.WaypointCommand;
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
    private int routeRetryCooldown = 0;

    private static final int ROUTE_RETRY_TICKS = 100;

    private AutoPilotContext ctx;
    private TrainActionManager actionManager;
    private final List<WaypointCommand> pendingCommands = new java.util.ArrayList<>();
    private int waitTicks = 0;

    public AutoPilotImpl() {
        this.ctx = null;
        this.actionManager = null;
        log.info("[AP] created empty");
    }

    public AutoPilotImpl(AutoPilotContext ctx) {
        this(ctx, null);
    }

    public AutoPilotImpl(AutoPilotContext ctx, TrainActionManager actionManager) {
        this.ctx = ctx;
        this.actionManager = actionManager;
        log.info("[AP] created");
    }

    public AutoPilotImpl(Itinerary itinerary, Mode mode, int waitTicks, List<WaypointCommand> pendingCommands) {
        this.ctx = null;
        this.actionManager = null;
        this.itinerary = itinerary;
        this.mode = mode;
        this.waitTicks = waitTicks;
        if (pendingCommands != null) {
            this.pendingCommands.addAll(pendingCommands);
        }
        log.info("[AP] created from deserialization");
    }

    public void reinitialize(AutoPilotContext ctx, TrainActionManager actionManager) {
        this.ctx = ctx;
        this.actionManager = actionManager;
        log.info("[AP] reinitialized");
    }

    public int getWaitTicks() {
        return waitTicks;
    }

    public void setWaitTicks(int waitTicks) {
        this.waitTicks = waitTicks;
    }

    public List<WaypointCommand> getPendingCommands() {
        return pendingCommands;
    }

    public void setPendingCommands(List<WaypointCommand> pendingCommands) {
        this.pendingCommands.clear();
        if (pendingCommands != null) {
            this.pendingCommands.addAll(pendingCommands);
        }
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
        this.routeRetryCooldown = 0;
        this.waitTicks = 0;
        this.pendingCommands.clear();
    }

    @Override
    public boolean activate() {
        return activate(false);
    }

    @Override
    public boolean activate(boolean force) {
        log.info("[AP] activate(force=" + force + ") speed=" + ctx.currentSpeed()
            + " itin=" + (itinerary != null && itinerary.isValid())
            + " pf=" + (pathfinder != null));
        if (itinerary == null || !itinerary.isValid()) return false;
        if (pathfinder == null) return false;
        if (!force && ctx.currentSpeed() != 0) return false;
        mode = Mode.FOLLOWING;
        currentRoute = List.of();
        lastSegment = null;
        routeRetryCooldown = 0;
        waitTicks = 0;
        pendingCommands.clear();
        itinerary.reset();
        if (actionManager != null) {
            actionManager.forceSegmentReset();
        }
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
        if (actionManager != null) {
            actionManager.ensureForkRoute(from, to);
        }
    }

    @Override
    public void deactivate() {
        log.info("[AP] deactivate → IDLE");
        mode = Mode.IDLE;
        waitTicks = 0;
        pendingCommands.clear();
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

        if (mode == Mode.WAITING) {
            if (waitTicks > 0) {
                waitTicks--;
            }
            if (waitTicks == 0) {
                // Wait finished! Run any remaining commands for this waypoint
                while (!pendingCommands.isEmpty()) {
                    WaypointCommand cmd = pendingCommands.remove(0);
                    if (cmd.kind() == WaypointCommand.Kind.WAIT) {
                        this.waitTicks = cmd.seconds() * WaypointCommand.TICKS_PER_SECOND;
                        // remain in Mode.WAITING
                        return false;
                    } else {
                        if (actionManager != null) {
                            actionManager.executeCommand(cmd);
                        }
                    }
                }
                // No more commands/waits for this waypoint: resume following and advance itinerary
                mode = Mode.FOLLOWING;
                itinerary.advance();
                currentRoute = List.of();
                lastSegment = null;

                if (itinerary.state() == Itinerary.State.DONE || itinerary.currentWaypoint().isEmpty()) {
                    log.info("[AP] itinerary DONE → IDLE");
                    mode = Mode.IDLE;
                    return false;
                }
            } else {
                return false;
            }
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

            // Initialize pending commands for the arrived waypoint
            pendingCommands.clear();
            pendingCommands.addAll(wp.commands());

            // Run commands until we hit a WAIT or exhaust the list
            while (!pendingCommands.isEmpty()) {
                WaypointCommand cmd = pendingCommands.remove(0);
                if (cmd.kind() == WaypointCommand.Kind.WAIT) {
                    this.waitTicks = cmd.seconds() * WaypointCommand.TICKS_PER_SECOND;
                    this.mode = Mode.WAITING;
                    break;
                } else {
                    if (actionManager != null) {
                        actionManager.executeCommand(cmd);
                    }
                }
            }

            if (mode == Mode.WAITING) {
                return false;
            }

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
            if (routeRetryCooldown > 0) {
                routeRetryCooldown--;
                return false;
            }
            log.info("[AP] calculating route to wp {}...", wp.targetId());
            if (!calculateRoute()) {
                log.warn("[AP] calculateRoute failed, retrying in {} ticks", ROUTE_RETRY_TICKS);
                routeRetryCooldown = ROUTE_RETRY_TICKS;
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
                if (actionManager != null) {
                    actionManager.ensureForkRoute(currentSeg, nextSeg);
                }

                if (!ctx.isSegmentFree(nextSeg)) {
                    log.info("[AP] next segment {} is occupied, firing event", nextSeg.getId());
                    if (actionManager != null) {
                        actionManager.notifySegmentOccupied(nextSeg);
                    }
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
