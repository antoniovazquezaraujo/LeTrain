package letrain.core.segments.impl;

import letrain.core.segments.PathStep;
import letrain.core.segments.RailNode;
import java.util.List;
import java.util.ArrayList;

public class RailNodeImpl implements RailNode {
    private final List<PathStep> outSteps = new ArrayList<>();

    @Override
    public List<PathStep> getOutSteps() {
        return outSteps;
    }

    public void addOutStep(PathStep step) {
        outSteps.add(step);
    }
}
