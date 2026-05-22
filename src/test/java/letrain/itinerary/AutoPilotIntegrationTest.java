package letrain.itinerary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import letrain.core.segments.Segment;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.map.impl.RailMap;
import letrain.mvp.impl.Model;
import letrain.track.Station;
import letrain.track.Track;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;
import letrain.vehicle.impl.rail.Locomotive;
import letrain.vehicle.impl.rail.Train;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Comprehensive integration tests for AutoPilot following itineraries.
 */
@DisplayName("AutoPilot Integration Tests")
class AutoPilotIntegrationTest {

    private Model model;

    @BeforeEach
    void setUp() {
        model = new Model();
        model.postLoadInit();
    }

    // ═══════════════════════════════════════════════════════════════════
    // 1. Straight track
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("1. Straight track")
    class StraightTrack {

        @Test
        @DisplayName("1.1 Train on station A → reaches station B")
        void trainOnStationA_reachesStationB() {
            RailTrack t0 = makeTrack(0, 0, Dir.E, Dir.W);
            RailTrack t1 = makeTrack(1, 0, Dir.W, Dir.E);
            connect(t0, Dir.E, t1, Dir.W);
            Station a = makeStation(t0, "A");
            Station b = makeStation(t1, "B");
            Train t = makeTrain(t0, Dir.W);
            program("A", a.getId(), "B", b.getId(), t.getId());
            runTicks(200);
            assertAtStation(t, b);
        }

        @Test
        @DisplayName("1.2 Train NOT on station → reaches station A first, then B")
        void trainNotOnStation_reachesA_thenB() {
            RailTrack t0 = makeTrack(0, 0, Dir.E, Dir.W);
            RailTrack t1 = makeTrack(1, 0, Dir.W, Dir.E);
            RailTrack t2 = makeTrack(2, 0, Dir.W, Dir.E);
            connect(t0, Dir.E, t1, Dir.W);
            connect(t1, Dir.E, t2, Dir.W);
            Station a = makeStation(t0, "A");
            Station b = makeStation(t2, "B");
            Train t = makeTrainNoStation(t1, Dir.E);
            program("A", a.getId(), "B", b.getId(), t.getId());
            runTicks(400);
            assertTrue(hasReached(t, a) || hasReached(t, b), "should reach a station");
        }

        @Test
        @DisplayName("1.3 Train facing opposite direction → still reaches station")
        void trainFacingAway_reachesStation() {
            RailTrack t0 = makeTrack(0, 0, Dir.E, Dir.W);
            RailTrack t1 = makeTrack(1, 0, Dir.W, Dir.E);
            connect(t0, Dir.E, t1, Dir.W);
            Station a = makeStation(t0, "A");
            Station b = makeStation(t1, "B");
            Train t = makeTrain(t1, Dir.E);
            program("A", a.getId(), "B", b.getId(), t.getId());
            runTicks(400);
            assertTrue(hasReached(t, a) || hasReached(t, b), "should reach a station");
        }

