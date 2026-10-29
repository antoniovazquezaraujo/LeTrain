package letrain.construction;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import letrain.planner.RoutePlan;
import letrain.planner.RouteStep;

/**
 * Recording fake for {@link TrackMaterializer}; no model or rail map involved.
 */
final class RecordingTrackMaterializer implements TrackMaterializer {

    final List<Integer> builtIndexes = new ArrayList<>();
    final List<RouteStep> previousSteps = new ArrayList<>();
    final List<RouteStep> nextSteps = new ArrayList<>();

    Optional<String> rejection = Optional.empty();
    RuntimeException prepareFailure;
    RuntimeException buildFailure;
    int prepareCalls;
    int finishCalls;

    @Override
    public Optional<String> prepare(RoutePlan plan) {
        prepareCalls++;
        if (prepareFailure != null) {
            throw prepareFailure;
        }
        return rejection;
    }

    @Override
    public void buildStep(RoutePlan plan, int index, RouteStep previous, RouteStep next) {
        if (buildFailure != null) {
            throw buildFailure;
        }
        builtIndexes.add(index);
        previousSteps.add(previous);
        nextSteps.add(next);
    }

    @Override
    public void finish() {
        finishCalls++;
    }
}
