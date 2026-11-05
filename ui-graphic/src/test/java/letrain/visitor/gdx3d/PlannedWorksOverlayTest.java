package letrain.visitor.gdx3d;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import letrain.construction.PlannedWork;
import letrain.map.Point;
import letrain.mvp.Presenter.TrackType;
import letrain.planner.RoutePlan;
import letrain.planner.RoutePolicy;
import letrain.planner.RouteStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Pure projected-route mapping for the 3D map. */
@DisplayName("3D planned works overlay")
class PlannedWorksOverlayTest {

    private static PlannedWork draft() {
        RoutePlan plan =
                RoutePlan.feasible(List.of(new RouteStep(new Point(1, 2), TrackType.NORMAL_TRACK),
                        new RouteStep(new Point(2, 2), TrackType.NORMAL_TRACK),
                        new RouteStep(new Point(3, 2), TrackType.NORMAL_TRACK)), 300, 30);
        return PlannedWork.draft(1, new Point(1, 2), new Point(3, 2), RoutePolicy.DIRECT, plan, 30);
    }

    @Test
    @DisplayName("a draft paints every route cell and both endpoints")
    void should_PaintDraftRoute() {
        List<PlannedWorksOverlay.Cell> cells = PlannedWorksOverlay.cells(draft());

        assertEquals(3, cells.size());
        assertTrue(cells.get(0).endpoint());
        assertTrue(cells.get(2).endpoint());
        assertFalse(cells.get(1).endpoint());
        assertEquals("1,2", cells.get(0).x() + "," + cells.get(0).y());
    }

    @Test
    @DisplayName("only remaining cells are painted for a paused work, but endpoints stay")
    void should_PaintPausedRemainder() {
        PlannedWork paused = draft().withPausedProgress(1, 10, 100);

        List<PlannedWorksOverlay.Cell> cells = PlannedWorksOverlay.cells(paused);

        assertEquals(3, cells.size());
        assertTrue(
                cells.stream().anyMatch(cell -> cell.x() == 1 && cell.y() == 2 && cell.endpoint()));
        assertTrue(cells.stream().anyMatch(cell -> cell.x() == 2 && cell.y() == 2));
        assertTrue(
                cells.stream().anyMatch(cell -> cell.x() == 3 && cell.y() == 2 && cell.endpoint()));
    }

    @Test
    @DisplayName("working, done and cancelled works have no projected route")
    void should_HideNonProjectedStates() {
        assertTrue(
                PlannedWorksOverlay.cells(draft().withState(PlannedWork.State.WORKING)).isEmpty());
        assertTrue(PlannedWorksOverlay.cells(draft().withState(PlannedWork.State.DONE)).isEmpty());
        assertTrue(PlannedWorksOverlay.cells(draft().withState(PlannedWork.State.CANCELLED))
                .isEmpty());
        assertTrue(PlannedWorksOverlay.cells(null).isEmpty());
    }
}
