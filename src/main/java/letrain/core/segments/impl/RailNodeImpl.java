package letrain.core.segments.impl;

import letrain.core.segments.PathStep;
import letrain.core.segments.RailNode;
import letrain.track.Track;
import java.util.List;
import java.util.ArrayList;

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
}
