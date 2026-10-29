package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import letrain.construction.ConstructionProgress;
import letrain.construction.ConstructionState;
import letrain.map.Point;
import letrain.mvp.Presenter.TrackType;
import letrain.planner.RoutePlan;
import letrain.planner.RouteStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Construction HUD: pure mapping and formatting")
class ConstructionOverlayStatusTest {

    private static RoutePlan plan() {
        return RoutePlan.feasible(List.of(new RouteStep(new Point(0, 0), TrackType.NORMAL_TRACK),
                new RouteStep(new Point(1, 0), TrackType.BRIDGE_TRACK),
                new RouteStep(new Point(2, 0), TrackType.NORMAL_TRACK)), 1500, 640);
    }

    @Test
    @DisplayName("only the remaining cells are ghosts; A/B stay visible")
    void cells_skipBuiltCellsAndKeepEndpoints() {
        List<RoutePreviewOverlay.Cell> cells = ConstructionOverlay.cells(plan(), 1);

        assertFalse(
                cells.stream().anyMatch(cell -> cell.x() == 0 && cell.y() == 0 && !cell.endpoint()),
                "the built start is not a route ghost");
        assertTrue(cells.stream().anyMatch(cell -> cell.x() == 1 && cell.y() == 0
                && cell.glyph().equals(RoutePreviewOverlay.ROUTE_GLYPH)));
        assertTrue(cells.stream().anyMatch(cell -> cell.x() == 1 && cell.y() == 0
                && cell.style() == RoutePreviewOverlay.Style.SPECIAL));
        assertTrue(cells.stream().anyMatch(
                cell -> cell.endpoint() && cell.glyph().equals(RoutePreviewOverlay.START_GLYPH)));
        assertTrue(cells.stream().anyMatch(
                cell -> cell.endpoint() && cell.glyph().equals(RoutePreviewOverlay.END_GLYPH)));
    }

    @Test
    @DisplayName("built/total, plan cost and time feed the status line")
    void format_usesProgressAndPlanFigures() {
        RoutePlan plan = plan();
        ConstructionProgress progress = new ConstructionProgress(ConstructionState.BUILDING, 1,
                plan.steps().size(), plan.steps().get(1), 0, null);

        String line = ConstructionStatus.format(progress, plan, false);

        assertEquals("Building · 1/3 · $1.500 · 33 min · Esc cancel", line);
    }

    @Test
    @DisplayName("the armed confirmation has its own fixed text")
    void format_armedConfirmation() {
        RoutePlan plan = plan();
        ConstructionProgress progress = new ConstructionProgress(ConstructionState.BUILDING, 1,
                plan.steps().size(), plan.steps().get(1), 0, null);

        assertEquals(ConstructionStatus.CONFIRM_CANCEL,
                ConstructionStatus.format(progress, plan, true));
    }
}
