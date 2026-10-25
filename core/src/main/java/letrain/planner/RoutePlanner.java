package letrain.planner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import letrain.economy.EconomyManager;
import letrain.economy.EconomyManager.ExpenseType;
import letrain.ground.GroundMap;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.mvp.Presenter.TrackType;
import letrain.utils.ValidationUtils;

/**
 * Cost-aware route planner for scenario authoring.
 *
 * <p>
 * Given a terrain, the current economy and a {@link RoutePolicy}, it returns the ordered cells a
 * hypothetical {@code connect} command would build, plus the real price and construction time. It
 * is a <b>pure read-only</b> spike: it never mutates the terrain, the economy or the rail map, and
 * it does not enqueue construction. The two-layer authoring model (plan first, build later) is
 * described in ADR-034.
 *
 * <p>
 * <b>Grid and metric.</b> A* over the 8-direction grid. {@link RoutePolicy#DIRECT} optimises
 * geometric distance (orthogonal 1, diagonal {@code sqrt(2)}); the other policies optimise the real
 * per-cell construction cost after blocking. Every returned plan reports the real construction
 * cost/time of its cells regardless of the optimisation metric.
 *
 * <p>
 * <b>Curvature.</b> The search state is a cell <em>plus the heading used to arrive at it</em>. The
 * first step may take any of the eight directions; every following step must turn at most 45
 * degrees, i.e. {@code Math.abs(previous.angularDistance(next)) <= 1}. This is the same rule
 * {@code RailTrackMaker} enforces when placing track, so a plan never asks for an unbuildable
 * 90/135/180-degree bend. The diagonal corner-cutting rule is unchanged.
 *
 * <p>
 * <b>Comfort preferences.</b> Search cost is the primary construction cost (or geometric distance
 * for {@code DIRECT}) plus policy-specific comfort terms. They are never added to the reported
 * cost/time.
 *
 * <p>
 * For the avoid policies ({@code AVOID_MOUNTAINS}, {@code AVOID_WATER}, {@code AVOID_BOTH}) the
 * comfort terms are fractions of the <b>normal-track cost</b> (floor 1 when it is free):
 *
 * <ul>
 * <li>{@code 0.25 × baseline} per 45-degree turn,
 * <li>{@code 0.5 × baseline} per blocked neighbour (clearance),
 * <li>{@code 0.05 × baseline} per cell of Euclidean deviation from the straight segment.
 * </ul>
 *
 * These terms are intentionally strong enough to let an avoid route be a little more expensive than
 * the absolute cheapest one when it buys clearance from water/rock or fewer bends. The primary
 * construction cost still carries the most weight per cell, and the reported totals stay real.
 *
 * <p>
 * <b>Hard clearance pass.</b> Avoid policies first run the search with a hard constraint: every
 * chosen cell except the start and end must have zero blocked neighbours under the active policy,
 * keeping the track at least one cell away from the offending terrain. If that pass is infeasible
 * (narrow corridor, enclosed endpoint, map edge) the same search runs again without the hard
 * constraint and its best plan is returned. The comfort terms still apply in both passes and are
 * search-only; reported cost/time are always the real cells. {@code DIRECT} and {@code CHEAPEST}
 * never use the hard clearance pass.
 *
 * <p>
 * For {@code DIRECT} and {@code CHEAPEST} the comfort terms are tiny deterministic tie-breakers
 * relative to the cheapest per-cell primary cost; those policies keep their original primary
 * semantics. See {@link #TIE_TURN_FRACTION} and {@link #TIE_SHAPE_FRACTION}.
 *
 * <p>
 * <b>Clearance.</b> A cell's clearance penalty is the number of its eight neighbours that are
 * blocked <em>under the active policy</em>: missing/non-buildable terrain plus the policy's own
 * block (water under {@code AVOID_WATER}, rock under {@code AVOID_MOUNTAINS}, both under
 * {@code AVOID_BOTH}). Under {@code DIRECT} and {@code CHEAPEST} buildable water and rock are not
 * blocked, so they do not penalise clearance; those policies already cross them.
 *
 * <p>
 * <b>Determinism.</b> Neighbours are expanded in a fixed order and the open set breaks f-score ties
 * by insertion sequence (FIFO), so the same inputs always produce the same route. The heuristic is
 * admissible and consistent for both primary metrics because the comfort penalties are non-negative
 * and ignored by the heuristic.
 *
 * <p>
 * <b>Terrain normalisation.</b> Industry values {@code 10..29} are treated as {@code GROUND}, the
 * same normalisation {@code RailTrackMaker.detectTrackType()} uses. Endpoints must be buildable
 * ground/industry; water or rock endpoints are rejected with a reason.
 */
