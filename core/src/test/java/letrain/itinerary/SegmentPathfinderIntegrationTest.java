package letrain.itinerary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.map.impl.RailMap;
import letrain.segments.RailwayGraph;
import letrain.segments.Segment;
import letrain.segments.TopologyService;
import letrain.segments.impl.TopologyServiceImpl;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SegmentPathfinder with real topology (fixed trackToSegment)")
class SegmentPathfinderIntegrationTest {

    private RailwayGraph graph;
    private Segment sBranch1, sBranch2;

    private RailMap railMap;
    private RailTrack t0, t1, t3, t5;
    private ForkRailTrack fork;

    @BeforeEach
    void setUp() {
        railMap = new RailMap();

        t0 = makeTrack(railMap, 0, 0);
        t1 = makeTrack(railMap, 1, 0);
        fork = new ForkRailTrack(1);
        fork.setPosition(new Point(2, 0));
        fork.addRoute(Dir.W, Dir.E);
        fork.addRoute(Dir.W, Dir.SE);
        fork.setNormalRoute();
        railMap.addTrack(new Point(2, 0), fork);
        t3 = makeTrack(railMap, 3, 0);
        RailTrack t4 = makeTrack(railMap, 4, 0);
        t5 = makeTrack(railMap, 3, 1);
        RailTrack t6 = makeTrack(railMap, 4, 2);

        t0.connect(Dir.E, t1);
        t1.connect(Dir.W, t0);
        t1.connect(Dir.E, fork);
        fork.connect(Dir.W, t1);
        fork.connect(Dir.E, t3);
        t3.connect(Dir.W, fork);
        t3.connect(Dir.E, t4);
        t4.connect(Dir.W, t3);
        fork.connect(Dir.SE, t5);
        t5.connect(Dir.NW, fork);
        t5.connect(Dir.SE, t6);
        t6.connect(Dir.NW, t5);

        TopologyService topo = new TopologyServiceImpl();
        graph = topo.discover(railMap);

        sBranch1 = graph.getSegment(t3); // branch E
        sBranch2 = graph.getSegment(t5); // branch SE
    }

    @Test
    @DisplayName("getSegment should work for all tracks including nodes")
    void allTracksFound() {
        assertNotNull(graph.getSegment(t0), "t0 (dead-end)");
        assertNotNull(graph.getSegment(t1), "t1 (intermediate)");
        assertNotNull(graph.getSegment(fork), "fork (node)");
        assertNotNull(graph.getSegment(t3), "t3 (branch)");
    }

    @Test
    @DisplayName("A* finds a route on the real topology (replaces the removed BFS findPath)")
    void aStarFindsTheBranchFromTheRealTopology() {
        // Arrange: the discovered graph connects t0 to the E branch through t1 and the fork.
        Segment from = graph.getSegment(t0);
        Segment to = graph.getSegment(t3);
        assertNotNull(from);
        assertNotNull(to);

        // Act
        List<Segment> path = new AStarPathfinder(graph).find(from, to, Optional.empty());

        // Assert: the route exists and starts/ends on the requested segments.
        assertFalse(path.isEmpty(), "the real graph connects t0 to the t3 branch");
        assertEquals(from, path.get(0));
        assertEquals(to, path.get(path.size() - 1));
    }

    @Test
    @DisplayName("A* returns empty between disconnected components of the real topology")
    void aStarReturnsEmptyForAnIsolatedRealTopology() {
        // Arrange: one RailMap with two disconnected two-track components.
        RailMap disconnected = new RailMap();
        RailTrack a0 = makeTrack(disconnected, 0, 0);
        RailTrack a1 = makeTrack(disconnected, 1, 0);
        a0.connect(Dir.E, a1);
        a1.connect(Dir.W, a0);
        RailTrack b0 = makeTrack(disconnected, 20, 20);
        RailTrack b1 = makeTrack(disconnected, 21, 20);
        b0.connect(Dir.E, b1);
        b1.connect(Dir.W, b0);

        RailwayGraph isolatedGraph = new TopologyServiceImpl().discover(disconnected);
        Segment from = isolatedGraph.getSegment(a0);
        Segment to = isolatedGraph.getSegment(b0);
        assertNotNull(from);
        assertNotNull(to);

        // Act
        List<Segment> path = new AStarPathfinder(isolatedGraph).find(from, to, Optional.empty());

        // Assert: no route across components (the old BFS testFindNonExistentPath equivalent).
        assertTrue(path.isEmpty(), "disconnected components must not produce a route");
    }

    private RailTrack makeTrack(RailMap map, int x, int y) {
        RailTrack t = new RailTrack();
        t.setPosition(new Point(x, y));
        t.addRoute(Dir.E, Dir.W);
        t.addRoute(Dir.W, Dir.E);
        map.addTrack(new Point(x, y), t);
        return t;
    }
}
