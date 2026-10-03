package letrain.itinerary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
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
import org.junit.jupiter.api.Test;

/**
 * Issue #653: applying a program again (editor REPROGRAM, console) must reset the train automation
 * coherently before the new text builds it back. Pending waypoint actions of the old plan must not
 * resume against the new one, a stale block wait must not survive, and a running train must be
 * announced because its service restarts from the first waypoint (ADR-009: every APPLY wipes and
 * recreates the state).
 */
@DisplayName("Issue #653: hot program re-apply")
class HotReprogramIntegrationTest {

    private Model model;

    @BeforeEach
    void setUp() {
        model = new Model(1);
        model.postLoadInit();
    }

    @Test
    @DisplayName("a stale waypoint action from the old plan does not run after the swap")
    void hotReapply_doesNotResumeStaleWaypointActions() {
        List<RailTrack> line = line(0, 10, 0);
        Station a = station(line.get(0), "a");
        Sensor s1 = sensor(line.get(4), "s1");
        Sensor s2 = sensor(line.get(6), "s2");
        station(line.get(9), "b");
        Train train = placeTrain(line.get(0), Dir.W);
        train.setStationId(a.getId());
        String oldProgram = """
                create itinerary "maneuver" {
                    add station "a",
                        stop at sensor %d speed 3,
                        stop at sensor %d speed 3,
                        reverse
                    add station "b"
                }
                assign itinerary "maneuver" to train %d;
                train %d set autopilot true;
                train %d set speed 3;
                """.formatted(s1.getId(), s2.getId(), train.getId(), train.getId(), train.getId());
        String newProgram = """
                create itinerary "maneuver" {
                    add station "a",
                        stop at sensor %d speed 3,
                        stop at sensor %d speed 3
                    add station "b"
                }
                assign itinerary "maneuver" to train %d;
                train %d set autopilot true;
                train %d set speed 3;
                """.formatted(s1.getId(), s2.getId(), train.getId(), train.getId(), train.getId());
        model.setProgram(oldProgram);
        // First action done and the mission to s2 active: the old plan has "reverse" pending.
        runUntil(() -> headX(train) >= 5
                && train.getAutopilot().mission().filter(TrainMission::isActive).isPresent(), 800);
        assertFalse(((Locomotive) train.getDirectorLinker()).isReversed(),
                "the old plan must not have reversed yet");

        model.setProgram(newProgram);
        runTicks(400);

        assertFalse(((Locomotive) train.getDirectorLinker()).isReversed(),
                "the pending reverse of the old plan must not resume after the hot swap");
    }

    @Test
    @DisplayName("re-applying clears the block wait and the deferred speed")
    void hotReapply_clearsBlockWaitAndDeferredSpeed() {
        List<RailTrack> line = line(0, 10, 0);
        Station a = station(line.get(0), "a");
        station(line.get(4), "b");
        Train train = placeTrain(line.get(0), Dir.W);
        train.setStationId(a.getId());
        String program = """
                create itinerary "plan" {
                    add station "a"
                    add station "b"
                }
                assign itinerary "plan" to train %d;
                train %d set autopilot true;
                train %d set speed 3;
                """.formatted(train.getId(), train.getId(), train.getId());
        model.setProgram(program);
        runUntil(() -> headX(train) >= 2, 400);
        train.getSafetyManager().onEmergencyStop();
        train.setSavedTargetSpeed(3);
        assertTrue(train.getSafetyManager().isWaitingForBlock(),
                "precondition: the train is waiting");

        // A new program that does not mention this train: only the engine reset can clear the wait.
        model.setProgram("""
                create itinerary "other" {
                    add station "a"
                    add station "b"
                }
                """);

        assertFalse(train.getSafetyManager().isWaitingForBlock(),
                "a stale block wait must not survive a re-program");
        assertEquals(-1, train.getSavedTargetSpeed(),
                "the deferred speed of the old program must be dropped");
    }

    @Test
    @DisplayName("a hot swap of a running train warns through the user channel")
    void hotReapply_warnsWhenTrainWasRunning() {
        List<String> messages = new ArrayList<>();
        model.setUserMessageSink((title, text) -> messages.add(title + ": " + text));
        List<RailTrack> line = line(0, 10, 0);
        Station a = station(line.get(0), "a");
        station(line.get(4), "b");
        Train train = placeTrain(line.get(0), Dir.W);
        train.setStationId(a.getId());
        String program = """
                create itinerary "plan" {
                    add station "a"
                    add station "b"
                }
                assign itinerary "plan" to train %d;
                train %d set autopilot true;
                train %d set speed 3;
                """.formatted(train.getId(), train.getId(), train.getId());
        model.setProgram(program);
        runUntil(() -> headX(train) >= 2, 400);

        model.setProgram(program);

        assertTrue(
                messages.stream()
                        .anyMatch(m -> m.contains("Train " + train.getId() + " was running")),
                "a hot swap must not restart a running train silently, got: " + messages);
    }

    @Test
    @DisplayName("applying an empty program also resets the automation")
    void emptyProgram_resetsAutomation() {
        List<RailTrack> line = line(0, 10, 0);
        Station a = station(line.get(0), "a");
        Sensor s1 = sensor(line.get(4), "s1");
        station(line.get(9), "b");
        Train train = placeTrain(line.get(0), Dir.W);
        train.setStationId(a.getId());
        model.setProgram("""
                create itinerary "maneuver" {
                    add station "a",
                        stop at sensor %d speed 3,
                        reverse
                    add station "b"
                }
                assign itinerary "maneuver" to train %d;
                train %d set autopilot true;
                train %d set speed 3;
                """.formatted(s1.getId(), train.getId(), train.getId(), train.getId()));
        runUntil(() -> train.getAutopilot().mission().filter(TrainMission::isActive).isPresent(),
                400);

        model.setProgram("");

        assertTrue(train.getAutopilot().mission().filter(TrainMission::isActive).isEmpty(),
                "the running mission must be cancelled");
        assertEquals(AutoPilot.Mode.IDLE, train.getAutopilot().mode(),
                "the autopilot must return to IDLE");
    }

    // ── helpers (mirror WaypointManeuverIntegrationTest) ──

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

    private int headX(Train train) {
        return train.getPhysicalFront().getTrack().getPosition().getX();
    }
}
