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
 * <b>Determinism.</b> Neighbours are expanded in a fixed order and the open set breaks f-score ties
 * by insertion sequence (FIFO), so the same inputs always produce the same route. The heuristic is
 * admissible and consistent for both metrics.
 *
 * <p>
 * <b>Terrain normalisation.</b> Industry values {@code 10..29} are treated as {@code GROUND}, the
 * same normalisation {@code RailTrackMaker.detectTrackType()} uses. Endpoints must be buildable
 * ground/industry; water or rock endpoints are rejected with a reason.
 */
public final class RoutePlanner {

    private static final double SQRT_2 = Math.sqrt(2.0);

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
    private static final int[][] NEIGHBOURS =
            {{0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}};

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

        boolean distanceMetric = policy == RoutePolicy.DIRECT;
        double minCellCost = minCellCost(economy);
        CellKey startKey = CellKey.of(start);
        CellKey endKey = CellKey.of(end);

        Map<CellKey, Double> gScore = new HashMap<>();
        Map<CellKey, CellKey> cameFrom = new HashMap<>();
        Map<CellKey, Integer> terrainByKey = new HashMap<>();
        PriorityQueue<QueueEntry> open = new PriorityQueue<>(QUEUE_ORDER);
        long sequence = 0;

        gScore.put(startKey, 0.0);
        terrainByKey.put(startKey, GroundMap.GROUND);
        double startHeuristic = distanceMetric ? octile(startKey, endKey)
                : minCellCost * chebyshev(startKey, endKey);
        open.add(new QueueEntry(startHeuristic, 0.0, sequence++, startKey));

        int expanded = 0;
        while (!open.isEmpty()) {
            QueueEntry current = open.poll();
            CellKey currentKey = current.key();
            Double bestKnown = gScore.get(currentKey);
            if (bestKnown == null || current.g() > bestKnown) {
                continue; // Stale queue entry.
            }
            if (currentKey.equals(endKey)) {
                return buildFeasiblePlan(terrainByKey, cameFrom, endKey, economy);
            }
            if (++expanded > MAX_EXPANDED_NODES) {
                return RoutePlan.infeasible("Route search exceeded " + MAX_EXPANDED_NODES
                        + " expanded cells for policy " + policy + "; no route found");
            }

            for (int[] direction : NEIGHBOURS) {
                int nx = currentKey.x() + direction[0];
                int ny = currentKey.y() + direction[1];
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
                double moveCost =
                        distanceMetric ? (direction[0] != 0 && direction[1] != 0 ? SQRT_2 : 1.0)
                                : constructionCost(economy, normalized);
                double tentative = current.g() + moveCost;
                Double old = gScore.get(neighbour);
                if (old != null && tentative >= old) {
                    continue;
                }
                gScore.put(neighbour, tentative);
                cameFrom.put(neighbour, currentKey);
                terrainByKey.put(neighbour, normalized);
                double heuristic = distanceMetric ? octile(neighbour, endKey)
                        : minCellCost * chebyshev(neighbour, endKey);
                open.add(new QueueEntry(tentative + heuristic, tentative, sequence++, neighbour));
            }
        }

        return RoutePlan.infeasible("No route from " + start + " to " + end + " under policy "
                + policy + " (blocked by terrain or map bounds)");
    }

    private static RoutePlan buildFeasiblePlan(Map<CellKey, Integer> terrainByKey,
            Map<CellKey, CellKey> cameFrom, CellKey endKey, EconomyManager economy) {
        List<CellKey> path = new ArrayList<>();
        CellKey cursor = endKey;
        path.add(cursor);
        while (cameFrom.containsKey(cursor)) {
            cursor = cameFrom.get(cursor);
            path.add(cursor);
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

    private static boolean isBlocked(RoutePolicy policy, int normalizedTerrain) {
        return switch (policy) {
            case AVOID_MOUNTAINS -> normalizedTerrain == GroundMap.ROCK;
            case AVOID_WATER -> normalizedTerrain == GroundMap.WATER;
            case AVOID_BOTH ->
                normalizedTerrain == GroundMap.WATER || normalizedTerrain == GroundMap.ROCK;
            case DIRECT, CHEAPEST -> false;
        };
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

    /** Coordinates with value semantics; {@link Point} intentionally has no meaningful hashCode. */
    private record CellKey(int x, int y) {
        static CellKey of(Point point) {
            return new CellKey(point.getX(), point.getY());
        }
    }

    private record QueueEntry(double f, double g, long seq, CellKey key) {
    }
}
