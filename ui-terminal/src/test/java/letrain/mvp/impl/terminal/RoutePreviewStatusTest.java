package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import letrain.map.Point;
import letrain.mvp.Presenter.TrackType;
import letrain.planner.RoutePlan;
import letrain.planner.RoutePlanningProgress;
import letrain.planner.RoutePolicy;
import letrain.planner.RoutePreviewSession;
import letrain.planner.RouteStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("2D route preview: status line formatting")
class RoutePreviewStatusTest {

    @Test
    @DisplayName("cost is shown in game currency with dot grouping")
    void formatsCost() {
        assertEquals("$400", RoutePreviewStatus.formatCost(400));
        assertEquals("$123.400", RoutePreviewStatus.formatCost(123_400));
        assertEquals("$1.234.500", RoutePreviewStatus.formatCost(1_234_500));
    }

    @Test
    @DisplayName("ticks become game minutes and hours, rounding a non-zero build up")
    void formatsTicks() {
        assertEquals("0 min", RoutePreviewStatus.formatTicks(0));
        assertEquals("1 min", RoutePreviewStatus.formatTicks(1));
        assertEquals("1 min", RoutePreviewStatus.formatTicks(19));
        assertEquals("1 min", RoutePreviewStatus.formatTicks(20));
        assertEquals("12 min", RoutePreviewStatus.formatTicks(240));
        assertEquals("1 h", RoutePreviewStatus.formatTicks(1_200));
        assertEquals("1 h 12 min", RoutePreviewStatus.formatTicks(1_440));
    }

    @Test
    @DisplayName("PREVIEW shows one cost/time/segments line plus the key hints")
    void formatsFeasiblePreview() {
        RoutePlan plan =
                RoutePlan.feasible(
                        List.of(new RouteStep(new Point(0, 0), TrackType.NORMAL_TRACK),
                                new RouteStep(new Point(1, 0), TrackType.NORMAL_TRACK)),
                        123_400, 240);

        String status = RoutePreviewStatus.format(RoutePreviewSession.State.PREVIEW,
                RoutePolicy.CHEAPEST, plan, 280, null);

        assertTrue(status.contains("Connect · CHEAPEST (may cross)"), status);
        assertTrue(status.contains("$123.400"), status);
        assertTrue(status.contains("14 min"), status);
        assertTrue(status.contains("2 cells"), status);
        assertTrue(status.contains("Enter select"), status);
        assertTrue(status.contains("Space A/B"), status);
        assertTrue(status.contains("[ ] policy"), status);
        assertTrue(status.contains("Esc discard"), status);
    }

    @Test
    @DisplayName("EDIT mode uses its own prefix and Enter update hint")
    void formatsEditPreview() {
        RoutePlan plan =
                RoutePlan.feasible(
                        List.of(new RouteStep(new Point(0, 0), TrackType.NORMAL_TRACK),
                                new RouteStep(new Point(1, 0), TrackType.NORMAL_TRACK)),
                        123_400, 240);

        String status = RoutePreviewStatus.format(RoutePreviewSession.State.PREVIEW,
                RoutePolicy.CHEAPEST, plan, 280, null, null, true, true);

        assertTrue(status.startsWith("Edit route · CHEAPEST (may cross)"), status);
        assertTrue(status.contains("Enter update"), status);
        assertFalse(status.contains("Enter save"), status);
    }

    @Test
    @DisplayName("a build warning is appended to the feasible line and disables Enter")
    void formatsBuildWarningWithCostAndPolicy() {
        RoutePlan plan =
                RoutePlan.feasible(
                        List.of(new RouteStep(new Point(0, 0), TrackType.NORMAL_TRACK),
                                new RouteStep(new Point(1, 0), TrackType.NORMAL_TRACK)),
                        123_400, 240);

        String status = RoutePreviewStatus.format(RoutePreviewSession.State.PREVIEW,
                RoutePolicy.CHEAPEST, plan, 280, null, "cell (0,0) already has track");

        assertTrue(status.contains("Connect · CHEAPEST (may cross)"), status);
        assertTrue(status.contains("$123.400"), status);
        assertTrue(status.contains("14 min"), status);
        assertTrue(status.contains("2 cells"), status);
        assertTrue(status.contains("! cannot build: cell (0,0) already has track"), status);
        assertTrue(status.contains("Enter disabled"), status);
        assertFalse(status.contains("Enter select"), status);
        assertTrue(status.contains("Space A/B"), status);
        assertTrue(status.contains("[ ] policy"), status);
        assertTrue(status.contains("Esc discard"), status);
    }

    @Test
    @DisplayName("an infeasible plan keeps its planner reason even if a warning is supplied")
    void infeasibleReasonWinsOverBuildWarning() {
        String status = RoutePreviewStatus.format(RoutePreviewSession.State.PREVIEW,
                RoutePolicy.AVOID_ALL, RoutePlan.infeasible("No route can cross water and rock"), 0,
                "No route can cross water and rock", "cell (0,0) already has track");

        assertTrue(status.contains("no route: No route can cross water and rock"), status);
        assertFalse(status.contains("cannot build"), status);
        assertFalse(status.contains("$"), status);
    }

