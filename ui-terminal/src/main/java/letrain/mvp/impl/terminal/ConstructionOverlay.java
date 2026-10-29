package letrain.mvp.impl.terminal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import letrain.map.Point;
import letrain.planner.RoutePlan;
import letrain.planner.RouteStep;

/**
 * Maps a construction job to the glyphs still pending on the 2D map (ADR-037).
 *
 * <p>
 * Built cells are real rails and are never painted as ghosts. Only the remaining plan cells get the
 * route-preview circle, plus the A/B endpoint markers so the author keeps seeing the whole route
 * while it is being laid. The mapping is pure: no Lanterna, viewport or colour decisions here.
 */
final class ConstructionOverlay {

    private ConstructionOverlay() {}

    /**
     * Cells to paint for {@code plan} after {@code builtSteps} cells were materialised.
     *
     * @param plan plan being constructed
     * @param builtSteps number of already built cells (from the progress snapshot)
     */
    static List<RoutePreviewOverlay.Cell> cells(RoutePlan plan, int builtSteps) {
        if (plan == null || !plan.feasible() || plan.steps().isEmpty()) {
            return List.of();
        }
        Map<Long, RoutePreviewOverlay.Cell> byPosition = new LinkedHashMap<>();
        int from = Math.max(0, Math.min(builtSteps, plan.steps().size()));
        for (int index = from; index < plan.steps().size(); index++) {
            RouteStep step = plan.steps().get(index);
            Point point = step.point();
            byPosition.put(key(point),
                    new RoutePreviewOverlay.Cell(point.getX(), point.getY(),
                            RoutePreviewOverlay.glyphFor(step.trackType()), false,
                            RoutePreviewOverlay.styleFor(step.trackType())));
        }
        Point start = plan.steps().get(0).point();
        byPosition.put(key(start), new RoutePreviewOverlay.Cell(start.getX(), start.getY(),
                RoutePreviewOverlay.START_GLYPH, true, RoutePreviewOverlay.Style.NORMAL));
        Point end = plan.steps().get(plan.steps().size() - 1).point();
        byPosition.put(key(end), new RoutePreviewOverlay.Cell(end.getX(), end.getY(),
                RoutePreviewOverlay.END_GLYPH, true, RoutePreviewOverlay.Style.NORMAL));
        return List.copyOf(byPosition.values());
    }

    private static long key(Point point) {
        return (((long) point.getX()) << 32) ^ (point.getY() & 0xFFFFFFFFL);
    }
}
