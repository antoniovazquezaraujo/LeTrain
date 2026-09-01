package letrain.itinerary.impl;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import letrain.itinerary.Waypoint;
import letrain.itinerary.WaypointCommand;
import letrain.track.Station;
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
        currentProcessingWaypoint = waypoint;
        pendingCommands.clear();
        pendingCommands.addAll(waypoint.commands());
        runPendingCommands();
    }

    @Override
    public void onSpeedChanged(int speed) {
        if (speed == 0 && pendingCommandToResume != null) {
            WaypointCommand cmd = pendingCommandToResume;
            pendingCommandToResume = null;
            if (executeCommand(cmd)) {
                return;
            }
            runPendingCommands();
        }
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
        while (!pendingCommands.isEmpty()) {
            WaypointCommand cmd = pendingCommands.remove(0);
            if (executeCommand(cmd)) {
                return;
            }
        }

        if (savedTargetSpeed > 0) {
            train.setSpeed(savedTargetSpeed);
            savedTargetSpeed = 0;
        }

        letrain.itinerary.AutoPilot autopilot = train.getAutopilot();
        if (autopilot != null && autopilot.itinerary().isPresent()) {
            if (autopilot.mode() == letrain.itinerary.AutoPilot.Mode.IDLE) {
                log.info("Train {} autopilot IDLE", train.getId());
                return;
            }

            autopilot.advanceWaypoint();
            autopilot.clearRoute();
            currentProcessingWaypoint = null;

            autopilot.currentWaypoint().ifPresent(wp -> {
                if (train.isCurrentlyOn(wp)) {
                    log.info("Train {} consecutive waypoint reached", train.getId());
                    pendingCommands.clear();
                    pendingCommands.addAll(wp.commands());
                    runPendingCommands();
                }
            });

            if (autopilot.mode() == letrain.itinerary.AutoPilot.Mode.FOLLOWING
                    && this.train.getSafetyManager() != null) {
                this.train
                        .notifyAutopilotSegmentEntered(this.train.resolveCurrentSegmentFromGraph());
                this.train.getSafetyManager().acquireInitialLocks();
            }
        }
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
            default:
                return false;
        }
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
