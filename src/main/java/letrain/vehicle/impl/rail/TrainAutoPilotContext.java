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
    public int targetSpeed() {
        var d = train.getDirectorLinker();
        return d instanceof Locomotive ? ((Locomotive) d).getTargetSpeed() : 0;
    }

    @Override
    public void setTargetSpeed(int speed) {
        if (train.getDirectorLinker() != null) {
            train.getDirectorLinker().setTargetSpeed(speed);
        }
    }

    @Override
    public void reverse() {
        if (train.getDirectorLinker() instanceof Locomotive loco) {
            loco.toggleReversed();
        }
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
        PathStep exitStep = from.getSteps().getSecond();
        if (exitStep == null) return;
        var node = exitStep.getRailNode();
        if (node == null || !(node.getTrack() instanceof ForkRailTrack fork)) return;

        // Use the fork node's outSteps directly (getNextSteps goes to wrong node)
        for (var step : node.getOutSteps()) {
            Segment nextSeg = graph.getSegment(step);
            if (nextSeg != null && nextSeg.equals(to)) {
                if (fork.isUsingAlternativeRoute() != isAlternativeRouteNeeded(fork, step.getDir())) {
                    fork.flipRoute();
                }
                return;
            }
        }
    }

    @Override
    public boolean isAtTarget(Waypoint wp) {
        if (train.getModel() == null) return false;
        switch (wp.type()) {
            case STATION:
                return train.getStationId() == wp.targetId();
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

    @Override
    public void load() {
        Station st = train.getStationAtTrain();
        if (st != null) {
            train.startLoadProcess(st);
        }
    }

    @Override
    public void unload() {
        Station st = train.getStationAtTrain();
        if (st != null) {
            train.startUnloadProcess(st);
        }
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

    private boolean isAlternativeRouteNeeded(ForkRailTrack fork, letrain.map.Dir targetDir) {
        // Check if the targetDir is the alternative route of the fork
        var alt = fork.getRouter().getAlternativeRoute();
        return alt != null && alt.getValue() == targetDir;
    }
}
