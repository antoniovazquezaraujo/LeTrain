package letrain.vehicle.rail.rail2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.OptionalInt;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.mvp.impl.Model;
import letrain.segments.RailwayGraph;
import letrain.segments.Segment;
import letrain.track.Track;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.TrainSafetyManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #633, step 1: pure walker that counts the rails the physical front can still advance inside
 * its current segment before leaving it (the boundary the train must stop at).
 */
@DisplayName("TrainSafetyManager.railsToBoundary (issue #633, step 1)")
class RailsToBoundaryTest {

    private Model model;

    @BeforeEach
    void setUp() {
        model = new Model(1);
        model.postLoadInit();
    }

    /** Layout: t0(0,0) t1(1,0) t2(2,0) fork(3,0) t4(4,0) t5(5,0). */
    private static final class World {
        RailTrack t0;
        RailTrack t1;
        RailTrack t2;
        RailTrack t4;
        RailTrack t5;
        ForkRailTrack fork;
    }

    private World smallWorld() {
        World world = new World();
        world.t0 = addTrack(0, 0);
        world.t1 = addTrack(1, 0);
        world.t2 = addTrack(2, 0);
        world.fork = addFork(3, 0);
        world.t4 = addTrack(4, 0);
        world.t5 = addTrack(5, 0);
        connect(world.t0, world.t1);
        connect(world.t1, world.t2);
        connect(world.t2, world.fork);
        connect(world.fork, world.t4);
        connect(world.t4, world.t5);
        return world;
    }

    private RailTrack addTrack(int x, int y) {
        RailTrack track = new RailTrack();
        track.setPosition(new Point(x, y));
        track.addRoute(Dir.E, Dir.W);
        track.addRoute(Dir.W, Dir.E);
        model.getRailMap().addTrack(new Point(x, y), track);
        return track;
    }

    private ForkRailTrack addFork(int x, int y) {
        ForkRailTrack fork = new ForkRailTrack(model.nextForkId());
        fork.setPosition(new Point(x, y));
        fork.addRoute(Dir.W, Dir.E);
        fork.addRoute(Dir.E, Dir.W);
        model.getRailMap().addTrack(new Point(x, y), fork);
        model.addFork(fork);
        return fork;
    }

    private void connect(Track from, Track to) {
        from.connect(Dir.E, to);
        to.connect(Dir.W, from);
    }

    private Locomotive placeOn(Track track, Dir entryFrom) {
        Locomotive loco = new Locomotive(1, "L", "RED");
        track.enterLinkerFromDir(entryFrom, loco);
        return loco;
    }

    @Test
    @DisplayName("head in the middle of the segment counts the boundary rail as inside")
    void headInMiddle_countsRailsInsideSegment() {
        World world = smallWorld();
        RailwayGraph graph = model.getRailwayGraph();
        Segment segmentA = graph.getSegment(world.t1);
        assertNotNull(segmentA);

        // t1 -> t2 (inside), -> fork (inside: boundary node shared by both segments), -> t4 (out).
        assertEquals(OptionalInt.of(2),
                TrainSafetyManager.railsToBoundary(placeOn(world.t1, Dir.W), segmentA, graph));
        // From t2 only the fork is inside.
        assertEquals(OptionalInt.of(1),
                TrainSafetyManager.railsToBoundary(placeOn(world.t2, Dir.W), segmentA, graph));
    }

    @Test
    @DisplayName("head on the boundary rail: the next advance already leaves, so 0")
    void headOnBoundaryFork_returnsZero() {
        World world = smallWorld();
        RailwayGraph graph = model.getRailwayGraph();
        Segment segmentA = graph.getSegment(world.t1);

        assertEquals(OptionalInt.of(0),
                TrainSafetyManager.railsToBoundary(placeOn(world.fork, Dir.W), segmentA, graph));
    }

    @Test
    @DisplayName("an explicitly passed segment rules even if the head is physically in another one")
    void explicitSegmentRules() {
        World world = smallWorld();
        RailwayGraph graph = model.getRailwayGraph();
        Segment segmentB = graph.getSegment(world.t4);

        // Head on t1 (segment A); asked for segment B, the next rail (t2) is already outside.
        assertEquals(OptionalInt.of(0),
                TrainSafetyManager.railsToBoundary(placeOn(world.t1, Dir.W), segmentB, graph));
    }

    @Test
    @DisplayName("a dead end before any boundary is unknown (empty)")
    void deadEndAhead_returnsEmpty() {
        World world = smallWorld();
        RailwayGraph graph = model.getRailwayGraph();
        Segment segmentB = graph.getSegment(world.t4);

        assertEquals(OptionalInt.empty(),
                TrainSafetyManager.railsToBoundary(placeOn(world.t5, Dir.W), segmentB, graph));
    }

    @Test
    @DisplayName("a null segment falls back to the head's own segment")
    void nullSegment_fallsBackToHeadSegment() {
        World world = smallWorld();
        RailwayGraph graph = model.getRailwayGraph();

        assertEquals(OptionalInt.of(2),
                TrainSafetyManager.railsToBoundary(placeOn(world.t1, Dir.W), null, graph));
    }

    @Test
    @DisplayName("head not on a rail or missing inputs are unknown (empty)")
    void unknownInputs_returnEmpty() {
        World world = smallWorld();
        RailwayGraph graph = model.getRailwayGraph();
        Segment segmentA = graph.getSegment(world.t1);

        assertTrue(TrainSafetyManager.railsToBoundary(null, segmentA, graph).isEmpty());
        assertTrue(TrainSafetyManager.railsToBoundary(placeOn(world.t1, Dir.W), segmentA, null)
                .isEmpty());

        Locomotive offTrack = new Locomotive(2, "X", "RED");
        offTrack.setTrack(mock(Track.class));
        offTrack.setDir(Dir.E);
        assertTrue(TrainSafetyManager.railsToBoundary(offTrack, segmentA, graph).isEmpty());
    }

    @Test
    @DisplayName("a pure loop with no boundary trips the safety guard and is unknown (empty)")
    void pureLoop_tripsGuard() {
        // Four rails closed in a ring: the walk can never leave, only the guard ends it.
        RailTrack r0 = ringTrack(0, 0);
        RailTrack r1 = ringTrack(1, 0);
        RailTrack r2 = ringTrack(1, -1);
        RailTrack r3 = ringTrack(0, -1);
        r0.addRoute(Dir.W, Dir.E);
        r0.addRoute(Dir.S, Dir.E);
        r1.addRoute(Dir.W, Dir.S);
        r2.addRoute(Dir.N, Dir.W);
        r3.addRoute(Dir.E, Dir.N);
        r0.connect(Dir.E, r1);
        r1.connect(Dir.W, r0);
        r1.connect(Dir.S, r2);
        r2.connect(Dir.N, r1);
        r2.connect(Dir.W, r3);
        r3.connect(Dir.E, r2);
        r3.connect(Dir.N, r0);
        r0.connect(Dir.S, r3);

        RailwayGraph graph = mock(RailwayGraph.class);
        when(graph.containsTrack(any(), any())).thenReturn(true);
        Segment segment = mock(Segment.class);

        assertTrue(
                TrainSafetyManager.railsToBoundary(placeOn(r0, Dir.W), segment, graph).isEmpty());
    }

    private RailTrack ringTrack(int x, int y) {
        RailTrack track = new RailTrack();
        track.setPosition(new Point(x, y));
        return track;
    }
}
