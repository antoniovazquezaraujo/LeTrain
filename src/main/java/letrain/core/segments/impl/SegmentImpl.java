package letrain.core.segments.impl;

import letrain.core.segments.PathStep;
import letrain.core.segments.Segment;
import letrain.utils.Pair;

public class SegmentImpl implements Segment {
    private final String id;
    private final Pair<PathStep, PathStep> steps;

    public SegmentImpl(String id, PathStep step1, PathStep step2) {
        this.id = id;
        this.steps = new Pair<>(step1, step2);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Pair<PathStep, PathStep> getSteps() {
        return steps;
    }

    @Override
    public String toString() {
        return "Segment[" + id + "]";
    }
}
