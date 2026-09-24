package letrain.itinerary.impl;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import letrain.itinerary.AutoPilot;
import letrain.itinerary.Itinerary;
import letrain.itinerary.Punctuality;
import letrain.itinerary.SegmentPathfinder;
import letrain.itinerary.Timetable;
import letrain.itinerary.TrainActionManager;
import letrain.itinerary.Waypoint;
import letrain.itinerary.WaypointCommand;
import letrain.map.Dir;
import letrain.segments.Port;
import letrain.segments.RailNode;
import letrain.segments.RailwayGraph;
import letrain.segments.Segment;
import letrain.time.GameClock;
import letrain.time.GameTime;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;
import letrain.utils.SimulationScheduler;
import letrain.vehicle.rail.impl.Train;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Real AutoPilot implementation. Controls a train automatically along an itinerary. */
public class AutoPilotImpl implements AutoPilot {

    private static final Logger log = LoggerFactory.getLogger(AutoPilotImpl.class);

    private Itinerary itinerary;
    private Mode mode = Mode.IDLE;
    private SegmentPathfinder pathfinder;
    private List<Segment> currentRoute = List.of();
    private int currentIndex = 0;

    private Train train;
    private final List<WaypointCommand> pendingCommands = new java.util.ArrayList<>();
    private int waitTicks = 0;

    // ADR-022 phase 2b: per-service timetable state (in memory, deterministic).
    private final Punctuality punctuality = new Punctuality();
    /** Absolute game minute of the last resolved schedule event; unset means "no event yet". */
    private static final long NO_SCHEDULE_EVENT = Long.MIN_VALUE;
    private long scheduleCursor = NO_SCHEDULE_EVENT;
    /** Invalidates delayed retention releases from a previous hold. */
    private long retentionSerial = 0;
    /** Last departure recorded for the current stop; avoids a duplicate after a reload. */
    private Waypoint lastDepartureWaypoint;
    private long lastDepartureTarget = NO_SCHEDULE_EVENT;

    public AutoPilotImpl() {
        this.train = null;

        log.info("[AP] created empty");
    }

    public AutoPilotImpl(Train train) {
        this(train, null);
    }

    public AutoPilotImpl(Train train, TrainActionManager actionManager) {
        this.train = train;

        log.info("[AP] created");
    }

    public AutoPilotImpl(Itinerary itinerary, Mode mode, int waitTicks,
            List<WaypointCommand> pendingCommands, int currentIndex) {
        this.train = null;

        this.itinerary = itinerary;
        this.mode = mode;
        this.waitTicks = waitTicks;
        if (pendingCommands != null) {
            this.pendingCommands.addAll(pendingCommands);
        }
        this.currentIndex = currentIndex;
        log.info("[AP] created from deserialization");
    }