public final class RoutePlanner {

    private static final double SQRT_2 = Math.sqrt(2.0);

    /**
     * Tiny turn tie-breaker for {@code DIRECT}/{@code CHEAPEST}, as a fraction of the cheapest
     * per-cell primary cost.
     */
    static final double TIE_TURN_FRACTION = 1e-3;

    /**
     * Tiny shape tie-breaker for {@code DIRECT}/{@code CHEAPEST}, as a fraction of the cheapest
     * per-cell primary cost.
     */
    static final double TIE_SHAPE_FRACTION = 1e-5;

    /** Avoid-policy comfort: search cost per 45-degree turn, as a fraction of normal-track cost. */
    static final double AVOID_TURN_FRACTION = 0.25;

    /** Avoid-policy comfort: search cost per blocked neighbour, per cell. */
    static final double AVOID_CLEARANCE_FACTOR = 0.5;

    /** Avoid-policy comfort: search cost per cell of Euclidean deviation from the straight line. */
    static final double AVOID_DEVIATION_FRACTION = 0.05;

    /**
     * Safety cap for pathological searches over the procedural (unbounded) terrain. Reaching it
     * returns an infeasible plan instead of looping forever. Large enough not to affect authoring
     * distances, small enough to bound worst-case latency.
     */
    static final int MAX_EXPANDED_NODES = 1_000_000;

    /**
     * Fixed neighbour order (N, NE, E, SE, S, SW, W, NW). Together with the insertion-sequence
     * tie-break it makes equal-cost routes reproducible across runs and JVMs.
     */
    private static final List<Move> MOVES = List.of(new Move(0, -1, Dir.N), new Move(1, -1, Dir.NE),
            new Move(1, 0, Dir.E), new Move(1, 1, Dir.SE), new Move(0, 1, Dir.S),
            new Move(-1, 1, Dir.SW), new Move(-1, 0, Dir.W), new Move(-1, -1, Dir.NW));

    private static final Comparator<QueueEntry> QUEUE_ORDER =
            Comparator.comparingDouble(QueueEntry::f).thenComparingLong(QueueEntry::seq);

    private RoutePlanner() {
        // Utility class.
    }

    /**
     * Plans a route between two buildable points.
     *
     * @param terrain terrain to read (never modified)
     * @param economy source of real prices and construction delays (never modified)
     * @param start first cell to build (included in the plan)
     * @param end last cell to build (included in the plan)
     * @param policy terrain constraints and optimisation metric
     * @return an immutable plan; infeasible with a human-readable reason when no route exists
     * @throws NullPointerException if any argument is null
     */
    public static RoutePlan plan(GroundMap terrain, EconomyManager economy, Point start, Point end,
            RoutePolicy policy) {
        ValidationUtils.requireNonNull(terrain, "terrain");
        ValidationUtils.requireNonNull(economy, "economy");
        ValidationUtils.requireNonNull(start, "start");
        ValidationUtils.requireNonNull(end, "end");
        ValidationUtils.requireNonNull(policy, "policy");

        String startProblem = endpointProblem(terrain, start, "Start");
        if (startProblem != null) {
            return RoutePlan.infeasible(startProblem);
        }
        String endProblem = endpointProblem(terrain, end, "End");
        if (endProblem != null) {
            return RoutePlan.infeasible(endProblem);
        }

        // A same-cell route has nothing to build: feasible no-op with zero cost/time.
        if (start.equals(end)) {
            return RoutePlan.feasible(List.of(), 0, 0);
        }

        SearchCosts costs = searchCosts(economy, policy);
        if (costs.avoidPolicy()) {
            // First pass: keep every non-endpoint cell at least one cell away from the terrain the
            // policy blocks. Narrow corridors and coastal endpoints fall back to the old search.
            RoutePlan clear = search(terrain, economy, start, end, policy, costs, true);
            if (clear.feasible()) {
                return clear;
            }
        }
        return search(terrain, economy, start, end, policy, costs, false);
    }

