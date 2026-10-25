package letrain.planner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
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
 * <b>Grid and metric.</b> A* over the 8-direction grid. {@link RoutePolicy#DIRECT} and the three
 * avoid policies optimise geometric distance (orthogonal 1, diagonal {@code sqrt(2)}) after
 * blocking their named terrain; only {@link RoutePolicy#CHEAPEST} optimises the real per-cell
 * construction cost. Every returned plan reports the real construction cost/time of its cells
 * regardless of the optimisation metric.
 *
 * <p>
 * <b>Curvature.</b> The search state is a cell <em>plus the heading used to arrive at it</em>. The
 * first step may take any of the eight directions; every following step must turn at most 45
 * degrees, i.e. {@code Math.abs(previous.angularDistance(next)) <= 1}. This is the same rule
 * {@code RailTrackMaker} enforces when placing track, so a plan never asks for an unbuildable
 * 90/135/180-degree bend. A diagonal only rejects corners whose two orthogonals are non-buildable;
 * water/rock orthogonals may be crossed diagonally as long as the target is a buildable,
 * policy-allowed cell.
 *
 * <p>
 * <b>Comfort preferences.</b> Search cost is the primary metric plus policy-specific comfort terms.
 * They are never added to the reported cost/time.
 *
 * <p>
 * For the avoid policies ({@code AVOID_MOUNTAINS}, {@code AVOID_WATER}, {@code AVOID_ALL}) the
 * comfort terms are expressed in <b>distance units</b>, so they refine near-equal routes without
 * overwhelming the geometric metric:
 *
 * <ul>
 * <li>{@code 0.25} per 45-degree turn,
 * <li>{@code 0.5} per blocked neighbour (clearance),
 * <li>{@code 0.05} per cell of Euclidean deviation from the straight segment.
 * </ul>
 *
 * Allowed water/rock is never made expensive by the avoid optimisation: under
 * {@code AVOID_MOUNTAINS} a bridge may still be the geometric shortcut, and under
 * {@code AVOID_WATER} a tunnel may be. The reported cost/time then includes those real prices.
 *
 * <p>
 * <b>Hard clearance pass.</b> Avoid policies first run the search with a hard constraint: every
 * chosen cell except the start and end must have zero blocked neighbours under the active policy,
 * keeping the track at least one cell away from the <em>named blocked</em> terrain (rock for
 * {@code AVOID_MOUNTAINS}, water for {@code AVOID_WATER}, every avoidable terrain for
 * {@code AVOID_ALL}). Allowed terrain gets no hard clearance. If that pass is infeasible (narrow
 * corridor, enclosed endpoint, map edge) the same search runs again without the hard constraint and
 * its best plan is returned. The comfort terms still apply in both passes and are search-only;
 * reported cost/time are always the real cells. {@code DIRECT} and {@code CHEAPEST} never use the
 * hard clearance pass.
 *
 * <p>
 * {@code DIRECT} keeps tiny deterministic distance tie-breakers; see {@link #TIE_TURN_FRACTION} and
 * {@link #TIE_SHAPE_FRACTION}.
 *
 * <p>
 * <b>CHEAPEST ordering.</b> {@code CHEAPEST} uses a true lexicographic A* cost, in this order: (1)
 * real construction cost, (2) obstacle-adjacent land cells (water or rock neighbours; bridge and
 * tunnel cells themselves are exempt), (3) 45-degree turns, (4) deviation from the straight
 * start-end segment. So an equal-cost route avoids water shores and reentrant rock nooks, then
 * prefers fewer bends and a straighter line. Shape keys are never allowed to beat a lower
 * construction cost of any size, and the reported cost/time remain the real cells' values.
 *
 * <p>
 * <b>Clearance.</b> A cell's clearance penalty is the number of its eight neighbours that are
 * blocked <em>under the active policy</em>: missing/non-buildable terrain plus the policy's own
 * block (water under {@code AVOID_WATER}, rock under {@code AVOID_MOUNTAINS}, all avoidable terrain
 * under {@code AVOID_ALL}). Under {@code DIRECT} and {@code CHEAPEST} buildable water and rock are
 * not blocked, so this policy clearance does not apply; {@code CHEAPEST}'s separate
 * obstacle-adjacency key is the only shape penalty on them.
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

    /** Tiny turn tie-breaker for {@code DIRECT}, as a fraction of the unit distance step. */
    static final double TIE_TURN_FRACTION = 1e-3;

    /** Tiny shape tie-breaker for {@code DIRECT}, as a fraction of the unit distance step. */
    static final double TIE_SHAPE_FRACTION = 1e-5;

    /** Avoid-policy comfort: search cost per 45-degree turn, in distance units. */
    static final double AVOID_TURN_FRACTION = 0.25;

    /** Avoid-policy comfort: search cost per blocked neighbour, per cell, in distance units. */
    static final double AVOID_CLEARANCE_FACTOR = 0.5;

    /** Avoid-policy comfort: search cost per cell of Euclidean deviation, in distance units. */
    static final double AVOID_DEVIATION_FRACTION = 0.05;

    /**
     * Safety cap for pathological searches over the procedural (unbounded) terrain. Reaching it
     * returns an infeasible plan instead of looping forever. Large enough not to affect authoring
     * distances, small enough to bound worst-case latency.
     */
    static final int MAX_EXPANDED_NODES = 1_000_000;

    /**
     * Progress reports are emitted at phase start, every this many expanded nodes and at the end.
     * Reporting per expansion would flood the UI and defeat the purpose of an activity signal.
     */
    static final int PROGRESS_INTERVAL_EXPANSIONS = 2_048;

    /**
     * Cancellation is polled every this many expanded nodes so a long search stops promptly without
     * paying the cost of a volatile read on every iteration.
     */
    static final int CANCELLATION_CHECK_INTERVAL_EXPANSIONS = 512;

    /**
     * Fixed neighbour order (N, NE, E, SE, S, SW, W, NW). Together with the insertion-sequence
     * tie-break it makes equal-cost routes reproducible across runs and JVMs.
     */
    private static final List<Move> MOVES = List.of(new Move(0, -1, Dir.N), new Move(1, -1, Dir.NE),
            new Move(1, 0, Dir.E), new Move(1, 1, Dir.SE), new Move(0, 1, Dir.S),
            new Move(-1, 1, Dir.SW), new Move(-1, 0, Dir.W), new Move(-1, -1, Dir.NW));

    private static final Comparator<QueueEntry> QUEUE_ORDER =
            Comparator.comparingDouble(QueueEntry::f).thenComparingLong(QueueEntry::seq);

    private static final Comparator<LexQueueEntry> LEX_QUEUE_ORDER =
            Comparator.comparing(LexQueueEntry::f).thenComparingLong(LexQueueEntry::seq);

    private RoutePlanner() {
        // Utility class.
    }

    /**
     * Plans a route between two buildable points without progress or cancellation. Backwards
     * compatible overload used by synchronous callers and tests.
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
        return plan(terrain, economy, start, end, policy, RoutePlanningListener.NO_OP, () -> false);
    }

    /**
     * Plans a route between two buildable points with throttled progress and cooperative
     * cancellation.
     *
     * <p>
     * The listener is called at phase start, every {@value #PROGRESS_INTERVAL_EXPANSIONS} expanded
     * nodes and once at the end. Cancellation is checked at start and every
     * {@value #CANCELLATION_CHECK_INTERVAL_EXPANSIONS} expansions. When cancellation is observed
     * the planner throws {@link CancellationException} instead of returning a partial plan; callers
     * such as {@link RoutePlanningJob} translate that into their own cancelled state.
     *
     * @param terrain terrain to read (never modified)
     * @param economy source of real prices and construction delays (never modified)
     * @param start first cell to build (included in the plan)
     * @param end last cell to build (included in the plan)
     * @param policy terrain constraints and optimisation metric
     * @param listener progress consumer (may be {@link RoutePlanningListener#NO_OP})
     * @param cancelled cancellation signal; returns {@code true} when the plan must stop
     * @return an immutable plan; infeasible with a human-readable reason when no route exists
     * @throws NullPointerException if any argument is null
     * @throws CancellationException if {@code cancelled} becomes {@code true} or the worker thread
     *         is interrupted
     */
    public static RoutePlan plan(GroundMap terrain, EconomyManager economy, Point start, Point end,
            RoutePolicy policy, RoutePlanningListener listener, BooleanSupplier cancelled) {
        ValidationUtils.requireNonNull(terrain, "terrain");
        ValidationUtils.requireNonNull(economy, "economy");
        ValidationUtils.requireNonNull(start, "start");
        ValidationUtils.requireNonNull(end, "end");
        ValidationUtils.requireNonNull(policy, "policy");
        ValidationUtils.requireNonNull(listener, "listener");
        ValidationUtils.requireNonNull(cancelled, "cancelled");

        Progress progress = new Progress(listener, cancelled);
        progress.checkCancelled();

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
            progress.enterPhase(RoutePlanningProgress.Phase.FALLBACK, start);
            progress.finish(RoutePlanningProgress.Phase.FALLBACK, 0, end);
            return RoutePlan.feasible(List.of(), 0, 0);
        }

        if (policy == RoutePolicy.CHEAPEST) {
            return searchCheapest(terrain, economy, start, end);
        }

        SearchCosts costs = searchCosts(economy, policy);
        if (costs.avoidPolicy()) {
            // First pass: keep every non-endpoint cell at least one cell away from the terrain the
            // policy blocks. Narrow corridors and coastal endpoints fall back to the old search.
            RoutePlan clear = search(terrain, economy, start, end, policy, costs, true,
                    RoutePlanningProgress.Phase.CLEARANCE, progress);
            if (clear.feasible()) {
                return clear;
            }
        }
        return search(terrain, economy, start, end, policy, costs, false,
                RoutePlanningProgress.Phase.FALLBACK, progress);
    }

    private static SearchCosts searchCosts(EconomyManager economy, RoutePolicy policy) {
        boolean avoidPolicy = policy != RoutePolicy.DIRECT;
        // Both DIRECT and avoid policies minimise geometric distance; only CHEAPEST uses the
        // lexicographic construction-cost search below.
        boolean distanceMetric = true;
        double minCellCost = minCellCost(economy);

        double turnPenalty;
        double clearancePenalty;
        double deviationPenalty;
        if (avoidPolicy) {
            // Comfort weights are in distance units so they cannot overwhelm geometric distance.
            turnPenalty = AVOID_TURN_FRACTION;
            clearancePenalty = AVOID_CLEARANCE_FACTOR;
            deviationPenalty = AVOID_DEVIATION_FRACTION;
        } else {
            // DIRECT: tiny deterministic tie-breakers relative to the unit distance step.
            turnPenalty = TIE_TURN_FRACTION;
            clearancePenalty = TIE_SHAPE_FRACTION;
            deviationPenalty = TIE_SHAPE_FRACTION;
        }
        return new SearchCosts(distanceMetric, avoidPolicy, minCellCost, turnPenalty,
                clearancePenalty, deviationPenalty);
    }

    private static RoutePlan search(GroundMap terrain, EconomyManager economy, Point start,
            Point end, RoutePolicy policy, SearchCosts costs, boolean requireClearance,
            RoutePlanningProgress.Phase phase, Progress progress) {
        progress.enterPhase(phase, start);
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
                progress.finish(phase, expanded, end);
                return buildFeasiblePlan(terrainByKey, cameFrom, currentState, economy);
            }
            expanded++;
            if (expanded > MAX_EXPANDED_NODES) {
                progress.finish(phase, expanded - 1, new Point(currentKey.x(), currentKey.y()));
                return RoutePlan.infeasible("Route search exceeded " + MAX_EXPANDED_NODES
                        + " expanded cells for policy " + policy + "; no route found");
            }
            if (expanded % PROGRESS_INTERVAL_EXPANSIONS == 0) {
                progress.report(phase, expanded, currentKey);
            }
            if (expanded % CANCELLATION_CHECK_INTERVAL_EXPANSIONS == 0) {
                progress.checkCancelled();
            }

            for (Move move : MOVES) {
                Dir nextDirection = move.direction();
                if (!isTurnAllowed(currentState.heading(), nextDirection)) {
                    continue; // RailTrackMaker's 45-degree rule.
                }
                int dx = move.dx();
                int dy = move.dy();
                if (dx != 0 && dy != 0 && !diagonalIsAllowed(terrain, currentKey, dx, dy)) {
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

        progress.finish(phase, expanded, null);
        return RoutePlan.infeasible("No route from " + start + " to " + end + " under policy "
                + policy + " (blocked by terrain, 45-degree curvature or map bounds)");
    }

    /**
     * {@code CHEAPEST} search with a true lexicographic cost: construction cost first, then
     * obstacle-adjacent land cells, then 45-degree turns, then deviation from the straight segment.
     * The heuristic only estimates construction cost, so it stays admissible and consistent; the
     * secondary keys are non-negative and additive. The first goal popped is therefore optimal in
     * that order.
     */
    private static RoutePlan searchCheapest(GroundMap terrain, EconomyManager economy, Point start,
            Point end) {
        double minCellCost = minCellCost(economy);
        CellKey startKey = CellKey.of(start);
        CellKey endKey = CellKey.of(end);

        Map<SearchState, PathCost> gScore = new HashMap<>();
        Map<SearchState, SearchState> cameFrom = new HashMap<>();
        Map<CellKey, Integer> terrainByKey = new HashMap<>();
        PriorityQueue<LexQueueEntry> open = new PriorityQueue<>(LEX_QUEUE_ORDER);
        long sequence = 0;

        SearchState startState = new SearchState(startKey, null);
        PathCost startCost = new PathCost(0, 0, 0, 0);
        gScore.put(startState, startCost);
        terrainByKey.put(startKey, GroundMap.GROUND);
        double startHeuristic = minCellCost * chebyshev(startKey, endKey);
        open.add(new LexQueueEntry(startCost.withPrimaryAdded(startHeuristic), startCost,
                sequence++, startState));

        int expanded = 0;
        while (!open.isEmpty()) {
            LexQueueEntry current = open.poll();
            SearchState currentState = current.state();
            CellKey currentKey = currentState.cell();
            PathCost bestKnown = gScore.get(currentState);
            if (bestKnown == null || current.g().compareTo(bestKnown) > 0) {
                continue; // Stale queue entry.
            }
            if (currentKey.equals(endKey)) {
                return buildFeasiblePlan(terrainByKey, cameFrom, currentState, economy);
            }
            if (++expanded > MAX_EXPANDED_NODES) {
                return RoutePlan.infeasible("Route search exceeded " + MAX_EXPANDED_NODES
                        + " expanded cells for policy CHEAPEST; no route found");
            }

            for (Move move : MOVES) {
                Dir nextDirection = move.direction();
                if (!isTurnAllowed(currentState.heading(), nextDirection)) {
                    continue;
                }
                int dx = move.dx();
                int dy = move.dy();
                if (dx != 0 && dy != 0 && !diagonalIsAllowed(terrain, currentKey, dx, dy)) {
                    continue;
                }
                int nx = currentKey.x() + dx;
                int ny = currentKey.y() + dy;
                Integer rawTerrain = terrain.getValueAt(nx, ny);
                if (rawTerrain == null) {
                    continue;
                }
                int normalized = normalize(rawTerrain);
                if (!isBuildable(normalized)) {
                    continue;
                }

                CellKey neighbour = new CellKey(nx, ny);
                if (neighbour.equals(startKey)) {
                    continue;
                }
                SearchState neighbourState = new SearchState(neighbour, nextDirection);
                int turn = currentState.heading() != null && currentState.heading() != nextDirection
                        ? 1
                        : 0;
                double primary = constructionCost(economy, normalized);
                double deviation = deviation(neighbour, startKey, endKey);
                int obstacleNeighbours =
                        normalized == GroundMap.WATER || normalized == GroundMap.ROCK ? 0
                                : obstacleNeighbourCount(terrain, neighbour);
                PathCost tentative = current.g().add(primary, obstacleNeighbours, turn, deviation);
                PathCost old = gScore.get(neighbourState);
                if (old != null && tentative.compareTo(old) >= 0) {
                    continue;
                }
                gScore.put(neighbourState, tentative);
                cameFrom.put(neighbourState, currentState);
                terrainByKey.put(neighbour, normalized);
                double heuristic = minCellCost * chebyshev(neighbour, endKey);
                open.add(new LexQueueEntry(tentative.withPrimaryAdded(heuristic), tentative,
                        sequence++, neighbourState));
            }
        }

        return RoutePlan.infeasible("No route from " + start + " to " + end + " under policy "
                + "CHEAPEST (blocked by terrain, 45-degree curvature or map bounds)");
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
            case AVOID_ALL ->
                normalizedTerrain == GroundMap.WATER || normalizedTerrain == GroundMap.ROCK;
            case DIRECT, CHEAPEST -> false;
        };
    }

    /**
     * A diagonal move is allowed only when at least one of its two orthogonal neighbours is
     * <b>buildable</b>. Missing or unsupported orthogonals reject the move (it would cut a corner
     * through non-existent ground), but water/rock orthogonals do not: a diagonal land-to-land move
     * may cross their corner even under a policy that blocks that terrain. The diagonal target is
     * still validated by {@code isBuildable} + {@code isBlocked(policy)} by the caller, so a route
     * never steps onto the forbidden terrain itself.
     */
    private static boolean diagonalIsAllowed(GroundMap terrain, CellKey from, int dx, int dy) {
        boolean horizontal = isBuildableCell(terrain, from.x() + dx, from.y());
        boolean vertical = isBuildableCell(terrain, from.x(), from.y() + dy);
        return horizontal || vertical;
    }

    /** True when a cell exists and its terrain value is buildable (ground, water or rock). */
    private static boolean isBuildableCell(GroundMap terrain, int x, int y) {
        Integer rawTerrain = terrain.getValueAt(x, y);
        return rawTerrain != null && isBuildable(normalize(rawTerrain));
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

    /** Package-private test hook for the {@code CHEAPEST} obstacle-adjacency key. */
    static int obstacleNeighboursAt(GroundMap terrain, Point point) {
        return obstacleNeighbourCount(terrain, CellKey.of(point));
    }

    /** Package-private test hook for asserting water clearance independently of rock. */
    static int waterNeighboursAt(GroundMap terrain, Point point) {
        int water = 0;
        for (Move move : MOVES) {
            Integer rawTerrain =
                    terrain.getValueAt(point.getX() + move.dx(), point.getY() + move.dy());
            if (rawTerrain != null && normalize(rawTerrain) == GroundMap.WATER) {
                water++;
            }
        }
        return water;
    }

    /**
     * Counts the eight neighbours of a land {@code cell} that are water or rock. Used only by
     * {@code CHEAPEST} as its second lexicographic key; water/rock route cells are exempt at the
     * call site, so crossing a lake or tunnelling is still allowed.
     */
    private static int obstacleNeighbourCount(GroundMap terrain, CellKey cell) {
        int obstacles = 0;
        for (Move move : MOVES) {
            Integer rawTerrain = terrain.getValueAt(cell.x() + move.dx(), cell.y() + move.dy());
            if (rawTerrain != null) {
                int normalized = normalize(rawTerrain);
                if (normalized == GroundMap.WATER || normalized == GroundMap.ROCK) {
                    obstacles++;
                }
            }
        }
        return obstacles;
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

    /**
     * Tiny adapter that owns the start timestamp and the cancellation checks. Keeping it in one
     * place makes progress and cancellation calls uniform across the clearance and fallback passes.
     */
    private static final class Progress {

        private final RoutePlanningListener listener;
        private final BooleanSupplier cancelled;
        private final long startedNanos;

        private Progress(RoutePlanningListener listener, BooleanSupplier cancelled) {
            this.listener = listener;
            this.cancelled = cancelled;
            this.startedNanos = System.nanoTime();
        }

        private void enterPhase(RoutePlanningProgress.Phase phase, Point current) {
            checkCancelled();
            emit(phase, 0, current);
        }

        private void report(RoutePlanningProgress.Phase phase, int expandedNodes, CellKey current) {
            emit(phase, expandedNodes,
                    current == null ? null : new Point(current.x(), current.y()));
        }

        private void finish(RoutePlanningProgress.Phase phase, int expandedNodes, Point current) {
            emit(phase, expandedNodes, current);
        }

        private void checkCancelled() {
            if (cancelled.getAsBoolean() || Thread.currentThread().isInterrupted()) {
                throw new CancellationException("Route planning cancelled");
            }
        }

        private void emit(RoutePlanningProgress.Phase phase, int expandedNodes, Point current) {
            listener.onProgress(
                    new RoutePlanningProgress(phase, expandedNodes, elapsedMillis(), current));
        }

        private long elapsedMillis() {
            return (System.nanoTime() - startedNanos) / 1_000_000L;
        }
    }

    /** Immutable per-plan weights so the clearance pass and its fallback use identical costs. */
    private record SearchCosts(boolean distanceMetric, boolean avoidPolicy, double minCellCost,
            double turnPenalty, double clearancePenalty, double deviationPenalty) {
    }

    /**
     * Lexicographic cost for {@code CHEAPEST}: construction cost, then obstacle-adjacent land
     * cells, then 45-degree turns, then deviation. All components are additive and non-negative.
     */
    private record PathCost(double constructionCost, int obstacleNeighbours, int turns,
            double deviation) implements Comparable<PathCost> {

        PathCost add(double constructionDelta, int obstacleDelta, int turnDelta,
                double deviationDelta) {
            return new PathCost(constructionCost + constructionDelta,
                    obstacleNeighbours + obstacleDelta, turns + turnDelta,
                    deviation + deviationDelta);
        }

        /** Adds a heuristic estimate to the primary key only, leaving the tie-break keys alone. */
        PathCost withPrimaryAdded(double extra) {
            return new PathCost(constructionCost + extra, obstacleNeighbours, turns, deviation);
        }

        @Override
        public int compareTo(PathCost other) {
            int comparison = Double.compare(constructionCost, other.constructionCost);
            if (comparison != 0) {
                return comparison;
            }
            comparison = Integer.compare(obstacleNeighbours, other.obstacleNeighbours);
            if (comparison != 0) {
                return comparison;
            }
            comparison = Integer.compare(turns, other.turns);
            if (comparison != 0) {
                return comparison;
            }
            return Double.compare(deviation, other.deviation);
        }
    }

    private record LexQueueEntry(PathCost f, PathCost g, long seq, SearchState state) {
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
