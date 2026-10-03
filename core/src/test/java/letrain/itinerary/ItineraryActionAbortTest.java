package letrain.itinerary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import letrain.command.PlayerCommandExecutor;
import letrain.itinerary.impl.WaypointImpl;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.map.impl.RailMap;
import letrain.mvp.impl.Model;
import letrain.track.Sensor;
import letrain.track.Station;
import letrain.track.rail.RailTrack;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Issues #647/#648: the waypoint choreography must abort loudly instead of silently dropping an
 * action whose mission cannot start (missing destination) or continuing on a crashed consist.
 *
 * <p>
 * The parse-time rejection of an itinerary with an unknown destination is covered by
 * {@code DslVisibilityTest} (D1 batch); these tests cover the run-time paths: the target disappears
 * after the itinerary was created (track editing / savegame loaded on another map) and a saved
 * action without a destination. A crash aborts the pending actions of any waypoint, including the
 * ones deferred by a scheduled {@code wait}.
 */
@DisplayName("Waypoint actions: rejected missions and crashes abort the choreography (#647/#648)")
class ItineraryActionAbortTest {

    private Model model;

    @BeforeEach
    void setUp() {
        model = new Model(1);
        model.postLoadInit();
    }

    // ═══════════════════════════════════════════════════════════════════
    // #647: missing 'stop at' target
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Missing mission target")
    class MissingMissionTarget {

        @Test
        @DisplayName("a target removed after the itinerary was created warns and aborts the rest")
        void targetRemovedAtRuntime_warnsAndAbortsTheRemainingActions() {
            List<RailTrack> line = line(0, 8, 0);
            Station a = station(line.get(0), "a");
            station(line.get(7), "b");
            Sensor sensor = sensor(line.get(3), "s3");
            Train train = placeTrain(line.get(0), Dir.W);
            train.setStationId(a.getId());
            setTime(8, 0);
            Locomotive loco = (Locomotive) train.getDirectorLinker();
            List<String> messages = new ArrayList<>();
            model.setUserMessageSink((title, text) -> messages.add(text));

            List<String> errors = model.setProgram("""
                    create itinerary "m" {
                        add station "a", arrival 08:00,
                            stop at sensor %d speed 2,
                            reverse,
                            departure 09:00;
                        add station "b"
                    }
                    assign itinerary "m" to train %d;
                    """.formatted(sensor.getId(), train.getId()));
            assertTrue(errors.isEmpty(), "unexpected errors: " + errors);

            // The sensor disappears after the itinerary was created: the action cannot resolve its
            // target at run time.
            model.removeSensor(sensor);
            String error = PlayerCommandExecutor
                    .execute("train " + train.getId() + " set autopilot true;", model);
            assertNull(error, error);
            runTicks(50);

            assertTrue(messages.stream().anyMatch(m -> m.contains("not found")),
                    "the missing target must warn visibly: " + messages);
            assertFalse(loco.isReversed(),
                    "the remaining reverse action must be aborted, not skipped silently");
        }

