package letrain.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import letrain.economy.EconomyManager;
import letrain.economy.EconomyManager.ExpenseType;
import letrain.ground.GroundMap;
import letrain.map.Point;
import letrain.mvp.Presenter.TrackType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RoutePlannerTest {

    private static final int NORMAL = GroundMap.GROUND;
    private static final int WATER = GroundMap.WATER;
    private static final int ROCK = GroundMap.ROCK;

    private static EconomyManager economy() {
        EconomyManager economy = mock(EconomyManager.class);
        when(economy.getCost(ExpenseType.CONSTRUCTED_NORMAL_RAIL_TRACK)).thenReturn(100f);
        when(economy.getCost(ExpenseType.CONSTRUCTED_BRIDGE_RAIL_TRACK)).thenReturn(20_000f);
        when(economy.getCost(ExpenseType.CONSTRUCTED_TUNNEL_RAIL_TRACK)).thenReturn(70_000f);
        when(economy.getConstructionDelay(TrackType.NORMAL_TRACK)).thenReturn(0);
        when(economy.getConstructionDelay(TrackType.BRIDGE_TRACK)).thenReturn(20);
        when(economy.getConstructionDelay(TrackType.TUNNEL_TRACK)).thenReturn(30);
        return economy;
    }

    private static Point p(int x, int y) {
        return new Point(x, y);
    }

    private static boolean contains(RoutePlan plan, int x, int y) {
        return plan.steps().stream()
                .anyMatch(step -> step.point().getX() == x && step.point().getY() == y);
    }

    private static List<Point> points(RoutePlan plan) {
        return plan.steps().stream().map(RouteStep::point).toList();
    }

    @Test
    @DisplayName("flat ground between two points uses normal track and one price per cell")
    void should_PlanStraightNormalTrack_When_TerrainIsFlat() {
        FakeGroundMap terrain = new FakeGroundMap().rect(-1, -1, 4, 1, NORMAL);
        RoutePlan plan =
                RoutePlanner.plan(terrain, economy(), p(0, 0), p(3, 0), RoutePolicy.DIRECT);

        assertTrue(plan.feasible());
        assertEquals(List.of(p(0, 0), p(1, 0), p(2, 0), p(3, 0)), points(plan));
        assertTrue(
                plan.steps().stream().allMatch(step -> step.trackType() == TrackType.NORMAL_TRACK));
        assertEquals(400.0, plan.totalCost(), 1e-9);
        assertEquals(0, plan.totalConstructionTicks());
    }

    @Test
    @DisplayName("water cells become bridges with bridge price and delay")
    void should_UseBridgeTrack_When_RouteCrossesWater() {
        FakeGroundMap terrain =
                new FakeGroundMap().rect(-1, -1, 4, 1, NORMAL).put(1, 0, WATER).put(2, 0, WATER);
        RoutePlan plan =
                RoutePlanner.plan(terrain, economy(), p(0, 0), p(3, 0), RoutePolicy.DIRECT);

        assertTrue(plan.feasible());
        assertEquals(TrackType.BRIDGE_TRACK, typeAt(plan, 1, 0));
        assertEquals(TrackType.BRIDGE_TRACK, typeAt(plan, 2, 0));
        assertEquals(100 + 20_000 + 20_000 + 100, plan.totalCost(), 1e-9);
        assertEquals(40, plan.totalConstructionTicks());
    }

    @Test
    @DisplayName("rock cells become tunnels with tunnel price and delay")
    void should_UseTunnelTrack_When_RouteCrossesRock() {
        FakeGroundMap terrain =
                new FakeGroundMap().rect(-1, -1, 4, 1, NORMAL).put(1, 0, ROCK).put(2, 0, ROCK);
        RoutePlan plan =
                RoutePlanner.plan(terrain, economy(), p(0, 0), p(3, 0), RoutePolicy.DIRECT);

        assertTrue(plan.feasible());
        assertEquals(TrackType.TUNNEL_TRACK, typeAt(plan, 1, 0));
        assertEquals(TrackType.TUNNEL_TRACK, typeAt(plan, 2, 0));
        assertEquals(100 + 70_000 + 70_000 + 100, plan.totalCost(), 1e-9);
        assertEquals(60, plan.totalConstructionTicks());
    }

    @Test
    @DisplayName("AVOID_MOUNTAINS routes around rock and never plans a tunnel")
    void should_AvoidRock_When_PolicyIsAvoidMountains() {
        FakeGroundMap terrain =
                new FakeGroundMap().rect(-1, -2, 3, 2, NORMAL).put(1, 0, ROCK).put(1, -1, ROCK);
        RoutePlan plan = RoutePlanner.plan(terrain, economy(), p(0, 0), p(2, 0),
                RoutePolicy.AVOID_MOUNTAINS);

        assertTrue(plan.feasible());
        assertFalse(contains(plan, 1, 0));
        assertFalse(
                plan.steps().stream().anyMatch(step -> step.trackType() == TrackType.TUNNEL_TRACK));
        assertTrue(contains(plan, 1, 1));
    }

    @Test
    @DisplayName("AVOID_WATER routes around water and never plans a bridge")
    void should_AvoidWater_When_PolicyIsAvoidWater() {
        FakeGroundMap terrain = new FakeGroundMap().rect(-1, -2, 5, 2, NORMAL).put(2, -1, WATER)
                .put(2, 0, WATER).put(2, 1, WATER);
        RoutePlan plan =
                RoutePlanner.plan(terrain, economy(), p(0, 0), p(4, 0), RoutePolicy.AVOID_WATER);

        assertTrue(plan.feasible());
        assertFalse(
                plan.steps().stream().anyMatch(step -> step.trackType() == TrackType.BRIDGE_TRACK));
        assertTrue(plan.steps().stream().anyMatch(step -> Math.abs(step.point().getY()) == 2));
        assertEquals(500.0, plan.totalCost(), 1e-9);
    }

    @Test
    @DisplayName("AVOID_BOTH routes through the ground gap between water and rock")
    void should_AvoidWaterAndRock_When_PolicyIsAvoidBoth() {
        FakeGroundMap terrain = new FakeGroundMap().rect(-1, -2, 6, 3, NORMAL);
        for (int y = -1; y <= 1; y++) {
            terrain.put(2, y, WATER).put(3, y, ROCK);
        }
        RoutePlan plan =
                RoutePlanner.plan(terrain, economy(), p(0, 0), p(5, 0), RoutePolicy.AVOID_BOTH);

        assertTrue(plan.feasible());
        assertTrue(
                plan.steps().stream().allMatch(step -> step.trackType() == TrackType.NORMAL_TRACK));
        assertTrue(plan.steps().stream().anyMatch(step -> Math.abs(step.point().getY()) == 2));
    }

    @Test
    @DisplayName("AVOID_BOTH is infeasible when water and rock form a closed wall")
    void should_ReturnInfeasible_When_NoAllowedCellConnectsStartAndEnd() {
        FakeGroundMap terrain = new FakeGroundMap().rect(-1, -1, 6, 1, NORMAL);
        for (int y = -1; y <= 1; y++) {
            terrain.put(2, y, WATER).put(3, y, ROCK);
        }
        RoutePlan plan =
                RoutePlanner.plan(terrain, economy(), p(0, 0), p(5, 0), RoutePolicy.AVOID_BOTH);

        assertFalse(plan.feasible());
        assertTrue(plan.reason().isPresent());
        assertTrue(plan.reason().orElseThrow().contains("AVOID_BOTH"));
        assertTrue(plan.steps().isEmpty());
        assertEquals(0.0, plan.totalCost(), 1e-9);
        assertEquals(0, plan.totalConstructionTicks());
    }

    @Test
    @DisplayName("DIRECT follows the shortest line even over water and reports its real cost")
    void should_IgnoreTerrainCost_When_PolicyIsDirect() {
        FakeGroundMap terrain = new FakeGroundMap().rect(-1, -1, 3, 1, NORMAL).put(1, 0, WATER);
        RoutePlan direct =
                RoutePlanner.plan(terrain, economy(), p(0, 0), p(2, 0), RoutePolicy.DIRECT);

        assertTrue(direct.feasible());
        assertEquals(TrackType.BRIDGE_TRACK, typeAt(direct, 1, 0));
        assertEquals(100 + 20_000 + 100, direct.totalCost(), 1e-9);
        assertEquals(20, direct.totalConstructionTicks());
    }

    @Test
    @DisplayName("CHEAPEST detours over ground instead of paying for a bridge")
    void should_PreferNormalDetour_When_PolicyIsCheapest() {
        FakeGroundMap terrain = new FakeGroundMap().rect(-1, -1, 3, 1, NORMAL).put(1, 0, WATER);
        RoutePlan cheapest =
                RoutePlanner.plan(terrain, economy(), p(0, 0), p(2, 0), RoutePolicy.CHEAPEST);

        assertTrue(cheapest.feasible());
        assertFalse(cheapest.steps().stream()
                .anyMatch(step -> step.trackType() == TrackType.BRIDGE_TRACK));
        assertEquals(3, cheapest.steps().size());
        assertEquals(300.0, cheapest.totalCost(), 1e-9);
        assertEquals(0, cheapest.totalConstructionTicks());
    }

    @Test
    @DisplayName("industry cells count as ground, as RailTrackMaker does")
    void should_TreatIndustryAsGround_When_EndpointsOrRouteUseIt() {
        FakeGroundMap terrain =
                new FakeGroundMap().rect(0, 0, 2, 0, NORMAL).put(0, 0, GroundMap.GOLD_MINE)
                        .put(1, 0, GroundMap.MINE).put(2, 0, GroundMap.JEWELRY_STORE);

        RoutePlan plan =
                RoutePlanner.plan(terrain, economy(), p(0, 0), p(2, 0), RoutePolicy.DIRECT);

        assertTrue(plan.feasible());
        assertEquals(3, plan.steps().size());
        assertTrue(
                plan.steps().stream().allMatch(step -> step.trackType() == TrackType.NORMAL_TRACK));
        assertEquals(300.0, plan.totalCost(), 1e-9);
    }

    @Test
    @DisplayName("equal-cost symmetric routes always resolve to the same one")
    void should_BreakTiesDeterministically_When_TwoRoutesHaveEqualCost() {
        FakeGroundMap terrain = new FakeGroundMap().rect(-1, -2, 3, 2, NORMAL).put(1, 0, ROCK);
        RoutePlan first = RoutePlanner.plan(terrain, economy(), p(0, 0), p(2, 0),
                RoutePolicy.AVOID_MOUNTAINS);

        for (int i = 0; i < 20; i++) {
            RoutePlan again = RoutePlanner.plan(terrain, economy(), p(0, 0), p(2, 0),
                    RoutePolicy.AVOID_MOUNTAINS);
            assertEquals(first, again);
        }
        // Fixed neighbour order (N, NE, E, SE, ...) plus FIFO tie-break: the first diagonal
        // candidate (1,-1) wins over its symmetric (1,1) counterpart.
        assertEquals(List.of(p(0, 0), p(1, -1), p(2, 0)), points(first));
    }

    @Test
    @DisplayName("start and end must be buildable ground")
    void should_ReturnInfeasible_When_EndpointIsNotBuildable() {
        FakeGroundMap waterStart = new FakeGroundMap().rect(0, 0, 2, 0, NORMAL).put(0, 0, WATER);
        RoutePlan waterPlan =
                RoutePlanner.plan(waterStart, economy(), p(0, 0), p(2, 0), RoutePolicy.DIRECT);
        assertFalse(waterPlan.feasible());
        assertTrue(waterPlan.reason().orElseThrow().contains("water"));

        FakeGroundMap rockEnd = new FakeGroundMap().rect(0, 0, 2, 0, NORMAL).put(2, 0, ROCK);
        RoutePlan rockPlan =
                RoutePlanner.plan(rockEnd, economy(), p(0, 0), p(2, 0), RoutePolicy.DIRECT);
        assertFalse(rockPlan.feasible());
        assertTrue(rockPlan.reason().orElseThrow().contains("rock"));

        FakeGroundMap noData = new FakeGroundMap().rect(1, 0, 2, 0, NORMAL);
        RoutePlan noDataPlan =
                RoutePlanner.plan(noData, economy(), p(0, 0), p(2, 0), RoutePolicy.DIRECT);
        assertFalse(noDataPlan.feasible());
        assertTrue(noDataPlan.reason().orElseThrow().contains("no terrain data"));

        FakeGroundMap unknown = new FakeGroundMap().rect(0, 0, 2, 0, NORMAL).put(0, 0, 5);
        RoutePlan unknownPlan =
                RoutePlanner.plan(unknown, economy(), p(0, 0), p(2, 0), RoutePolicy.DIRECT);
        assertFalse(unknownPlan.feasible());
        assertTrue(unknownPlan.reason().orElseThrow().contains("unsupported terrain value"));
    }

    @Test
    @DisplayName("start equal to end is a feasible no-op")
    void should_ReturnEmptyFeasiblePlan_When_StartEqualsEnd() {
        FakeGroundMap terrain = new FakeGroundMap().rect(0, 0, 2, 2, NORMAL);

        RoutePlan plan =
                RoutePlanner.plan(terrain, economy(), p(1, 1), p(1, 1), RoutePolicy.CHEAPEST);

        assertTrue(plan.feasible());
        assertTrue(plan.steps().isEmpty());
        assertEquals(0.0, plan.totalCost(), 1e-9);
        assertEquals(0, plan.totalConstructionTicks());
        assertTrue(plan.reason().isEmpty());
    }

    @Test
    @DisplayName("planning does not mutate the terrain and step positions are defensive copies")
    void should_NotMutateInputs_When_Planning() {
        FakeGroundMap terrain = new FakeGroundMap().rect(-1, -1, 4, 1, NORMAL).put(1, 0, WATER);
        Point start = p(0, 0);
        Point end = p(3, 0);

        RoutePlan plan = RoutePlanner.plan(terrain, economy(), start, end, RoutePolicy.DIRECT);

        assertEquals(NORMAL, terrain.getValueAt(0, 0));
        assertEquals(WATER, terrain.getValueAt(1, 0));
        assertEquals(NORMAL, terrain.getValueAt(3, 0));
        start.setX(99);
        end.setY(99);
        assertEquals(0, plan.steps().get(0).point().getX());
        assertEquals(0, plan.steps().get(plan.steps().size() - 1).point().getY());
    }

    @Test
    @DisplayName("null arguments are rejected")
    void should_RejectNullArguments() {
        FakeGroundMap terrain = new FakeGroundMap().rect(0, 0, 2, 0, NORMAL);
        EconomyManager economy = economy();

        assertThrows(NullPointerException.class,
                () -> RoutePlanner.plan(null, economy, p(0, 0), p(2, 0), RoutePolicy.DIRECT));
        assertThrows(NullPointerException.class,
                () -> RoutePlanner.plan(terrain, null, p(0, 0), p(2, 0), RoutePolicy.DIRECT));
        assertThrows(NullPointerException.class,
                () -> RoutePlanner.plan(terrain, economy, null, p(2, 0), RoutePolicy.DIRECT));
        assertThrows(NullPointerException.class,
                () -> RoutePlanner.plan(terrain, economy, p(0, 0), null, RoutePolicy.DIRECT));
        assertThrows(NullPointerException.class,
                () -> RoutePlanner.plan(terrain, economy, p(0, 0), p(2, 0), null));
    }

    private static TrackType typeAt(RoutePlan plan, int x, int y) {
        return plan.steps().stream()
                .filter(step -> step.point().getX() == x && step.point().getY() == y)
                .map(RouteStep::trackType).findFirst().orElse(null);
    }

    @Test
    @DisplayName("RoutePlan validates its invariants")
    void should_ValidatePlanInvariants() {
        assertNotNull(RoutePlan.infeasible("because"));
        assertThrows(NullPointerException.class, () -> RoutePlan.infeasible(null));
        assertThrows(IllegalArgumentException.class, () -> RoutePlan.infeasible(" "));
        assertThrows(IllegalArgumentException.class, () -> RoutePlan.feasible(List.of(), -1, 0));
        assertThrows(IllegalArgumentException.class, () -> RoutePlan.feasible(List.of(), 0, -1));
    }
}
