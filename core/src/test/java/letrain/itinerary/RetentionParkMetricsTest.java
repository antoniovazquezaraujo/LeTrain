package letrain.itinerary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import letrain.command.PlayerCommandExecutor;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.mvp.impl.Model;
import letrain.track.Sensor;
import letrain.track.Station;
import letrain.track.Track;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;
import letrain.time.GameTime;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * ADR-022 phase 2b integration tests: schedule retention at stations and sensors, the new
 * {@code park} action and the punctuality metrics shown by {@code info train}. The clock is driven
 * with {@code time set} plus ticks, mirroring {@code SimulationController} order (scheduler, clock,
 * physics).
 */
@DisplayName("Retention, park and punctuality (ADR-022 phase 2b)")
class RetentionParkMetricsTest {

    private Model model;

    @BeforeEach
    void setUp() {
        model = new Model(1);
        model.postLoadInit();
    }

    // ═══════════════════════════════════════════════════════════════════
    // Retention
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Retention until departure")
    class Retention {

        @Test
        @DisplayName("waits at the station until the departure and leaves exactly then")
        void holdsUntilDeparture_andLeavesOnTime() {
            List<RailTrack> line = makeLine(4);
            Station a = makeStation(line.get(0), "A");
            Station b = makeStation(line.get(3), "B");
            Train t = makeTrain(line.get(0), Dir.W);
            Locomotive loco = (Locomotive) t.getDirectorLinker();
            setTime(8, 0);

            List<String> errors = model.setProgram("""
                    create itinerary "Ruta" {
                        add station %d departure 08:05
                        add station %d
                    }
                    assign itinerary "Ruta" to train %d;
                    train %d set autopilot true;
                    train %d set speed 3;
                    """.formatted(a.getId(), b.getId(), t.getId(), t.getId(), t.getId()));
            assertTrue(errors.isEmpty(), "unexpected errors: " + errors);
            assertEquals(AutoPilot.Mode.WAITING, t.getAutopilot().mode(),
                    "the autopilot must hold while early");
            assertEquals(0, loco.getTargetSpeed(),
                    "a requested speed during the hold must be deferred, not applied");

            runTicks((int) model.getGameClock().ticksUntil(new GameTime(1, 8, 4)));
            assertEquals(new GameTime(1, 8, 4), model.getGameClock().now());
            assertEquals(0, t.getSpeed(), "must not leave before the departure");
            assertEquals(0, loco.getTargetSpeed(), "must not leave before the departure");
            assertEquals(a.getId(), t.getStationId());

            runTicks((int) model.getGameClock().ticksUntil(new GameTime(1, 8, 5)));
            assertEquals(new GameTime(1, 8, 5), model.getGameClock().now());
            assertEquals(0, loco.getTargetSpeed(),
                    "release happens at the departure minute, not before");

            runTicks(1);
            assertEquals(AutoPilot.Mode.FOLLOWING, t.getAutopilot().mode());
            assertTrue(loco.getTargetSpeed() > 0, "departure must restore the cruise speed");
        }

        @Test
        @DisplayName("arriving late departs immediately and measures the deviation")
        void arrivingLate_departsImmediately() {
            List<RailTrack> line = makeLine(4);
            Station a = makeStation(line.get(0), "A");
            Station b = makeStation(line.get(3), "B");
            Train t = makeTrain(line.get(0), Dir.W);
            Locomotive loco = (Locomotive) t.getDirectorLinker();
            setTime(8, 10);

            List<String> errors = model.setProgram("""
                    create itinerary "Ruta" {
                        add station %d arrival 08:05, departure 08:05
                        add station %d
                    }
                    assign itinerary "Ruta" to train %d;
                    train %d set autopilot true;
                    train %d set speed 3;
                    """.formatted(a.getId(), b.getId(), t.getId(), t.getId(), t.getId()));
            assertTrue(errors.isEmpty(), "unexpected errors: " + errors);
            assertNotEquals(AutoPilot.Mode.WAITING, t.getAutopilot().mode(),
                    "a late train must not hold");
            assertEquals(3, loco.getTargetSpeed(), "a late train departs immediately");

            String info = infoTrain(loco.getId());
            assertTrue(info.contains("Station " + a.getId() + ": arrival +5 min, departure +5 min"),
                    info);
            assertTrue(info.contains("Current: +5 min"), info);
            assertTrue(info.contains("Average: +5.0 min"), info);
            assertTrue(info.contains("Max: +5 min"), info);

            runTicks(20);
            assertTrue(t.getSpeed() > 0, "the train must be moving right away");
        }

