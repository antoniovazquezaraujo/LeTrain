package letrain.core.segments.impl;

import letrain.core.segments.PathStep;
import letrain.core.segments.RailNode;
import letrain.map.Dir;
import java.util.Objects;

public class PathStepImpl implements PathStep {
    private final RailNode node;
    private final Dir dir;

    public PathStepImpl(RailNode node, Dir dir) {
        this.node = node;
        this.dir = dir;
    }

    @Override
    public RailNode getRailNode() {
        return node;
    }

    @Override
    public Dir getDir() {
        return dir;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PathStepImpl pathStep = (PathStepImpl) o;
        return Objects.equals(node, pathStep.node) && dir == pathStep.dir;
    }

    @Override
    public int hashCode() {
        return Objects.hash(node, dir);
    }
}
