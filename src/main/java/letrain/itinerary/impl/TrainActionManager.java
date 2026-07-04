package letrain.itinerary.impl;

import letrain.itinerary.WaypointCommand;
import letrain.track.rail.RailTrack;
import letrain.vehicle.rail.Linker;
import letrain.vehicle.rail.impl.Train;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class TrainActionManager implements letrain.itinerary.TrainActionManager {
    public static final Logger log = LoggerFactory.getLogger(Train.class);
    Train train;
    private final transient List<WaypointCommand> pendingCommands;
    private transient int waitTicks = 0;

    public TrainActionManager(Train train) {
        this.train = train;
        this.pendingCommands = new CopyOnWriteArrayList<>();
    }

    @Override
    public void checkWaypointArrival() {
        if (!train.isAutoMode()) {
            return;
        }
        letrain.itinerary.AutoPilot autopilot = train.getAutopilot();
        if (autopilot == null || autopilot.mode() != letrain.itinerary.AutoPilot.Mode.FOLLOWING) {
            return;
        }
        java.util.Optional<letrain.itinerary.Waypoint> wpOpt = autopilot.currentWaypoint();
        if (wpOpt.isEmpty()) {
            return;
        }
        letrain.itinerary.Waypoint wp = wpOpt.get();
        if (isAtTarget(wp)) {
            pendingCommands.clear();
            pendingCommands.addAll(wp.commands());
            runPendingCommands();
        }
    }

    private void runPendingCommands() {
        while (!pendingCommands.isEmpty()) {
            WaypointCommand cmd = pendingCommands.remove(0);
            if (cmd.kind() == WaypointCommand.Kind.WAIT) {
                this.waitTicks = cmd.seconds() * WaypointCommand.TICKS_PER_SECOND;
                scheduleResume(this.waitTicks);
                return;
            } else {
                executeCommand(cmd);
            }
        }

        letrain.itinerary.AutoPilot autopilot = train.getAutopilot();
        if (autopilot != null && autopilot.itinerary().isPresent()) {
            autopilot.advanceWaypoint();
            autopilot.clearRoute();
            if (autopilot.mode() == letrain.itinerary.AutoPilot.Mode.IDLE) {
                log.info("Train {} itinerary DONE → IDLE", train.getId());
                train.setAutoMode(false);
                return;
            }

            autopilot.currentWaypoint().ifPresent(wp -> {
                if (train.getStationId() == wp.targetId()) {
                    log.info("Train {} consecutive waypoint reached", train.getId());
                    pendingCommands.clear();
                    pendingCommands.addAll(wp.commands());
                    runPendingCommands();
                }
            });
        }
    }

    private void executeCommand(WaypointCommand command) {
        if (command == null) {
            return;
        }
        switch (command.kind()) {
            case LOAD:
                train.load();
                break;
            case UNLOAD:
                train.unload();
                break;
            case REVERSE:
                train.reverse();
                break;
            case SPEED:
                train.setSpeed(command.targetSpeed());
                break;
            default:
                break;
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
        checkWaypointArrival();
    }

    private void acquireInitialLocks() {
        if (this.train.getModel() != null && this.train.isAutoMode()) {
            Linker head = this.train.getPhysicalFront();
            if (head != null && head.getTrack() instanceof RailTrack) {
                letrain.segments.Segment currentSeg = this.train.getModel().getRailwayGraph()
                        .getSegment((RailTrack) head.getTrack());
                if (currentSeg != null) {
                    this.train.getAutopilot().onSegmentEntered(currentSeg);
                }
            }
        }
        if (this.train.getSafetyManager() != null && this.train.getModel() != null) {
            this.train.getSafetyManager().acquireInitialLocks();
        }
    }

    private boolean isAtTarget(letrain.itinerary.Waypoint wp) {
        if (train.getModel() == null)
            return false;
        switch (wp.type()) {
            case STATION:
                if (train.getStationId() == wp.targetId()) {
                    return true;
                }
                letrain.track.Station curSt = train.getLogisticsManager().getStationAtTrain();
                if (curSt != null && curSt.getId() == wp.targetId()) {
                    return true;
                }
                letrain.track.Station st = train.getModel().getStation(wp.targetId());
                if (st != null) {
                    for (var linker : train.getLinkers()) {
                        letrain.track.Track t = linker.getTrack();
                        if (t != null && t.equals(st.getTrack())) {
                            return true;
                        }
                    }
                }
                return false;
            case SENSOR:
                for (var linker : train.getLinkers()) {
                    letrain.track.Track t = linker.getTrack();
                    if (t != null && t.getSensor() != null && t.getSensor().getId() == wp.targetId()) {
                        return true;
                    }
                }
                return false;
        }
        return false;
    }
}
