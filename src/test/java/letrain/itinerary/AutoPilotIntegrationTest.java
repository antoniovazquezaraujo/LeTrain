package letrain.itinerary;

import letrain.map.Dir;
import letrain.map.Point;
import letrain.map.impl.RailMap;
import letrain.mvp.impl.Model;
import letrain.track.Station;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;
import letrain.vehicle.impl.rail.Locomotive;
import letrain.vehicle.impl.rail.Train;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for AutoPilot following itineraries on real tracks.
 * ADR-010 cases: builds track layouts, places trains and stations,
 * executes DSL programs, runs simulation ticks, verifies results.
 */
@DisplayName("AutoPilot Integration Tests")
class AutoPilotIntegrationTest {

    private Model model;
    private RailMap railMap;

    @BeforeEach
    void setUp() {
        model = new Model();
        model.postLoadInit();
    }

    // ═══════════════════════════════════════════════════════════════════
    // Case 1.1: Vía recta, Madrid → Barcelona
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("1.1 Straight track: Madrid → Barcelona")
    void straightTrack_twoStations_trainArrivesAtDestination() {
        // --- Build: [Madrid]═══[Barcelona] ---
        RailTrack t0 = makeTrack(0, 0, Dir.E, Dir.W);
        RailTrack t1 = makeTrack(1, 0, Dir.W, Dir.E);
        connect(t0, Dir.E, t1, Dir.W);

        Station madrid = makeStation(t0, "Madrid");
        Station barcelona = makeStation(t1, "Barcelona");

        Train train = makeTrain(t0, Dir.W);

        // --- DSL ---
        model.setProgram("""
            station %d set name "Madrid";
            station %d set name "Barcelona";
            create itinerary "Ruta 1" {
                add station "Madrid"
                add station "Barcelona"
            }
            assign itinerary "Ruta 1" to train %d;
            train %d set autopilot true;
            """.formatted(madrid.getId(), barcelona.getId(), train.getId(), train.getId()));

        assertTrue(train.isAutoMode(), "autopilot should be ON after DSL");

        // --- Run simulation ---
        runTicks(200);

        // --- Verify ---
        assertEquals(barcelona.getId(), train.getStationId(),
            "train should have arrived at Barcelona");
    }

    // ═══════════════════════════════════════════════════════════════════
    // Case 1.2: Vía recta, SPEED 5 → STOP
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("1.2 Straight track: SPEED 5, STOP at destination")
    void straightTrack_speed5_stopAtDestination() {
        RailTrack t0 = makeTrack(0, 0, Dir.E, Dir.W);
        RailTrack t1 = makeTrack(1, 0, Dir.W, Dir.E);
        RailTrack t2 = makeTrack(2, 0, Dir.W, Dir.E);
        connect(t0, Dir.E, t1, Dir.W);
        connect(t1, Dir.E, t2, Dir.W);

        Station madrid = makeStation(t0, "Madrid");
        Station barcelona = makeStation(t2, "Barcelona");

        Train train = makeTrain(t0, Dir.W);

        model.setProgram("""
            station %d set name "Madrid";
            station %d set name "Barcelona";
            create itinerary "Ruta 1" {
                add station "Madrid" SPEED 5
                add station "Barcelona" STOP
            }
            assign itinerary "Ruta 1" to train %d;
            train %d set autopilot true;
            """.formatted(madrid.getId(), barcelona.getId(), train.getId(), train.getId()));

        assertTrue(train.isAutoMode());

        runTicks(800);

        assertEquals(barcelona.getId(), train.getStationId(),
            "train should arrive at Barcelona");
        assertEquals(0, train.getSpeed(), "train should be fully stopped after STOP");
    }

    // ═══════════════════════════════════════════════════════════════════
    // Case 4.1: Fork — Madrid → Zaragoza → Barcelona
    // ═══════════════════════════════════════════════════════════════════

    @Test
    @Disabled("A* route not found on fork layouts — bug to fix")
    @DisplayName("4.1 Fork: Madrid → Zaragoza, autopilot changes fork")
    void fork_autoChangesRoute() {
        /*
            Layout:
                t0(0,0) → t1(1,0) → fork(2,0) → t3(3,0)
                                      ↓ N
                                    t2(2,1) [Zaragoza]

            Normal route: W→E (straight). Alternative: W→N (branch).
            Madrid=t0, Zaragoza=t2. Barcelona not in this test.
        */
        RailTrack t0 = makeTrack(0, 0, Dir.E, Dir.W);
        RailTrack t1 = makeTrack(1, 0, Dir.W, Dir.E);
        ForkRailTrack fork = new ForkRailTrack(1);
        fork.setPosition(new Point(2, 0));
        fork.addRoute(Dir.W, Dir.E);   // straight
        fork.addRoute(Dir.E, Dir.W);
        fork.addRoute(Dir.W, Dir.N);   // branch (north to Zaragoza)
        fork.addRoute(Dir.N, Dir.W);
        fork.setNormalRoute();
        railMap().addTrack(new Point(2, 0), fork);
        model.addFork(fork);

        RailTrack t3 = makeTrack(3, 0, Dir.W, Dir.E);
        RailTrack t2 = makeTrack(2, 1, Dir.S, Dir.N);

        connect(t0, Dir.E, t1, Dir.W);
        connect(t1, Dir.E, fork, Dir.W);
        connect(fork, Dir.E, t3, Dir.W);
        connect(fork, Dir.N, t2, Dir.S);

        Station madrid = makeStation(t0, "Madrid");
        Station zaragoza = makeStation(t2, "Zaragoza");

        Train train = makeTrain(t0, Dir.W);
        assertFalse(fork.isUsingAlternativeRoute(), "fork starts in normal position");

        model.setProgram("""
            station %d set name "Madrid";
            station %d set name "Zaragoza";
            create itinerary "Ruta 1" {
                add station "Madrid"
                add station "Zaragoza"
            }
            assign itinerary "Ruta 1" to train %d;
            train %d set autopilot true;
            """.formatted(madrid.getId(), zaragoza.getId(), train.getId(), train.getId()));

        assertTrue(train.isAutoMode());

        runTicks(300);

        assertEquals(zaragoza.getId(), train.getStationId(),
            "train should be at Zaragoza");
        assertTrue(fork.isUsingAlternativeRoute(),
            "AutoPilot should have changed fork to reach Zaragoza");
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

    private ForkRailTrack makeFork(int x, int y, int id) {
        ForkRailTrack f = new ForkRailTrack(id);
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

    private Train makeTrain(RailTrack startTrack, Dir entryFrom) {
        Locomotive loco = new Locomotive(model.nextLocomotiveId(), "A");
        loco.setEngineOn(true);
        Train train = new Train(model.nextTrainId());
        train.setModel(model);
        train.pushBack(loco);
        train.setDirectorLinker(loco);
        model.addLocomotive(loco);
        startTrack.enterLinkerFromDir(entryFrom, loco);
        // If the track has a station, mark the train as arrived
        if (startTrack.getSensor() instanceof Station st) {
            train.setStationId(st.getId());
        }
        return train;
    }

    private void runTicks(int count) {
        for (int i = 0; i < count; i++) {
            model.moveLocomotives();
            model.loadAndUnloadTrains();
        }
        model.removeDestroyedTrains();
        System.out.println("[TEST] After " + count + " ticks: autoMode="
            + model.getLocomotives().get(0).getTrain().isAutoMode()
            + " speed=" + model.getLocomotives().get(0).getSpeed()
            + " stationId=" + model.getLocomotives().get(0).getTrain().getStationId());
    }
}
