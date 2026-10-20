package letrain.itinerary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import letrain.map.Point;
import letrain.segments.Port;
import letrain.segments.RailNode;
import letrain.segments.Segment;
import letrain.segments.TransitionType;
import letrain.segments.impl.RailwayGraphImpl;
import letrain.segments.impl.SegmentImpl;
import letrain.track.Track;
import letrain.track.rail.RailTrack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * #816: the A* contract behind the priority-queue rewrite. The heuristic must remain admissible and
 * consistent for the rail-count metric (so the first target popped is optimal), equal-cost ties
 * must be deterministic (FIFO by insertion sequence), and a graph without positions must fall back
 * to a plain Dijkstra-like search that is still optimal.
 */
@DisplayName("A* heuristic and optimality (#816)")
class AStarPathfinderContractTest {

    // ------------------------------------------------------------------
    // Heuristic: admissibility and consistency
    // ------------------------------------------------------------------

    @Test
    @DisplayName("the heuristic never exceeds the real remaining cost and is consistent")
    void should_BeAdmissibleAndConsistentOnAChain() {
        // Arrange: a straight chain whose positions advance exactly by each segment's track count.
        MiniGraph graph = new MiniGraph();
        Junction a = new Junction(0, 0);
        Junction b = new Junction(1, 0);
        Junction c = new Junction(4, 0);
        Junction d = new Junction(6, 0);
        Junction e = new Junction(10, 0);
        Segment ab = graph.segment(a, b, 1);
        Segment bc = graph.segment(b, c, 3);
        Segment cd = graph.segment(c, d, 2);
        Segment de = graph.segment(d, e, 4);
        AStarPathfinder finder = new AStarPathfinder(graph.graph());

        // Act: the heuristic from each segment entered from its start junction.
        int hAb = finder.heuristic(state(ab, graph.portAt(ab, a)), de);
        int hBc = finder.heuristic(state(bc, graph.portAt(bc, b)), de);
        int hCd = finder.heuristic(state(cd, graph.portAt(cd, c)), de);
        int hDe = finder.heuristic(state(de, graph.portAt(de, d)), de);

        // Assert: h(n) <= real remaining cost (sum of the track counts still to enter).
        assertEquals(5, hAb, "chebyshev(0->6) - trackCount(ab)");
        assertEquals(2, hBc, "chebyshev(1->6) - trackCount(bc)");
        assertEquals(0, hCd, "clamped at zero");
        assertEquals(0, hDe, "the target state has no remaining cost");
        assertTrue(hAb <= 3 + 2 + 4, "real remaining after ab");
        assertTrue(hBc <= 2 + 4, "real remaining after bc");
        assertTrue(hCd <= 4, "real remaining after cd");

        // Assert consistency: h(a) <= c(a,b) + h(b) along every link of the chain.
        assertTrue(hAb <= graph.weight(bc) + hBc, "ab -> bc");
        assertTrue(hBc <= graph.weight(cd) + hCd, "bc -> cd");
        assertTrue(hCd <= graph.weight(de) + hDe, "cd -> de");
    }

    @Test
    @DisplayName("the bound is measured from the state's entry port, not a fixed segment end")
    void should_UseTheEntryPortForTheChebyshevBound() {
        // Arrange: the current segment spans (0,0)-(3,0); the target endpoints are (6,0) and
        // (10,0).
        MiniGraph graph = new MiniGraph();
        Junction a = new Junction(0, 0);
        Junction b = new Junction(3, 0);
        Junction targetStart = new Junction(6, 0);
        Junction targetEnd = new Junction(10, 0);
        Segment current = graph.segment(a, b, 3);
        Segment target = graph.segment(targetStart, targetEnd, 2);
        AStarPathfinder finder = new AStarPathfinder(graph.graph());

        // Act
        int fromA = finder.heuristic(state(current, graph.portAt(current, a)), target);
        int fromB = finder.heuristic(state(current, graph.portAt(current, b)), target);

        // Assert: entering from the near end gives a tighter (still admissible) bound.
        assertEquals(3, fromA, "chebyshev((0,0)->(6,0)) - 3");
        assertEquals(0, fromB, "chebyshev((3,0)->(6,0)) - 3");
    }

    @Test
    @DisplayName("without positions/nodes the heuristic falls back to zero (Dijkstra)")
    void should_FallBackToZeroWithoutPositions() {
        MiniGraph graph = new MiniGraph();
        Junction a = new Junction(null, null);
        Junction b = new Junction(null, null);
        Junction c = new Junction(null, null);
        Segment from = graph.segment(a, b, 4);
        Segment target = graph.segment(b, c, 2);
        AStarPathfinder finder = new AStarPathfinder(graph.graph());

        assertEquals(0, finder.heuristic(state(from, graph.portAt(from, a)), target));
    }

