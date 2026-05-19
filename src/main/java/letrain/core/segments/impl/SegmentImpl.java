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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SegmentImpl segment = (SegmentImpl) o;
        // Same ID → same logical segment
        if (id != null && id.equals(segment.id)) return true;
        // Fallback: compare by steps (order-independent)
        if (steps == null || segment.steps == null) return false;
        return (java.util.Objects.equals(steps.getFirst(), segment.steps.getFirst()) && 
                java.util.Objects.equals(steps.getSecond(), segment.steps.getSecond())) ||
               (java.util.Objects.equals(steps.getFirst(), segment.steps.getSecond()) && 
                java.util.Objects.equals(steps.getSecond(), segment.steps.getFirst()));
    }

    @Override
    public int hashCode() {
        if (steps == null) return 0;
        // Hash independiente del orden de los pasos
        return steps.getFirst().hashCode() + steps.getSecond().hashCode();
    }
}
