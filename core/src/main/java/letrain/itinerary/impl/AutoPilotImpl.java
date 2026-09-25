package letrain.itinerary.impl;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import letrain.itinerary.AutoPilot;
import letrain.itinerary.Itinerary;
import letrain.itinerary.Punctuality;
import letrain.itinerary.SegmentPathfinder;
import letrain.itinerary.Timetable;
import letrain.itinerary.TrainActionManager;
import letrain.itinerary.TrainMission;
import letrain.itinerary.Waypoint;
import letrain.itinerary.WaypointCommand;
import letrain.map.Dir;
import letrain.segments.Port;
import letrain.segments.RailNode;
import letrain.segments.RailwayGraph;
import letrain.segments.Segment;
import letrain.time.GameClock;
import letrain.time.GameTime;
import letrain.track.Sensor;
import letrain.track.Station;
import letrain.track.Track;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;
import letrain.utils.SimulationScheduler;
import letrain.vehicle.rail.Linker;
import letrain.vehicle.rail.RailIterator;
import letrain.vehicle.rail.impl.Locomotive;
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

    // Issue #619: one-shot mission state (in memory only: a mission is a maneuver, not a plan that
    // survives a reload; the command journal keeps the order so a replay can start it again).
    /** Active or last finished mission. */
    private transient TrainMission mission;
    /** The mission reversed the train at start because the destination was only reachable back. */
    private transient boolean missionReversed;
    /** Last plan failure was because the destination is physically behind the current sense. */
    private transient boolean missionPlanFailedBehind;
    /** Console sink for mission messages; null in scripts (log only). */
    private transient Consumer<String> missionNotifier;
    /** Ticks the mission train has been stopped without a block/schedule/loading reason. */
    private transient int missionStalledTicks;
    /** Guard for the physical walks that look for the destination / the end of the track. */
    private static final int MISSION_MAX_WALK = 1000;
    /**
     * Grace a mission train may stay stopped without a block/schedule/loading reason before the
     * mission fails with a warning (review m3). 1200 ticks = one game hour (ADR-022 clock).
     */
    private static final int MISSION_STALL_TICKS = 1200;

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
        if (mission == null && mode == Mode.FOLLOWING && itinerary == null) {
            // Missions are not serialized: a train saved in the middle of one would load "auto"
            // with nothing to follow. Fall back to manual instead of staying stuck.
            log.info("[AP] reinitialize: no mission or itinerary after load → IDLE");
            mode = Mode.IDLE;
        }
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
        if (mission != null && mission.isActive()) {
            // A mission is not an itinerary: no waypoint events while it runs.
            return Optional.empty();
        }
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
        cancelMissionQuietly();
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
        // The itinerary takes over any running mission.
        cancelMissionQuietly();
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
        letrain.mvp.Model model = train != null ? train.getModel() : null;
        if (model == null) {
            return null;
        }
        switch (wp.type()) {
            case STATION:
                Station st = model.getStation(wp.targetId());
                return st != null ? segmentAtPosition(st.getPosition()) : null;
            case SENSOR:
                letrain.track.Sensor se = model.getSensor(wp.targetId());
                return se != null ? segmentAtPosition(se.getPosition()) : null;
            default:
                return null;
        }
    }

    /** Segment that contains the given map position, or null when there is no track/graph. */
    private Segment segmentAtPosition(letrain.map.Point pos) {
        if (pos == null || train == null || train.getModel() == null) {
            return null;
        }
        letrain.segments.RailwayGraph graph = train.getModel().getRailwayGraph();
        if (graph == null) {
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
        if (mission != null && mission.isActive()) {
            onMissionSegmentEntered(currentSeg);
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
        cancelMission();
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

    /***********************************************************
     * Issue #619: one-shot missions (stop at / stop when blocked) Issue #645: stop on contact
     * (coupling approach)
     **********************************************************/

    @Override
    public Optional<TrainMission> mission() {
        return Optional.ofNullable(mission);
    }

    @Override
    public void setMissionNotifier(Consumer<String> notifier) {
        this.missionNotifier = notifier;
    }

    @Override
    public Optional<Segment> missionTargetSegment() {
        if (mission == null || !mission.isActive()) {
            return Optional.empty();
        }
        return Optional.ofNullable(getMissionTargetSegment(mission));
    }

    @Override
    public boolean startMission(TrainMission m) {
        if (train == null || m == null) {
            return false;
        }
        if (mission != null && mission.isActive()) {
            // One-shot orders are replaceable: the new one cancels the running mission.
            mission.cancel();
        } else if (m.origin() == TrainMission.Origin.LOOSE && itinerary != null
                && mode != Mode.IDLE) {
            m.fail();
            warnMission("Train " + train.getId() + " is running an itinerary; use 'train "
                    + train.getId() + " set autopilot false;' or add it to the itinerary");
            return false;
        }

        int desired = m.speed() > 0 ? m.speed() : currentDesiredSpeed();
        if (desired <= 0) {
            m.fail();
            warnMission("Train " + train.getId() + " has no speed set; add 'speed N' to the order");
            return false;
        }

        // The physical front direction must be consistent with the current sense before planning:
        // the linkers refresh their dirs at every advance, and a stopped train may have them stale.
        train.getMovementManager().refreshLinkersDirection();

        // A previous wait (e.g. a finished "stop when blocked" mission) is superseded by the new
        // order: clear it while the train is stopped, acquireInitialLocks will re-evaluate the
        // block with the new desired speed.
        if (train.getSpeed() == 0 && train.getSafetyManager() != null
                && train.getSafetyManager().isWaitingForBlock()) {
            train.getSafetyManager().cancelBlockWait();
        }

        boolean reverse = false;
        currentRoute = List.of();
        missionReversed = false;
        if (m.kind() == TrainMission.Kind.ON_CONTACT) {
            // Issue #645: no route, no brake plan and no auto-reversal; the train drives straight
            // at the ordered speed until the physical contact (onContact/onCrash).
        } else if (m.kind() == TrainMission.Kind.STATION || m.kind() == TrainMission.Kind.SENSOR) {
            if (getTrainCurrentSegment() == null || getMissionTargetSegment(m) == null) {
                m.fail();
                warnMission("Train " + train.getId() + ": " + m.description() + " not found");
                return false;
            }
            if (!planMissionRoute(m)) {
                m.fail();
                if (m.isItineraryManeuver() && missionPlanFailedBehind) {
                    warnMission("Train " + train.getId() + ": no route to " + m.description()
                            + " from the current sense; add 'reverse' to the itinerary");
                } else if (m.isItineraryManeuver()) {
                    warnMission("Train " + train.getId() + ": no route to " + m.description());
                } else {
                    warnMission("Train " + train.getId() + ": no route to " + m.description()
                            + " in either direction");
                }
                return false;
            }
            reverse = missionReversed;
        } else if (m.kind() == TrainMission.Kind.END_OF_TRACK) {
            if (walkRailsToEnd(travelDir()) < 0) {
                m.fail();
                warnMission("Train " + train.getId() + ": no end of track ahead");
                return false;
            }
        }

        mission = m;
        m.start();
        mode = Mode.FOLLOWING;
        missionStalledTicks = 0;
        if (headOnTarget()) {
            completeMission("already at " + m.description());
            return true;
        }
        if (m.kind() == TrainMission.Kind.ON_CONTACT && !hasRailAhead()) {
            // Issue #645 review M2: repeating the order while already pressed against the buffer
            // (no rail ahead) must complete again: the train cannot drive into anything, no
            // contact event will ever fire and the mission would stay active forever. Being
            // already pressed against a vehicle is completed by the instant contact check at
            // start (Locomotive.update).
            completeMission("already at the contact");
            return true;
        }
        // On the first switch there is no re-entry event: orient it before moving.
        if (currentRoute.size() >= 2) {
            ensureForkRoute(currentRoute.get(0), currentRoute.get(1));
        }
        train.setSpeed(desired);
        if (reverse) {
            train.reverse();
        }
        applyMissionStopPlan();
        if (m.kind() == TrainMission.Kind.WHEN_BLOCKED && train.getSpeed() == 0
                && train.getSafetyManager() != null
                && train.getSafetyManager().isWaitingForBlock()) {
            // Already blocked and stopped when the order arrived: complete in place.
            completeMission("blocked");
        } else if ((m.kind() == TrainMission.Kind.END_OF_TRACK
                || m.kind() == TrainMission.Kind.WHEN_BLOCKED) && missionRailsToStop() == 0) {
            completeMission("already at the end of track");
        }
        log.info("[AP] mission started: train {} {}, speed {}", train.getId(), m.description(),
                desired);
        return true;
    }

    @Override
    public void onRailAdvanced() {
        if (mission == null || !mission.isActive() || mode != Mode.FOLLOWING) {
            return;
        }

        switch (mission.kind()) {
            case STATION, SENSOR -> {
                if (headOnTarget()) {
                    completeMission("arrived at " + mission.description());
                    return;
                }
                applyMissionStopPlan();
            }
            case END_OF_TRACK -> {
                int rails = missionRailsToStop();
                if (rails < 0) {
                    failMission("lost the end of track ahead");
                } else if (rails == 0) {
                    completeMission("reached the end of track");
                } else {
                    applyStopPlan(rails);
                }
            }
            case WHEN_BLOCKED -> {
                // The mission ends when the train is actually stopped at the boundary: the #633
                // plan rolls it and brakes on the last rail of its canton first (user decision).
                if (train.getSafetyManager() != null && train.getSafetyManager().isWaitingForBlock()
                        && train.getSpeed() == 0) {
                    completeMission("blocked");
                    return;
                }
                int rails = missionRailsToStop();
                if (rails == 0) {
                    completeMission("blocked at the end of track");
                } else if (rails > 0) {
                    applyStopPlan(rails);
                }
            }
            case ON_CONTACT -> {
                // Issue #645: no braking curve. The coupling approach drives at the ordered speed
                // until the physical contact stops it; the mission completes in onContact (or
                // fails in onCrash at or above the crash threshold, normal physics).
            }
        }
    }

    @Override
    public void onTick() {
        if (mission == null || !mission.isActive() || mode != Mode.FOLLOWING) {
            missionStalledTicks = 0;
            return;
        }
        // A stop-when-blocked mission completes once the train is stopped at its canton boundary
        // (the safety plan rolls it there; there is no rail hook after the last move).
        if (mission.kind() == TrainMission.Kind.WHEN_BLOCKED && train.getSafetyManager() != null
                && train.getSafetyManager().isWaitingForBlock() && train.getSpeed() == 0) {
            completeMission("blocked");
            return;
        }
        boolean stoppedWithoutReason = train.getSpeed() == 0
                && (train.getSafetyManager() == null
                        || !train.getSafetyManager().isWaitingForBlock())
                && !train.isHeldBySchedule() && (train.getLogisticsManager() == null
                        || !train.getLogisticsManager().isLoading());
        if (!stoppedWithoutReason) {
            missionStalledTicks = 0;
            return;
        }
        missionStalledTicks++;
        if (missionStalledTicks >= MISSION_STALL_TICKS) {
            failMission("could not reach " + mission.description() + " (stalled)");
        }
    }

    /**
     * Issue #645: the coupling approach completes on the first low-speed physical contact. The
     * train is already emergency-stopped by {@code Train.notifyContact} and stays pressed against
     * the vehicle ahead, ready for {@code couple}. A contact with the buffer ahead (dead end) also
     * completes it: the physical stop is the goal of the order.
     */
    @Override
    public void onContact(letrain.map.Point pos, int speed) {
        if (mission == null || !mission.isActive() || mode != Mode.FOLLOWING) {
            return;
        }
        if (mission.kind() == TrainMission.Kind.ON_CONTACT) {
            completeMission("contact ahead");
        }
    }

    /**
     * Issue #645: at or above the crash threshold the contact is a crash (normal physics, no
     * shield); the mission fails with a warning instead of waiting for a train being destroyed.
     */
    @Override
    public void onCrash(letrain.map.Point pos, int speed) {
        if (mission == null || !mission.isActive() || mode != Mode.FOLLOWING) {
            return;
        }
        if (mission.kind() == TrainMission.Kind.ON_CONTACT) {
            failMission("crashed before touching the vehicle ahead");
        }
    }

    /**
     * Plans the route to a station/sensor. The physical walk decides first (the destination may be
     * inside the current segment, where A* cannot tell the sense): ahead → forward; only behind →
     * the order reverses once. When neither walk sees the destination, A* decides trusting that
     * switches will be oriented on the way.
     */
    private boolean planMissionRoute(TrainMission m) {
        ensurePathfinder();
        missionPlanFailedBehind = false;
        Segment currentSeg = getTrainCurrentSegment();
        Segment targetSeg = getMissionTargetSegment(m);
        if (pathfinder == null || currentSeg == null || targetSeg == null) {
            return false;
        }
        RailTrack targetTrack = missionTargetTrack(m);
        Dir dir = travelDir();
        // Itinerary maneuvers never auto-reverse: the author writes the 'reverse' explicitly.
        boolean allowReverse = !m.isItineraryManeuver();
        boolean sameSegment = currentSeg.equals(targetSeg);
        boolean ahead =
                targetTrack != null && dir != null && walkRailsToTrack(targetTrack, dir) >= 0;
        boolean behind = targetTrack != null && dir != null
                && walkRailsToTrack(targetTrack, dir.inverse()) >= 0;
        if (ahead) {
            if (m.isItineraryManeuver()) {
                // The author prepares switches with fork actions (ADR-022 phase 2f): follow the
                // physical walk so a forced switch is respected instead of A* re-orienting it.
                List<Segment> walked = walkRouteToTrack(targetTrack, dir);
                if (!walked.isEmpty()) {
                    currentRoute = walked;
                    return true;
                }
            }
            currentRoute = pathfinder.find(currentSeg,
                    Optional.ofNullable(getTrainExitPort(currentSeg)), targetSeg, Optional.empty());
            if (!currentRoute.isEmpty() || sameSegment) {
                return true;
            }
            // The physical walk found it, but A* cannot route from here (e.g. the destination is
            // beyond a switch it would have to orient): fall through so the order fails loudly
            // instead of starting a mission that cannot be followed (review m3).
        }
        if (allowReverse && behind) {
            currentRoute = pathfinder.find(currentSeg,
                    Optional.ofNullable(oppositePort(currentSeg)), targetSeg, Optional.empty());
            if (!currentRoute.isEmpty() || sameSegment) {
                missionReversed = true;
                return true;
            }
        }
        if (!allowReverse && behind) {
            // The destination is physically behind the current sense (inside the same segment A*
            // cannot tell): an itinerary maneuver needs an explicit 'reverse' (ADR-022 phase 2f).
            missionPlanFailedBehind = true;
            currentRoute = List.of();
            return false;
        }
        if (!sameSegment) {
            currentRoute = pathfinder.find(currentSeg,
                    Optional.ofNullable(getTrainExitPort(currentSeg)), targetSeg, Optional.empty());
            if (!currentRoute.isEmpty()) {
                return true;
            }
        }
        if (allowReverse) {
            currentRoute = pathfinder.find(currentSeg,
                    Optional.ofNullable(oppositePort(currentSeg)), targetSeg, Optional.empty());
            if (!currentRoute.isEmpty()) {
                missionReversed = true;
                return true;
            }
        }
        currentRoute = List.of();
        return false;
    }

    private void onMissionSegmentEntered(Segment currentSeg) {
        if (mission.kind() != TrainMission.Kind.STATION
                && mission.kind() != TrainMission.Kind.SENSOR) {
            return;
        }
        if (pathfinder != null && (currentRoute.isEmpty() || !currentRoute.contains(currentSeg))) {
            // The train left the planned route: try to re-plan from here and, when there is no way
            // to the destination, fail with a warning instead of rolling on forever (review m3).
            Segment targetSeg = getMissionTargetSegment(mission);
            List<Segment> route = targetSeg == null ? List.of()
                    : pathfinder.find(currentSeg, Optional.ofNullable(getTrainExitPort(currentSeg)),
                            targetSeg, Optional.empty());
            if (route.isEmpty()) {
                failMission("lost the route to " + mission.description());
                return;
            }
            currentRoute = route;
        }
        int index = currentRoute.indexOf(currentSeg);
        if (index != -1 && index + 1 < currentRoute.size()) {
            ensureForkRoute(currentSeg, currentRoute.get(index + 1));
        }
    }

    /**
     * Applies the braking curve towards the mission stop point, using the same helpers as the
     * safety layer (issue #633). It only caps the target speed down and engages the brake; the
     * block wait gate always wins, so safety is never overridden.
     */
    private void applyMissionStopPlan() {
        if (mission == null || !mission.isActive() || mode != Mode.FOLLOWING) {
            return;
        }
        if (train.isPendingReverse()) {
            // The reversal has not happened yet: the walk would use the old sense.
            return;
        }
        // No isWaitingForBlock guard is needed: the plan only caps the target down and brakes, so
        // it can never raise the safety layer's own boundary plan (issue #619).
        int railsToStop = missionRailsToStop();
        if (railsToStop >= 0) {
            applyStopPlan(railsToStop);
        }
    }

    private void applyStopPlan(int railsToStop) {
        Locomotive loco = directorLocomotive();
        if (loco == null) {
            return;
        }
        if (railsToStop <= 0) {
            loco.setTargetSpeedDirect(0);
            return;
        }
        int cap = Locomotive.maxSpeedForRails(railsToStop);
        if (loco.getTargetSpeed() > cap) {
            loco.setTargetSpeedDirect(cap);
        }
        if (loco.brakingRailsFromCurrentState() > railsToStop) {
            loco.setTargetSpeedDirect(0);
        }
    }

    /**
     * Advances from the head to the mission's stop point walking the physical route with the
     * switches as they are now. {@code -1} when it cannot be determined (target not on the walk,
     * loop, head without direction). For end of track / blocked it returns the standoff one rail
     * before the buffer, so the train does not contact it.
     *
     * <p>
     * {@code stop on contact} has no stop point: it intentionally drives up to the physical contact
     * (issue #645), so it always returns {@code -1} and no braking curve is applied.
     */
    private int missionRailsToStop() {
        if (mission == null || train == null || train.isPendingReverse()) {
            return -1;
        }
        Dir dir = travelDir();
        if (dir == null) {
            return -1;
        }
        if (mission.kind() == TrainMission.Kind.ON_CONTACT) {
            return -1;
        }
        if (mission.kind() == TrainMission.Kind.STATION
                || mission.kind() == TrainMission.Kind.SENSOR) {
            return walkRailsToTrack(missionTargetTrack(mission), dir);
        }
        int railsToEnd = walkRailsToEnd(dir);
        return railsToEnd < 0 ? -1 : Math.max(0, railsToEnd - 1);
    }

    /** Rails ahead until the given track is reached, following the physical route. -1 unknown. */
    private int walkRailsToTrack(RailTrack target, Dir dir) {
        if (target == null || dir == null) {
            return -1;
        }
        Linker head = train != null ? train.getPhysicalFront() : null;
        if (head == null || !(head.getTrack() instanceof RailTrack headTrack)) {
            return -1;
        }
        if (headTrack == target) {
            return 0;
        }
        RailIterator it = new RailIterator(headTrack, dir);
        int steps = 0;
        while (steps < MISSION_MAX_WALK && it.advance()) {
            steps++;
            if (it.getTrack() == target) {
                return steps;
            }
        }
        return -1;
    }

    /**
     * Rails ahead until the track ends. 0 = head on the last rail. -1 unknown (loop/guard).
     */
    private int walkRailsToEnd(Dir dir) {
        if (dir == null) {
            return -1;
        }
        Linker head = train != null ? train.getPhysicalFront() : null;
        if (head == null || !(head.getTrack() instanceof RailTrack headTrack)) {
            return -1;
        }
        RailIterator it = new RailIterator(headTrack, dir);
        int steps = 0;
        while (steps < MISSION_MAX_WALK && it.advance()) {
            steps++;
        }
        return steps >= MISSION_MAX_WALK ? -1 : steps;
    }

    /**
     * Route (segments from the current one to the target) following the physical walk with the
     * switches as they are now. Empty when the walk cannot reach the target. Itinerary maneuvers
     * use it so a switch forced by a fork action is respected (ADR-022 phase 2f).
     */
    private List<Segment> walkRouteToTrack(RailTrack target, Dir dir) {
        if (target == null || dir == null || train == null || train.getModel() == null) {
            return List.of();
        }
        RailwayGraph graph = train.getModel().getRailwayGraph();
        Linker head = train.getPhysicalFront();
        if (graph == null || head == null || !(head.getTrack() instanceof RailTrack headTrack)) {
            return List.of();
        }
        List<Segment> route = new java.util.ArrayList<>();
        RailIterator it = new RailIterator(headTrack, dir);
        int steps = 0;
        while (steps <= MISSION_MAX_WALK) {
            // Fork rails are nodes shared by two segments: skip them so the route lists the
            // segments actually travelled (a fork's own segment would repeat the neighbour).
            if (!(it.getTrack() instanceof ForkRailTrack)
                    && it.getTrack() instanceof RailTrack rail) {
                Segment segment = graph.getSegment(rail);
                if (segment != null
                        && (route.isEmpty() || !route.get(route.size() - 1).equals(segment))) {
                    route.add(segment);
                }
            }
            if (it.getTrack() == target) {
                return route;
            }
            if (!it.advance()) {
                return List.of();
            }
            steps++;
        }
        return List.of();
    }

    /** Track of the station/sensor the mission drives to, or null. */
    private RailTrack missionTargetTrack(TrainMission m) {
        letrain.mvp.Model model = train != null ? train.getModel() : null;
        if (model == null) {
            return null;
        }
        letrain.map.Point pos = null;
        if (m.kind() == TrainMission.Kind.STATION) {
            Station st = model.getStation(m.targetId());
            pos = st != null ? st.getPosition() : null;
        } else if (m.kind() == TrainMission.Kind.SENSOR) {
            letrain.track.Sensor se = model.getSensor(m.targetId());
            pos = se != null ? se.getPosition() : null;
        }
        if (pos == null) {
            return null;
        }
        Track track = model.getRailMap().getTrackAt(pos);
        return track instanceof RailTrack rt ? rt : null;
    }

    private Segment getMissionTargetSegment(TrainMission m) {
        letrain.mvp.Model model = train != null ? train.getModel() : null;
        if (model == null) {
            return null;
        }
        if (m.kind() == TrainMission.Kind.STATION) {
            Station st = model.getStation(m.targetId());
            return st != null ? segmentAtPosition(st.getPosition()) : null;
        }
        if (m.kind() == TrainMission.Kind.SENSOR) {
            letrain.track.Sensor se = model.getSensor(m.targetId());
            return se != null ? segmentAtPosition(se.getPosition()) : null;
        }
        if (m.kind() == TrainMission.Kind.ON_CONTACT) {
            // Issue #645: the approach target is resolved dynamically to the first vehicle ahead
            // (the physical walk with the switches as they are now). The safety layer uses it to
            // let the maneuver enter the canton occupied by loco-less trains (e.g. its own
            // detached part).
            return approachedVehicleSegment();
        }
        return null;
    }

    /**
     * Canton of the first vehicle ahead on the physical walk (issue #645), or null when there is
     * none in sight. Only vehicles of other trains count: a link of our own consist ahead is not a
     * contact target.
     */
    private Segment approachedVehicleSegment() {
        if (train == null || train.getModel() == null) {
            return null;
        }
        RailwayGraph graph = train.getModel().getRailwayGraph();
        Linker head = train.getPhysicalFront();
        Dir dir = travelDir();
        if (graph == null || head == null || dir == null
                || !(head.getTrack() instanceof RailTrack headTrack)) {
            return null;
        }
        RailIterator it = new RailIterator(headTrack, dir);
        int steps = 0;
        while (steps < MISSION_MAX_WALK && it.advance()) {
            steps++;
            Linker occupant = it.getTrack().getLinker();
            if (occupant != null && occupant.getTrain() != null && occupant.getTrain() != train) {
                return it.getTrack() instanceof RailTrack rail ? graph.getSegment(rail) : null;
            }
        }
        return null;
    }

    /** The head (leading vehicle) is on the mission's station/sensor cell. */
    private boolean headOnTarget() {
        if (mission == null || train == null) {
            return false;
        }
        Linker head = train.getPhysicalFront();
        if (head == null || !(head.getTrack() instanceof RailTrack rt)) {
            return false;
        }
        Sensor component = rt.getComponent();
        if (mission.kind() == TrainMission.Kind.STATION) {
            return component instanceof Station st && st.getId() == mission.targetId();
        }
        // Plain sensor only: speed signals and stations have their own id counters and could share
        // the numeric id.
        return component != null && !(component instanceof Station)
                && !(component instanceof letrain.track.SpeedSignal)
                && component.getId() == mission.targetId();
    }

    private void completeMission(String reason) {
        if (mission == null) {
            return;
        }
        mission.complete();
        mode = modeAfterMission(mission);
        stopTrainForMission();
        notifyMission("Train " + train.getId() + " " + reason);
    }

    private void failMission(String reason) {
        if (mission == null) {
            return;
        }
        mission.fail();
        mode = modeAfterMission(mission);
        stopTrainForMission();
        warnMission("Train " + train.getId() + " " + reason);
    }

    /**
     * A loose mission returns the train to manual; an itinerary maneuver keeps the autopilot
     * following the plan (the waypoint action flow continues with the next action/departure).
     */
    private Mode modeAfterMission(TrainMission finished) {
        return finished.isItineraryManeuver() ? Mode.FOLLOWING : Mode.IDLE;
    }

    private void cancelMission() {
        if (mission != null && mission.isActive()) {
            mission.cancel();
            notifyMission("Train " + train.getId() + " mission cancelled");
        }
    }

    private void cancelMissionQuietly() {
        if (mission != null && mission.isActive()) {
            mission.cancel();
        }
    }

    /** The mission is over: forget the deferred speed and brake to a full stop. */
    private void stopTrainForMission() {
        if (train == null) {
            return;
        }
        train.setSavedTargetSpeed(-1);
        train.setSpeed(0);
        // A "stop when blocked" mission may end while the safety layer holds a block wait. The
        // autopilot is now IDLE, so onBlockReleased would never clear it and the wait gate would
        // swallow later manual speed orders. Clearing it here is inert: the target is already 0
        // and a manual train cannot resume on release (review M1).
        if (train.getSafetyManager() != null && train.getSafetyManager().isWaitingForBlock()) {
            train.getSafetyManager().cancelBlockWait();
        }
    }

    private Locomotive directorLocomotive() {
        return train != null && train.getDirectorLinker() instanceof Locomotive loco ? loco : null;
    }

    /** Travel direction of the physical front (leading vehicle), or null. */
    private Dir travelDir() {
        Linker head = train != null ? train.getPhysicalFront() : null;
        return head != null ? head.getRealDir() : null;
    }

    /**
     * True when the physical front has a rail connected ahead (issue #645 review M2): when it has
     * none the train is pressed against a buffer and cannot drive into anything, so a contact
     * approach is already satisfied.
     */
    private boolean hasRailAhead() {
        Linker head = train != null ? train.getPhysicalFront() : null;
        return head != null && head.getTrack() != null
                && head.getTrack().getConnected(head.getDir()) != null;
    }

    /** The other port of the current segment (the one not used by the current sense). */
    private Port oppositePort(Segment seg) {
        if (seg == null) {
            return null;
        }
        var ports = seg.getPorts();
        if (ports == null) {
            return null;
        }
        Port first = ports.getFirst();
        Port second = ports.getSecond();
        Port exit = getTrainExitPort(seg);
        if (exit != null) {
            if (first != null && first.getNode().equals(exit.getNode())) {
                return second;
            }
            if (second != null && second.getNode().equals(exit.getNode())) {
                return first;
            }
        }
        return second != null ? second : first;
    }

    private void ensurePathfinder() {
        if (pathfinder != null || train == null || train.getModel() == null) {
            return;
        }
        if (train.getModel().getRailwayGraph() == null) {
            return;
        }
        pathfinder = new letrain.itinerary.AStarPathfinder(train.getModel().getRailwayGraph(),
                train.getModel().getBlockManager(), train);
    }

    /** Speed the mission runs at when the order has no explicit one. */
    private int currentDesiredSpeed() {
        if (train == null || train.getDirectorLinker() == null) {
            return 0;
        }
        int target = train.getDirectorLinker().getTargetSpeed();
        if (target == 0 && train.hasSavedTargetSpeed()) {
            target = train.getSavedTargetSpeed();
        }
        return target;
    }

    /**
     * Success notice: log only. A completed maneuver is not a problem, so it is not shown on the
     * console (user UX decision); problems still go through {@link #warnMission}.
     */
    private void notifyMission(String text) {
        log.info("[AP] {}", text);
    }

    /** Problem notice: log plus the console sink when there is one (scripts stay log-only). */
    private void warnMission(String text) {
        log.warn("[AP] {}", text);
        if (missionNotifier != null) {
            missionNotifier.accept(text);
        }
    }
}