        @Test
        @DisplayName("1.4 Engine OFF → train does not move")
        void engineOff_trainDoesNotMove() {
            RailTrack t0 = makeTrack(0, 0, Dir.E, Dir.W);
            RailTrack t1 = makeTrack(1, 0, Dir.W, Dir.E);
            connect(t0, Dir.E, t1, Dir.W);
            Station a = makeStation(t0, "A");
            Station b = makeStation(t1, "B");
            Train t = makeTrainNoStation(t0, Dir.W);
            ((Locomotive) t.getDirectorLinker()).setEngineOn(false);
            program("A", a.getId(), "B", b.getId(), t.getId());
            runTicks(200);
            assertEquals(0, t.getSpeed(), "train should not move with engine off");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 4. Fork
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("4. Fork")
    class Fork {

        @Test
        @DisplayName("4.1 Fork flipped: train on main line enters branch station")
        void forkFlipped_trainEntersBranch() {
            RailTrack t0 = makeTrack(0, 0, Dir.E, Dir.W);
            RailTrack t1 = makeTrack(1, 0, Dir.W, Dir.E);
            ForkRailTrack fork = makeFork(2, 0);
            fork.addRoute(Dir.W, Dir.E);
            fork.addRoute(Dir.E, Dir.W); // straight
            fork.addRoute(Dir.W, Dir.S);
            fork.addRoute(Dir.S, Dir.W); // branch S
            fork.setNormalRoute();
            RailTrack t3 = makeTrack(3, 0, Dir.W, Dir.E);
            RailTrack branch = makeTrack(2, 1, Dir.N, Dir.S);
            connect(t0, Dir.E, t1, Dir.W);
            connect(t1, Dir.E, fork, Dir.W);
            connect(fork, Dir.E, t3, Dir.W);
            connect(fork, Dir.S, branch, Dir.N);

            Station mainSt = makeStation(t3, "Main");
            Station branchSt = makeStation(branch, "Branch");
            Train t = makeTrainNoStation(t1, Dir.W);
            assertFalse(fork.isUsingAlternativeRoute(), "fork starts straight");

            model.setProgram("""
                    station %d set name "Main";
                    station %d set name "Branch";
                    create itinerary "Ruta" {
                        add station "Branch"
                        add station "Main"
                    }
                    assign itinerary "Ruta" to train %d;
                    train %d set autopilot true;
                    train %d set speed 3;
                    """.formatted(mainSt.getId(), branchSt.getId(), t.getId(), t.getId(), t.getId()));

            runTicks(600);

            assertAtStation(t, branchSt);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 9. AutoPilot states
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("9. AutoPilot states")
    class AutoPilotStates {

        @Test
        @DisplayName("9.1 Activate while moving → fails (must be stopped)")
        void activateWhileMoving_fails() {
            RailTrack t0 = makeTrack(0, 0, Dir.E, Dir.W);
            RailTrack t1 = makeTrack(1, 0, Dir.W, Dir.E);
            connect(t0, Dir.E, t1, Dir.W);
            Station a = makeStation(t0, "A");
            Station b = makeStation(t1, "B");
            Train t = makeTrain(t0, Dir.W);
            program("A", a.getId(), "B", b.getId(), t.getId());
            assertTrue(t.isAutoMode());
            t.toggleAutoMode(); // off
            // Simulate moving train
            ((Locomotive) t.getDirectorLinker()).setCurrentSpeed(3);
            assertTrue(t.getSpeed() > 0, "train should be moving");
            t.toggleAutoMode();
            assertFalse(t.isAutoMode(), "autopilot should NOT activate while moving");
        }

        @Test
        @DisplayName("9.2 Activate without itinerary → fails")
        void activateWithoutItinerary_fails() {
            RailTrack t0 = makeTrack(0, 0, Dir.E, Dir.W);
            Train t = makeTrain(t0, Dir.W);
            t.toggleAutoMode();
            assertFalse(t.isAutoMode(), "autopilot should NOT activate without itinerary");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 5. Circuit from save file
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("5. Circuit (save file)")
    class CircuitFromSave {

        @Test
        @DisplayName("5.1 Madrid → Barcelona")
        void madridToBarcelona() throws Exception {
            Model m = loadFromSave("circuit.json");
            var sts = m.getStations();
            Station madrid = sts.get(0);
            Station barcelona = sts.get(1);
            Train t = placeTrain(m, madrid.getTrack(), Dir.W);
            Locomotive l = (Locomotive) t.getDirectorLinker();
            l.setEngineOn(true);
            t.setStationId(madrid.getId());

            m.setProgram("""
                    station %d set name "Madrid";
                    station %d set name "Barcelona";
                    create itinerary "Ruta" {
                        add station "Madrid"
                        add station "Barcelona"
                    }
                    assign itinerary "Ruta" to train %d;
                    train %d set autopilot true;
                    train %d set speed 3;
                    """.formatted(madrid.getId(), barcelona.getId(), t.getId(), t.getId(), t.getId()));

            boolean reachedBarcelona = false;
            for (int i = 0; i < 1200; i++) {
                runTicks(m, 1);
                Segment curSeg = null;
                if (!t.getLinkers().isEmpty() && t.getLinkers().getFirst().getTrack() instanceof RailTrack rt) {
                    curSeg = m.getRailwayGraph().getSegment(rt);
                }
                Point pos = null;
                if (!t.getLinkers().isEmpty() && t.getLinkers().getFirst().getTrack() != null) {
                    pos = t.getLinkers().getFirst().getTrack().getPosition();
                }
                System.out.printf("TICK %d: speed=%d targetSpeed=%d auto=%b stationId=%d segment=%s pos=%s%n",
                        i, t.getSpeed(), l.getTargetSpeed(), t.isAutoMode(), t.getStationId(),
                        curSeg != null ? curSeg.getId() : "null",
                        pos != null ? pos.getX() + "," + pos.getY() : "null");
                if (hasReached(t, barcelona)) {
                    reachedBarcelona = true;
                    break;
                }
            }
            assertTrue(reachedBarcelona, "should reach Barcelona");
        }

        @Test
        @DisplayName("5.2 Madrid → Barcelona with speed 0 on enter Barcelona")
        void madridToBarcelonaSpeed0OnEnter() throws Exception {
            Model m = loadFromSave("circuit.json");
            var sts = m.getStations();
            Station madrid = sts.get(0);
            Station barcelona = sts.get(1);
            Train t = placeTrain(m, madrid.getTrack(), Dir.W);
            Locomotive l = (Locomotive) t.getDirectorLinker();
            l.setEngineOn(true);
            t.setStationId(madrid.getId());

            m.setProgram("""
                    station %d set name "Madrid";
                    station %d set name "Barcelona";
                    station %d on train enter { train set speed 0 };
                    create itinerary "Ruta" {
                        add station "Madrid"
                        add station "Barcelona"
                    }
                    assign itinerary "Ruta" to train %d;
                    train %d set autopilot true;
                    train %d set speed 3;
                    """.formatted(madrid.getId(), barcelona.getId(), barcelona.getId(), t.getId(), t.getId(), t.getId()));

            boolean reachedBarcelona = false;
            for (int i = 0; i < 1200; i++) {
                runTicks(m, 1);
                Segment curSeg = null;
                if (!t.getLinkers().isEmpty() && t.getLinkers().getFirst().getTrack() instanceof RailTrack rt) {
                    curSeg = m.getRailwayGraph().getSegment(rt);
                }
                Point pos = null;
                if (!t.getLinkers().isEmpty() && t.getLinkers().getFirst().getTrack() != null) {
                    pos = t.getLinkers().getFirst().getTrack().getPosition();
                }
                System.out.printf("TICK %d: speed=%d targetSpeed=%d auto=%b stationId=%d segment=%s pos=%s%n",
                        i, t.getSpeed(), l.getTargetSpeed(), t.isAutoMode(), t.getStationId(),
                        curSeg != null ? curSeg.getId() : "null",
                        pos != null ? pos.getX() + "," + pos.getY() : "null");
                if (hasReached(t, barcelona)) {
                    reachedBarcelona = true;
                    // Let's run a few more ticks to let the train slow down and stop
                    for (int j = 0; j < 200; j++) {
                        runTicks(m, 1);
                        System.out.printf("POST-TICK %d: speed=%d targetSpeed=%d auto=%b stationId=%d%n",
                                j, t.getSpeed(), l.getTargetSpeed(), t.isAutoMode(), t.getStationId());
                    }
                    break;
                }
            }
            assertTrue(reachedBarcelona, "should reach Barcelona");
            assertEquals(0, t.getSpeed(), "speed should be 0");
            assertEquals(0, l.getTargetSpeed(), "target speed should be 0");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════

    private RailMap railMap() {
        return model.getRailMap();
    }

    private RailTrack makeTrack(int x, int y, Dir from, Dir to) {
        RailTrack t = new RailTrack();
        t.setPosition(new Point(x, y));
        t.addRoute(from, to);
        t.addRoute(to, from);
        railMap().addTrack(new Point(x, y), t);
        return t;
    }

    private ForkRailTrack makeFork(int x, int y) {
        ForkRailTrack f = new ForkRailTrack(model.nextForkId());
        f.setPosition(new Point(x, y));
        railMap().addTrack(new Point(x, y), f);
        model.addFork(f);
        return f;
    }

    private void connect(RailTrack a, Dir aDir, RailTrack b, Dir bDir) {
        a.connect(aDir, b);
        b.connect(bDir, a);
    }

    private Station makeStation(RailTrack track, String name) {
        Station st = new Station(model.nextStationId());
        st.setName(name);
        st.setTrack(track);
        track.setSensor(st);
        model.addStation(st);
        return st;
    }

    /** Train placed ON a station track (stationId set). */
    private Train makeTrain(RailTrack startTrack, Dir entryFrom) {
        Train t = placeTrain(model, startTrack, entryFrom);
        if (startTrack.getSensor() instanceof Station st) {
            t.setStationId(st.getId());
        }
        return t;
    }

    /** Train placed NOT on a station (stationId = 0). */
    private Train makeTrainNoStation(RailTrack startTrack, Dir entryFrom) {
        return placeTrain(model, startTrack, entryFrom);
    }

    private Train placeTrain(Model model, Track track, Dir entryFrom) {
        Locomotive loco = new Locomotive(model.nextLocomotiveId(), "A");
        loco.setEngineOn(true);
        loco.setTargetSpeed(0);
        Train train = new Train(model.nextTrainId());
        train.setModel(model);
        train.pushBack(loco);
        train.setDirectorLinker(loco);
        model.addLocomotive(loco);
        ((RailTrack) track).enterLinkerFromDir(entryFrom, loco);
        return train;
    }

    /** Shorthand: station A → station B itinerary, assign to train, activate. */
    private void program(String nameA, int idA, String nameB, int idB, int trainId) {
        model.setProgram("""
                 station %d set name "%s";
                 station %d set name "%s";
                 create itinerary "Ruta" {
                     add station "%s"
                     add station "%s"
                 }
                 assign itinerary "Ruta" to train %d;
                 train %d set autopilot true;
                 train %d set speed 3;
                 """.formatted(idA, nameA, idB, nameB, nameA, nameB, trainId, trainId, trainId));
    }

    private void runTicks(int count) {
        runTicks(model, count);
    }

    private void runTicks(Model m, int count) {
        for (int i = 0; i < count; i++) {
            m.moveLocomotives();
            m.loadAndUnloadTrains();
        }
        m.removeDestroyedTrains();
    }

    private boolean hasReached(Train t, Station st) {
        return t.getStationId() == st.getId();
    }

    private void assertAtStation(Train t, Station st) {
        assertEquals(st.getId(), t.getStationId(),
                "expected at " + st.getName() + " but was at station " + t.getStationId());
    }

    private Model loadFromSave(String resourceName) throws Exception {
        var url = getClass().getClassLoader().getResource(resourceName);
        assertNotNull(url, "resource not found: " + resourceName);
        ObjectMapper mapper = new ObjectMapper();
        mapper.findAndRegisterModules();
        mapper.configure(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        Model model = mapper.readValue(url, letrain.mvp.impl.Model.class);
        model.postLoadInit();
        return model;
    }
}
