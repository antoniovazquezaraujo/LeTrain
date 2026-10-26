package letrain.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;
import letrain.economy.EconomyManager;
import letrain.economy.EconomyManager.ExpenseType;
import letrain.ground.GroundMap;
import letrain.map.Dir;
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

    /**
     * Fails unless every consecutive pair of steps turns at most 45 degrees, like RailTrackMaker.
     */
    private static void assertLegalTurns(RoutePlan plan) {
        List<Point> points = points(plan);
        for (int i = 1; i < points.size(); i++) {
            Point from = points.get(i - 1);
            Point to = points.get(i);
            Dir direction = directionBetween(from, to);
            assertNotNull(direction, "non-adjacent steps in plan: " + points);
            if (i >= 2) {
                Dir previous = directionBetween(points.get(i - 2), from);
                assertTrue(RoutePlanner.isTurnAllowed(previous, direction),
                        "illegal turn at step " + i + ": " + points);
            }
        }
    }

    private static List<Dir> directions(RoutePlan plan) {
        List<Point> points = points(plan);
        List<Dir> directions = new ArrayList<>();
        for (int i = 1; i < points.size(); i++) {
            directions.add(directionBetween(points.get(i - 1), points.get(i)));
        }
        return directions;
    }

    private static Dir directionBetween(Point from, Point to) {
        int dx = to.getX() - from.getX();
        int dy = to.getY() - from.getY();
        if (dx < -1 || dx > 1 || dy < -1 || dy > 1) {
            return null;
        }
        if (dx == 0 && dy == 0) {
            return null;
        }
        if (dx == 1 && dy == 0) {
            return Dir.E;
        }
        if (dx == 1 && dy == -1) {
            return Dir.NE;
        }
        if (dx == 0 && dy == -1) {
            return Dir.N;
        }
        if (dx == -1 && dy == -1) {
            return Dir.NW;
        }
        if (dx == -1 && dy == 0) {
            return Dir.W;
        }
        if (dx == -1 && dy == 1) {
            return Dir.SW;
        }
        if (dx == 0 && dy == 1) {
            return Dir.S;
        }
        if (dx == 1 && dy == 1) {
            return Dir.SE;
        }
        return null;
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
                new FakeGroundMap().rect(-3, -4, 6, 4, NORMAL).put(1, 0, ROCK).put(1, -1, ROCK);
        RoutePlan plan = RoutePlanner.plan(terrain, economy(), p(0, 0), p(2, 0),
                RoutePolicy.AVOID_MOUNTAINS);

        assertTrue(plan.feasible());
        assertFalse(contains(plan, 1, 0));
        assertFalse(
                plan.steps().stream().anyMatch(step -> step.trackType() == TrackType.TUNNEL_TRACK));
        assertTrue(plan.steps().size() > 3);
        assertEquals(800.0, plan.totalCost(), 1e-9);
        assertLegalTurns(plan);
    }

    @Test
    @DisplayName("AVOID_WATER routes around water and never plans a bridge")
    void should_AvoidWater_When_PolicyIsAvoidWater() {
        FakeGroundMap terrain = new FakeGroundMap().rect(-3, -4, 8, 4, NORMAL).put(2, -1, WATER)
                .put(2, 0, WATER).put(2, 1, WATER);
        RoutePlan plan =
                RoutePlanner.plan(terrain, economy(), p(0, 0), p(4, 0), RoutePolicy.AVOID_WATER);

        assertTrue(plan.feasible());
        assertFalse(
                plan.steps().stream().anyMatch(step -> step.trackType() == TrackType.BRIDGE_TRACK));
        assertEquals(600.0, plan.totalCost(), 1e-9);
        assertLegalTurns(plan);
    }

    @Test
    @DisplayName("AVOID_ALL routes through the ground gap between water and rock")
    void should_AvoidWaterAndRock_When_PolicyIsAvoidBoth() {
        FakeGroundMap terrain = new FakeGroundMap().rect(-1, -2, 6, 3, NORMAL);
        for (int y = -1; y <= 1; y++) {
            terrain.put(2, y, WATER).put(3, y, ROCK);
        }
        RoutePlan plan =
                RoutePlanner.plan(terrain, economy(), p(0, 0), p(5, 0), RoutePolicy.AVOID_ALL);

        assertTrue(plan.feasible());
        assertTrue(
                plan.steps().stream().allMatch(step -> step.trackType() == TrackType.NORMAL_TRACK));
        assertTrue(plan.steps().stream().anyMatch(step -> Math.abs(step.point().getY()) == 2));
        assertLegalTurns(plan);
    }

    @Test
    @DisplayName("AVOID routes may run in a one-cell corridor adjacent to blocked terrain")
    void should_UseCorridor_When_ItRunsAlongsideBlockedTerrain() {
        // One-cell-wide ground corridor with rock on both sides. Avoid is a binary cell block,
        // not a clearance constraint, so touching the walls is fine.
        FakeGroundMap terrain = new FakeGroundMap().rect(0, -1, 4, 1, ROCK);
        for (int x = 0; x <= 4; x++) {
            terrain.put(x, 0, NORMAL);
        }
        RoutePlan plan = RoutePlanner.plan(terrain, economy(), p(0, 0), p(4, 0),
                RoutePolicy.AVOID_MOUNTAINS);

        assertTrue(plan.feasible());
        assertEquals(List.of(p(0, 0), p(1, 0), p(2, 0), p(3, 0), p(4, 0)), points(plan));
        assertEquals(500.0, plan.totalCost(), 1e-9);
        assertLegalTurns(plan);
    }

    @Test
    @DisplayName("AVOID_ALL is infeasible when water and rock form a closed wall")
    void should_ReturnInfeasible_When_NoAllowedCellConnectsStartAndEnd() {
        FakeGroundMap terrain = new FakeGroundMap().rect(-1, -1, 6, 1, NORMAL);
        for (int y = -1; y <= 1; y++) {
            terrain.put(2, y, WATER).put(3, y, ROCK);
        }
        RoutePlan plan =
                RoutePlanner.plan(terrain, economy(), p(0, 0), p(5, 0), RoutePolicy.AVOID_ALL);

        assertFalse(plan.feasible());
        assertTrue(plan.reason().isPresent());
        assertTrue(plan.reason().orElseThrow().contains("AVOID_ALL"));
        assertTrue(plan.steps().isEmpty());
        assertEquals(0.0, plan.totalCost(), 1e-9);
        assertEquals(0, plan.totalConstructionTicks());
    }

    @Test
    @DisplayName("diagonal land-to-land move is allowed across policy-blocked buildable corners")
    void should_AllowDiagonal_When_BothOrthogonalsArePolicyBlockedButBuildable() {
        FakeGroundMap terrain =
                new FakeGroundMap().rect(0, 0, 1, 1, NORMAL).put(1, 0, ROCK).put(0, 1, ROCK);

        RoutePlan plan = RoutePlanner.plan(terrain, economy(), p(0, 0), p(1, 1),
                RoutePolicy.AVOID_MOUNTAINS);

        assertTrue(plan.feasible());
        assertEquals(List.of(p(0, 0), p(1, 1)), points(plan));
        assertEquals(200.0, plan.totalCost(), 1e-9);
        assertEquals(TrackType.NORMAL_TRACK, typeAt(plan, 1, 1));
    }

    @Test
    @DisplayName("diagonal is allowed when at least one orthogonal cell is free")
    void should_AllowDiagonal_When_OnlyOneOrthogonalIsBlocked() {
        FakeGroundMap terrain = new FakeGroundMap().rect(0, 0, 1, 1, NORMAL).put(1, 0, ROCK);

        RoutePlan plan = RoutePlanner.plan(terrain, economy(), p(0, 0), p(1, 1),
                RoutePolicy.AVOID_MOUNTAINS);

        assertTrue(plan.feasible());
        assertEquals(List.of(p(0, 0), p(1, 1)), points(plan));
        assertEquals(200.0, plan.totalCost(), 1e-9);
    }

    @Test
    @DisplayName("a policy-blocked but buildable corner no longer forces a detour")
    void should_UseCorner_When_OrthogonalsArePolicyBlockedButBuildable() {
        FakeGroundMap terrain =
                new FakeGroundMap().rect(-1, -1, 3, 2, NORMAL).put(1, 0, ROCK).put(0, 1, ROCK);
        RoutePlan plan = RoutePlanner.plan(terrain, economy(), p(0, 0), p(2, 1),
                RoutePolicy.AVOID_MOUNTAINS);

        assertTrue(plan.feasible());
        assertEquals(List.of(p(0, 0), p(1, 1), p(2, 1)), points(plan));
        assertEquals(300.0, plan.totalCost(), 1e-9);
        assertTrue(plan.steps().stream()
                .noneMatch(step -> step.trackType() == TrackType.TUNNEL_TRACK));
        assertLegalTurns(plan);
    }

    @Test
    @DisplayName("non-buildable and missing terrain count as blocked for diagonal moves")
    void should_TreatUnknownAndMissingTerrainAsBlocked_When_DecidingDiagonal() {
        FakeGroundMap unknownTerrain =
                new FakeGroundMap().rect(0, 0, 1, 1, NORMAL).put(1, 0, 5).put(0, 1, 5);
        RoutePlan unknownPlan =
                RoutePlanner.plan(unknownTerrain, economy(), p(0, 0), p(1, 1), RoutePolicy.DIRECT);
        assertFalse(unknownPlan.feasible());

        FakeGroundMap missingTerrain =
                new FakeGroundMap().rect(0, 0, 0, 0, NORMAL).put(1, 1, NORMAL);
        RoutePlan missingPlan =
                RoutePlanner.plan(missingTerrain, economy(), p(0, 0), p(1, 1), RoutePolicy.DIRECT);
        assertFalse(missingPlan.feasible());
    }

    @Test
    @DisplayName("AVOID_ALL allows a one-cell diagonal land strip across water/rock")
    void should_AllowDiagonalLandStrip_When_PolicyBlocksBothTerrains() {
        // Only the main diagonal is land. Every orthogonal corner is water or rock: buildable, so
        // the diagonal land-to-land moves are legal even though AVOID_ALL blocks stepping on them.
        FakeGroundMap terrain = new FakeGroundMap().rect(-1, -1, 3, 3, WATER).put(0, 0, NORMAL)
                .put(1, 1, NORMAL).put(2, 2, NORMAL).put(1, 0, ROCK).put(0, 1, ROCK).put(2, 1, ROCK)
                .put(1, 2, ROCK);

        RoutePlan plan =
                RoutePlanner.plan(terrain, economy(), p(0, 0), p(2, 2), RoutePolicy.AVOID_ALL);

        assertTrue(plan.feasible());
        assertEquals(List.of(p(0, 0), p(1, 1), p(2, 2)), points(plan));
        assertEquals(300.0, plan.totalCost(), 1e-9);
        assertTrue(
                plan.steps().stream().allMatch(step -> step.trackType() == TrackType.NORMAL_TRACK));
        assertLegalTurns(plan);
    }

    @Test
    @DisplayName("AVOID_WATER allows a land diagonal across water-blocked corners")
    void should_AllowDiagonal_When_BothOrthogonalsAreWater() {
        FakeGroundMap terrain =
                new FakeGroundMap().rect(0, 0, 1, 1, WATER).put(0, 0, NORMAL).put(1, 1, NORMAL);

        RoutePlan plan =
                RoutePlanner.plan(terrain, economy(), p(0, 0), p(1, 1), RoutePolicy.AVOID_WATER);

        assertTrue(plan.feasible());
        assertEquals(List.of(p(0, 0), p(1, 1)), points(plan));
        assertEquals(200.0, plan.totalCost(), 1e-9);
    }

    @Test
    @DisplayName("a route still cannot step onto water/rock under AVOID_ALL")
    void should_RejectRoute_When_DiagonalStepWouldEnterBlockedTerrain() {
        // The middle of the only diagonal is water; the corner trick does not let the route build
        // on blocked terrain, so no legal route exists.
        FakeGroundMap terrain =
                new FakeGroundMap().rect(-1, -1, 3, 3, WATER).put(0, 0, NORMAL).put(2, 2, NORMAL);

        RoutePlan plan =
                RoutePlanner.plan(terrain, economy(), p(0, 0), p(2, 2), RoutePolicy.AVOID_ALL);

        assertFalse(plan.feasible());
        assertTrue(plan.reason().orElseThrow().contains("No route"));
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
    @DisplayName("CHEAPEST has no obstacle-proximity preference, only cost/turns/deviation")
    void should_KeepStraightCoastalLine_When_CheapestHasNoProximityTerm() {
        // Rock wall at y=0, ground from y=1 up. The straight line runs right next to the rock but
        // is the cheapest, straightest, least-deviating route; running close to rock is allowed.
        FakeGroundMap terrain = new FakeGroundMap().rect(-2, 0, 12, 6, NORMAL);
        for (int x = -2; x <= 12; x++) {
            terrain.put(x, 0, ROCK);
        }
        List<Point> straight = List.of(p(0, 1), p(1, 1), p(2, 1), p(3, 1), p(4, 1), p(5, 1),
                p(6, 1), p(7, 1), p(8, 1));

        RoutePlan direct =
                RoutePlanner.plan(terrain, economy(), p(0, 1), p(8, 1), RoutePolicy.DIRECT);
        RoutePlan cheapest =
                RoutePlanner.plan(terrain, economy(), p(0, 1), p(8, 1), RoutePolicy.CHEAPEST);

        assertTrue(direct.feasible());
        assertEquals(straight, points(direct));
        assertTrue(cheapest.feasible());
        assertEquals(straight, points(cheapest));
        assertEquals(900.0, direct.totalCost(), 1e-9);
        assertEquals(900.0, cheapest.totalCost(), 1e-9);
        assertLegalTurns(cheapest);
    }

    @Test
    @DisplayName("AVOID_MOUNTAINS crosses a lake by bridge; CHEAPEST takes the cheaper detour")
    void should_CrossWater_When_AvoidingMountainsOnLake() {
        FakeGroundMap terrain = new FakeGroundMap().rect(-4, -6, 12, 6, NORMAL);
        for (int x = 3; x <= 5; x++) {
            for (int y = -2; y <= 2; y++) {
                terrain.put(x, y, WATER);
            }
        }

        RoutePlan avoid = RoutePlanner.plan(terrain, economy(), p(0, 0), p(8, 0),
                RoutePolicy.AVOID_MOUNTAINS);
        assertTrue(avoid.feasible());
        assertEquals(List.of(p(0, 0), p(1, 0), p(2, 0), p(3, 0), p(4, 0), p(5, 0), p(6, 0), p(7, 0),
                p(8, 0)), points(avoid));
        assertEquals(TrackType.BRIDGE_TRACK, typeAt(avoid, 3, 0));
        assertEquals(TrackType.BRIDGE_TRACK, typeAt(avoid, 4, 0));
        assertEquals(TrackType.BRIDGE_TRACK, typeAt(avoid, 5, 0));
        assertFalse(avoid.steps().stream()
                .anyMatch(step -> step.trackType() == TrackType.TUNNEL_TRACK));
        assertEquals(6 * 100 + 3 * 20_000, avoid.totalCost(), 1e-9);
        assertEquals(3 * 20, avoid.totalConstructionTicks());
        assertLegalTurns(avoid);

        RoutePlan cheapest =
                RoutePlanner.plan(terrain, economy(), p(0, 0), p(8, 0), RoutePolicy.CHEAPEST);
        assertTrue(cheapest.feasible());
        assertFalse(cheapest.steps().stream()
                .anyMatch(step -> step.trackType() == TrackType.BRIDGE_TRACK));
        assertEquals(900.0, cheapest.totalCost(), 1e-9);
        assertEquals(0, cheapest.totalConstructionTicks());
    }

    @Test
    @DisplayName("AVOID_WATER crosses a rock wall by tunnel; CHEAPEST takes the cheaper detour")
    void should_CrossRock_When_AvoidingWaterOnRockWall() {
        FakeGroundMap terrain = new FakeGroundMap().rect(-4, -6, 12, 6, NORMAL);
        for (int x = 3; x <= 5; x++) {
            for (int y = -2; y <= 2; y++) {
                terrain.put(x, y, ROCK);
            }
        }

        RoutePlan avoid =
                RoutePlanner.plan(terrain, economy(), p(0, 0), p(8, 0), RoutePolicy.AVOID_WATER);
        assertTrue(avoid.feasible());
        assertEquals(List.of(p(0, 0), p(1, 0), p(2, 0), p(3, 0), p(4, 0), p(5, 0), p(6, 0), p(7, 0),
                p(8, 0)), points(avoid));
        assertEquals(TrackType.TUNNEL_TRACK, typeAt(avoid, 3, 0));
        assertEquals(TrackType.TUNNEL_TRACK, typeAt(avoid, 4, 0));
        assertEquals(TrackType.TUNNEL_TRACK, typeAt(avoid, 5, 0));
        assertFalse(avoid.steps().stream()
                .anyMatch(step -> step.trackType() == TrackType.BRIDGE_TRACK));
        assertEquals(6 * 100 + 3 * 70_000, avoid.totalCost(), 1e-9);
        assertEquals(3 * 30, avoid.totalConstructionTicks());
        assertLegalTurns(avoid);

        RoutePlan cheapest =
                RoutePlanner.plan(terrain, economy(), p(0, 0), p(8, 0), RoutePolicy.CHEAPEST);
        assertTrue(cheapest.feasible());
        assertFalse(cheapest.steps().stream()
                .anyMatch(step -> step.trackType() == TrackType.TUNNEL_TRACK));
        assertEquals(900.0, cheapest.totalCost(), 1e-9);
        assertEquals(0, cheapest.totalConstructionTicks());
    }

    @Test
    @DisplayName("CHEAPEST detours over ground instead of paying for a bridge")
    void should_PreferNormalDetour_When_PolicyIsCheapest() {
        FakeGroundMap terrain = new FakeGroundMap().rect(-4, -4, 6, 4, NORMAL).put(1, 0, WATER);
        RoutePlan cheapest =
                RoutePlanner.plan(terrain, economy(), p(0, 0), p(2, 0), RoutePolicy.CHEAPEST);

        assertTrue(cheapest.feasible());
        assertFalse(cheapest.steps().stream()
                .anyMatch(step -> step.trackType() == TrackType.BRIDGE_TRACK));
        // With the 45-degree rule, going around a single blocked cell needs a wider sweep than
        // the old three-cell detour (which turned NE->SE, 90 degrees).
        assertEquals(8, cheapest.steps().size());
        assertEquals(800.0, cheapest.totalCost(), 1e-9);
        assertEquals(0, cheapest.totalConstructionTicks());
        assertLegalTurns(cheapest);
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
        FakeGroundMap terrain = new FakeGroundMap().rect(-4, -4, 8, 4, NORMAL).put(2, 0, ROCK);
        RoutePlan first = RoutePlanner.plan(terrain, economy(), p(0, 0), p(4, 0),
                RoutePolicy.AVOID_MOUNTAINS);

        for (int i = 0; i < 20; i++) {
            RoutePlan again = RoutePlanner.plan(terrain, economy(), p(0, 0), p(4, 0),
                    RoutePolicy.AVOID_MOUNTAINS);
            assertEquals(first, again);
        }
        // Fixed neighbour order (N, NE, E, SE, ...) plus FIFO tie-break: the shortest northern
        // detour wins over its mirror image through the south.
        assertEquals(List.of(p(0, 0), p(1, -1), p(2, -1), p(3, -1), p(4, 0)), points(first));
        assertEquals(500.0, first.totalCost(), 1e-9);
    }

    @Test
    @DisplayName("the 45-degree rule rejects a direct 90-degree turn but allows two 45-degree steps")
    void should_EnforceRailCurvature_When_CheckingTurns() {
        assertFalse(RoutePlanner.isTurnAllowed(Dir.E, Dir.N));
        assertFalse(RoutePlanner.isTurnAllowed(Dir.E, Dir.S));
        assertFalse(RoutePlanner.isTurnAllowed(Dir.N, Dir.W));
        assertTrue(RoutePlanner.isTurnAllowed(Dir.E, Dir.SE));
        assertTrue(RoutePlanner.isTurnAllowed(Dir.E, Dir.NE));
        assertTrue(RoutePlanner.isTurnAllowed(Dir.NE, Dir.N));
        assertTrue(RoutePlanner.isTurnAllowed(Dir.E, Dir.E));
        assertTrue(RoutePlanner.isTurnAllowed(null, Dir.N));
    }

    @Test
    @DisplayName("a net 90-degree bend is built as two legal 45-degree steps")
    void should_ReachNinetyDegreeBend_When_BuiltAsTwoFortyFiveSteps() {
        // Rocks at (1,-1) and (0,-1) force the route east first; the E->N change must then be split
        // into E->NE and NE->N.
        FakeGroundMap terrain =
                new FakeGroundMap().rect(-2, -4, 4, 2, NORMAL).put(1, -1, ROCK).put(0, -1, ROCK);
        RoutePlan plan = RoutePlanner.plan(terrain, economy(), p(0, 0), p(2, -2),
                RoutePolicy.AVOID_MOUNTAINS);

        assertTrue(plan.feasible());
        assertLegalTurns(plan);
        List<Dir> directions = directions(plan);
        boolean hasEastNorthEastAndNorth = false;
        for (int i = 0; i + 2 < directions.size(); i++) {
            if (directions.get(i) == Dir.E && directions.get(i + 1) == Dir.NE
                    && directions.get(i + 2) == Dir.N) {
                hasEastNorthEastAndNorth = true;
            }
        }
        assertTrue(hasEastNorthEastAndNorth, "expected an E->NE->N bend in " + directions);
        assertEquals(400.0, plan.totalCost(), 1e-9);
    }

    @Test
    @DisplayName("a shortest route with a 90-degree bend is not returned")
    void should_NotReturnShortestRoute_When_ItViolatesCurvature() {
        FakeGroundMap terrain = new FakeGroundMap().rect(-3, -4, 8, 4, NORMAL).put(2, -1, WATER)
                .put(2, 0, WATER).put(2, 1, WATER);
        RoutePlan plan =
                RoutePlanner.plan(terrain, economy(), p(0, 0), p(4, 0), RoutePolicy.AVOID_WATER);

        // SE,SE,NE,NE is the shortest geometric detour but its second NE is a 90-degree turn.
        List<Point> illegalShortcut = List.of(p(0, 0), p(1, -1), p(2, -2), p(3, -1), p(4, 0));
        assertTrue(plan.feasible());
        assertFalse(points(plan).equals(illegalShortcut));
        assertTrue(plan.steps().size() > illegalShortcut.size());
        assertLegalTurns(plan);
    }

    @Test
    @DisplayName("equal-cost routes prefer the one with fewer turns")
    void should_PreferFewerTurns_When_PrimaryCostTies() {
        FakeGroundMap terrain = new FakeGroundMap().rect(-3, -5, 9, 5, NORMAL);
        RoutePlan plan =
                RoutePlanner.plan(terrain, economy(), p(0, 0), p(6, 0), RoutePolicy.CHEAPEST);

        // The straight route has no turns; an E,NE,E,E,SE,E route costs the same per cell.
        assertEquals(List.of(p(0, 0), p(1, 0), p(2, 0), p(3, 0), p(4, 0), p(5, 0), p(6, 0)),
                points(plan));
        assertEquals(700.0, plan.totalCost(), 1e-9);
        assertEquals(0, plan.totalConstructionTicks());
    }

    @Test
    @DisplayName("AVOID_WATER may run adjacent to water but never includes a water cell")
    void should_AllowWaterAdjacency_When_PolicyIsAvoidWater() {
        // Sea at y=0, ground from y=1 up. The straight y=1 line touches water all along its side,
        // but avoids water as a cell, so it is the expected shortest, no-turn route.
        FakeGroundMap terrain = new FakeGroundMap().rect(-2, 0, 12, 6, NORMAL);
        for (int x = -2; x <= 12; x++) {
            terrain.put(x, 0, WATER);
        }

        RoutePlan plan =
                RoutePlanner.plan(terrain, economy(), p(0, 1), p(8, 1), RoutePolicy.AVOID_WATER);

        assertTrue(plan.feasible());
        assertEquals(List.of(p(0, 1), p(1, 1), p(2, 1), p(3, 1), p(4, 1), p(5, 1), p(6, 1), p(7, 1),
                p(8, 1)), points(plan));
        assertFalse(
                plan.steps().stream().anyMatch(step -> step.trackType() == TrackType.BRIDGE_TRACK));
        assertEquals(900.0, plan.totalCost(), 1e-9);
        assertLegalTurns(plan);
    }

    @Test
    @DisplayName("AVOID_MOUNTAINS may run adjacent to rock but never includes a rock cell")
    void should_AllowRockAdjacency_When_PolicyIsAvoidMountains() {
        // Rock wall at y=0, ground from y=1 up. The straight y=1 line runs beside the rock but
        // never on it, so it is the expected shortest, no-turn route.
        FakeGroundMap terrain = new FakeGroundMap().rect(-2, 0, 12, 6, NORMAL);
        for (int x = -2; x <= 12; x++) {
            terrain.put(x, 0, ROCK);
        }

        RoutePlan plan = RoutePlanner.plan(terrain, economy(), p(0, 1), p(8, 1),
                RoutePolicy.AVOID_MOUNTAINS);

        assertTrue(plan.feasible());
        assertEquals(List.of(p(0, 1), p(1, 1), p(2, 1), p(3, 1), p(4, 1), p(5, 1), p(6, 1), p(7, 1),
                p(8, 1)), points(plan));
        assertFalse(
                plan.steps().stream().anyMatch(step -> step.trackType() == TrackType.TUNNEL_TRACK));
        assertEquals(900.0, plan.totalCost(), 1e-9);
        assertLegalTurns(plan);
    }

    @Test
    @DisplayName("CHEAPEST keeps a materially cheaper route even when it hugs rock")
    void should_PreferMateriallyCheaperRoute_When_ShapeWouldBePrettier() {
        // A rock wall with a one-cell gap lets the straight seven-cell route through (700). Without
        // the gap the legal detour costs 1000; the gap route is valid in both terrains, so the
        // cheaper but rock-adjacent route must still win over that prettier alternative.
        FakeGroundMap withGap = new FakeGroundMap().rect(-3, -6, 9, 6, NORMAL);
        FakeGroundMap withoutGap = new FakeGroundMap().rect(-3, -6, 9, 6, NORMAL);
        for (int y = -3; y <= 3; y++) {
            withoutGap.put(3, y, ROCK);
            if (y != 0) {
                withGap.put(3, y, ROCK);
            }
        }

        RoutePlan gapPlan =
                RoutePlanner.plan(withGap, economy(), p(0, 0), p(6, 0), RoutePolicy.CHEAPEST);
        RoutePlan detourPlan =
                RoutePlanner.plan(withoutGap, economy(), p(0, 0), p(6, 0), RoutePolicy.CHEAPEST);

        assertTrue(gapPlan.feasible());
        assertTrue(detourPlan.feasible());
        assertEquals(700.0, gapPlan.totalCost(), 1e-9);
        assertEquals(1000.0, detourPlan.totalCost(), 1e-9);
        assertTrue(gapPlan.totalCost() < detourPlan.totalCost());
        assertEquals(List.of(p(0, 0), p(1, 0), p(2, 0), p(3, 0), p(4, 0), p(5, 0), p(6, 0)),
                points(gapPlan));
        assertLegalTurns(gapPlan);
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

    @Test
    @DisplayName("progress is reported at phase start, throttled intervals and the end")
    void should_ReportThrottledProgress_When_PlanningALongRoute() {
        FakeGroundMap terrain = new FakeGroundMap().rect(0, 0, 3_000, 0, NORMAL);
        List<RoutePlanningProgress> reports = new ArrayList<>();

        RoutePlan plan = RoutePlanner.plan(terrain, economy(), p(0, 0), p(3_000, 0),
                RoutePolicy.DIRECT, reports::add, () -> false);

        assertTrue(plan.feasible());
        assertFalse(reports.isEmpty());
        RoutePlanningProgress first = reports.get(0);
        RoutePlanningProgress last = reports.get(reports.size() - 1);
        assertEquals(RoutePlanningProgress.Phase.SEARCH, first.phase());
        assertEquals(0, first.expandedNodes());
        assertEquals(p(0, 0), first.current());
        assertTrue(last.expandedNodes() >= RoutePlanner.PROGRESS_INTERVAL_EXPANSIONS);
        assertEquals(p(3_000, 0), last.current());
        assertTrue(
                reports.stream()
                        .anyMatch(report -> report
                                .expandedNodes() == RoutePlanner.PROGRESS_INTERVAL_EXPANSIONS),
                "expected a report at the throttled interval: " + reports.size() + " reports");
        for (int i = 1; i < reports.size(); i++) {
            assertTrue(reports.get(i).expandedNodes() >= reports.get(i - 1).expandedNodes());
            assertTrue(reports.get(i).elapsedMillis() >= reports.get(i - 1).elapsedMillis());
        }
    }

    @Test
    @DisplayName("an already-cancelled plan stops before touching the terrain")
    void should_ThrowCancellation_When_CancelledAtStart() {
        FakeGroundMap terrain = new FakeGroundMap().rect(0, 0, 30, 0, NORMAL);
        AtomicBoolean cancelled = new AtomicBoolean(true);

        assertThrows(CancellationException.class,
                () -> RoutePlanner.plan(terrain, economy(), p(0, 0), p(30, 0), RoutePolicy.DIRECT,
                        RoutePlanningListener.NO_OP, cancelled::get));
    }

    @Test
    @DisplayName("cancellation is polled at a throttled interval and stops a long search")
    void should_StopPromptly_When_CancelledDuringPlanning() {
        FakeGroundMap terrain = new FakeGroundMap().rect(0, 0, 3_000, 0, NORMAL);
        AtomicBoolean cancelled = new AtomicBoolean(false);
        List<RoutePlanningProgress> reports = new ArrayList<>();

        assertThrows(CancellationException.class, () -> RoutePlanner.plan(terrain, economy(),
                p(0, 0), p(3_000, 0), RoutePolicy.DIRECT, progress -> {
                    reports.add(progress);
                    if (progress.expandedNodes() >= RoutePlanner.PROGRESS_INTERVAL_EXPANSIONS) {
                        cancelled.set(true);
                    }
                }, cancelled::get));

        assertTrue(cancelled.get());
        assertTrue(
                reports.stream()
                        .anyMatch(report -> report
                                .expandedNodes() == RoutePlanner.PROGRESS_INTERVAL_EXPANSIONS),
                "the listener must observe the interval before cancellation is checked");
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