        @Test
        @DisplayName("retention also applies to sensors")
        void sensorRetention_holdsUntilDeparture() {
            List<RailTrack> line = makeLine(9);
            Station a = makeStation(line.get(0), "A");
            Station b = makeStation(line.get(8), "B");
            Sensor sensor = new Sensor(model.nextSensorId());
            sensor.setName("S");
            line.get(2).setComponent(sensor);
            model.addSensor(sensor);
            Train t = makeTrain(line.get(0), Dir.W);
            setTime(8, 0);

            List<String> errors = model.setProgram("""
                    create itinerary "Ruta" {
                        add sensor %d departure 08:10
                        add station %d
                    }
                    assign itinerary "Ruta" to train %d;
                    train %d set autopilot true;
                    train %d set speed 2;
                    """.formatted(sensor.getId(), b.getId(), t.getId(), t.getId(), t.getId()));
            assertTrue(errors.isEmpty(), "unexpected errors: " + errors);

            runUntil(() -> t.getAutopilot().mode() == AutoPilot.Mode.WAITING && t.getSpeed() == 0,
                    300);
            assertEquals(AutoPilot.Mode.WAITING, t.getAutopilot().mode(),
                    "the sensor must retain the train");
            assertNotEquals(b.getId(), t.getStationId(), "B must not be reached while held");

            runTicks((int) model.getGameClock().ticksUntil(new GameTime(1, 8, 10)) + 1);
            assertEquals(AutoPilot.Mode.FOLLOWING, t.getAutopilot().mode(),
                    "the scheduled departure must release the sensor hold");
            runUntil(() -> t.getStationId() == b.getId(), 600);
            assertEquals(b.getId(), t.getStationId());
        }

        @Test
        @DisplayName("safety wins: a blocked next block keeps the train waiting after its departure")
        void blockedNextBlock_keepsTheTrainAtTheWaypoint() {
            RailTrack t0 = makeTrack(0, 0);
            RailTrack t1 = makeTrack(1, 0);
            connect(t0, t1);
            ForkRailTrack fork = makeFork(2, 0);
            fork.addRoute(Dir.W, Dir.E);
            fork.addRoute(Dir.E, Dir.W);
            connect(t1, fork);
            RailTrack t3 = makeTrack(3, 0);
            RailTrack t4 = makeTrack(4, 0);
            RailTrack t5 = makeTrack(5, 0);
            connect(fork, t3);
            connect(t3, t4);
            connect(t4, t5);
            Station a = makeStation(t0, "A");
            Station b = makeStation(t5, "B");
            model.postLoadInit();

            Train blocked = makeTrain(t4, Dir.W); // occupies the segment beyond the fork
            Train t = makeTrain(t0, Dir.W);
            ((Locomotive) blocked.getDirectorLinker()).setEngineOn(false);
            blocked.getSafetyManager().claimOccupiedSegments();
            letrain.segments.Segment blockedSegment = model.getRailwayGraph().getSegment(t4);
            assertTrue(model.getBlockManager().getOwners(blockedSegment).contains(blocked));

            setTime(8, 0);
            List<String> errors = model.setProgram("""
                    create itinerary "Ruta" {
                        add station %d departure 08:05
                        add station %d
                    }
                    assign itinerary "Ruta" to train %d;
                    train %d set autopilot true;
                    train %d set speed 3;
                    """.formatted(a.getId(), b.getId(), t.getId(), t.getId(), t.getId()));
            assertTrue(errors.isEmpty(), "unexpected errors: " + errors);

            // Release happens at 08:05, but the next block is owned by the other train.
            runTicks((int) model.getGameClock().ticksUntil(new GameTime(1, 8, 5)) + 2);
            assertEquals(AutoPilot.Mode.FOLLOWING, t.getAutopilot().mode(),
                    "the schedule released it");
            assertFalse(t.getSafetyManager().hasPermissionToMove(),
                    "the block must keep the train waiting");
            runTicks(60);
            assertEquals(0, t.getSpeed(), "the schedule must not override the block");
            assertTrue(t.getPhysicalFront().getPosition().getX() <= 2,
                    "the train must not enter the blocked segment");
        }