    public void reinitialize(Train train, TrainActionManager actionManager) {
        this.train = train;
        if (mode == Mode.WAITING) {
            // The delayed release lives in the transient scheduler: re-arm it after a load so a
            // train saved while holding does not stay stuck forever.
            if (!retainUntilDeparture()) {
                SimulationScheduler scheduler = scheduler();
                if (scheduler != null) {
                    scheduler.schedule(0, () -> {
                        if (mode == Mode.WAITING) {
                            completeRetention();
                        }
                    });
                }
            }
        }
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
    public Optional<Waypoint> currentWaypoint() {
        if (itinerary != null && currentIndex < itinerary.waypoints().size()) {
            return Optional.of(itinerary.waypoints().get(currentIndex));
        }
        return Optional.empty();
    }

    @Override
    public void advanceWaypoint() {
        if (itinerary == null || itinerary.waypoints().isEmpty()) {
            return;
        }
        currentIndex++;
        if (currentIndex >= itinerary.waypoints().size()) {
            currentIndex = 0;
        }
    }

    @Override
    public int currentWaypointIndex() {
        return currentIndex;
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

        this.waitTicks = 0;
        this.pendingCommands.clear();
        this.currentIndex = 0;
        // A new itinerary is a new service: sequence cursor and punctuality history restart.
        this.scheduleCursor = NO_SCHEDULE_EVENT;
        this.lastDepartureWaypoint = null;
        this.lastDepartureTarget = NO_SCHEDULE_EVENT;
        this.punctuality.clear();
        this.retentionSerial++;
    }

    @Override
    public boolean activate() {
        if (train == null) {
            return false;
        }
        log.info("[AP] activate() speed=" + getTrainSpeed() + " itin="
                + (itinerary != null && itinerary.isValid()) + " pf=" + (pathfinder != null));
        if (itinerary == null || !itinerary.isValid()) {
            return false;
        }
        if (pathfinder == null) {
            return false;
        }
        mode = Mode.FOLLOWING;
        currentRoute = List.of();
        waitTicks = 0;
        pendingCommands.clear();
        currentIndex = 0;
        log.info("[AP] activate → FOLLOWING");

        // Actuación inicial reactiva
        Segment currentSeg = getTrainCurrentSegment();
        if (currentSeg != null) {
            onSegmentEntered(currentSeg);
        }

        return true;
    }

    private int getTrainSpeed() {
        return train != null ? train.getSpeed() : 0;
    }

    private Segment getTrainCurrentSegment() {
        if (train == null || train.getModel() == null) {
            return null;
        }

        letrain.segments.RailwayGraph graph = train.getModel().getRailwayGraph();
        if (graph == null) {
            return null;
        }

        var physicalFront = train.getPhysicalFront();
        if (physicalFront == null || physicalFront.getTrack() == null) {
            return null;
        }

        letrain.track.Track t = physicalFront.getTrack();
        return t instanceof letrain.track.rail.RailTrack
                ? graph.getSegment((letrain.track.rail.RailTrack) t)
                : null;
    }

    private Segment getTrainTargetSegment(Waypoint wp) {
        if (train == null || train.getModel() == null) {
            return null;
        }
        letrain.segments.RailwayGraph graph = train.getModel().getRailwayGraph();
        if (graph == null) {
            return null;
        }

        letrain.map.Point pos = null;
        switch (wp.type()) {
            case STATION:
                letrain.track.Station st = train.getModel().getStation(wp.targetId());
                pos = st != null ? st.getPosition() : null;
                break;
            case SENSOR:
                var sensor = train.getModel().getSensor(wp.targetId());
                pos = sensor != null ? sensor.getPosition() : null;
                break;
        }
        if (pos == null) {
            return null;
        }

        letrain.track.rail.RailTrack track = train.getModel().getRailMap().getTrackAt(pos);
        return track != null ? graph.getSegment(track) : null;
    }

    public void setMode(Mode mode) {
        this.mode = mode;
    }

    @Override
    public void onSegmentEntered(Segment currentSeg) {
        if (currentSeg == null) {
            return;
        }
        log.info("[AP] onSegmentEntered: newSegment={}, mode={}",
                currentSeg != null ? currentSeg.getId() : "null", mode);
        if (mode != Mode.FOLLOWING) {
            return;
        }


        Optional<Waypoint> currentWpOpt = currentWaypoint();
        if (currentWpOpt.isEmpty()) {
            log.info(
                    "[AP] onSegmentEntered: itinerary has no current waypoint. Setting mode to IDLE");
            mode = Mode.IDLE;
            return;
        }

        Waypoint wp = currentWpOpt.get();
        log.info("[AP] onSegmentEntered: current target waypoint is {}", wp.targetId());

        // Si la ruta está vacía o nos desviamos, calcular ruta
        if (currentRoute.isEmpty() || !currentRoute.contains(currentSeg)) {
            log.info("[AP] calculating route to wp {}...", wp.targetId());
            if (!calculateRoute()) {
                log.warn("[AP] calculateRoute failed");
                return;
            }
        }

        // Orientar las agujas para el siguiente tramo de la ruta
        int index = currentRoute.indexOf(currentSeg);
        log.info("[AP] onSegmentEntered: current segment index in route = {}", index);
        if (index != -1 && index + 1 < currentRoute.size()) {
            Segment nextSeg = currentRoute.get(index + 1);
            log.info("[AP] onSegmentEntered: orienting fork for next segment {} from {}",
                    nextSeg.getId(), currentSeg.getId());
            ensureForkRoute(currentSeg, nextSeg);
        }
    }

    @Override
    public void resumeWaiting() {
        if (mode != Mode.WAITING) {
            return;
        }
        log.info("[AP] resumeWaiting from wait");
        this.waitTicks = 0;
        this.mode = Mode.FOLLOWING;
    }

    /***********************************************************
     * ADR-022 phase 2b: retention and punctuality
     **********************************************************/

    @Override
    public void measureArrival(Waypoint waypoint) {
        if (waypoint == null || waypoint.arrival().isEmpty()) {
            return;
        }
        GameClock clock = gameClock();
        if (clock == null) {
            return;
        }
        long nowAbs = Timetable.absoluteMinute(clock.now());
        long targetAbs = resolveScheduleTime(waypoint.arrival().get(), nowAbs);
        int delta = (int) (nowAbs - targetAbs);
        punctuality.recordArrival(waypoint.type(), waypoint.targetId(), delta);
        scheduleCursor = Math.max(scheduleCursor, targetAbs);
        // A new arrival opens a new stop: its departure may be recorded again.
        lastDepartureWaypoint = null;
        log.info("[AP] arrival at {} {} scheduled {} -> delta {} min", waypoint.type(),
                waypoint.targetId(), Timetable.toGameTime(targetAbs), delta);
    }

    @Override
    public boolean retainUntilDeparture() {
        if (mode == Mode.IDLE || itinerary == null) {
            return false;
        }
        Waypoint waypoint = currentWaypoint().orElse(null);
        if (waypoint == null || waypoint.departure().isEmpty()) {
            return false;
        }
        GameClock clock = gameClock();
        SimulationScheduler scheduler = scheduler();
        if (clock == null || scheduler == null) {
            return false;
        }
        long nowAbs = Timetable.absoluteMinute(clock.now());
        long targetAbs = resolveScheduleTime(waypoint.departure().get(), nowAbs);
        long delta = nowAbs - targetAbs;
        if (delta >= 0) {
            // Due or late: the train departs now and the deviation is measured. The guard keeps a
            // reload inside the due window from recording the same departure twice.
            if (waypoint != lastDepartureWaypoint || targetAbs != lastDepartureTarget) {
                punctuality.recordDeparture(waypoint.type(), waypoint.targetId(), (int) delta);
                lastDepartureWaypoint = waypoint;
                lastDepartureTarget = targetAbs;
            }
            scheduleCursor = Math.max(scheduleCursor, targetAbs);
            log.info("[AP] departure from {} {} scheduled {} -> delta {} min", waypoint.type(),
                    waypoint.targetId(), Timetable.toGameTime(targetAbs), delta);
            return false;
        }
        GameTime target = Timetable.toGameTime(targetAbs);
        long ticks = Math.max(0, clock.ticksUntil(target));
        mode = Mode.WAITING;
        final long serial = ++retentionSerial;
        log.info("[AP] holding at {} {} until {} ({} ticks, {} min early)", waypoint.type(),
                waypoint.targetId(), target, ticks, -delta);
        scheduler.schedule((int) Math.min(ticks, Integer.MAX_VALUE),
                () -> onRetentionDue(waypoint, targetAbs, serial));
        return true;
    }

    @Override
    public Optional<Punctuality> punctuality() {
        return punctuality.isEmpty() ? Optional.empty() : Optional.of(punctuality);
    }

    /**
     * Delayed release of a schedule hold; re-arms itself if the clock has not reached the departure
     * yet (it may have been rewound by {@code time set}) and ignores stale releases.
     */
    private void onRetentionDue(Waypoint waypoint, long targetAbs, long serial) {
        if (mode != Mode.WAITING || serial != retentionSerial) {
            return;
        }
        if (currentWaypoint().orElse(null) != waypoint) {
            return;
        }
        GameClock clock = gameClock();
        SimulationScheduler scheduler = scheduler();
        if (clock == null || scheduler == null) {
            completeRetention();
            return;
        }
        long nowAbs = Timetable.absoluteMinute(clock.now());
        if (nowAbs < targetAbs) {
            long ticks = Math.max(0, clock.ticksUntil(Timetable.toGameTime(targetAbs)));
            scheduler.schedule((int) Math.min(ticks, Integer.MAX_VALUE),
                    () -> onRetentionDue(waypoint, targetAbs, serial));
            return;
        }
        completeRetention();
    }

    private void completeRetention() {
        mode = Mode.FOLLOWING;
        if (train != null && train.getActionManager() != null) {
            train.getActionManager().onRetentionReleased();
        }
    }

    /**
     * Resolves a time of day onto a monotonic absolute minute: after the previous schedule event
     * when there is one (sequence with midnight rollover), otherwise the nearest occurrence around
     * the current time (see {@link Timetable#resolveNearest}).
     */
    private long resolveScheduleTime(LocalTime time, long nowAbs) {
        return scheduleCursor != NO_SCHEDULE_EVENT ? Timetable.resolveAfter(scheduleCursor, time)
                : Timetable.resolveNearest(nowAbs, time);
    }

    private GameClock gameClock() {
        if (train == null || train.getModel() == null) {
            return null;
        }
        return train.getModel().getGameClock();
    }

    private SimulationScheduler scheduler() {
        if (train == null || train.getModel() == null) {
            return null;
        }
        return train.getModel().getScheduler();
    }

    @Override
    public void clearRoute() {
        this.currentRoute = List.of();

    }

    @Override
    public void ensureForkRoute(Segment from, Segment to) {
        if (train == null || train.getModel() == null) {
            return;
        }
        RailwayGraph graph = train.getModel().getRailwayGraph();
        if (graph == null) {
            return;
        }

        var fromPorts = from.getPorts();
        var toPorts = to.getPorts();

        Port entryPort = null;
        Port exitPort = null;
        RailNode node = null;

        if (fromPorts != null && toPorts != null) {
            for (Port pFrom : new Port[] {fromPorts.getFirst(), fromPorts.getSecond()}) {
                if (pFrom == null) {
                    continue;
                }
                for (Port pTo : new Port[] {toPorts.getFirst(), toPorts.getSecond()}) {
                    if (pTo == null) {
                        continue;
                    }
                    if (pFrom.getNode().equals(pTo.getNode())) {
                        node = pFrom.getNode();
                        entryPort = pFrom;
                        exitPort = pTo;
                        break;
                    }
                }
                if (node != null) {
                    break;
                }
            }
        }

        if (node == null) {
            log.warn("[AP] ensureForkRoute {}->{}: no shared node found", from.getId(), to.getId());
            return;
        }
        if (!(node.getTrack() instanceof ForkRailTrack fork)) {
            log.debug("[AP] ensureForkRoute {}->{}: shared node is not a fork ({})", from.getId(),
                    to.getId(), node.getTrack());
            return;
        }

        if (entryPort != null && exitPort != null) {
            boolean routeChanged = node.setRoute(entryPort, exitPort);
            log.info("[AP] ensureForkRoute {}->{} using ports: entry={}, exit={}, routeChanged={}",
                    from.getId(), to.getId(), entryPort.getType(), exitPort.getType(),
                    routeChanged);
            return;
        }
        log.warn("[AP] ensureForkRoute {}->{}: no ports matched for the shared node", from.getId(),
                to.getId());
    }

    @Override
    public void replaceRouteSegment(Segment oldSeg, Segment newSeg) {
        if (currentRoute == null || currentRoute.isEmpty()) {
            return;
        }
        List<Segment> newRoute = new java.util.ArrayList<>(currentRoute);
        int index = newRoute.indexOf(oldSeg);
        if (index != -1) {
            newRoute.set(index, newSeg);
            currentRoute = List.copyOf(newRoute);
            log.info("[AP] replaceRouteSegment: replaced {} with {} at index {}", oldSeg.getId(),
                    newSeg.getId(), index);
            if (index > 0) {
                ensureForkRoute(newRoute.get(index - 1), newSeg);
            }
            if (index + 1 < newRoute.size()) {
                ensureForkRoute(newSeg, newRoute.get(index + 1));
            }
        }
    }

    @Override
    public void deactivate() {
        log.info("[AP] deactivate → IDLE");
        mode = Mode.IDLE;
        waitTicks = 0;
        pendingCommands.clear();
    }

    private Port getTrainExitPort(Segment currentSeg) {
        if (train == null || currentSeg == null) {
            return null;
        }
        var physicalFront = train.getPhysicalFront();
        if (physicalFront == null || physicalFront.getTrack() == null) {
            return null;
        }

        Dir dir = physicalFront.getRealDir();
        if (dir == null) {
            return null;
        }

        if (!(physicalFront.getTrack() instanceof RailTrack headTrack)) {
            return null;
        }
        if (train.getModel() == null) {
            return null;
        }
        RailwayGraph graph = train.getModel().getRailwayGraph();
        if (graph == null) {
            return null;
        }

        letrain.vehicle.rail.RailIterator it =
                new letrain.vehicle.rail.RailIterator(headTrack, dir);
        int maxIterations = 1000;
        letrain.track.Track boundaryTrack = headTrack;
        while (it.advance() && maxIterations-- > 0) {
            letrain.track.Track t = it.getTrack();
            if (t instanceof RailTrack rt) {
                if (!graph.containsTrack(currentSeg, rt)) {
                    if (rt instanceof ForkRailTrack || rt.getConnections().size() != 2) {
                        boundaryTrack = rt;
                    }
                    break;
                }
                boundaryTrack = t;
            }
        }

        letrain.utils.Pair<Port, Port> ports = currentSeg.getPorts();
        if (ports != null) {
            if (ports.getFirst() != null
                    && ports.getFirst().getNode().getTrack() == boundaryTrack) {
                return ports.getFirst();
            }
            if (ports.getSecond() != null
                    && ports.getSecond().getNode().getTrack() == boundaryTrack) {
                return ports.getSecond();
            }
            if (it.getTrack() != null) {
                letrain.track.Track nextTrack = it.getTrack();
                if (ports.getFirst() != null
                        && ports.getFirst().getNode().getTrack() == nextTrack) {
                    return ports.getFirst();
                }
                if (ports.getSecond() != null
                        && ports.getSecond().getNode().getTrack() == nextTrack) {
                    return ports.getSecond();
                }
            }
        }
        return null;
    }

    private boolean calculateRoute() {
        if (pathfinder == null || itinerary == null) {
            return false;
        }
        Waypoint wp = currentWaypoint().orElse(null);
        if (wp == null) {
            return false;
        }

        Segment currentSeg = getTrainCurrentSegment();
        Segment targetSeg = getTrainTargetSegment(wp);
        log.info("[AP] calcRoute currentSeg={} targetSeg={}",
                currentSeg != null ? currentSeg.getId() : "null",
                targetSeg != null ? targetSeg.getId() : "null");
        if (currentSeg == null || targetSeg == null) {
            return false;
        }

        Port exitPort = getTrainExitPort(currentSeg);
        log.info("[AP] calcRoute exitPort={}", exitPort != null ? exitPort.getType() : "null");
        currentRoute = pathfinder.find(currentSeg, Optional.ofNullable(exitPort), targetSeg,
                wp.entryDir());
        log.info("[AP] calcRoute result: {} segments{} route={}", currentRoute.size(),
                currentRoute.isEmpty() ? " → ROUTE NOT FOUND" : "",
                currentRoute.stream().map(Segment::getId).toList());
        return !currentRoute.isEmpty();
    }
}
