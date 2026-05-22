package letrain.segments.impl;

import java.util.ArrayList;
import java.util.List;

import letrain.segments.PathStep;
import letrain.segments.RailNode;
import letrain.track.Track;

public class RailNodeImpl implements RailNode {
    private final List<PathStep> outSteps = new ArrayList<>();
    private final Track track;

    public RailNodeImpl(Track track) {
        this.track = track;
    }

    @Override
    public List<PathStep> getOutSteps() {
        return outSteps;
    }

    @Override
    public Track getTrack() {
        return track;
    }

    public void addOutStep(PathStep step) {
        outSteps.add(step);
    }

    @Override
    public String toString() {
        if (track instanceof letrain.track.rail.ForkRailTrack) {
            letrain.track.rail.ForkRailTrack fork = (letrain.track.rail.ForkRailTrack) track;
            return "Fork(" + fork.getId() + ")@" + track.getPosition();
        }
        return "DeadEnd@" + track.getPosition();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RailNodeImpl railNode = (RailNodeImpl) o;
        return java.util.Objects.equals(track.getPosition(), railNode.track.getPosition());
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(track.getPosition());
    }
}
