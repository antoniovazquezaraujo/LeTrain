package letrain.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.map.impl.RailMap;
import letrain.mvp.impl.Model;
import letrain.track.Station;
import letrain.track.rail.RailTrack;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import letrain.vehicle.rail.impl.Wagon;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Batch 2 syntax decisions for direct train orders: U1 (no {@code set N} shortcut), U2
 * ({@code invert}/{@code reverse} synonyms), U3 ({@code park} direct), the bare {@code stop} order
 * and U6 ({@code uncouple} without a count means all; {@code 0} is invalid).
 */
@DisplayName("Direct train order syntax (batch 2: U1, U2, U3, U6, stop)")
class DirectTrainSyntaxTest {

    private Model model;
    private Train train;
    private Locomotive loco;
    private Station a;
    private Station b;
    private List<RailTrack> rails;
    private final List<String> panel = new ArrayList<>();

    @BeforeEach
    void setUp() {
        model = new Model(1);
        model.postLoadInit();
        model.setUserMessageSink((title, text) -> panel.add(text));
        rails = line(-5, 12, 0);
        a = station(rails.get(0), "A");
        b = station(rails.get(11), "B");
        loco = new Locomotive(model.nextLocomotiveId(), "A");
        loco.setEngineOn(true);
        train = new Train(model.nextTrainId());
        train.setModel(model);
        train.pushBack(loco);
        train.setDirectorLinker(loco);
        model.addLocomotive(loco);
        rails.get(0).enterLinkerFromDir(Dir.W, loco);
        train.getSafetyManager().acquireInitialLocks();
    }

    private String run(String script) {
        return PlayerCommandExecutor.execute(script, model, null, null, null);
    }

    @Nested
    @DisplayName("Speed (U1)")
    class Speed {
        @Test
        @DisplayName("`set speed N` works and the old `set N` shortcut is gone")
        void setSpeedKeyword() {
            assertNull(run("train 1 set speed 5;"), "set speed must run");
            assertEquals(5, loco.getTargetSpeed());

            assertNotNull(run("train 1 set 5;"), "the old shortcut must be a syntax error");
        }
    }

    @Nested
    @DisplayName("Sense (U2)")
    class Sense {
        @Test
        @DisplayName("invert and reverse are synonyms in a direct order")
        void invertAndReverse() {
            assertFalse(loco.isReversed());
            assertNull(run("train 1 reverse;"), "reverse must run");
            assertTrue(loco.isReversed(), "reverse flips the sense");
            assertNull(run("train 1 invert;"), "invert must run");
            assertFalse(loco.isReversed(), "invert flips it back");
        }
    }

    @Nested
    @DisplayName("Park and stop (U3)")
    class ParkAndStop {
        @Test
        @DisplayName("park switches the engine off and keeps the running autopilot")
        void park_keepsAutopilot() {
            model.setProgram("""
                    create itinerary "Ruta" {
                        add station 1
                        add station 2
                    }
                    assign itinerary "Ruta" to train 1;
                    train 1 set autopilot true;
                    """);
            assertTrue(train.isAutoMode(), "the itinerary must keep the autopilot on");

            assertNull(run("train 1 park;"), "park must run");
            assertFalse(loco.isEngineOn(), "park must switch the engine off");
            assertTrue(train.isAutoMode(), "park must keep the autopilot");
        }

        @Test
        @DisplayName("stop brakes and turns the autopilot off")
        void stop_turnsAutopilotOff() {
            model.setProgram("""
                    create itinerary "Ruta" {
                        add station 1
                        add station 2
                    }
                    assign itinerary "Ruta" to train 1;
                    train 1 set autopilot true;
                    """);
            assertTrue(train.isAutoMode());

            assertNull(run("train 1 stop;"), "stop must run");
            assertFalse(train.isAutoMode(), "stop must turn the autopilot off");
        }
    }

