package letrain.itinerary.impl;

import letrain.itinerary.WaypointCommand;
import letrain.map.Dir;
import letrain.segments.RailNode;
import letrain.segments.RailwayGraph;
import letrain.segments.Segment;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;
import letrain.vehicle.Tractor;
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
    private int savedSpeedBeforeReverse = -1;
    public TrainActionManager(Train train){
        this.train = train;
        this.pendingCommands = new CopyOnWriteArrayList<>();
    }
    @Override
    public void executeCommand(WaypointCommand command) {
        if (command == null) {
            return;
        }
        switch (command.kind()) {
            case LOAD:
                letrain.track.Station loadStation = train.getLogisticsManager().getStationAtTrain();
                if (loadStation != null) {
                    train.getLogisticsManager().startLoadProcess(loadStation);
                }
                break;
            case UNLOAD:
                letrain.track.Station unloadStation = train.getLogisticsManager().getStationAtTrain();
                if (unloadStation != null) {
                    train.getLogisticsManager().startUnloadProcess(unloadStation);
                }
                break;
            case REVERSE:
                Tractor dirLinker = train.getDirectorLinker();
                if (dirLinker != null) {
                    if (dirLinker.getSpeed() > 0) {
                        setSavedSpeedBeforeReverse(dirLinker.getTargetSpeed());
                        dirLinker.setTargetSpeed(0);
                        train.pendingReverse = true;
                    } else {
                        dirLinker.toggleReversed();
                        train.pendingReverse = false;
                    }
                }
                break;
            case SPEED:
                Tractor speedLinker = train.getDirectorLinker();
                if (speedLinker != null) {
                    setSavedSpeedBeforeReverse(-1);
                    speedLinker.setSpeed(command.targetSpeed());
                    if (command.targetSpeed() > 0 && train.getModel() != null) {
                        train.getSafetyManager().acquireInitialLocks();
                    }
                }
                break;
            default:
                break;
        }

    }

    @Override
    public void ensureForkRoute(Segment from, Segment to) {
        if (this.train.getModel() == null)
            return;
        RailwayGraph graph = this.train.getModel().getRailwayGraph();
        if (graph == null)
            return;

        // Find the fork between 'from' and 'to' and set the correct route
        var fromSteps = from.getSteps();
        var toSteps = to.getSteps();
        if (fromSteps == null || toSteps == null)
            return;

        var f1 = fromSteps.getFirst();
        var f2 = fromSteps.getSecond();
        var t1 = toSteps.getFirst();
        var t2 = toSteps.getSecond();

        RailNode node = null;
        if (f1 != null && f1.getRailNode() != null) {
            var n1 = f1.getRailNode();
            if ((t1 != null && n1.equals(t1.getRailNode())) || (t2 != null && n1.equals(t2.getRailNode()))) {
                node = n1;
            }
        }
        if (node == null && f2 != null && f2.getRailNode() != null) {
            var n2 = f2.getRailNode();
            if ((t1 != null && n2.equals(t1.getRailNode())) || (t2 != null && n2.equals(t2.getRailNode()))) {
                node = n2;
            }
        }

        if (node == null) {
            log.warn("[FORK] ensureForkRoute {}->{}: no shared node found", from.getId(), to.getId());
            return;
        }
        if (!(node.getTrack() instanceof ForkRailTrack fork)) {
            log.debug("[FORK] ensureForkRoute {}->{}: shared node is not a fork ({})", from.getId(), to.getId(),
                    node.getTrack());
            return;
        }

        // Use the fork node's outSteps directly (getNextSteps goes to wrong node)
        for (var step : node.getOutSteps()) {
            Segment nextSeg = graph.getSegment(step);
            log.debug("[FORK] ensureForkRoute {}->{}: outStep dir={} seg={}", from.getId(), to.getId(), step.getDir(),
                    nextSeg != null ? nextSeg.getId() : "null");
            if (nextSeg != null && nextSeg.equals(to)) {
                Dir targetDir = step.getDir();
                // Check if the targetDir is the alternative route of the fork
                var alt = fork.getRouter().getAlternativeRoute();
                boolean altNeeded = alt != null && alt.getValue() == targetDir;
                log.info("[FORK] ensureForkRoute {}->{}: MATCH fork={} altNeeded={} currentAlt={}", from.getId(),
                        to.getId(), fork.getId(), altNeeded, fork.isUsingAlternativeRoute());
                if (fork.isUsingAlternativeRoute() != altNeeded) {
                    fork.flipRoute();
                }
                return;
            }
        }
        log.warn("[FORK] ensureForkRoute {}->{}: no outStep leads to target seg", from.getId(), to.getId());

    }

    @Override
    public void notifySegmentOccupied(Segment segment) {
        this.train.notifySegmentOccupied(segment);
    }

    @Override
    public void forceSegmentReset() {
        //TODO: qué hacer aquí?
    }

    @Override
    public void scheduleResume(int ticks) {
        if (train.getModel() != null && train.getModel().getScheduler() != null) {
            train.getModel().getScheduler().schedule(ticks, () -> {
                this.train.actionManager.resumeWaiting();
                this.train.actionManager.acquireInitialLocks();
            });
        }
    }
    @Override
    public void resumeWaiting() {
        this.waitTicks = 0;
        runPendingCommands();
        checkWaypointArrival();
    }
    @Override
    public void runPendingCommands() {
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

//        if (autopilot != null && autopilot.itinerary().isPresent()) {
//            letrain.itinerary.Itinerary itin = autopilot.itinerary().get();
//            itin.advance();
//            autopilot.clearRoute();
//            if (itin.state() == letrain.itinerary.Itinerary.State.DONE) {
//                log.info("Train {} itinerary DONE → IDLE", id);
//                autopilot.deactivate();
//                return;
//            }
//
//            itin.currentWaypoint().ifPresent(wp -> {
//                if (railStationId == wp.targetId()) {
//                    log.info("Train {} consecutive waypoint reached", id);
//                    pendingCommands.clear();
//                    pendingCommands.addAll(wp.commands());
//                    runPendingCommands();
//                }
//            });
//        }
    }

    @Override
    public void checkWaypointArrival() {
//        if (!autoMode || autopilot == null || autopilot.mode() != letrain.itinerary.AutoPilot.Mode.FOLLOWING) {
//            return;
//        }
//        Optional<Itinerary> itinOpt = autopilot.itinerary();
//        if (itinOpt.isEmpty()) {
//            return;
//        }
//        letrain.itinerary.Itinerary itin = itinOpt.get();
//        Optional<letrain.itinerary.Waypoint> wpOpt = itin.currentWaypoint();
//        if (wpOpt.isEmpty()) {
//            return;
//        }
//        letrain.itinerary.Waypoint wp = wpOpt.get();
//        letrain.itinerary.AutoPilotContext ctx = new TrainAutoPilotContext(this);
//        if (ctx.isAtTarget(wp)) {
//            pendingCommands.clear();
//            pendingCommands.addAll(wp.commands());
//            runPendingCommands();
//        }
    }















    @Override
    public void acquireInitialLocks() {

        if (this.train.getModel() != null && this.train.isAutoMode() && this.train.getAutopilot() != null) {
            Linker head = this.train.getPhysicalFront();
            if (head != null && head.getTrack() instanceof RailTrack) {
                letrain.segments.Segment currentSeg = this.train.getModel().getRailwayGraph().getSegment((RailTrack) head.getTrack());
                if (currentSeg != null) {
                    this.train.getAutopilot().onSegmentEntered(currentSeg);
                }
            }
        }
        if (this.train.getSafetyManager() != null && this.train.getModel() != null) {
            this.train.getSafetyManager().acquireInitialLocks();
        }

    }

    @Override
    public int getSavedSpeedBeforeReverse() {
        return savedSpeedBeforeReverse;
    }

    @Override
    public void setSavedSpeedBeforeReverse(int savedSpeedBeforeReverse) {
        this.savedSpeedBeforeReverse = savedSpeedBeforeReverse;
    }
}
