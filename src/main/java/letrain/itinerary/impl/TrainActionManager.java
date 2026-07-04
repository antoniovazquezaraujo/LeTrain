package letrain.itinerary.impl;

import letrain.itinerary.WaypointCommand;
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
    public void onWaypointReached(Train train, letrain.itinerary.Waypoint waypoint) {
        pendingCommands.clear();
        pendingCommands.addAll(waypoint.commands());
        runPendingCommands();
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
                if (train.isCurrentlyOn(wp)) {
                    log.info("Train {} consecutive waypoint reached", train.getId());
                    pendingCommands.clear();
                    pendingCommands.addAll(wp.commands());
                    runPendingCommands();
                }
            });

            if (autopilot.mode() == letrain.itinerary.AutoPilot.Mode.FOLLOWING && this.train.getSafetyManager() != null) {
                if (this.train.getSafetyManager().getCurrentSegment() != null) {
                    this.train.notifyAutopilotSegmentEntered(this.train.getSafetyManager().getCurrentSegment());
                }
                this.train.getSafetyManager().acquireInitialLocks();
            }
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
    }

    private void acquireInitialLocks() {
        if (this.train.getModel() != null && this.train.isAutoMode()
                && this.train.getSafetyManager().getCurrentSegment() != null) {
            this.train.notifyAutopilotSegmentEntered(this.train.getSafetyManager().getCurrentSegment());
        }
        if (this.train.getSafetyManager() != null && this.train.getModel() != null) {
            this.train.getSafetyManager().acquireInitialLocks();
        }
    }

}