    private static SearchCosts searchCosts(EconomyManager economy, RoutePolicy policy) {
        boolean distanceMetric = policy == RoutePolicy.DIRECT;
        boolean avoidPolicy = !distanceMetric && policy != RoutePolicy.CHEAPEST;
        double minCellCost = minCellCost(economy);
        double normalCost = economy.getCost(ExpenseType.CONSTRUCTED_NORMAL_RAIL_TRACK);
        double baseline = Math.max(1.0, normalCost);

        double turnPenalty;
        double clearancePenalty;
        double deviationPenalty;
        if (avoidPolicy) {
            turnPenalty = baseline * AVOID_TURN_FRACTION;
            clearancePenalty = baseline * AVOID_CLEARANCE_FACTOR;
            deviationPenalty = baseline * AVOID_DEVIATION_FRACTION;
        } else {
            // Keep DIRECT/CHEAPEST primary semantics: tiny deterministic tie-breakers only.
            double tieBase = distanceMetric ? 1.0 : Math.max(1.0, minCellCost);
            turnPenalty = tieBase * TIE_TURN_FRACTION;
            clearancePenalty = tieBase * TIE_SHAPE_FRACTION;
            deviationPenalty = tieBase * TIE_SHAPE_FRACTION;
        }
        return new SearchCosts(distanceMetric, avoidPolicy, minCellCost, turnPenalty,
                clearancePenalty, deviationPenalty);
    }

