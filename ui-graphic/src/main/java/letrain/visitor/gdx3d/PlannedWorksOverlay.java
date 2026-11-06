package letrain.visitor.gdx3d;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import letrain.construction.PlannedWork;
import letrain.planner.RoutePlan;
import letrain.planner.RouteStep;

/**
 * Maps a planned work to the cells the 3D map should show as a projected route.
 *
 * <p>
 * Draft works show their full plan; paused works show only the remaining steps plus their
 * endpoints. Working, done and cancelled works are not painted: the first is being materialised and
 * the other two no longer need a projected path. The mapping is pure so it can be tested without a
 * GL context.
 */
public final class PlannedWorksOverlay {

    /** One projected cell; endpoint cells use the larger A/B marker. */
    public record Cell(int x, int y, boolean endpoint) {
    }

    private PlannedWorksOverlay() {}

    /** Cells to paint for {@code work}; empty for states that have no projected route. */
    public static List<Cell> cells(PlannedWork work) {
        if (work == null || work.plan() == null) {
            return List.of();
        }
        boolean paused = work.state() == PlannedWork.State.PAUSED;
        if (work.state() != PlannedWork.State.DRAFT && !paused) {
            return List.of();
        }
        int fromStep = paused ? work.builtSteps() : 0;
        return cells(work.plan(), fromStep);
    }

    /** Pending cells of a route from {@code fromStep}, including both A/B endpoints. */
    public static List<Cell> cells(RoutePlan plan, int fromStep) {
        if (plan == null || !plan.feasible() || plan.steps().isEmpty()) {
            return List.of();
        }
        int start = Math.max(0, Math.min(fromStep, plan.steps().size()));
        Map<Long, Cell> byPosition = new LinkedHashMap<>();
        for (int index = start; index < plan.steps().size(); index++) {
            RouteStep step = plan.steps().get(index);
            put(byPosition, step.point().getX(), step.point().getY(), false);
        }
        // Endpoints stay visible after a pause even when their cells were already built.
        put(byPosition, plan.steps().get(0).point().getX(), plan.steps().get(0).point().getY(),
                true);
        RouteStep last = plan.steps().get(plan.steps().size() - 1);
        put(byPosition, last.point().getX(), last.point().getY(), true);
        return List.copyOf(byPosition.values());
    }

    private static void put(Map<Long, Cell> cells, int x, int y, boolean endpoint) {
        long key = (((long) x) << 32) ^ (y & 0xFFFFFFFFL);
        Cell previous = cells.get(key);
        if (previous == null || endpoint) {
            cells.put(key, new Cell(x, y, endpoint));
        }
    }
}
