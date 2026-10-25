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

    /** Asserts hard clearance for every cell except the start and end (which are exempt). */
    private static void assertInteriorClearance(RoutePlan plan, GroundMap terrain,
            RoutePolicy policy) {
        List<RouteStep> steps = plan.steps();
        for (int i = 1; i < steps.size() - 1; i++) {
            Point point = steps.get(i).point();
            assertTrue(RoutePlanner.hasClearance(terrain, policy, point),
                    "cell " + point + " touches blocked terrain in " + points(plan));
        }
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
        assertEquals(900.0, plan.totalCost(), 1e-9);
        assertLegalTurns(plan);
        assertInteriorClearance(plan, terrain, RoutePolicy.AVOID_MOUNTAINS);
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
        assertTrue(plan.steps().stream().anyMatch(step -> Math.abs(step.point().getY()) == 3));
        // The hard-clearance pass keeps every non-endpoint cell at least one cell away from the
        // three-cell water column, so the detour is wider than the old six-cell fallback.
        assertEquals(900.0, plan.totalCost(), 1e-9);
        assertLegalTurns(plan);
        assertInteriorClearance(plan, terrain, RoutePolicy.AVOID_WATER);
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
        assertLegalTurns(plan);
    }

    @Test
    @DisplayName("hard clearance falls back when a one-cell corridor is the only route")
    void should_FallBack_When_HardClearanceIsImpossible() {
        // One-cell-wide ground corridor with rock on both sides: every interior cell touches rock,
        // so the constrained pass cannot succeed and the fallback must still return the corridor.
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
        assertFalse(RoutePlanner.hasClearance(terrain, RoutePolicy.AVOID_MOUNTAINS, p(2, 0)));
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
    @DisplayName("diagonal shortcut between two policy-blocked orthogonal cells is rejected")
    void should_RejectDiagonal_When_BothOrthogonalsAreBlockedByPolicy() {
        FakeGroundMap terrain =
                new FakeGroundMap().rect(0, 0, 1, 1, NORMAL).put(1, 0, ROCK).put(0, 1, ROCK);

        RoutePlan plan = RoutePlanner.plan(terrain, economy(), p(0, 0), p(1, 1),
                RoutePolicy.AVOID_MOUNTAINS);

        assertFalse(plan.feasible());
        assertTrue(plan.reason().orElseThrow().contains("No route"));
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
    @DisplayName("a corner blocked for diagonals forces the planner to detour")
    void should_Detour_When_DiagonalWouldCutABlockedCorner() {
        FakeGroundMap terrain =
                new FakeGroundMap().rect(-1, -1, 3, 2, NORMAL).put(1, 0, ROCK).put(0, 1, ROCK);
        RoutePlan plan = RoutePlanner.plan(terrain, economy(), p(0, 0), p(2, 1),
                RoutePolicy.AVOID_MOUNTAINS);

        assertTrue(plan.feasible());
        assertFalse(contains(plan, 1, 1));
        assertTrue(plan.steps().size() > 3);
        assertTrue(plan.steps().stream()
                .noneMatch(step -> step.trackType() == TrackType.TUNNEL_TRACK));
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
    @DisplayName("DIRECT and CHEAPEST do not apply the hard clearance detour")
    void should_KeepCoastalStraightLine_When_PolicyIsDirectOrCheapest() {
        // Rock wall at y=0, ground from y=1 up. Avoid policies move inland; DIRECT and CHEAPEST
        // keep the straight line because they never apply the hard clearance pass.
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
        // Fixed neighbour order (N, NE, E, SE, ...) plus FIFO tie-break: the northern detour wins
        // over its mirror image through the south, one full cell clear of the rock.
        assertEquals(List.of(p(0, 0), p(0, -1), p(1, -2), p(2, -2), p(3, -2), p(4, -1), p(4, 0)),
                points(first));
        assertEquals(700.0, first.totalCost(), 1e-9);
        assertInteriorClearance(first, terrain, RoutePolicy.AVOID_MOUNTAINS);
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
        assertEquals(600.0, plan.totalCost(), 1e-9);
        assertInteriorClearance(plan, terrain, RoutePolicy.AVOID_MOUNTAINS);
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
    @DisplayName("at equal cost and turns, a coastal route loses to a one-cell-inland one")
    void should_PreferClearanceFromWater_When_PrimaryCostAndTurnsTie() {
        // A lake occupies x=2..4, y=0..1. Both the y=-1 (coastal) and y=-2 (inland) detours need
        // seven cells and two turns, so only the clearance preference separates them.
        FakeGroundMap terrain = new FakeGroundMap().rect(-3, -5, 9, 5, NORMAL);
        for (int x = 2; x <= 4; x++) {
            terrain.put(x, 0, WATER).put(x, 1, WATER);
        }
        RoutePlan plan =
                RoutePlanner.plan(terrain, economy(), p(0, 0), p(6, 0), RoutePolicy.AVOID_WATER);

        assertTrue(plan.feasible());
        assertEquals(List.of(p(0, 0), p(0, -1), p(1, -2), p(2, -2), p(3, -2), p(4, -2), p(5, -2),
                p(6, -1), p(6, 0)), points(plan));
        assertEquals(900.0, plan.totalCost(), 1e-9);
        assertLegalTurns(plan);
        assertInteriorClearance(plan, terrain, RoutePolicy.AVOID_WATER);
    }

    @Test
    @DisplayName("AVOID_WATER prefers one cell inland over hugging the coastline")
    void should_PreferInlandRoute_When_AvoidingWaterCoast() {
        // Sea at y=0, ground from y=1 up. The straight coastal route has the same nine cells and
        // two
        // turns as the inland one, but every coastal cell touches water; comfort must win.
        FakeGroundMap terrain = new FakeGroundMap().rect(-2, 0, 12, 6, NORMAL);
        for (int x = -2; x <= 12; x++) {
            terrain.put(x, 0, WATER);
        }

        RoutePlan plan =
                RoutePlanner.plan(terrain, economy(), p(0, 1), p(8, 1), RoutePolicy.AVOID_WATER);

        assertTrue(plan.feasible());
        assertEquals(List.of(p(0, 1), p(1, 2), p(2, 2), p(3, 2), p(4, 2), p(5, 2), p(6, 2), p(7, 2),
                p(8, 1)), points(plan));
        assertEquals(9, plan.steps().size());
        assertFalse(
                plan.steps().stream().anyMatch(step -> step.trackType() == TrackType.BRIDGE_TRACK));
        assertLegalTurns(plan);
        assertInteriorClearance(plan, terrain, RoutePolicy.AVOID_WATER);
        assertEquals(900.0, plan.totalCost(), 1e-9);
        assertEquals(0, plan.totalConstructionTicks());
    }

    @Test
    @DisplayName("AVOID_MOUNTAINS also prefers one cell away from a rock wall")
    void should_PreferInlandRoute_When_AvoidingMountainWall() {
        FakeGroundMap terrain = new FakeGroundMap().rect(-2, 0, 12, 6, NORMAL);
        for (int x = -2; x <= 12; x++) {
            terrain.put(x, 0, ROCK);
        }

        RoutePlan plan = RoutePlanner.plan(terrain, economy(), p(0, 1), p(8, 1),
                RoutePolicy.AVOID_MOUNTAINS);

        assertTrue(plan.feasible());
        assertEquals(List.of(p(0, 1), p(1, 2), p(2, 2), p(3, 2), p(4, 2), p(5, 2), p(6, 2), p(7, 2),
                p(8, 1)), points(plan));
        assertEquals(9, plan.steps().size());
        assertFalse(
                plan.steps().stream().anyMatch(step -> step.trackType() == TrackType.TUNNEL_TRACK));
        assertLegalTurns(plan);
        assertInteriorClearance(plan, terrain, RoutePolicy.AVOID_MOUNTAINS);
        assertEquals(900.0, plan.totalCost(), 1e-9);
    }

    @Test
    @DisplayName("at equal cost, turns and clearance, the route closest to the straight line wins")
    void should_PreferLessDeviation_When_OtherPreferencesTie() {
        // Under CHEAPEST water is expensive but not blocked, so the comfort terms stay tiny. A
        // water wall at y=0 forces two seven-cell, two-turn detours of equal primary cost; the
        // nearer one (y=-1) must win on deviation.
        FakeGroundMap terrain = new FakeGroundMap().rect(-3, -5, 9, 5, NORMAL);
        for (int x = 1; x <= 5; x++) {
            terrain.put(x, 0, WATER);
        }
        RoutePlan plan =
                RoutePlanner.plan(terrain, economy(), p(0, 0), p(6, 0), RoutePolicy.CHEAPEST);

        assertTrue(plan.feasible());
        assertEquals(List.of(p(0, 0), p(1, -1), p(2, -1), p(3, -1), p(4, -1), p(5, -1), p(6, 0)),
                points(plan));
        assertLegalTurns(plan);
        assertEquals(700.0, plan.totalCost(), 1e-9);
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