    @Test
    @DisplayName("an infeasible PREVIEW shows the reason and no cost")
    void formatsInfeasiblePreview() {
        String status = RoutePreviewStatus.format(RoutePreviewSession.State.PREVIEW,
                RoutePolicy.AVOID_ALL, RoutePlan.infeasible("No route can cross water and rock"), 0,
                "No route can cross water and rock");

        assertTrue(status.contains("AVOID_ALL (no water/rock)"), status);
        assertTrue(status.contains("no route: No route can cross water and rock"), status);
        assertTrue(status.contains("Space A/B"), status);
        assertTrue(status.contains("[ ] policy"), status);
        assertTrue(!status.contains("$"), "an infeasible plan must not show a price: " + status);
    }

    @Test
    @DisplayName("PLANNING shows phase, nodes, elapsed and an animated bar but no cost or Enter")
    void formatsPlanning() {
        RoutePlanningProgress progress = new RoutePlanningProgress(
                RoutePlanningProgress.Phase.SEARCH, 234_000, 3_200, new Point(3, 4));

        String status = RoutePreviewStatus.formatPlanning(progress);

        assertTrue(status.contains("Connect · PLANNING · SEARCH"), status);
        assertTrue(status.contains("234k nodes"), status);
        assertTrue(status.contains("3.2 s"), status);
        assertTrue(status.contains("[░░██░░░░]") || status.contains("██"), status);
        assertTrue(status.contains("Esc cancel"), status);
        assertTrue(!status.contains("$"), "PLANNING must not show a price: " + status);
        assertTrue(!status.contains("Enter"), "PLANNING must not offer Enter: " + status);
    }

    @Test
    @DisplayName("PLANNING before the first progress report says starting and zeros")
    void formatsPlanningBeforeFirstReport() {
        String status = RoutePreviewStatus.formatPlanning(null);

        assertTrue(status.contains("PLANNING · starting · 0 nodes · 0.0 s"), status);
    }

    @Test
    @DisplayName("node, second and bar helpers are deterministic and bounded")
    void formatsPlanningHelpers() {
        assertEquals("0 nodes", RoutePreviewStatus.formatNodes(0));
        assertEquals("1 node", RoutePreviewStatus.formatNodes(1));
        assertEquals("999 nodes", RoutePreviewStatus.formatNodes(999));
        assertEquals("1.0k nodes", RoutePreviewStatus.formatNodes(1_000));
        assertEquals("234k nodes", RoutePreviewStatus.formatNodes(234_000));
        assertEquals("1.2M nodes", RoutePreviewStatus.formatNodes(1_200_000));

        assertEquals("0.0 s", RoutePreviewStatus.formatSeconds(0));
        assertEquals("3.2 s", RoutePreviewStatus.formatSeconds(3_200));

        String bar = RoutePreviewStatus.progressBar(234_000, 3_200);
        assertEquals(RoutePreviewStatus.PLANNING_BAR_WIDTH + 2, bar.length(), bar);
        assertTrue(bar.startsWith("["), bar);
        assertTrue(bar.endsWith("]"), bar);
        assertEquals(2, bar.chars().filter(c -> c == '█').count(), bar);
        assertEquals(bar, RoutePreviewStatus.progressBar(234_000, 3_200));
        assertTrue(!bar.equals(RoutePreviewStatus.progressBar(234_000, 3_500)),
                "the bar must move with the snapshot");
    }

    @Test
    @DisplayName("the policy label says what each avoid policy blocks")
    void labelsPoliciesWithTheirBlockedTerrain() {
        assertEquals("AVOID_MOUNTAINS (no rock)",
                RoutePreviewStatus.policyLabel(RoutePolicy.AVOID_MOUNTAINS));
        assertEquals("AVOID_WATER (no water)",
                RoutePreviewStatus.policyLabel(RoutePolicy.AVOID_WATER));
        assertEquals("AVOID_ALL (no water/rock)",
                RoutePreviewStatus.policyLabel(RoutePolicy.AVOID_ALL));
        assertEquals("DIRECT (crosses all)", RoutePreviewStatus.policyLabel(RoutePolicy.DIRECT));
    }

    @Test
    @DisplayName("picking states tell the author to move, Enter and Esc")
    void formatsPickingStates() {
        assertEquals("Connect · PICK_START · move + Enter · Esc cancel", RoutePreviewStatus
                .format(RoutePreviewSession.State.PICK_START, RoutePolicy.CHEAPEST, null, 0, null));
        assertEquals("Connect · PICK_END · A set · move + Enter · Esc cancel", RoutePreviewStatus
                .format(RoutePreviewSession.State.PICK_END, RoutePolicy.CHEAPEST, null, 0, null));
        assertTrue(RoutePreviewStatus.format(RoutePreviewSession.State.PICK_END,
                RoutePolicy.CHEAPEST, null, 0, "End is water").contains("End is water"));
    }
}
