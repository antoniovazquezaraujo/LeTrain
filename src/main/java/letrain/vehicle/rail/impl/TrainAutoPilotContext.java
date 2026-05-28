package letrain.vehicle.rail.impl;

import letrain.itinerary.AutoPilotContext;
import letrain.itinerary.Waypoint;
import letrain.map.Point;
import letrain.segments.BlockManager;
import letrain.segments.RailwayGraph;
import letrain.segments.Segment;
import letrain.track.Station;
import letrain.track.Track;
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
}