    @Nested
    @DisplayName("Coupling counts (U6)")
    class Coupling {
        private Train threeVehicleTrain() {
            // Rebuild as loco (x=0) + wagon (x=-1) + wagon (x=-2) on the same train, behind.
            Wagon w1 = new Wagon("b");
            Wagon w2 = new Wagon("c");
            model.addWagon(w1);
            model.addWagon(w2);
            rails.get(4).enterLinkerFromDir(Dir.W, w1); // x = -1
            rails.get(3).enterLinkerFromDir(Dir.W, w2); // x = -2
            train.pushBack(w1);
            train.pushBack(w2);
            train.rebind();
            train.getMovementManager().refreshLinkersDirection();
            return train;
        }

        @Test
        @DisplayName("couple/uncouple with 0 warn and do nothing (0 is not 'all' anymore)")
        void zeroCount_warnsAndDoesNothing() {
            threeVehicleTrain();
            assertEquals(3, train.getLinkers().size());

            assertNull(run("train 1 couple forward 0;"), "the order parses");
            assertTrue(panel.stream().anyMatch(m -> m.contains("Invalid vehicle count 0")),
                    "expected the invalid-count notice, got: " + panel);
            assertEquals(3, train.getLinkers().size(), "nothing may be coupled");
        }

        @Test
        @DisplayName("uncouple with no count detaches every vehicle on that side")
        void uncoupleWithoutCount_detachesAll() {
            threeVehicleTrain();
            assertEquals(3, train.getLinkers().size());

            assertNull(run("train 1 uncouple backward;"), "uncouple without count must run");
            assertEquals(1, train.getLinkers().size(), "the loco must stay alone");
        }

        @Test
        @DisplayName("uncouple with an explicit count still detaches exactly that many")
        void uncoupleWithCount_detachesThatMany() {
            threeVehicleTrain();
            assertEquals(3, train.getLinkers().size());

            assertNull(run("train 1 uncouple backward 1;"), "uncouple 1 must run");
            assertEquals(2, train.getLinkers().size(), "one wagon must leave");
        }
    }

    @Nested
    @DisplayName("Waypoint counts (U6)")
    class WaypointCounts {
        @Test
        @DisplayName("a waypoint uncouple 0 rejects the itinerary with a notice")
        void waypointZeroCount_rejectsItinerary() {
            List<String> messages = new ArrayList<>();
            model.setUserMessageSink((title, text) -> messages.add(title + ": " + text));

            List<String> errors = model.setProgram("""
                    create itinerary "bad" {
                        add station 1, uncouple backward 0
                        add station 2
                    }
                    """);

            assertTrue(errors.isEmpty(), "it parses: " + errors);
            assertTrue(messages.stream().anyMatch(m -> m.contains("invalid vehicle count")),
                    "expected the invalid-count notice, got: " + messages);
            assertTrue(train.getAutopilot().itinerary().isEmpty(),
                    "the itinerary must not be created");
        }
    }

    // ── Fixture helpers ─────────────────────────────────────────────

    private RailMap railMap() {
        return model.getRailMap();
    }

    private RailTrack track(int x, int y) {
        RailTrack track = new RailTrack();
        track.setPosition(new Point(x, y));
        track.addRoute(Dir.E, Dir.W);
        track.addRoute(Dir.W, Dir.E);
        railMap().addTrack(new Point(x, y), track);
        return track;
    }

    private List<RailTrack> line(int x0, int count, int y) {
        List<RailTrack> rails = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rails.add(track(x0 + i, y));
        }
        for (int i = 0; i + 1 < rails.size(); i++) {
            connect(rails.get(i), Dir.E, rails.get(i + 1), Dir.W);
        }
        return rails;
    }

    private void connect(RailTrack from, Dir fromDir, RailTrack to, Dir toDir) {
        from.connect(fromDir, to);
        to.connect(toDir, from);
    }

    private Station station(RailTrack track, String name) {
        Station station = new Station(model.nextStationId());
        station.setName(name);
        station.setTrack(track);
        track.setComponent(station);
        model.addStation(station);
        return station;
    }
}
