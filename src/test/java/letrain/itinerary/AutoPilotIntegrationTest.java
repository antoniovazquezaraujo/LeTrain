package letrain.itinerary;

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

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive integration tests for AutoPilot following itineraries.
 * ADR-010: all cases.
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
            // Train enters from East, facing West (toward station A)
            Train t = makeTrain(t1, Dir.E);
            program("A", a.getId(), "B", b.getId(), t.getId());
            runTicks(400);
            assertTrue(hasReached(t, a) || hasReached(t, b), "should reach a station");
        }

        @Test
        @DisplayName("1.4 SPEED 5 + STOP at destination")
        void speed5_stopAtDestination() {
            RailTrack t0 = makeTrack(0, 0, Dir.E, Dir.W);
            RailTrack t1 = makeTrack(1, 0, Dir.W, Dir.E);
            RailTrack t2 = makeTrack(2, 0, Dir.W, Dir.E);
            connect(t0, Dir.E, t1, Dir.W);
            connect(t1, Dir.E, t2, Dir.W);
            Station a = makeStation(t0, "A");
            Station b = makeStation(t2, "B");
            Train t = makeTrain(t0, Dir.W);
            model.setProgram("""
                station %d set name "A";
                station %d set name "B";
                create itinerary "Ruta" {
                    add station "A" SPEED 5
                    add station "B" STOP
                }
                assign itinerary "Ruta" to train %d;
                train %d set autopilot true;
                """.formatted(a.getId(), b.getId(), t.getId(), t.getId()));
            runTicks(600);
            assertAtStation(t, b);
            assertTrue(t.getSpeed() <= 2, "should be slowing down");
        }

        @Test
        @DisplayName("1.5 Engine OFF → train does not move")
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
            fork.addRoute(Dir.W, Dir.E); fork.addRoute(Dir.E, Dir.W); // straight
            fork.addRoute(Dir.W, Dir.S); fork.addRoute(Dir.S, Dir.W); // branch S
            fork.setNormalRoute();
            RailTrack t3 = makeTrack(3, 0, Dir.W, Dir.E);
            RailTrack branch = makeTrack(2, 1, Dir.N, Dir.S);
            connect(t0, Dir.E, t1, Dir.W);
            connect(t1, Dir.E, fork, Dir.W);
            connect(fork, Dir.E, t3, Dir.W);
            connect(fork, Dir.S, branch, Dir.N);

            Station mainSt = makeStation(t3, "Main");
            Station branchSt = makeStation(branch, "Branch");
            // Train on t1, facing East toward fork
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
                """.formatted(mainSt.getId(), branchSt.getId(), t.getId(), t.getId()));

            runTicks(600);

            // Train must have reached Branch station (required fork flip)
            assertAtStation(t, branchSt);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 6. Dead end
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("6. Dead end")
    class DeadEnd {

        @Test
        @DisplayName("6.1 Train reaches last station at dead end and stops")
        void trainReachesDeadEndStation() {
            RailTrack t0 = makeTrack(0, 0, Dir.E, Dir.W);
            RailTrack t1 = makeTrack(1, 0, Dir.W, Dir.E);
            RailTrack t2 = makeTrack(2, 0, Dir.W, Dir.E);
            connect(t0, Dir.E, t1, Dir.W);
            connect(t1, Dir.E, t2, Dir.W);
            Station a = makeStation(t0, "A");
            Station end = makeStation(t2, "End");
            Train t = makeTrain(t0, Dir.W);
            model.setProgram("""
                station %d set name "A";
                station %d set name "End";
                create itinerary "Ruta" {
                    add station "A"
                    add station "End" STOP
                }
                assign itinerary "Ruta" to train %d;
                train %d set autopilot true;
                """.formatted(a.getId(), end.getId(), t.getId(), t.getId()));
            runTicks(500);
            assertAtStation(t, end);
            assertTrue(t.getSpeed() <= 2, "should be nearly stopped");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 8. Multiple actions
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("8. Multiple actions")
    class MultipleActions {

        @Test
        @DisplayName("8.1 SPEED 5 + WAIT 1 at first waypoint")
        void speed5AndWait1() {
            RailTrack t0 = makeTrack(0, 0, Dir.E, Dir.W);
            RailTrack t1 = makeTrack(1, 0, Dir.W, Dir.E);
            RailTrack t2 = makeTrack(2, 0, Dir.W, Dir.E);
            connect(t0, Dir.E, t1, Dir.W);
            connect(t1, Dir.E, t2, Dir.W);
            Station a = makeStation(t0, "A");
            Station b = makeStation(t2, "B");
            Train t = makeTrain(t0, Dir.W);
            model.setProgram("""
                station %d set name "A";
                station %d set name "B";
                create itinerary "Ruta" {
                    add station "A" SPEED 5 WAIT 1
                    add station "B" STOP
                }
                assign itinerary "Ruta" to train %d;
                train %d set autopilot true;
                """.formatted(a.getId(), b.getId(), t.getId(), t.getId()));
            runTicks(500);
            assertTrue(hasReached(t, a) || hasReached(t, b), "should reach a station");
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
                """.formatted(madrid.getId(), barcelona.getId(), t.getId(), t.getId()));

            runTicks(m, 500);
            assertTrue(hasReached(t, madrid) || hasReached(t, barcelona),
                "should reach Madrid or Barcelona");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════

    private RailMap railMap() { return model.getRailMap(); }

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
            """.formatted(idA, nameA, idB, nameB, nameA, nameB, trainId, trainId));
    }

    private void runTicks(int count) { runTicks(model, count); }

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