    private static RoutePlan search(GroundMap terrain, EconomyManager economy, Point start,
            Point end, RoutePolicy policy, SearchCosts costs, boolean requireClearance) {
        boolean distanceMetric = costs.distanceMetric();
        double minCellCost = costs.minCellCost();
        double turnPenalty = costs.turnPenalty();
        double clearancePenalty = costs.clearancePenalty();
        double deviationPenalty = costs.deviationPenalty();

        CellKey startKey = CellKey.of(start);
        CellKey endKey = CellKey.of(end);

        Map<SearchState, Double> gScore = new HashMap<>();
        Map<SearchState, SearchState> cameFrom = new HashMap<>();
        Map<CellKey, Integer> terrainByKey = new HashMap<>();
        PriorityQueue<QueueEntry> open = new PriorityQueue<>(QUEUE_ORDER);
        long sequence = 0;

        SearchState startState = new SearchState(startKey, null);
        gScore.put(startState, 0.0);
        terrainByKey.put(startKey, GroundMap.GROUND);
        double startHeuristic = distanceMetric ? octile(startKey, endKey)
                : minCellCost * chebyshev(startKey, endKey);
        open.add(new QueueEntry(startHeuristic, 0.0, sequence++, startState));

        int expanded = 0;
        while (!open.isEmpty()) {
            QueueEntry current = open.poll();
            SearchState currentState = current.state();
            CellKey currentKey = currentState.cell();
            Double bestKnown = gScore.get(currentState);
            if (bestKnown == null || current.g() > bestKnown) {
                continue; // Stale queue entry.
            }
            if (currentKey.equals(endKey)) {
                return buildFeasiblePlan(terrainByKey, cameFrom, currentState, economy);
            }
            if (++expanded > MAX_EXPANDED_NODES) {
                return RoutePlan.infeasible("Route search exceeded " + MAX_EXPANDED_NODES
                        + " expanded cells for policy " + policy + "; no route found");
            }

            for (Move move : MOVES) {
                Dir nextDirection = move.direction();
                if (!isTurnAllowed(currentState.heading(), nextDirection)) {
                    continue; // RailTrackMaker's 45-degree rule.
                }
                int dx = move.dx();
                int dy = move.dy();
                if (dx != 0 && dy != 0 && !diagonalIsAllowed(terrain, policy, currentKey, dx, dy)) {
                    continue;
                }
                int nx = currentKey.x() + dx;
                int ny = currentKey.y() + dy;
                Integer rawTerrain = terrain.getValueAt(nx, ny);
                if (rawTerrain == null) {
                    continue; // No terrain data: cannot build there.
                }
                int normalized = normalize(rawTerrain);
                if (!isBuildable(normalized) || isBlocked(policy, normalized)) {
                    continue;
                }

                CellKey neighbour = new CellKey(nx, ny);
                if (neighbour.equals(startKey)) {
                    continue; // The start has no entry cost.
                }
                int blockedNeighbours = blockedNeighbourCount(terrain, policy, neighbour);
                // Hard clearance: start/end are exempt, every other chosen cell must be at least
                // one cell away from policy-blocked terrain.
                if (requireClearance && !neighbour.equals(endKey) && blockedNeighbours > 0) {
                    continue;
                }
                SearchState neighbourState = new SearchState(neighbour, nextDirection);
                double primary = distanceMetric ? (dx != 0 && dy != 0 ? SQRT_2 : 1.0)
                        : constructionCost(economy, normalized);
                int turn = currentState.heading() != null && currentState.heading() != nextDirection
                        ? 1
                        : 0;
                double deviation = deviation(neighbour, startKey, endKey);
                double tentative = current.g() + primary + turnPenalty * turn
                        + clearancePenalty * blockedNeighbours + deviationPenalty * deviation;
                Double old = gScore.get(neighbourState);
                if (old != null && tentative >= old) {
                    continue;
                }
                gScore.put(neighbourState, tentative);
                cameFrom.put(neighbourState, currentState);
                terrainByKey.put(neighbour, normalized);
                double heuristic = distanceMetric ? octile(neighbour, endKey)
                        : minCellCost * chebyshev(neighbour, endKey);
                open.add(new QueueEntry(tentative + heuristic, tentative, sequence++,
                        neighbourState));
            }
        }

        return RoutePlan.infeasible("No route from " + start + " to " + end + " under policy "
                + policy + " (blocked by terrain, 45-degree curvature or map bounds)");
    }

    private static RoutePlan buildFeasiblePlan(Map<CellKey, Integer> terrainByKey,
            Map<SearchState, SearchState> cameFrom, SearchState endState, EconomyManager economy) {
        List<CellKey> path = new ArrayList<>();
        SearchState cursor = endState;
        path.add(cursor.cell());
        while (cameFrom.containsKey(cursor)) {
            cursor = cameFrom.get(cursor);
            path.add(cursor.cell());
        }
        Collections.reverse(path);

        List<RouteStep> steps = new ArrayList<>(path.size());
        double totalCost = 0;
        long totalTicks = 0;
        for (CellKey key : path) {
            int terrain = terrainByKey.get(key);
            TrackType trackType = trackTypeFor(terrain);
            steps.add(new RouteStep(new Point(key.x(), key.y()), trackType));
            totalCost += constructionCost(economy, terrain);
            totalTicks += Math.max(0, economy.getConstructionDelay(trackType));
        }
        return RoutePlan.feasible(steps, totalCost, totalTicks);
    }