    // ------------------------------------------------------------------
    // Optimality against brute force
    // ------------------------------------------------------------------

    @Test
    @DisplayName("A* matches a brute-force simple-path search on deterministic seeded graphs")
    void should_MatchBruteForceOnSeededGraphs() {
        for (int seed = 1; seed <= 5; seed++) {
            MiniGraph graph = seededGrid(seed);
            AStarPathfinder finder = new AStarPathfinder(graph.graph());
            List<Segment> segments = graph.segments();
            Random pairs = new Random(seed * 1000L);

            for (int pair = 0; pair < 8; pair++) {
                Segment from = segments.get(pairs.nextInt(segments.size()));
                Segment to = segments.get(pairs.nextInt(segments.size()));
                int optimal = bruteForce(graph, from, to);
                String label = "seed " + seed + " pair " + pair;

                List<Segment> path = finder.find(from, to, Optional.empty());

                assertFalse(optimal == Integer.MAX_VALUE, label + " starts disconnected");
                assertEquals(optimal, pathCost(graph, path),
                        label + " must return a minimum-cost path: " + ids(path));
            }
        }
    }

    @Test
    @DisplayName("without positions the Dijkstra fallback is still optimal")
    void should_StayOptimalWithoutPositions() {
        // Arrange: no positions at all, so every heuristic is 0.
        MiniGraph graph = new MiniGraph();
        Junction start = new Junction(null, null);
        Junction split = new Junction(null, null);
        Junction cheap = new Junction(null, null);
        Junction expensive = new Junction(null, null);
        Junction cheapEnd = new Junction(null, null);
        Junction expensiveEnd = new Junction(null, null);
        Segment from = graph.segment(start, split, 1);
        Segment cheapSegment = graph.segment(split, cheap, 2);
        Segment expensiveSegment = graph.segment(split, expensive, 9);
        Segment cheapTarget = graph.segment(cheap, cheapEnd, 1);
        Segment expensiveTarget = graph.segment(expensive, expensiveEnd, 1);
        AStarPathfinder finder = new AStarPathfinder(graph.graph());

        // Act
        List<Segment> path = finder.find(from, cheapTarget, Optional.empty());
        List<Segment> longPath = finder.find(from, expensiveTarget, Optional.empty());

        // Assert
        assertEquals(List.of(from, cheapSegment, cheapTarget), path);
        assertEquals(List.of(from, expensiveSegment, expensiveTarget), longPath);
        assertEquals(bruteForce(graph, from, cheapTarget), pathCost(graph, path));
    }

    // ------------------------------------------------------------------
    // Deterministic tie-break (FIFO)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("equal-cost ties are deterministic and resolved FIFO by insertion order")
    void should_BreakEqualCostTiesFifo() {
        // Arrange: a short route (S -> A -> T) and a longer one (S -> B -> C -> T) cost the same.
        MiniGraph graph = new MiniGraph();
        Junction start = new Junction(0, 0);
        Junction split = new Junction(1, 0);
        Junction shortMid = new Junction(2, 0);
        Junction longMid = new Junction(1, 1);
        Junction join = new Junction(2, 1);
        Segment s = graph.segment(start, split, 1);
        Segment a = graph.segment(split, shortMid, 3); // S -> A costs 3
        Segment b = graph.segment(split, longMid, 1); // S -> B -> C costs 1 + 2
        Segment c = graph.segment(longMid, join, 2);
        Segment t = graph.segment(shortMid, join, 1); // target, endpoints (2,0) and (2,1)
        AStarPathfinder finder = new AStarPathfinder(graph.graph());
        assertEquals(graph.weight(a) + graph.weight(t),
                graph.weight(b) + graph.weight(c) + graph.weight(t), "both routes must tie");

        // Act: repeated fresh searches.
        List<String> first = ids(finder.find(s, t, Optional.empty()));
        for (int run = 0; run < 20; run++) {
            assertEquals(first,
                    ids(new AStarPathfinder(graph.graph()).find(s, t, Optional.empty())),
                    "run " + run + " must return the same route");
        }

        // Assert: A was queued before C, so FIFO resolves the equal-f target states to S -> A -> T.
        assertEquals(List.of("S0", "S1", "S4"), first,
                "the shorter route queued first wins the FIFO tie");
        assertEquals(graph.weight(a) + graph.weight(t), pathCost(graph, List.of(s, a, t)));
    }

    // ------------------------------------------------------------------
    // Helpers: a tiny in-memory railway graph with positions and track counts
    // ------------------------------------------------------------------

    private static AStarPathfinder.NodeState state(Segment segment, Port entryPort) {
        return new AStarPathfinder.NodeState(segment, entryPort);
    }

