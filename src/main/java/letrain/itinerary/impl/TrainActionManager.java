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

public class TrainActionManager implements letrain.itinerary.TrainActionManager {
    public static final Logger log = LoggerFactory.getLogger(Train.class);
    Train train;
    private int savedSpeedBeforeReverse = -1;
    public TrainActionManager(Train train){
        this.train = train;
    }
    @Override
    public void executeCommand(WaypointCommand command) {
        if (command == null) {
            return;
        }
        switch (command.kind()) {
            case LOAD:
                letrain.track.Station loadStation = train.getStationAtTrain();
                if (loadStation != null) {
                    train.startLoadProcess(loadStation);
                }
                break;
            case UNLOAD:
                letrain.track.Station unloadStation = train.getStationAtTrain();
                if (unloadStation != null) {
                    train.startUnloadProcess(unloadStation);
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
                this.train.resumeWaiting();
            });
        }
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