    /**
     * Validates an endpoint: it must exist and normalise to {@code GROUND}. Industries are ground;
     * water and rock are not accepted as endpoints in this spike (a later {@code connect} command
     * may relax it), and unknown values are rejected too.
     */
    private static String endpointProblem(GroundMap terrain, Point point, String label) {
        Integer value = terrain.getValueAt(point);
        if (value == null) {
            return label + " " + point + " has no terrain data";
        }
        int normalized = normalize(value);
        if (normalized == GroundMap.WATER) {
            return label + " " + point
                    + " is water; endpoints must be buildable ground or industry";
        }
        if (normalized == GroundMap.ROCK) {
            return label + " " + point + " is rock; endpoints must be buildable ground or industry";
        }
        if (normalized != GroundMap.GROUND) {
            return label + " " + point + " has unsupported terrain value " + value
                    + "; endpoints must be buildable ground or industry";
        }
        return null;
    }

    /** Industry values 10..29 are ground, matching {@code RailTrackMaker.detectTrackType()}. */
    static int normalize(int terrain) {
        return terrain >= 10 && terrain <= 29 ? GroundMap.GROUND : terrain;
    }

    private static boolean isBuildable(int normalizedTerrain) {
        return normalizedTerrain == GroundMap.GROUND || normalizedTerrain == GroundMap.WATER
                || normalizedTerrain == GroundMap.ROCK;
    }

    /**
     * Rail curvature rule shared with {@code RailTrackMaker}: consecutive headings may differ by at
     * most one 45-degree step. The first step ({@code previous == null}) is always allowed.
     */
    static boolean isTurnAllowed(Dir previous, Dir next) {
        return previous == null || Math.abs(previous.angularDistance(next)) <= 1;
    }

    private static boolean isBlocked(RoutePolicy policy, int normalizedTerrain) {
        return switch (policy) {
            case AVOID_MOUNTAINS -> normalizedTerrain == GroundMap.ROCK;
            case AVOID_WATER -> normalizedTerrain == GroundMap.WATER;
            case AVOID_BOTH ->
                normalizedTerrain == GroundMap.WATER || normalizedTerrain == GroundMap.ROCK;
            case DIRECT, CHEAPEST -> false;
        };
    }

    /**
     * A diagonal move is allowed only when at least one of its two orthogonal neighbours is
     * traversable. If both orthogonals are blocked (no terrain data, non-buildable value, or
     * blocked by the policy), the move would cut a corner between two cells and is rejected. This
     * applies to every policy; for {@code DIRECT}/{@code CHEAPEST} water and rock are traversable,
     * so only missing/unknown terrain blocks them.
     */
    private static boolean diagonalIsAllowed(GroundMap terrain, RoutePolicy policy, CellKey from,
            int dx, int dy) {
        boolean horizontal = isTraversable(terrain, policy, from.x() + dx, from.y());
        boolean vertical = isTraversable(terrain, policy, from.x(), from.y() + dy);
        return horizontal || vertical;
    }

    /** True when a cell exists, is buildable and the current policy does not block it. */
    private static boolean isTraversable(GroundMap terrain, RoutePolicy policy, int x, int y) {
        Integer rawTerrain = terrain.getValueAt(x, y);
        if (rawTerrain == null) {
            return false;
        }
        int normalized = normalize(rawTerrain);
        return isBuildable(normalized) && !isBlocked(policy, normalized);
    }

    /**
     * True when none of the eight neighbours of {@code point} is blocked under the active policy,
     * i.e. the hard-clearance condition for a non-endpoint cell. Package-private so tests can
     * assert the invariant directly.
     */
    static boolean hasClearance(GroundMap terrain, RoutePolicy policy, Point point) {
        return blockedNeighbourCount(terrain, policy, CellKey.of(point)) == 0;
    }

    /**
     * Counts the eight neighbours of {@code cell} that are blocked under the active policy. Missing
     * or non-buildable terrain and the policy's own blocked water/rock all count; buildable water
     * and rock do not count under {@code DIRECT}/{@code CHEAPEST}. Higher means less clearance.
     */
    private static int blockedNeighbourCount(GroundMap terrain, RoutePolicy policy, CellKey cell) {
        int blocked = 0;
        for (Move move : MOVES) {
            if (!isTraversable(terrain, policy, cell.x() + move.dx(), cell.y() + move.dy())) {
                blocked++;
            }
        }
        return blocked;
    }

