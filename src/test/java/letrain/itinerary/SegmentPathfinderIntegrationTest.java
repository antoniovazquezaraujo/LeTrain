package letrain.itinerary;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
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
    @DisplayName("findPath should find route between connected segments")
    void findPathBetweenSegments() {
        Segment s0 = graph.getSegment(t0);
        Segment sBranch1 = graph.getSegment(t3);
        assertNotNull(s0);
        assertNotNull(sBranch1);

        List<Segment> path = graph.findPath(s0, sBranch1);
        assertFalse(path.isEmpty(), "Should find path from start to branch E");
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
