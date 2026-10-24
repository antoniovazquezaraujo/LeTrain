package letrain.itinerary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.map.impl.RailMap;
import letrain.mvp.impl.Model;
import letrain.segments.Port;
import letrain.segments.RailNode;
import letrain.segments.RailwayGraph;
import letrain.segments.Segment;
import letrain.track.Station;
import letrain.track.Track;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regression for #830: three parallel segments between the same two forks. The autopilot must
 * orient the fork the train is actually approaching (its exit port), not the first shared node, and
 * must be able to enter and leave the middle loop (station 4) without reversing.
 *
 * <pre>
 *            f1 (0,0)                         f2 (0,4)
 *   S1 (left)  SW  \                     /  NW  (B)
 *                   \                   /
 *   S0 (main)       S ------ 0,1..0,3 ------ N  (trunk)
 *                   /                   \
 *   S2 (right) SE  /                     \  NE  (A)
 * </pre>
 */
@DisplayName("Autopilot fork orientation on a triple parallel loop (#830)")
class ForkLoopOrientationTest {

    private Model model;
    private RailMap railMap;
    private ForkRailTrack f1;
    private ForkRailTrack f2;
    private RailTrack t01;
    private RailTrack t11;
    private RailTrack t21;
    private Station station1;
    private Station station2;
    private Station station3;
    private Station station4;
    private Station station5;
    private Segment s0;
    private Segment s1;
    private Segment s2;

    @BeforeEach
    void setUp() {
        model = new Model();
        model.postLoadInit();
        railMap = model.getRailMap();

        f1 = makeFork(0, 0);
        f1.addRoute(Dir.S, Dir.SW); // original (A) -> S1
        f1.addRoute(Dir.S, Dir.SE); // alternative (B) -> S2
        f1.setNormalRoute();

        f2 = makeFork(0, 4);
        f2.addRoute(Dir.N, Dir.NE); // original (A) -> S2
        f2.addRoute(Dir.N, Dir.NW); // alternative (B) -> S1
        f2.setNormalRoute();

        t01 = makeTrack(0, 1, Dir.N, Dir.S);
        RailTrack t02 = makeTrack(0, 2, Dir.N, Dir.S);
        RailTrack t03 = makeTrack(0, 3, Dir.N, Dir.S);
        t11 = makeTrack(-1, 1, Dir.NE, Dir.S);
        RailTrack t12 = makeTrack(-1, 2, Dir.N, Dir.S);
        RailTrack t13 = makeTrack(-1, 3, Dir.N, Dir.SE);
        t21 = makeTrack(1, 1, Dir.NW, Dir.S);
        RailTrack t22 = makeTrack(1, 2, Dir.N, Dir.S);
        RailTrack t23 = makeTrack(1, 3, Dir.N, Dir.SW);

        connect(f1, Dir.S, t01, Dir.N);
        connect(t01, Dir.S, t02, Dir.N);
        connect(t02, Dir.S, t03, Dir.N);
        connect(t03, Dir.S, f2, Dir.N);
        connect(f1, Dir.SW, t11, Dir.NE);
        connect(t11, Dir.S, t12, Dir.N);
        connect(t12, Dir.S, t13, Dir.N);
        connect(t13, Dir.SE, f2, Dir.NW);
        connect(f1, Dir.SE, t21, Dir.NW);
        connect(t21, Dir.S, t22, Dir.N);
        connect(t22, Dir.S, t23, Dir.N);
        connect(t23, Dir.SW, f2, Dir.NE);

        station1 = makeStation(t01, "1");
        station3 = makeStation(t02, "3");
        station2 = makeStation(t03, "2");
        station4 = makeStation(t12, "4");
        station5 = makeStation(t22, "5");

        RailwayGraph graph = model.getRailwayGraph();
        s0 = graph.getSegment(t02);
        s1 = graph.getSegment(t12);
        s2 = graph.getSegment(t22);
        assertNotNull(s0);
        assertNotNull(s1);
        assertNotNull(s2);
        assertEquals(3, java.util.Set.of(s0, s1, s2).size(), "three distinct parallel segments");
    }