    /**
     * Euclidean distance from {@code cell} to the straight segment {@code start}-{@code end}. Used
     * as the "stay close to the direct line" shape term.
     */
    private static double deviation(CellKey cell, CellKey start, CellKey end) {
        double vx = end.x() - start.x();
        double vy = end.y() - start.y();
        double lengthSquared = vx * vx + vy * vy;
        if (lengthSquared == 0) {
            return 0;
        }
        double wx = cell.x() - start.x();
        double wy = cell.y() - start.y();
        double t = (wx * vx + wy * vy) / lengthSquared;
        t = Math.max(0, Math.min(1, t));
        double projectedX = start.x() + t * vx;
        double projectedY = start.y() + t * vy;
        return Math.hypot(cell.x() - projectedX, cell.y() - projectedY);
    }

    private static TrackType trackTypeFor(int normalizedTerrain) {
        return switch (normalizedTerrain) {
            case GroundMap.WATER -> TrackType.BRIDGE_TRACK;
            case GroundMap.ROCK -> TrackType.TUNNEL_TRACK;
            case GroundMap.GROUND -> TrackType.NORMAL_TRACK;
            default -> throw new IllegalArgumentException(
                    "Not a buildable terrain value: " + normalizedTerrain);
        };
    }

    private static double constructionCost(EconomyManager economy, int normalizedTerrain) {
        return switch (trackTypeFor(normalizedTerrain)) {
            case NORMAL_TRACK -> economy.getCost(ExpenseType.CONSTRUCTED_NORMAL_RAIL_TRACK);
            case BRIDGE_TRACK -> economy.getCost(ExpenseType.CONSTRUCTED_BRIDGE_RAIL_TRACK);
            case TUNNEL_TRACK -> economy.getCost(ExpenseType.CONSTRUCTED_TUNNEL_RAIL_TRACK);
            default -> throw new IllegalArgumentException(
                    "Unexpected construction track type for terrain " + normalizedTerrain);
        };
    }

    private static double minCellCost(EconomyManager economy) {
        double normal = economy.getCost(ExpenseType.CONSTRUCTED_NORMAL_RAIL_TRACK);
        double bridge = economy.getCost(ExpenseType.CONSTRUCTED_BRIDGE_RAIL_TRACK);
        double tunnel = economy.getCost(ExpenseType.CONSTRUCTED_TUNNEL_RAIL_TRACK);
        return Math.max(0, Math.min(normal, Math.min(bridge, tunnel)));
    }

    private static double chebyshev(CellKey from, CellKey to) {
        return Math.max(Math.abs(from.x() - to.x()), Math.abs(from.y() - to.y()));
    }

    private static double octile(CellKey from, CellKey to) {
        int dx = Math.abs(from.x() - to.x());
        int dy = Math.abs(from.y() - to.y());
        int min = Math.min(dx, dy);
        int max = Math.max(dx, dy);
        return (max - min) + SQRT_2 * min;
    }

    /** Immutable per-plan weights so the clearance pass and its fallback use identical costs. */
    private record SearchCosts(boolean distanceMetric, boolean avoidPolicy, double minCellCost,
            double turnPenalty, double clearancePenalty, double deviationPenalty) {
    }

    /** One of the eight grid steps and the heading it represents. */
    private record Move(int dx, int dy, Dir direction) {
    }

    /** A* state: the cell plus the heading used to reach it ({@code null} only for the start). */
    private record SearchState(CellKey cell, Dir heading) {
    }

    /** Coordinates with value semantics; {@link Point} intentionally has no meaningful hashCode. */
    private record CellKey(int x, int y) {
        static CellKey of(Point point) {
            return new CellKey(point.getX(), point.getY());
        }
    }

    private record QueueEntry(double f, double g, long seq, SearchState state) {
    }
}
