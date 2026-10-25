package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import letrain.map.Point;
import letrain.mvp.Presenter.TrackType;
import letrain.planner.RoutePlan;
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
        assertTrue(status.contains("←/→ policy"), status);
        assertTrue(status.contains("Esc discard"), status);
    }

    @Test
    @DisplayName("an infeasible PREVIEW shows the reason and no cost")
    void formatsInfeasiblePreview() {
        String status = RoutePreviewStatus.format(RoutePreviewSession.State.PREVIEW,
                RoutePolicy.AVOID_BOTH, RoutePlan.infeasible("No route can cross water and rock"),
                0, "No route can cross water and rock");

        assertTrue(status.contains("AVOID_BOTH (no rock/water)"), status);
        assertTrue(status.contains("no route: No route can cross water and rock"), status);
        assertTrue(status.contains("←/→ policy"), status);
        assertTrue(!status.contains("$"), "an infeasible plan must not show a price: " + status);
    }

    @Test
    @DisplayName("the policy label says what each avoid policy blocks")
    void labelsPoliciesWithTheirBlockedTerrain() {
        assertEquals("AVOID_MOUNTAINS (no rock)",
                RoutePreviewStatus.policyLabel(RoutePolicy.AVOID_MOUNTAINS));
        assertEquals("AVOID_WATER (no water)",
                RoutePreviewStatus.policyLabel(RoutePolicy.AVOID_WATER));
        assertEquals("AVOID_BOTH (no rock/water)",
                RoutePreviewStatus.policyLabel(RoutePolicy.AVOID_BOTH));
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