        @Test
        @DisplayName("a waypoint without times keeps the old pass-through behaviour and no metric")
        void withoutTimes_noRetention_noPunctuality() {
            List<RailTrack> line = makeLine(4);
            Station a = makeStation(line.get(0), "A");
            Station b = makeStation(line.get(3), "B");
            Train t = makeTrain(line.get(0), Dir.W);
            Locomotive loco = (Locomotive) t.getDirectorLinker();
            setTime(8, 0);

            List<String> errors = model.setProgram("""
                    create itinerary "Ruta" {
                        add station %d
                        add station %d
                    }
                    assign itinerary "Ruta" to train %d;
                    train %d set autopilot true;
                    train %d set speed 3;
                    """.formatted(a.getId(), b.getId(), t.getId(), t.getId(), t.getId()));
            assertTrue(errors.isEmpty(), "unexpected errors: " + errors);
            assertEquals(AutoPilot.Mode.FOLLOWING, t.getAutopilot().mode());
            assertEquals(3, loco.getTargetSpeed(), "no times means no retention");

            runTicks(150);
            assertTrue(t.getSpeed() > 0 || t.getStationId() == b.getId(),
                    "the train must move as before");
            assertFalse(infoTrain(loco.getId()).contains("Punctuality"),
                    "a train without times must not show any metric");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Park
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Park")
    class Park {

        @Test
        @DisplayName("parks engine off with autopilot on and the scheduled departure starts it")
        void park_engineOffAutopilotOn_departureStartsEngine() {
            List<RailTrack> line = makeLine(4);
            Station a = makeStation(line.get(0), "A");
            Station b = makeStation(line.get(3), "B");
            Train t = makeTrain(line.get(0), Dir.W);
            Locomotive loco = (Locomotive) t.getDirectorLinker();
            setTime(8, 0);

            List<String> errors = model.setProgram("""
                    create itinerary "Ruta" {
                        add station %d park, departure 08:03
                        add station %d
                    }
                    assign itinerary "Ruta" to train %d;
                    train %d set autopilot true;
                    train %d set speed 3;
                    """.formatted(a.getId(), b.getId(), t.getId(), t.getId(), t.getId()));
            assertTrue(errors.isEmpty(), "unexpected errors: " + errors);
            assertFalse(loco.isEngineOn(), "park must switch the engine off");
            assertTrue(t.isAutoMode(), "park must keep the autopilot");
            assertEquals(AutoPilot.Mode.WAITING, t.getAutopilot().mode());
            assertEquals(0, loco.getTargetSpeed());

            runTicks((int) model.getGameClock().ticksUntil(new GameTime(1, 8, 3)));
            assertFalse(loco.isEngineOn(), "still parked before the departure");

            runTicks(2);
            assertTrue(loco.isEngineOn(), "the scheduled departure must start the engine");
            assertEquals(3, loco.getTargetSpeed(), "the deferred speed must be restored");
            runTicks(60);
            assertTrue(t.getSpeed() > 0, "the train must move after the departure");
        }

        @Test
        @DisplayName("park while moving brakes first and only then stops the engine")
        void parkWhileMoving_brakesThenEngineOff() {
            // B sits mid-line (C is beyond it) so the stop is an inertia brake, not a buffer
            // contact: the train must stop first and only then switch the engine off.
            List<RailTrack> line = makeLine(9);
            Station a = makeStation(line.get(0), "A");
            Station b = makeStation(line.get(4), "B");
            Station c = makeStation(line.get(8), "C");
            Train t = makeTrain(line.get(0), Dir.W);
            Locomotive loco = (Locomotive) t.getDirectorLinker();
            setTime(8, 0);

            List<String> errors = model.setProgram("""
                    create itinerary "Ruta" {
                        add station %d
                        add station %d park
                        add station %d
                    }
                    assign itinerary "Ruta" to train %d;
                    train %d set autopilot true;
                    train %d set speed 2;
                    """.formatted(a.getId(), b.getId(), c.getId(), t.getId(), t.getId(),
                    t.getId()));
            assertTrue(errors.isEmpty(), "unexpected errors: " + errors);

            runUntil(() -> !loco.isEngineOn(), 500);
            assertFalse(loco.isEngineOn(), "park must end with the engine off");
            assertEquals(0, t.getSpeed(), "the train must be stopped when parked");
            assertTrue(t.isAutoMode(), "park must keep the autopilot");
            assertNotEquals(c.getId(), t.getStationId(), "the train must park at B, not reach C");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Determinism
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Determinism")
    class Determinism {

        @Test
        @DisplayName("same clock commands and ticks produce the same punctuality state")
        void replay_producesIdenticalState() {
            assertEquals(scenarioSnapshot(), scenarioSnapshot(),
                    "retention and metrics must be deterministic on tick-driven game time");
        }

        /** Runs the late-departure scenario on a fresh model and returns a canonical snapshot. */
        private String scenarioSnapshot() {
            Model m = new Model(1);
            m.postLoadInit();
            List<RailTrack> line = makeLine(m, 4);
            Station a = makeStation(m, line.get(0), "A");
            Station b = makeStation(m, line.get(3), "B");
            Train t = makeTrain(m, line.get(0), Dir.W);
            Locomotive loco = (Locomotive) t.getDirectorLinker();
            setTime(m, 8, 10);

            List<String> errors = m.setProgram("""
                    create itinerary "Ruta" {
                        add station %d arrival 08:05, departure 08:05
                        add station %d
                    }
                    assign itinerary "Ruta" to train %d;
                    train %d set autopilot true;
                    train %d set speed 3;
                    """.formatted(a.getId(), b.getId(), t.getId(), t.getId(), t.getId()));
            assertTrue(errors.isEmpty(), "unexpected errors: " + errors);

            runTicks(m, 150);
            return m.getGameClock().now() + "|" + t.getStationId() + "|" + t.getSpeed() + "|"
                    + t.getAutopilot().mode() + "|"
                    + t.getAutopilot().punctuality().map(Punctuality::describe).orElse("");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════

    /** Horizontal line of straight tracks at y=0, connected west-east. */
    private List<RailTrack> makeLine(int length) {
        return makeLine(model, length);
    }

    private List<RailTrack> makeLine(Model m, int length) {
        List<RailTrack> tracks = new ArrayList<>();
        for (int x = 0; x < length; x++) {
            tracks.add(makeTrack(m, x, 0));
        }
        for (int x = 0; x + 1 < length; x++) {
            connect(tracks.get(x), tracks.get(x + 1));
        }
        return tracks;
    }

    private RailTrack makeTrack(int x, int y) {
        return makeTrack(model, x, y);
    }

    private RailTrack makeTrack(Model m, int x, int y) {
        RailTrack track = new RailTrack();
        track.setPosition(new Point(x, y));
        track.addRoute(Dir.E, Dir.W);
        track.addRoute(Dir.W, Dir.E);
        m.getRailMap().addTrack(new Point(x, y), track);
        return track;
    }

    private ForkRailTrack makeFork(int x, int y) {
        ForkRailTrack fork = new ForkRailTrack(model.nextForkId());
        fork.setPosition(new Point(x, y));
        model.getRailMap().addTrack(new Point(x, y), fork);
        model.addFork(fork);
        return fork;
    }

    private void connect(Track from, Track to) {
        from.connect(Dir.E, to);
        to.connect(Dir.W, from);
    }

    private Station makeStation(RailTrack track, String name) {
        return makeStation(model, track, name);
    }

    private Station makeStation(Model m, RailTrack track, String name) {
        Station station = new Station(m.nextStationId());
        station.setName(name);
        station.setTrack(track);
        track.setComponent(station);
        m.addStation(station);
        return station;
    }

    /** Train placed on a track, entering from the west (so it faces east). */
    private Train makeTrain(RailTrack track, Dir entryFrom) {
        return makeTrain(model, track, entryFrom);
    }

    private Train makeTrain(Model m, RailTrack track, Dir entryFrom) {
        Locomotive loco = new Locomotive(m.nextLocomotiveId(), "A", "RED");
        loco.setEngineOn(true);
        loco.setTargetSpeed(0);
        Train train = new Train(m.nextTrainId());
        train.setModel(m);
        train.pushBack(loco);
        train.setDirectorLinker(loco);
        m.addLocomotive(loco);
        track.enterLinkerFromDir(entryFrom, loco);
        if (track.getComponent() instanceof Station station) {
            train.setStationId(station.getId());
        }
        return train;
    }

    /** Console {@code time set} (the journaled path), asserting success. */
    private void setTime(int hour, int minute) {
        setTime(model, hour, minute);
    }

    private void setTime(Model m, int hour, int minute) {
        String error = PlayerCommandExecutor.execute("time set " + hour + ":" + minute + ";", m);
        assertNull(error, error);
    }

    private String infoTrain(int locomotiveId) {
        List<String> messages = new ArrayList<>();
        String error = PlayerCommandExecutor.execute("info train " + locomotiveId + ";", model,
                null, null, null, (title, text) -> messages.add(text), null);
        assertNull(error, error);
        return String.join("\n", messages);
    }

    private void runTicks(int count) {
        runTicks(model, count);
    }

    /**
     * One tick: scheduler first, then the game clock, then physics (SimulationController order).
     */
    private void runTicks(Model m, int count) {
        for (int i = 0; i < count; i++) {
            if (m.getScheduler() != null) {
                m.getScheduler().tick();
            }
            m.getGameClock().tick();
            m.moveLocomotives();
            m.loadAndUnloadTrains();
        }
        m.removeDestroyedTrains();
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