    private static List<String> ids(List<Segment> path) {
        return path.stream().map(Segment::getId).toList();
    }

    private static int pathCost(MiniGraph graph, List<Segment> path) {
        int cost = 0;
        for (int i = 1; i < path.size(); i++) {
            cost += graph.weight(path.get(i));
        }
        return cost;
    }

    private static int bruteForce(MiniGraph graph, Segment from, Segment to) {
        return dfs(graph, from, to, new HashSet<>(), 0);
    }

    private static int dfs(MiniGraph graph, Segment current, Segment to, Set<Segment> visited,
            int cost) {
        if (current.equals(to)) {
            return cost;
        }
        visited.add(current);
        int best = Integer.MAX_VALUE;
        for (Segment neighbour : graph.neighbours(current)) {
            if (visited.contains(neighbour)) {
                continue;
            }
            int candidate = dfs(graph, neighbour, to, visited, cost + graph.weight(neighbour));
            best = Math.min(best, candidate);
        }
        visited.remove(current);
        return best;
    }

    /** Deterministic 3x3 junction grid: a spanning tree plus seeded extra vertical edges. */
    private static MiniGraph seededGrid(int seed) {
        Random random = new Random(seed);
        int size = 3;
        MiniGraph graph = new MiniGraph();
        Junction[][] junctions = new Junction[size][size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                junctions[y][x] = new Junction(x, y);
            }
        }
        // Spanning tree: each row is a horizontal chain, the first column links the rows.
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                int weight = 1 + random.nextInt(5);
                if (x > 0) {
                    graph.segment(junctions[y][x - 1], junctions[y][x], weight);
                } else if (y > 0) {
                    graph.segment(junctions[y - 1][x], junctions[y][x], weight);
                }
            }
        }
        // Extra vertical edges (cycles); deterministic from the same seeded stream.
        for (int y = 0; y + 1 < size; y++) {
            for (int x = 1; x < size; x++) {
                if (random.nextBoolean()) {
                    graph.segment(junctions[y][x], junctions[y + 1][x], 1 + random.nextInt(5));
                }
            }
        }
        return graph;
    }

    private static final class Junction {
        private final RailNode node = mock(RailNode.class);
        private final List<Port> ports = new ArrayList<>();
        private final List<Segment> segments = new ArrayList<>();

        Junction(Integer x, Integer y) {
            when(node.getPorts()).thenReturn(ports);
            when(node.getTransitionType(any(), any())).thenReturn(TransitionType.DIVERGING);
            if (x == null || y == null) {
                when(node.getTrack()).thenReturn(null);
            } else {
                Track track = mock(Track.class);
                when(track.getPosition()).thenReturn(new Point(x, y));
                when(node.getTrack()).thenReturn(track);
            }
        }

        Port port() {
            Port port = mock(Port.class);
            when(port.getNode()).thenReturn(node);
            ports.add(port);
            return port;
        }
    }

    private static final class MiniGraph {
        private final RailwayGraphImpl graph = new RailwayGraphImpl();
        private final Map<Segment, Set<Segment>> neighbours = new HashMap<>();
        private final Map<Segment, Integer> weights = new HashMap<>();
        private final List<Segment> segments = new ArrayList<>();
        private int nextId;

        Segment segment(Junction a, Junction b, int trackCount) {
            Port portA = a.port();
            Port portB = b.port();
            Segment segment = new SegmentImpl("S" + nextId++, portA, portB);
            graph.registerSegment(portA, segment);
            graph.registerSegment(portB, segment);
            for (int i = 0; i < trackCount; i++) {
                graph.registerTrack(segment, new RailTrack());
            }
            weights.put(segment, trackCount);
            segments.add(segment);
            connect(segment, a);
            connect(segment, b);
            return segment;
        }

        private void connect(Segment segment, Junction junction) {
            for (Segment other : junction.segments) {
                neighbours.computeIfAbsent(segment, key -> new HashSet<>()).add(other);
                neighbours.computeIfAbsent(other, key -> new HashSet<>()).add(segment);
            }
            junction.segments.add(segment);
        }

        RailwayGraphImpl graph() {
            return graph;
        }

        List<Segment> segments() {
            return segments;
        }

        int weight(Segment segment) {
            return weights.get(segment);
        }

        Set<Segment> neighbours(Segment segment) {
            return neighbours.getOrDefault(segment, Set.of());
        }

        Port portAt(Segment segment, Junction junction) {
            for (Port port : new Port[] {segment.getPorts().getFirst(),
                    segment.getPorts().getSecond()}) {
                if (port.getNode() == junction.node) {
                    return port;
                }
            }
            throw new IllegalArgumentException("junction is not an endpoint of the segment");
        }
    }
}
