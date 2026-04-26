package letrain.core.segments.impl;

import letrain.core.segments.PathStep;
import letrain.core.segments.Segment;
import letrain.utils.Pair;

public class SegmentImpl implements Segment {
    private final Pair<PathStep, PathStep> steps;

    public SegmentImpl(PathStep step1, PathStep step2) {
        this.steps = new Pair<>(step1, step2);
    }

    @Override
    public Pair<PathStep, PathStep> getSteps() {
        return steps;
    }
}
