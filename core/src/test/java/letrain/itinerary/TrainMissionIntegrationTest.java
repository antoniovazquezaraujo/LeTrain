package letrain.itinerary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import letrain.command.PlayerCommandExecutor;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.map.impl.RailMap;
import letrain.mvp.impl.GameSaveService;
import letrain.mvp.impl.Model;
import letrain.track.Sensor;
import letrain.track.Station;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Issue #619: one-shot missions ordered from the console/scripts ({@code stop at ...} /
 * {@code stop when blocked}). The order carries the destination and the speed, the train drives and
 * ends stopped, and an active itinerary rejects the order.
 */
@DisplayName("Issue #619: one-shot 'stop at' missions")
class TrainMissionIntegrationTest {

    private Model model;

    @BeforeEach
    void setUp() {
        model = new Model(1);
        model.postLoadInit();
        model.updateGroundMap(new Point(-150, -150), 300, 300);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Destination reached: station and sensor
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Arrival")
    class Arrival {

        @Test
        @DisplayName("a single console order drives the train to the sensor and stops it there")
        void stopAtSensor_stopsOnTheSensor() {
            List<RailTrack> rails = line(0, 9, 0);
            Sensor sensor = sensor(rails.get(5), "S5");
            Train train = placeTrain(rails.get(0), Dir.W);

            List<String> messages = console(
                    "train " + train.getId() + " stop at sensor " + sensor.getId() + " speed 3;");

            runUntil(() -> missionFinished(train), 600);

            assertEquals(TrainMission.State.COMPLETED, mission(train).state());
            assertEquals(rails.get(5), headTrack(train), "the head must stop on the sensor rail");
            assertEquals(0, train.getSpeed(), "the train must end stopped");
            assertTrue(messages.stream().anyMatch(m -> m.contains("arrived at sensor")),
                    "expected an arrival notice, got: " + messages);
        }

        @Test
        @DisplayName("a station destination is reached and the train ends stopped")
        void stopAtStation_reachesStation() {
            List<RailTrack> rails = line(0, 8, 0);
            Station station = station(rails.get(4), "B");
            Train train = placeTrain(rails.get(0), Dir.W);

            console("train " + train.getId() + " stop at station " + station.getId() + " speed 3;");
            runUntil(() -> missionFinished(train), 600);

            assertEquals(TrainMission.State.COMPLETED, mission(train).state());
            assertEquals(rails.get(4), headTrack(train), "the head must stop on the station rail");
            assertEquals(station.getId(), train.getStationId());
            assertEquals(0, train.getSpeed());
        }

        @Test
        @DisplayName("without speed it uses the current target speed")
        void withoutSpeed_usesCurrentTargetSpeed() {
            List<RailTrack> rails = line(0, 9, 0);
            Sensor sensor = sensor(rails.get(6), "S6");
            Train train = placeTrain(rails.get(0), Dir.W);
            console("train " + train.getId() + " set speed 3;");
            runTicks(5);
            assertEquals(3, train.getDirectorLinker().getTargetSpeed(), "train should be rolling");

            console("train " + train.getId() + " stop at sensor " + sensor.getId() + ";");
            runUntil(() -> missionFinished(train), 600);

            assertEquals(TrainMission.State.COMPLETED, mission(train).state(),
                    "the order must have started with the current speed");
            assertEquals(rails.get(6), headTrack(train));
            assertEquals(0, train.getSpeed());
        }

        @Test
        @DisplayName("at rest with no speed and no current one: warns and does not move")
        void withoutSpeedAtRest_warnsAndDoesNotMove() {
            List<RailTrack> rails = line(0, 9, 0);
            Sensor sensor = sensor(rails.get(5), "S5");
            Train train = placeTrain(rails.get(0), Dir.W);

            List<String> messages =
                    console("train " + train.getId() + " stop at sensor " + sensor.getId() + ";");
            runTicks(50);

            assertTrue(messages.stream().anyMatch(m -> m.contains("no speed set")),
                    "expected a no-speed warning, got: " + messages);
            assertTrue(train.getAutopilot().mission().isEmpty(),
                    "a rejected order must not leave a mission");
            assertEquals(rails.get(0), headTrack(train), "the train must not move");
            assertEquals(0, train.getSpeed());
        }

        @Test
        @DisplayName("a braked train keeps its desired speed for the order without speed")
        void withoutSpeed_usesTheSavedDesiredSpeed() {
            List<RailTrack> rails = line(0, 9, 0);
            Sensor sensor = sensor(rails.get(6), "S6");
            Train train = placeTrain(rails.get(0), Dir.W);
            console("train " + train.getId() + " set speed 3;");
            runTicks(3);
            train.getMovementManager().initiateBraking();
            runTicks(5);
            assertEquals(0, train.getDirectorLinker().getTargetSpeed());
            assertTrue(train.hasSavedTargetSpeed(), "the desired speed must be remembered");

            List<String> messages =
                    console("train " + train.getId() + " stop at sensor " + sensor.getId() + ";");

            assertFalse(messages.stream().anyMatch(m -> m.contains("no speed set")),
                    "the saved desired speed should have been used, got: " + messages);
            assertTrue(mission(train).isActive());
        }
    }

    @Nested
    @DisplayName("Unreachable destinations")
    class Unreachable {

        @Test
        @DisplayName("a sensor in another isolated segment warns and does not move")
        void unreachable_warnsAndDoesNotMove() {
            List<RailTrack> rails = line(0, 4, 0);
            List<RailTrack> isolated = line(10, 3, 0);
            Sensor sensor = sensor(isolated.get(1), "Far");
            Train train = placeTrain(rails.get(0), Dir.W);

            List<String> messages = console(
                    "train " + train.getId() + " stop at sensor " + sensor.getId() + " speed 2;");
            runTicks(50);

            assertTrue(messages.stream().anyMatch(m -> m.contains("no route")),
                    "expected an unreachable warning, got: " + messages);
            assertTrue(train.getAutopilot().mission().isEmpty());
            assertEquals(rails.get(0), headTrack(train), "the train must not move");
        }

        @Test
        @DisplayName("a destination behind the train reverses it once and reaches it")
        void destinationBehind_reversesOnStart() {
            // Segment A = x -4..2 (station at x=-3); ahead there is only a fork into a dead-end
            // stub, so the only way to the station is backwards.
            List<RailTrack> approach = line(-4, 7, 0);
            ForkRailTrack fork = fork(3, 0, Dir.W, Dir.E);
            RailTrack stub = track(4, 0);
            connect(approach.get(approach.size() - 1), Dir.E, fork, Dir.W);
            fork.connect(Dir.E, stub);
            stub.connect(Dir.W, fork);
            Station behind = station(approach.get(1), "Behind");
            Train train = placeTrain(approach.get(4), Dir.W); // x = 0, faces east

            List<String> messages = console(
                    "train " + train.getId() + " stop at station " + behind.getId() + " speed 2;");

            assertFalse(messages.stream().anyMatch(m -> m.contains("no route")),
                    "the station is reachable backwards, got: " + messages);
            Locomotive loco = (Locomotive) train.getDirectorLinker();
            assertTrue(loco.isReversed(), "the order must reverse the train");
            runUntil(() -> missionFinished(train), 600);

            assertEquals(TrainMission.State.COMPLETED, mission(train).state());
            assertEquals(approach.get(1), headTrack(train), "the head must stop on the station");
            assertEquals(0, train.getSpeed());
        }
    }

    @Nested
    @DisplayName("End of track")
    class EndOfTrack {

        @Test
        @DisplayName("stop at end brakes on the last rail before the buffer, without contact")
        void stopAtEnd_stopsBeforeTheBuffer() {
            List<RailTrack> rails = line(0, 6, 0);
            Train train = placeTrain(rails.get(0), Dir.W);

            console("train " + train.getId() + " stop at end speed 4;");
            runUntil(() -> missionFinished(train), 600);

            assertEquals(TrainMission.State.COMPLETED, mission(train).state());
            assertEquals(rails.get(4), headTrack(train),
                    "the train must stop on the last rail before the buffer");
            assertEquals(0, train.getSpeed());
            assertFalse(train.isStalled(), "stop at end must not contact the buffer");
        }

        @Test
        @DisplayName("stop at end on a loop without an end warns and does not move")
        void stopAtEnd_onLoop_warns() {
            // A closed ring of four corner forks joined by four rails: the walk never finds an end.
            ForkRailTrack c0 = fork(0, 0, Dir.S, Dir.E);
            ForkRailTrack c1 = fork(2, 0, Dir.W, Dir.S);
            ForkRailTrack c2 = fork(2, 2, Dir.N, Dir.W);
            ForkRailTrack c3 = fork(0, 2, Dir.E, Dir.N);
            RailTrack h0 = track(1, 0);
            RailTrack h1 = track(1, 2);
            RailTrack v0 = vertical(2, 1);
            RailTrack v1 = vertical(0, 1);
            connect(c0, Dir.E, h0, Dir.W);
            connect(h0, Dir.E, c1, Dir.W);
            connect(c1, Dir.S, v0, Dir.N);
            connect(v0, Dir.S, c2, Dir.N);
            connect(c2, Dir.W, h1, Dir.E);
            connect(h1, Dir.W, c3, Dir.E);
            connect(c3, Dir.N, v1, Dir.S);
            connect(v1, Dir.N, c0, Dir.S);
            Train train = placeTrain(v1, Dir.S); // faces north into the ring

            List<String> messages = console("train " + train.getId() + " stop at end speed 2;");
            runTicks(30);

            assertTrue(messages.stream().anyMatch(m -> m.contains("no end of track")),
                    "expected an unreachable warning, got: " + messages);
            assertTrue(train.getAutopilot().mission().isEmpty());
            assertEquals(v1, headTrack(train), "the train must not move");
        }
    }

    @Nested
    @DisplayName("Blocked and safety")
    class BlockedAndSafety {

        @Test
        @DisplayName("stop when blocked ends at the first block and never resumes")
        void stopWhenBlocked_endsAtTheBlock() {
            World world = twoSegmentWorld();
            Train train = placeTrain(world.a.get(0), Dir.W);
            Train other = placeTrain(world.c.get(1), Dir.W); // occupies segment C
            other.getSafetyManager().claimOccupiedSegments();

            console("train " + train.getId() + " stop when blocked speed 3;");
            runUntil(() -> missionFinished(train), 600);

            assertEquals(TrainMission.State.COMPLETED, mission(train).state(),
                    "the block must complete the mission");
            assertTrue(train.getSafetyManager().isWaitingForBlock(), "train should be waiting");
            runUntil(() -> train.getSpeed() == 0, 300);

            assertEquals(0, train.getSpeed());
            assertEquals(world.b.get(world.b.size() - 1), headTrack(train),
                    "the train must stop on the last rail of its segment");

            // Free the block: the mission is over, the train must stay stopped.
            int xBefore = headX(train);
            console("train " + other.getId() + " set engine on;");
            console("train " + other.getId() + " set speed 3;");
            runUntil(() -> headX(other) >= 18, 600);
            other.setSpeed(0);
            runTicks(200);

            assertEquals(0, train.getSpeed(),
                    "a finished stop-when-blocked mission must not resume");
            assertEquals(xBefore, headX(train), "the train must not move after the block is freed");

            // A new order supersedes the finished mission: the stale block wait must not strand it.
            Sensor sensor = sensor(world.c.get(3), "S10");
            console("train " + train.getId() + " stop at sensor " + sensor.getId() + " speed 3;");
            runUntil(() -> missionFinished(train), 900);

            assertEquals(TrainMission.State.COMPLETED, mission(train).state());
            assertEquals(world.c.get(3), headTrack(train));
            assertEquals(0, train.getSpeed(), "the new mission must run and stop");
        }

        @Test
        @DisplayName("a stop at sensor blocked on the way waits and arrives after the release")
        void stopAtSensor_waitsForTheBlock_thenArrives() {
            World world = twoSegmentWorld();
            Sensor sensor = sensor(world.c.get(3), "S10");
            Train train = placeTrain(world.a.get(0), Dir.W);
            Train other = placeTrain(world.c.get(1), Dir.W); // blocks segment C
            other.getSafetyManager().claimOccupiedSegments();

            console("train " + train.getId() + " stop at sensor " + sensor.getId() + " speed 3;");
            runUntil(() -> train.getSafetyManager().isWaitingForBlock() && train.getSpeed() == 0,
                    600);
            assertTrue(mission(train).isActive(), "the mission must survive the block wait");
            assertEquals(0, train.getSpeed(), "the train should be waiting at its segment end");

            // The other train leaves segment C, freeing the block.
            console("train " + other.getId() + " set engine on;");
            console("train " + other.getId() + " set speed 3;");
            runUntil(() -> headX(other) >= 18, 600);
            other.setSpeed(0);

            runUntil(() -> missionFinished(train), 900);
            assertEquals(TrainMission.State.COMPLETED, mission(train).state(),
                    "the mission must finish after the block is released");
            assertEquals(world.c.get(3), headTrack(train), "the head must stop on the sensor");
            assertEquals(0, train.getSpeed());
        }
    }

    @Nested
    @DisplayName("Itinerary interaction")
    class ItineraryInteraction {

        @Test
        @DisplayName("with an active itinerary the order is rejected and the plan is untouched")
        void activeItinerary_rejectsAndKeepsThePlan() {
            List<RailTrack> rails = line(0, 6, 0);
            station(rails.get(0), "a");
            Station b = station(rails.get(5), "b");
            Sensor sensor = sensor(rails.get(2), "S2");
            Train train = placeTrain(rails.get(0), Dir.W);
            model.setProgram("""
                    create itinerary "Ruta" {
                        add station "b"
                        add station "a"
                    }
                    assign itinerary "Ruta" to train %d;
                    train %d set autopilot true;
                    train %d set speed 3;
                    """.formatted(train.getId(), train.getId(), train.getId()));
            runTicks(5);
            assertTrue(train.isAutoMode(), "the itinerary must be running");
            int indexBefore = train.getAutopilot().currentWaypointIndex();
            List<letrain.segments.Segment> routeBefore =
                    List.copyOf(train.getAutopilot().currentRoute());

            List<String> messages = console(
                    "train " + train.getId() + " stop at sensor " + sensor.getId() + " speed 2;");

            assertTrue(messages.stream().anyMatch(m -> m.contains("is running an itinerary")),
                    "expected the itinerary rejection, got: " + messages);
            assertTrue(train.getAutopilot().mission().isEmpty(), "the order must be rejected");
            assertEquals(indexBefore, train.getAutopilot().currentWaypointIndex());
            assertEquals(routeBefore, train.getAutopilot().currentRoute(),
                    "the itinerary route must be untouched");
            runUntil(() -> train.getStationId() == b.getId(), 900);
            assertEquals(b.getId(), train.getStationId(),
                    "the train must keep following its itinerary");
        }
    }

    @Nested
    @DisplayName("Lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("a new order replaces the running one and autopilot off cancels it")
        void replaceAndCancel() {
            List<RailTrack> rails = line(0, 10, 0);
            Sensor near = sensor(rails.get(4), "Near");
            Sensor far = sensor(rails.get(9), "Far");
            Train train = placeTrain(rails.get(0), Dir.W);

            console("train " + train.getId() + " stop at sensor " + far.getId() + " speed 3;");
            TrainMission first = mission(train);
            assertTrue(first.isActive());
            runTicks(20);

            console("train " + train.getId() + " stop at sensor " + near.getId() + " speed 2;");
            assertEquals(TrainMission.State.CANCELLED, first.state(),
                    "the replaced mission must be cancelled");
            TrainMission second = mission(train);
            assertTrue(second.isActive());
            runUntil(() -> missionFinished(train), 600);
            assertEquals(TrainMission.State.COMPLETED, second.state());
            assertEquals(rails.get(4), headTrack(train));

            console("train " + train.getId() + " stop at sensor " + far.getId() + " speed 3;");
            TrainMission third = mission(train);
            assertTrue(third.isActive());
            console("train " + train.getId() + " set autopilot false;");
            assertEquals(TrainMission.State.CANCELLED, third.state(),
                    "deactivating the autopilot must cancel the mission");
            assertEquals(AutoPilot.Mode.IDLE, train.getAutopilot().mode());
        }
    }

    @Nested
    @DisplayName("Determinism")
    class Determinism {

        @Test
        @DisplayName("the journaled order replays with the same outcome on a fresh copy")
        void replay_isDeterministic(@TempDir File dir) throws Exception {
            List<RailTrack> rails = line(0, 9, 0);
            Sensor sensor = sensor(rails.get(6), "S6");
            placeTrain(rails.get(0), Dir.W);

            GameSaveService saves = new GameSaveService();
            File file = new File(dir, "mission.ltr");
            assertTrue(saves.save(model, file), "could not save the base world");

            Model recorded = saves.load(file).orElseThrow();
            Model replayed = saves.load(file).orElseThrow();

            recorded.getCommandJournal().startRecording();
            PlayerCommandExecutor.execute("train 1 stop at sensor " + sensor.getId() + " speed 3;",
                    recorded, null, null, null);
            recorded.getCommandJournal().stopRecording();
            List<String> journal = recorded.getCommandJournal().entries();
            assertFalse(journal.isEmpty(), "the order must be journaled");

            for (String command : journal) {
                PlayerCommandExecutor.execute(command, replayed, null, null, null);
            }
            for (int i = 0; i < 120; i++) {
                tick(recorded);
                tick(replayed);
            }

            Train t1 = recorded.getTrainFromLocomotiveId(1);
            Train t2 = replayed.getTrainFromLocomotiveId(1);
            assertEquals(headX(t1), headX(t2), "replay must reach the same rail");
            assertEquals(t1.getSpeed(), t2.getSpeed(), "replay must reach the same speed");
            assertEquals(mission(t1).state(), mission(t2).state());
        }

        @Test
        @DisplayName("a game saved mid-mission loads manual and can take a new order")
        void saveMidMission_loadsManual(@TempDir File dir) throws Exception {
            List<RailTrack> rails = line(0, 9, 0);
            Sensor sensor = sensor(rails.get(6), "S6");
            Train train = placeTrain(rails.get(0), Dir.W);
            console("train " + train.getId() + " stop at sensor " + sensor.getId() + " speed 3;");
            runTicks(20);
            assertTrue(mission(train).isActive(), "the mission must be running before the save");

            GameSaveService saves = new GameSaveService();
            File file = new File(dir, "mid-mission.ltr");
            assertTrue(saves.save(model, file), "could not save mid-mission");
            Model loaded = saves.load(file).orElseThrow();
            Train loadedTrain = loaded.getTrainFromLocomotiveId(train.getId());
            assertFalse(loadedTrain.isAutoMode(),
                    "missions are not serialized: the train must load manual, not stuck");

            PlayerCommandExecutor.execute("train " + loadedTrain.getId() + " stop at sensor "
                    + sensor.getId() + " speed 2;", loaded, null, null, null);
            for (int i = 0; i < 2000 && (loadedTrain.getAutopilot().mission().isEmpty()
                    || loadedTrain.getAutopilot().mission().get().isActive()); i++) {
                tick(loaded);
            }
            TrainMission reloadedMission = loadedTrain.getAutopilot().mission().orElse(null);
            assertNotNull(reloadedMission, "the loaded train must accept a new order");
            assertEquals(TrainMission.State.COMPLETED, reloadedMission.state(),
                    "the new order must run on the loaded train");
            assertEquals(rails.get(6).getPosition().getX(), headX(loadedTrain));
            assertEquals(0, loadedTrain.getSpeed());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Fixture helpers
    // ═══════════════════════════════════════════════════════════════════

    /** A, F1, B (short), F2, C (with the sensor), F3, D (long): two real blocks on the way. */
    private static final class World {
        List<RailTrack> a;
        List<RailTrack> b;
        List<RailTrack> c;
        List<RailTrack> d;
    }

    private World twoSegmentWorld() {
        World world = new World();
        world.a = line(0, 3, 0); // x = 0..2
        ForkRailTrack f1 = fork(3, 0, Dir.W, Dir.E);
        world.b = line(4, 2, 0); // x = 4..5
        ForkRailTrack f2 = fork(6, 0, Dir.W, Dir.E);
        world.c = line(7, 8, 0); // x = 7..14
        ForkRailTrack f3 = fork(15, 0, Dir.W, Dir.E);
        world.d = line(16, 5, 0); // x = 16..20
        connect(world.a.get(world.a.size() - 1), Dir.E, f1, Dir.W);
        connect(f1, Dir.E, world.b.get(0), Dir.W);
        connect(world.b.get(world.b.size() - 1), Dir.E, f2, Dir.W);
        connect(f2, Dir.E, world.c.get(0), Dir.W);
        connect(world.c.get(world.c.size() - 1), Dir.E, f3, Dir.W);
        connect(f3, Dir.E, world.d.get(0), Dir.W);
        return world;
    }

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

    /** Vertical rail (north-south routing) for ring fixtures. */
    private RailTrack vertical(int x, int y) {
        RailTrack track = new RailTrack();
        track.setPosition(new Point(x, y));
        track.addRoute(Dir.N, Dir.S);
        track.addRoute(Dir.S, Dir.N);
        railMap().addTrack(new Point(x, y), track);
        return track;
    }

    private ForkRailTrack fork(int x, int y, Dir in, Dir out) {
        ForkRailTrack fork = new ForkRailTrack(model.nextForkId());
        fork.setPosition(new Point(x, y));
        fork.addRoute(in, out);
        fork.addRoute(out, in);
        railMap().addTrack(new Point(x, y), fork);
        model.addFork(fork);
        fork.setNormalRoute();
        return fork;
    }

    /** Straight line of {@code count} rails from x0 at y, connected east-west. */
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
        return train;
    }

    /** Runs a console command (typed form) and returns the notices shown to the user. */
    private List<String> console(String command) {
        List<String> messages = new ArrayList<>();
        PlayerCommandExecutor.execute(command, model, null, null, null,
                (title, text) -> messages.add(text), null, null, null);
        return messages;
    }

    private void runTicks(int count) {
        for (int i = 0; i < count; i++) {
            tick(model);
        }
        model.removeDestroyedTrains();
    }

    private void tick(Model m) {
        if (m.getScheduler() != null) {
            m.getScheduler().tick();
        }
        m.moveLocomotives();
        m.loadAndUnloadTrains();
    }

    private void runUntil(BooleanSupplier condition, int maxTicks) {
        for (int i = 0; i < maxTicks; i++) {
            if (condition.getAsBoolean()) {
                return;
            }
            tick(model);
        }
        model.removeDestroyedTrains();
    }

    private TrainMission mission(Train train) {
        return train.getAutopilot().mission().orElse(null);
    }

    private boolean missionFinished(Train train) {
        TrainMission mission = mission(train);
        return mission != null && !mission.isActive();
    }

    private RailTrack headTrack(Train train) {
        return (RailTrack) train.getPhysicalFront().getTrack();
    }

    private int headX(Train train) {
        return headTrack(train).getPosition().getX();
    }
}
