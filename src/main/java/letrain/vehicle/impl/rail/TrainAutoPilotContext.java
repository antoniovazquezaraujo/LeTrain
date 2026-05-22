package letrain.vehicle.impl.rail;

import letrain.core.segments.*;
import letrain.itinerary.*;
import letrain.map.Point;
import letrain.track.Station;
import letrain.track.Track;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;

/**
 * Bridges the AutoPilot to the real Train/Locomotive/Model.
 * Implements AutoPilotContext using the existing game objects.
 */
public class TrainAutoPilotContext implements AutoPilotContext {

    private final Train train;

    public TrainAutoPilotContext(Train train) {
        this.train = train;
    }

    @Override
    public int currentSpeed() {
        return train.getSpeed();
    }



    @Override
    public Segment currentSegment() {
        if (train.getModel() == null) return null;
        RailwayGraph graph = train.getModel().getRailwayGraph();
        if (graph == null) return null;
        var first = train.getLinkers().isEmpty() ? null : train.getLinkers().getFirst();
        if (first == null || first.getTrack() == null) return null;
        Track t = first.getTrack();
        return t instanceof RailTrack ? graph.getSegment((RailTrack) t) : null;
    }

    @Override
    public Segment targetSegment(Waypoint wp) {
        if (train.getModel() == null) return null;
        RailwayGraph graph = train.getModel().getRailwayGraph();
        if (graph == null) return null;

        Point pos = findTargetPosition(wp);
        if (pos == null) return null;

        RailTrack track = train.getModel().getRailMap().getTrackAt(pos);
        return track != null ? graph.getSegment(track) : null;
    }

    @Override
    public boolean isSegmentFree(Segment seg) {
        if (train.getModel() == null) return false;
        BlockManager bm = train.getModel().getBlockManager();
        var owners = bm.getOwners(seg);
        return owners.isEmpty() || (owners.size() == 1 && owners.contains(train));
    }

    @Override
    public void ensureForkRoute(Segment from, Segment to) {
        if (train.getModel() == null) return;
        RailwayGraph graph = train.getModel().getRailwayGraph();
        if (graph == null) return;

        // Find the fork between 'from' and 'to' and set the correct route
        var fromSteps = from.getSteps();
        var toSteps = to.getSteps();
        if (fromSteps == null || toSteps == null) return;

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
            org.slf4j.LoggerFactory.getLogger(TrainAutoPilotContext.class)
                .warn("[FORK] ensureForkRoute {}->{}: no shared node found", from.getId(), to.getId());
            return;
        }
        if (!(node.getTrack() instanceof ForkRailTrack fork)) {
            org.slf4j.LoggerFactory.getLogger(TrainAutoPilotContext.class)
                .debug("[FORK] ensureForkRoute {}->{}: shared node is not a fork ({})", from.getId(), to.getId(), node.getTrack());
            return;
        }

        // Use the fork node's outSteps directly (getNextSteps goes to wrong node)
        for (var step : node.getOutSteps()) {
            Segment nextSeg = graph.getSegment(step);
            org.slf4j.LoggerFactory.getLogger(TrainAutoPilotContext.class)
                .debug("[FORK] ensureForkRoute {}->{}: outStep dir={} seg={}", from.getId(), to.getId(), step.getDir(), nextSeg != null ? nextSeg.getId() : "null");
            if (nextSeg != null && nextSeg.equals(to)) {
                boolean altNeeded = isAlternativeRouteNeeded(fork, step.getDir());
                org.slf4j.LoggerFactory.getLogger(TrainAutoPilotContext.class)
                    .info("[FORK] ensureForkRoute {}->{}: MATCH fork={} altNeeded={} currentAlt={}", from.getId(), to.getId(), fork.getId(), altNeeded, fork.isUsingAlternativeRoute());
                if (fork.isUsingAlternativeRoute() != altNeeded) {
                    fork.flipRoute();
                }
                return;
            }
        }
        org.slf4j.LoggerFactory.getLogger(TrainAutoPilotContext.class)
            .warn("[FORK] ensureForkRoute {}->{}: no outStep leads to target seg", from.getId(), to.getId());
    }

    @Override
    public boolean isAtTarget(Waypoint wp) {
        if (train.getModel() == null) return false;
        switch (wp.type()) {
            case STATION:
                // Primary check: stationId matches target
                if (train.getStationId() == wp.targetId()) {
                    return true;
                }
                // Additional check: train may be physically at a station (model reports it)
                Station curSt = train.getStationAtTrain();
                if (curSt != null && curSt.getId() == wp.targetId()) {
                    return true;
                }
                // Check if any linker is directly on the station's track
                Station st = train.getModel().getStation(wp.targetId());
                if (st != null) {
                    for (var linker : train.getLinkers()) {
                        Track t = linker.getTrack();
                        if (t != null && t.equals(st.getTrack())) {
                            return true;
                        }
                    }
                }
                return false;
            case SENSOR:
                // Check if any linker's track has a sensor with the target ID
                for (var linker : train.getLinkers()) {
                    Track t = linker.getTrack();
                    if (t != null && t.getSensor() != null && t.getSensor().getId() == wp.targetId()) {
                        return true;
                    }
                }
                return false;
        }
        return false;
    }



    private Point findTargetPosition(Waypoint wp) {
        if (train.getModel() == null) return null;
        switch (wp.type()) {
            case STATION:
                Station st = train.getModel().getStation(wp.targetId());
                return st != null ? st.getPosition() : null;
            case SENSOR:
                var sensor = train.getModel().getSensor(wp.targetId());
                return sensor != null ? sensor.getPosition() : null;
        }
        return null;
    }

    @Override
    public void forceSegmentReset() {
        train.forceSegmentReset();
    }

    @Override
    public void notifySegmentOccupied(Segment segment) {
        train.notifySegmentOccupied(segment);
    }

    private boolean isAlternativeRouteNeeded(ForkRailTrack fork, letrain.map.Dir targetDir) {
        // Check if the targetDir is the alternative route of the fork
        var alt = fork.getRouter().getAlternativeRoute();
        return alt != null && alt.getValue() == targetDir;
    }
}