    @Test
    @DisplayName("ensureForkRoute orients the exit fork, not the first shared node")
    void ensureForkRoute_isDirectionAware() {
        Train train = placeTrain(t01, Dir.N);
        AutoPilot autopilot = train.getAutopilot();

        // Going to station 4 means entering S1 at f2, which is f2's alternative branch.
        autopilot.ensureForkRoute(s0, s1);
        assertFalse(f1.isUsingAlternativeRoute(), "f1 is behind the train and must stay normal");
        assertTrue(f2.isUsingAlternativeRoute(), "f2 must select the S1 branch (alternative)");
        RailNode f2Node = nodeOf(s0, f2);
        assertNotNull(f2Node);
        assertTrue(f2Node.isRouteActive(portOf(s0, f2), portOf(s1, f2)),
                "the S0 -> S1 branch at f2 must be active");

        // Going to station 5 means entering S2 at f2, which is f2's normal branch.
        f2.setAlternativeRoute();
        autopilot.ensureForkRoute(s0, s2);
        assertFalse(f1.isUsingAlternativeRoute(), "f1 must stay normal");
        assertFalse(f2.isUsingAlternativeRoute(), "f2 must select the S2 branch (normal)");
        assertTrue(f2Node.isRouteActive(portOf(s0, f2), portOf(s2, f2)),
                "the S0 -> S2 branch at f2 must be active");
    }

    @Test
    @DisplayName("itinerary 1-2-4-1-2-5 passes station 4 and continues without reverse")
    void itinerary_passesStation4_andContinues() {
        Train train = placeTrain(t01, Dir.N);
        train.setStationId(station1.getId());
        model.setProgram("""
                create itinerary "Loop" {
                    add station %d
                    add station %d
                    add station %d
                    add station %d
                    add station %d
                    add station %d
                }
                assign itinerary "Loop" to train %d;
                train %d set autopilot true;
                train %d set speed 3;
                """.formatted(station1.getId(), station2.getId(), station4.getId(),
                station1.getId(), station2.getId(), station5.getId(), train.getId(), train.getId(),
                train.getId()));

        List<Integer> visited = new ArrayList<>();
        int last = train.getStationId();
        if (last != 0) {
            visited.add(last);
        }
        for (int tick = 0; tick < 6000; tick++) {
            model.getScheduler().tick();
            model.moveLocomotives();
            model.loadAndUnloadTrains();
            if (train.getStationId() != last) {
                last = train.getStationId();
                if (last != 0) {
                    visited.add(last);
                }
            }
            if (visited.contains(station5.getId())) {
                break;
            }
        }
        model.removeDestroyedTrains();

        List<Integer> expected = List.of(station1.getId(), station2.getId(), station4.getId(),
                station1.getId(), station2.getId(), station5.getId());
        assertTrue(isSubsequence(visited, expected), "expected waypoints " + expected
                + " (station 3 is passed en route), visited=" + visited);
        assertFalse(train.getDirectorLinker().isReversed(),
                "the loop is closed: no reverse is needed");
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private RailTrack makeTrack(int x, int y, Dir from, Dir to) {
        RailTrack track = new RailTrack();
        track.setPosition(new Point(x, y));
        track.addRoute(from, to);
        track.addRoute(to, from);
        model.addTrack(new Point(x, y), track);
        return track;
    }

    private ForkRailTrack makeFork(int x, int y) {
        ForkRailTrack fork = new ForkRailTrack(model.nextForkId());
        fork.setPosition(new Point(x, y));
        model.addTrack(new Point(x, y), fork);
        return fork;
    }

    private void connect(RailTrack a, Dir aDir, RailTrack b, Dir bDir) {
        a.connect(aDir, b);
        b.connect(bDir, a);
    }

    private Station makeStation(RailTrack track, String name) {
        Station station = new Station(model.nextStationId());
        station.setName(name);
        station.setTrack(track);
        track.setComponent(station);
        model.addStation(station);
        return station;
    }

    private Train placeTrain(RailTrack startTrack, Dir entryFrom) {
        Locomotive loco = new Locomotive(model.nextLocomotiveId(), "A");
        loco.setEngineOn(true);
        loco.setTargetSpeed(0);
        Train train = new Train(model.nextTrainId());
        train.setModel(model);
        train.pushBack(loco);
        train.setDirectorLinker(loco);
        model.addLocomotive(loco);
        startTrack.enterLinkerFromDir(entryFrom, loco);
        return train;
    }


    /** True when {@code expected} appears in {@code actual} in order (extra stations allowed). */
    private static boolean isSubsequence(List<Integer> actual, List<Integer> expected) {
        int i = 0;
        for (int value : actual) {
            if (i < expected.size() && value == expected.get(i)) {
                i++;
            }
        }
        return i == expected.size();
    }

    private static Port portOf(Segment segment, Track atTrack) {
        for (Port port : new Port[] {segment.getPorts().getFirst(),
                segment.getPorts().getSecond()}) {
            if (port != null && port.getNode().getTrack() == atTrack) {
                return port;
            }
        }
        return null;
    }

    private static RailNode nodeOf(Segment segment, Track atTrack) {
        Port port = portOf(segment, atTrack);
        return port == null ? null : port.getNode();
    }
}
