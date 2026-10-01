package letrain.itinerary.impl;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import letrain.itinerary.TrainMission;
import letrain.itinerary.Waypoint;
import letrain.itinerary.WaypointCommand;
import letrain.track.Station;
import letrain.vehicle.Tractor;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TrainActionManager implements letrain.itinerary.TrainActionManager {
    public static final Logger log = LoggerFactory.getLogger(Train.class);
    Train train;
    private final transient List<WaypointCommand> pendingCommands;
    private transient int waitTicks = 0;
    private transient int savedTargetSpeed = 0;
    private transient WaypointCommand pendingCommandToResume = null;
    /** Mission started by the current waypoint action (ADR-022 phase 2f), if any. */
    private transient TrainMission pendingMission = null;
    private letrain.itinerary.Waypoint currentProcessingWaypoint;

    public TrainActionManager(Train train) {
        this.train = train;
        this.pendingCommands = new CopyOnWriteArrayList<>();
    }

    @Override
    public void onWaypointReached(Train train, Waypoint waypoint) {
        if (waypoint == currentProcessingWaypoint) {
            return;
        }
        startWaypoint(waypoint);
        runPendingCommands();
    }

    /**
     * Arrival bookkeeping shared by the arrival callback and the consecutive-waypoint chain:
     * measures the arrival, brakes for a scheduled departure and loads the waypoint's actions.
     */
    private void startWaypoint(Waypoint waypoint) {
        currentProcessingWaypoint = waypoint;
        pendingMission = null;
        letrain.itinerary.AutoPilot autopilot = train.getAutopilot();
        if (autopilot != null && autopilot.itinerary().isPresent()) {
            // Issue #645 follow-up: the waypoint is reached now, so its segment no longer counts
            // as pending even if the actions drive the train away from it (the safety layer uses
            // this to allow a parallel bypass of an occupied segment, e.g. own detached wagons).
            autopilot.markCurrentWaypointReached();
            // ADR-022 phase 2b: measure the arrival and, when the stop has a departure, start
            // braking now so the train waits at the waypoint instead of rolling past it.
            autopilot.measureArrival(waypoint);
            brakeForScheduledDeparture(waypoint);
        }
        pendingCommands.clear();
        pendingCommands.addAll(waypoint.commands());
    }

    /** A waypoint with a departure is a scheduled stop: brake on arrival (safety first). */
    private void brakeForScheduledDeparture(Waypoint waypoint) {
        if (waypoint == null || waypoint.departure().isEmpty()) {
            return;
        }
        Tractor director = train.getDirectorLinker();
        if (director != null && director.getSpeed() > 0) {
            train.getMovementManager().initiateBraking();
        }
    }

    @Override
    public void onSpeedChanged(int speed) {
        if (speed == 0 && pendingCommandToResume != null) {
            resumeDeferredCommand();
        }
    }

    /**
     * Buffer contacts stop the train from inside {@code Train.guardNotify}: the re-entrant
     * {@code notifySpeedChanged(0)} is dropped, so a command deferred for the stop (PARK,
     * LOAD/UNLOAD) must be resumed here or the waypoint would never complete. Crashes stall the
     * train and are ignored (the train is being destroyed).
     */
    @Override
    public void onContact(Train train, letrain.map.Point pos, int speed) {
        resumeDeferredCommand();
    }

    @Override
    public void onCrash(Train train, letrain.map.Point pos, int speed) {
        resumeDeferredCommand();
    }

    /** Runs a command that was waiting for the train to stop, then continues the waypoint. */
    private void resumeDeferredCommand() {
        if (pendingCommandToResume == null || train.isStalled()) {
            return;
        }
        WaypointCommand cmd = pendingCommandToResume;
        pendingCommandToResume = null;
        if (executeCommand(cmd)) {
            return;
        }
        runPendingCommands();
    }

    @Override
    public void onLoadingFinished(Train train) {
        if (savedTargetSpeed > 0 && pendingCommands.isEmpty()) {
            train.setSpeed(savedTargetSpeed);
            savedTargetSpeed = 0;
        }
        runPendingCommands();
    }

    private void runPendingCommands() {
        if (!completePendingCommands()) {
            return;
        }

        letrain.itinerary.AutoPilot autopilot = train.getAutopilot();
        if (autopilot == null || autopilot.itinerary().isEmpty()) {
            return;
        }

        int startIndex = autopilot.currentWaypointIndex();
        autopilot.advanceWaypoint();
        autopilot.clearRoute();
        currentProcessingWaypoint = null;

        // Consecutive waypoints: the train is already on the next stop, so its actions run now.
        // The chain is a bounded loop over the waypoints not processed yet in this pass: a
        // repeated stop neither recurses nor replays its actions.
        java.util.Set<Integer> processed = new java.util.HashSet<>();
        processed.add(startIndex);
        int waypointCount = autopilot.itinerary().map(it -> it.waypoints().size()).orElse(0);
        while (processed.size() < waypointCount) {
            int index = autopilot.currentWaypointIndex();
            if (processed.contains(index)) {
                break;
            }
            Waypoint wp = autopilot.currentWaypoint().orElse(null);
            if (wp == null || !train.isCurrentlyOn(wp)) {
                break;
            }
            processed.add(index);
            log.info("Train {} consecutive waypoint reached", train.getId());
            startWaypoint(wp);
            if (!completePendingCommands()) {
                return;
            }
            autopilot.advanceWaypoint();
            autopilot.clearRoute();
            currentProcessingWaypoint = null;
        }

        if (autopilot.mode() == letrain.itinerary.AutoPilot.Mode.FOLLOWING
                && this.train.getSafetyManager() != null) {
            this.train.notifyAutopilotSegmentEntered(this.train.resolveCurrentSegmentFromGraph());
            this.train.getSafetyManager().acquireInitialLocks();
        }
        resumePlanCruiseIfStopped(autopilot);
    }

    /**
     * Issue #645 follow-up: a waypoint without departure must not leave the plan's cruise dead.
     * After the actions end and the flow advanced to the next (not yet reached) waypoint, resume
     * the programmed speed when the train ended fully stopped. Deliberate states win: a park
     * (engines explicitly off) stays parked and a block wait keeps waiting; a train that is still
     * rolling (or already has a target) is left untouched.
     */
    private void resumePlanCruiseIfStopped(letrain.itinerary.AutoPilot autopilot) {
        if (autopilot.mode() != letrain.itinerary.AutoPilot.Mode.FOLLOWING
                || autopilot.currentWaypointReached()) {
            return;
        }
        if (train.getDirectorLinker() == null || train.getDirectorLinker().getTargetSpeed() != 0
                || train.getSpeed() != 0 || train.getProgrammedSpeed() <= 0) {
            return;
        }
        if (train.getSafetyManager() != null && train.getSafetyManager().isWaitingForBlock()) {
            return;
        }
        if (train.isPendingManualMode()) {
            // An invasion/emergency stop is switching the train to manual: never override it.
            return;
        }
        for (Locomotive locomotive : train.getLocomotives()) {
            if (!locomotive.isEngineOn()) {
                // Deliberate park without a later departure: keep the train parked.
                return;
            }
        }
        log.info("Train {} resumes the plan cruise {} after the waypoint actions", train.getId(),
                train.getProgrammedSpeed());
        train.setSpeed(train.getProgrammedSpeed());
    }

    /**
     * Runs the pending actions and the departure handling of the current waypoint.
     *
     * @return true when the waypoint is complete and the flow may advance; false when a command
     *         deferred (mission/wait/load) or the train is holding until a departure.
     */
    private boolean completePendingCommands() {
        while (!pendingCommands.isEmpty()) {
            WaypointCommand cmd = pendingCommands.remove(0);
            if (executeCommand(cmd)) {
                return false;
            }
        }

        letrain.itinerary.AutoPilot autopilot = train.getAutopilot();
        if (autopilot != null && autopilot.itinerary().isPresent()) {
            if (autopilot.mode() == letrain.itinerary.AutoPilot.Mode.IDLE) {
                log.info("Train {} autopilot IDLE", train.getId());
                return false;
            }

            // ADR-022 phase 2b: hold at the waypoint until its departure; when the time is due or
            // already past, the release completes the stop and measures the departure delta.
            if (autopilot.retainUntilDeparture()) {
                holdTrainAtWaypoint();
                return false;
            }
            Waypoint waypoint = autopilot.currentWaypoint().orElse(null);
            if (waypoint != null && waypoint.departure().isPresent()) {
                // A scheduled departure starts the engine (park left it explicitly off) and
                // resumes the cruise speed; safety still gates the actual movement afterwards.
                startEnginesAndResume();
            }
        }

        if (savedTargetSpeed > 0) {
            train.setSpeed(savedTargetSpeed);
            savedTargetSpeed = 0;
        }
        return true;
    }

    /** Keeps the train stopped while the schedule holds it; the departure will resume it. */
    private void holdTrainAtWaypoint() {
        if (train.getDirectorLinker() == null) {
            return;
        }
        if (train.getDirectorLinker().getSpeed() > 0) {
            train.getMovementManager().initiateBraking();
        } else {
            train.brake();
        }
    }

    /** Scheduled departure: engine on for the whole consist and restore the cruise speed. */
    private void startEnginesAndResume() {
        List<Locomotive> locomotives = train.getLocomotives();
        if (locomotives != null) {
            for (Locomotive locomotive : locomotives) {
                locomotive.setEngineOn(true);
            }
        }
        train.restoreSpeed();
    }

    @Override
    public void onRetentionReleased() {
        this.waitTicks = 0;
        runPendingCommands();
    }

    private boolean executeCommand(WaypointCommand command) {
        if (command == null) {
            return false;
        }
        switch (command.kind()) {
            case LOAD:
            case UNLOAD: {
                boolean isUnload = command.kind() == WaypointCommand.Kind.UNLOAD;
                Station station = train.getLogisticsManager() != null
                        ? train.getLogisticsManager().getStationAtTrain()
                        : null;
                if (station == null && train.getModel() != null && currentProcessingWaypoint != null
                        && currentProcessingWaypoint.type() == Waypoint.Type.STATION) {
                    station = train.getModel().getStation(currentProcessingWaypoint.targetId());
                }

                if (station != null && train.getLogisticsManager() != null) {
                    List<letrain.vehicle.rail.impl.Wagon> capableWagons =
                            train.getLogisticsManager().getCapableWagons(station, isUnload);
                    if (capableWagons.isEmpty()) {
                        log.info(
                                "Train {} has no capable wagons for {} at station {}, passing through without stopping",
                                train.getId(), command.kind(), station.getName());
                        return false;
                    }
                }

                if (train.getDirectorLinker() != null && train.getDirectorLinker().getSpeed() > 0) {
                    if (savedTargetSpeed <= 0) {
                        savedTargetSpeed = train.getDirectorLinker().getTargetSpeed() > 0
                                ? train.getDirectorLinker().getTargetSpeed()
                                : train.getDirectorLinker().getSpeed();
                    }
                    this.pendingCommandToResume = command;
                    train.getMovementManager().initiateBraking();
                    return true;
                }

                Station stopStation = train.getLogisticsManager() != null
                        ? train.getLogisticsManager().getStationAtTrain()
                        : null;
                if (stopStation != null) {
                    if (isUnload) {
                        train.unload();
                    } else {
                        train.load();
                    }
                    if (train.getLogisticsManager() != null
                            && train.getLogisticsManager().isLoading()) {
                        return true;
                    }
                } else {
                    log.info("Train {} stopped outside station for {}, skipping action",
                            train.getId(), command.kind());
                }
                return false;
            }
            case WAIT:
                this.waitTicks = command.seconds() * WaypointCommand.TICKS_PER_SECOND;
                scheduleResume(this.waitTicks);
                return true;
            case REVERSE:
                train.reverse();
                return false;
            case SPEED:
                train.setSpeed(command.targetSpeed());
                savedTargetSpeed = 0;
                return false;
            case STOP:
                train.getMovementManager().initiateBraking();
                train.setPendingManualMode(true);
                if (train.getAutopilot() != null) {
                    train.getAutopilot().deactivate();
                }
                pendingCommands.clear();
                return true;
            case PARK: {
                Tractor director = train.getDirectorLinker();
                if (director != null && director.getSpeed() > 0) {
                    // Brake first; the engine is switched off once the train is stopped.
                    pendingCommandToResume = command;
                    train.getMovementManager().initiateBraking();
                    return true;
                }
                if (director != null && director.getTargetSpeed() > 0) {
                    train.brake();
                }
                turnOffEngines();
                return false;
            }
            case COUPLE:
            case UNCOUPLE:
                return executeCouplingCommand(command);
            case MISSION:
                return executeMissionCommand(command);
            case FORK_SET_DIRECTION:
            case FORK_FLIP:
                executeForkCommand(command);
                return false;
            default:
                return false;
        }
    }

    /**
     * ADR-022 phase 2f: {@code uncouple}/{@code couple} waypoint actions. The engine only splits or
     * joins when the train is stopped, so a rolling train brakes first and the command is resumed
     * once it halts.
     */
    private boolean executeCouplingCommand(WaypointCommand command) {
        if (train.getDirectorLinker() != null && train.getDirectorLinker().getSpeed() > 0) {
            pendingCommandToResume = command;
            train.getMovementManager().initiateBraking();
            return true;
        }
        letrain.vehicle.rail.TrainCouplingManager coupling = train.getTrainCouplingManager();
        if (coupling == null) {
            return false;
        }
        String count = command.count() == letrain.vehicle.rail.TrainCouplingManager.ALL ? "all"
                : String.valueOf(command.count());
        if (command.kind() == WaypointCommand.Kind.COUPLE) {
            coupling.prepareLink(train, command.forward(), command.count());
            coupling.joinLinkers(train);
            log.info("Train {} waypoint action: coupled {} vehicle(s) {}", train.getId(), count,
                    command.forward() ? "forward" : "backward");
        } else {
            coupling.prepareUnlink(train, command.forward(), command.count());
            coupling.divideTrain(train,
                    () -> train.getModel() != null ? train.getModel().nextTrainId() : 0);
            log.info("Train {} waypoint action: uncoupled {} vehicle(s) {}", train.getId(), count,
                    command.forward() ? "forward" : "backward");
        }
        return false;
    }

    /**
     * ADR-022 phase 2f: a movement order is a mission that must complete before the next action
     * runs. The mission is itinerary-origin: it does not auto-reverse, and the autopilot keeps
     * following the plan when it ends. The next action only runs with the train fully stopped.
     */
    private boolean executeMissionCommand(WaypointCommand command) {
        letrain.itinerary.AutoPilot autopilot = train.getAutopilot();
        if (autopilot == null || command.missionKind() == null) {
            return false;
        }
        if (pendingMission != null) {
            if (pendingMission.isActive()) {
                pendingCommandToResume = command;
                return true;
            }
            if (train.getDirectorLinker() != null && train.getDirectorLinker().getSpeed() > 0) {
                // Mission finished but the train is still braking to a stop.
                pendingCommandToResume = command;
                train.getMovementManager().initiateBraking();
                return true;
            }
            pendingMission = null;
            return false;
        }
        TrainMission mission = TrainMission.forItinerary(command.missionKind(), command.targetId(),
                command.targetSpeed());
        if (!autopilot.startMission(mission)) {
            // Rejected with a warning (no route from the current sense, no speed, unknown target):
            // abort the rest of the waypoint's actions so the choreography does not continue in a
            // wrong state. The departure and the route to the next waypoint still run.
            if (!pendingCommands.isEmpty()) {
                log.warn(
                        "Train {} waypoint action: mission rejected, aborting the remaining {} action(s) of the waypoint",
                        train.getId(), pendingCommands.size());
                pendingCommands.clear();
            }
            return false;
        }
        pendingMission = mission;
        if (!mission.isActive()) {
            // The mission completed synchronously (already at the destination, already blocked or
            // already at the end of track): only wait if the train is still rolling to a stop.
            if (train.getDirectorLinker() != null && train.getDirectorLinker().getSpeed() > 0) {
                pendingCommandToResume = command;
                train.getMovementManager().initiateBraking();
                return true;
            }
            pendingMission = null;
            return false;
        }
        pendingCommandToResume = command;
        return true;
    }

    /** ADR-022 phase 2f: forces or prepares the switch before the train gets to it. */
    private void executeForkCommand(WaypointCommand command) {
        if (train.getModel() == null) {
            return;
        }
        letrain.track.rail.ForkRailTrack fork = train.getModel().getFork(command.forkId());
        if (fork == null) {
            log.warn("Train {} waypoint action: fork {} not found", train.getId(),
                    command.forkId());
            return;
        }
        if (command.kind() == WaypointCommand.Kind.FORK_FLIP) {
            fork.flipRoute();
            log.info("Train {} waypoint action: fork {} flipped", train.getId(), command.forkId());
            return;
        }
        String direction = command.forkDirection() == null ? "" : command.forkDirection();
        switch (direction) {
            case "straight" -> fork.setStraightRoute();
            case "curved" -> fork.setCurvedRoute();
            default -> {
                try {
                    letrain.map.Dir dir = letrain.map.Dir.valueOf(direction.toUpperCase());
                    if (fork.getOriginalRoute() != null
                            && fork.getOriginalRoute().getValue() == dir) {
                        fork.setNormalRoute();
                    } else if (fork.getAlternativeRoute() != null
                            && fork.getAlternativeRoute().getValue() == dir) {
                        fork.setAlternativeRoute();
                    }
                } catch (IllegalArgumentException e) {
                    log.warn("Train {} waypoint action: unknown fork direction '{}'", train.getId(),
                            direction);
                }
            }
        }
        log.info("Train {} waypoint action: fork {} set {}", train.getId(), command.forkId(),
                direction);
    }

    /** Explicit engine off for the whole consist; the autopilot keeps running (ADR-022 2b). */
    private void turnOffEngines() {
        List<Locomotive> locomotives = train.getLocomotives();
        if (locomotives != null) {
            for (Locomotive locomotive : locomotives) {
                locomotive.setEngineOn(false);
            }
        }
        log.info("Train {} parked: engine off, autopilot kept", train.getId());
    }

    private void scheduleResume(int ticks) {
        if (train.getModel() != null && train.getModel().getScheduler() != null) {
            train.getModel().getScheduler().schedule(ticks, () -> {
                resumeWaiting();
                this.acquireInitialLocks();
            });
        }
    }

    private void resumeWaiting() {
        this.waitTicks = 0;
        runPendingCommands();
    }

    private void acquireInitialLocks() {
        if (this.train.getModel() != null && this.train.isAutoMode()) {
            this.train.notifyAutopilotSegmentEntered(this.train.resolveCurrentSegmentFromGraph());
        }
        if (this.train.getSafetyManager() != null && this.train.getModel() != null) {
            this.train.getSafetyManager().acquireInitialLocks();
        }
    }
}