        @Test
        @DisplayName("a saved mission action without a destination aborts the rest")
        void missionWithoutDestination_abortsTheRemainingActions() {
            List<RailTrack> line = line(0, 4, 0);
            Station a = station(line.get(0), "a");
            Train train = placeTrain(line.get(0), Dir.W);
            train.setStationId(a.getId());
            Locomotive loco = (Locomotive) train.getDirectorLinker();

            // Corrupt/old saved action: MISSION without a missionKind. It used to be dropped
            // without a warning and the choreography continued.
            Waypoint waypoint = new WaypointImpl(Waypoint.Type.STATION, a.getId(),
                    List.of(WaypointCommand.mission(null, -1, 2), WaypointCommand.REVERSE));
            train.getActionManager().onWaypointReached(train, waypoint);

            assertFalse(loco.isReversed(), "the action after the invalid mission must be aborted");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // #648: crash aborts the pending waypoint actions
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Crash")
    class Crash {

        @Test
        @DisplayName("a crash while the waypoint waits aborts the actions scheduled for later")
        void crashDuringWait_abortsTheRemainingActions() {
            List<RailTrack> line = line(0, 8, 0);
            Station a = station(line.get(0), "a");
            station(line.get(7), "b");
            Train train = placeTrain(line.get(0), Dir.W);
            train.setStationId(a.getId());
            setTime(8, 0);
            Locomotive loco = (Locomotive) train.getDirectorLinker();

            List<String> errors = model.setProgram("""
                    create itinerary "m" {
                        add station "a", arrival 08:00,
                            wait 5,
                            reverse,
                            departure 09:00;
                        add station "b"
                    }
                    assign itinerary "m" to train %d;
                    train %d set autopilot true;
                    """.formatted(train.getId(), train.getId()));
            assertTrue(errors.isEmpty(), "unexpected errors: " + errors);

            runTicks(10);
            // A crash of the consist while the wait is pending (e.g. another train hits it).
            train.crashDestroy(loco.getPosition(), 8);
            runTicks(200);

            assertTrue(train.isStalled(), "the crash must stall the consist");
            assertFalse(loco.isReversed(),
                    "the wait must not resume the reverse action on the destroyed consist");
        }

        @Test
        @DisplayName("a crash mid-mission fails the mission with a warning and aborts the rest")
        void crashDuringMission_warnsAndAbortsTheRemainingActions() {
            List<RailTrack> rails = line(0, 40, 0); // long run-up: speed >= threshold at the buffer
            Station a = station(rails.get(0), "a");
            station(rails.get(39), "b");
            Train train = placeTrain(rails.get(0), Dir.W);
            train.setStationId(a.getId());
            setTime(8, 0);
            Locomotive loco = (Locomotive) train.getDirectorLinker();
            List<String> messages = new ArrayList<>();
            model.setUserMessageSink((title, text) -> messages.add(text));

            List<String> errors = model.setProgram("""
                    create itinerary "m" {
                        add station "a", arrival 08:00,
                            stop on contact speed 8,
                            reverse,
                            departure 09:00;
                        add station "b"
                    }
                    assign itinerary "m" to train %d;
                    train %d set autopilot true;
                    """.formatted(train.getId(), train.getId()));
            assertTrue(errors.isEmpty(), "unexpected errors: " + errors);

            runUntil(() -> train.isStalled(), 3000);
            runTicks(300);

            assertTrue(train.isStalled(), "the high-speed contact must crash the train");
            assertEquals(TrainMission.State.FAILED,
                    train.getAutopilot().mission().map(TrainMission::state).orElse(null),
                    "a crash fails the running mission");
            assertTrue(messages.stream().anyMatch(m -> m.contains("crashed")),
                    "the crash must warn visibly: " + messages);
            assertFalse(loco.isReversed(), "the reverse action must not run after the crash");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Fixture helpers
    // ═══════════════════════════════════════════════════════════════════

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

    private void connect(RailTrack a, Dir aDir, RailTrack b, Dir bDir) {
        a.connect(aDir, b);
        b.connect(bDir, a);
    }

    private Sensor sensor(RailTrack track, String name) {
        Sensor sensor = new Sensor(model.nextSensorId());
        sensor.setName(name);
        sensor.setTrack(track);
        track.setComponent(sensor);
        model.addSensor(sensor);
        return sensor;
    }

    private Station station(RailTrack track, String name) {
        Station station = new Station(model.nextStationId());
        station.setName(name);
        station.setTrack(track);
        track.setComponent(station);
        model.addStation(station);
        return station;
    }

    private Train placeTrain(RailTrack start, Dir entryFrom) {
        Locomotive loco = new Locomotive(model.nextLocomotiveId(), "A");
        loco.setEngineOn(true);
        loco.setTargetSpeed(0);
        Train train = new Train(model.nextTrainId());
        train.setModel(model);
        train.pushBack(loco);
        train.setDirectorLinker(loco);
        model.addLocomotive(loco);
        start.enterLinkerFromDir(entryFrom, loco);
        train.getSafetyManager().acquireInitialLocks();
        return train;
    }

    private void setTime(int hour, int minute) {
        String error =
                PlayerCommandExecutor.execute("time set " + hour + ":" + minute + ";", model);
        assertNull(error, error);
    }

    private void runTicks(int count) {
        for (int i = 0; i < count; i++) {
            if (model.getScheduler() != null) {
                model.getScheduler().tick();
            }
            model.getGameClock().tick();
            model.moveLocomotives();
            model.loadAndUnloadTrains();
        }
        model.removeDestroyedTrains();
    }

    private void runUntil(BooleanSupplier condition, int maxTicks) {
        for (int i = 0; i < maxTicks; i++) {
            if (condition.getAsBoolean()) {
                return;
            }
            runTicks(1);
        }
    }
}
