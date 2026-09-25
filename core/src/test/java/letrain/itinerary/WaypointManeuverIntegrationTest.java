package letrain.itinerary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import letrain.command.PlayerCommandExecutor;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.map.impl.RailMap;
import letrain.mvp.impl.Model;
import letrain.track.Sensor;
import letrain.track.Station;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import letrain.vehicle.rail.impl.Wagon;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Issue #626 (ADR-022 phase 2f): maneuvers inside itineraries. Waypoint actions are executed in
 * order, movement orders are missions that must complete before the next action, the departure
 * releases once the maneuver is done (late is measured, never truncated) and {@code stop at} does
 * not auto-reverse (the author writes the {@code reverse}).
 */
@DisplayName("Issue #626: waypoint maneuvers (run-around)")
class WaypointManeuverIntegrationTest {

    private Model model;

    @BeforeEach
    void setUp() {
        model = new Model(1);
        model.postLoadInit();
    }

    // ═══════════════════════════════════════════════════════════════════
    // Ordered execution and missions as actions
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Ordered execution")
    class OrderedExecution {

        @Test
        @DisplayName("a waypoint mission completes before the next action runs")
        void missionCompletesBeforeNextAction() {
            List<RailTrack> line = line(0, 9, 0);
            Station a = station(line.get(0), "a");
            Station b = station(line.get(8), "b");
            Sensor sensor = sensor(line.get(4), "s4");
            Train train = placeTrain(line.get(0), Dir.W);
            train.setStationId(a.getId());

            List<String> errors = model.setProgram("""
                    create itinerary "maneuver" {
                        add station "a"
                            stop at sensor %d speed 3,
                            reverse
                        add station "b"
                    }
                    assign itinerary "maneuver" to train %d;
                    train %d set autopilot true;
                    """.formatted(sensor.getId(), train.getId(), train.getId()));
            assertTrue(errors.isEmpty(), "unexpected errors: " + errors);

            runUntil(() -> ((Locomotive) train.getDirectorLinker()).isReversed(), 600);

            assertEquals(line.get(4), headTrack(train),
                    "the reverse must run only after the mission reached the sensor");
            assertEquals(0, train.getSpeed());
        }

        @Test
        @DisplayName("without an explicit reverse the maneuver warns and does not move the train")
        void missingReverse_warnsAndDoesNotMove() {
            List<RailTrack> line = line(0, 8, 0);
            Station a = station(line.get(4), "a");
            Station b = station(line.get(7), "b");
            Sensor behind = sensor(line.get(1), "s1");
            Train train = placeTrain(line.get(4), Dir.W); // faces east, sensor behind
            train.setStationId(a.getId());
            List<String> messages = new ArrayList<>();
            train.getAutopilot().setMissionNotifier(messages::add);

            List<String> errors = model.setProgram("""
                    create itinerary "maneuver" {
                        add station "a"
                            stop at sensor %d speed 2
                        add station "b"
                    }
                    assign itinerary "maneuver" to train %d;
                    train %d set autopilot true;
                    """.formatted(behind.getId(), train.getId(), train.getId()));
            assertTrue(errors.isEmpty(), "unexpected errors: " + errors);
            runTicks(80);

            assertTrue(
                    messages.stream().anyMatch(m -> m.contains("add 'reverse' to the itinerary")),
                    "expected the no-reverse warning, got: " + messages);
            assertEquals(line.get(4), headTrack(train), "the train must not move");
            assertEquals(0, train.getSpeed());
        }

        @Test
        @DisplayName("with the reverse written the same maneuver reaches the sensor behind")
        void withReverse_reachesSensorBehind() {
            List<RailTrack> line = line(0, 8, 0);
            Station a = station(line.get(4), "a");
            Station b = station(line.get(7), "b");
            Sensor behind = sensor(line.get(1), "s1");
            Train train = placeTrain(line.get(4), Dir.W);
            train.setStationId(a.getId());

            List<String> errors = model.setProgram("""
                    create itinerary "maneuver" {
                        add station "a"
                            reverse,
                            stop at sensor %d speed 2
                        add station "b"
                    }
                    assign itinerary "maneuver" to train %d;
                    train %d set autopilot true;
                    """.formatted(behind.getId(), train.getId(), train.getId()));
            assertTrue(errors.isEmpty(), "unexpected errors: " + errors);

            runUntil(() -> train.getSpeed() == 0 && headTrack(train) == line.get(1), 900);

            assertEquals(line.get(1), headTrack(train));
            assertTrue(((Locomotive) train.getDirectorLinker()).isReversed());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Departure after the maneuver
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Departure after the maneuver")
    class Departure {

        @Test
        @DisplayName("the departure holds until its time once the maneuver is done")
        void holdsUntilDeparture_afterManeuver() {
            List<RailTrack> line = line(0, 8, 0);
            Station a = station(line.get(0), "a");
            station(line.get(7), "b");
            Sensor sensor = sensor(line.get(3), "s3");
            Train train = placeTrain(line.get(0), Dir.W);
            train.setStationId(a.getId());
            setTime(8, 0);

            List<String> errors = model.setProgram("""
                    create itinerary "maneuver" {
                        add station "a" arrival 08:00,
                            stop at sensor %d speed 2,
                            departure 09:00
                        add station "b"
                    }
                    assign itinerary "maneuver" to train %d;
                    train %d set autopilot true;
                    """.formatted(sensor.getId(), train.getId(), train.getId()));
            assertTrue(errors.isEmpty(), "unexpected errors: " + errors);

            runUntil(() -> train.getAutopilot().mode() == AutoPilot.Mode.WAITING, 900);
            assertEquals(line.get(3), headTrack(train),
                    "the maneuver must finish at the sensor before holding");
            assertEquals(0, train.getSpeed());

            runTicks((int) model.getGameClock().ticksUntil(new letrain.time.GameTime(1, 9, 0)) + 2);
            assertEquals(AutoPilot.Mode.FOLLOWING, train.getAutopilot().mode(),
                    "the scheduled departure must release the hold");
            assertTrue(train.getDirectorLinker().getTargetSpeed() > 0,
                    "the train must resume after the maneuver and the hold");
        }

        @Test
        @DisplayName("a maneuver that ends after the departure time departs late and measures it")
        void lateManeuver_departsLate() {
            List<RailTrack> line = line(0, 12, 0);
            Station a = station(line.get(0), "a");
            station(line.get(11), "b");
            Sensor sensor = sensor(line.get(10), "s10");
            Train train = placeTrain(line.get(0), Dir.W);
            train.setStationId(a.getId());
            setTime(8, 0);

            List<String> errors = model.setProgram("""
                    create itinerary "maneuver" {
                        add station "a" arrival 08:00,
                            stop at sensor %d speed 2,
                            departure 08:02
                        add station "b"
                    }
                    assign itinerary "maneuver" to train %d;
                    train %d set autopilot true;
                    """.formatted(sensor.getId(), train.getId(), train.getId()));
            assertTrue(errors.isEmpty(), "unexpected errors: " + errors);

            runUntil(() -> train.getAutopilot().punctuality()
                    .map(p -> p.stops().stream().anyMatch(s -> s.departure().isPresent()))
                    .orElse(false), 2000);

            assertEquals(line.get(10), headTrack(train), "the maneuver is never truncated");
            Punctuality punctuality = train.getAutopilot().punctuality().orElseThrow();
            assertTrue(
                    punctuality.stops().stream().anyMatch(
                            s -> s.departure().isPresent() && s.departure().getAsInt() > 0),
                    "the late departure must be measured: " + punctuality.describe());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Fork action
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Fork actions")
    class ForkActions {

        @Test
        @DisplayName("fork set curved forces the switch when the action runs")
        void forkSetCurved_forcesTheSwitch() {
            List<RailTrack> line = line(0, 6, 0);
            RailTrack branch = track(2, 1);
            ForkRailTrack fork = fork(2, 0, Dir.W, Dir.E);
            fork.addRoute(Dir.W, Dir.S);
            fork.setNormalRoute();
            connect(line.get(1), Dir.E, fork, Dir.W);
            connect(fork, Dir.E, line.get(3), Dir.W);
            fork.connect(Dir.S, branch);
            branch.connect(Dir.N, fork);
            station(line.get(0), "a");
            station(line.get(5), "b");
            Train train = placeTrain(line.get(0), Dir.W);
            train.setStationId(model.findStationByName("a").getId());
            setTime(8, 0);
            assertFalse(fork.isUsingAlternativeRoute(), "fork starts straight");

            List<String> errors = model.setProgram("""
                    create itinerary "maneuver" {
                        add station "a" arrival 08:00,
                            fork %d set curved,
                            departure 09:00
                        add station "b"
                    }
                    assign itinerary "maneuver" to train %d;
                    train %d set autopilot true;
                    """.formatted(fork.getId(), train.getId(), train.getId()));
            assertTrue(errors.isEmpty(), "unexpected errors: " + errors);

            // The departure holds the train, so no route calculation re-orients the switch after
            // the action: the forced route stays visible.
            assertTrue(fork.isUsingAlternativeRoute(),
                    "the fork action must force the curved route");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Run-around (the contract of the issue)
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Run-around")
    class RunAround {

        private RailTrack railEast3;
        private RailTrack railEast4;
        private RailTrack railWagon;
        private RailTrack railSensor6;
        private ForkRailTrack westJunction;
        private ForkRailTrack eastJunction;
        private Station b;
        private Station c;
        private Sensor sensor5;
        private Sensor sensor6;

        private Train worldAndTrain() {
            // Main: -3 | F0(-2) | -1 sensor6 | 0 wagon | 1 B | F1(2) | 3 | 4 sensor5 | 5 | 6 C
            RailTrack west3 = track(-3, 0);
            westJunction = fork(-2, 0, Dir.S, Dir.E);
            westJunction.addRoute(Dir.S, Dir.W);
            westJunction.setNormalRoute();
            railSensor6 = track(-1, 0);
            railWagon = track(0, 0);
            RailTrack railB = track(1, 0);
            eastJunction = fork(2, 0, Dir.E, Dir.W);
            eastJunction.addRoute(Dir.E, Dir.S);
            eastJunction.setNormalRoute();
            railEast3 = track(3, 0);
            railEast4 = track(4, 0);
            RailTrack railEast5 = track(5, 0);
            RailTrack railEast6 = track(6, 0);

            RailTrack bypassWest = corner(-2, 1, Dir.E, Dir.N);
            RailTrack bypass1 = track(-1, 1);
            RailTrack bypass2 = track(0, 1);
            RailTrack bypass3 = track(1, 1);
            RailTrack bypassEast = corner(2, 1, Dir.N, Dir.W);

            connect(west3, Dir.E, westJunction, Dir.W);
            connect(westJunction, Dir.E, railSensor6, Dir.W);
            connect(railSensor6, Dir.E, railWagon, Dir.W);
            connect(railWagon, Dir.E, railB, Dir.W);
            connect(railB, Dir.E, eastJunction, Dir.W);
            connect(eastJunction, Dir.E, railEast3, Dir.W);
            connect(railEast3, Dir.E, railEast4, Dir.W);
            connect(railEast4, Dir.E, railEast5, Dir.W);
            connect(railEast5, Dir.E, railEast6, Dir.W);
            westJunction.connect(Dir.S, bypassWest);
            bypassWest.connect(Dir.N, westJunction);
            connect(bypassWest, Dir.E, bypass1, Dir.W);
            connect(bypass1, Dir.E, bypass2, Dir.W);
            connect(bypass2, Dir.E, bypass3, Dir.W);
            connect(bypass3, Dir.E, bypassEast, Dir.W);
            eastJunction.connect(Dir.S, bypassEast);
            bypassEast.connect(Dir.N, eastJunction);

            b = station(railB, "b");
            c = station(railEast6, "c");
            sensor5 = sensor(railEast4, "s5");
            sensor6 = sensor(railSensor6, "s6");

            // Loco at B (1,0) facing east, wagon behind at (0,0): the run-around leaves it.
            Locomotive loco = new Locomotive(model.nextLocomotiveId(), "A");
            loco.setEngineOn(true);
            Wagon wagon = new Wagon("b");
            Train train = new Train(model.nextTrainId());
            train.setModel(model);
            train.pushBack(loco);
            train.pushBack(wagon);
            train.setDirectorLinker(loco);
            model.addLocomotive(loco);
            model.addWagon(wagon);
            railB.enterLinkerFromDir(Dir.W, loco);
            railWagon.enterLinkerFromDir(Dir.W, wagon);
            train.rebind();
            train.getMovementManager().refreshLinkersDirection();
            train.setStationId(b.getId());
            train.getSafetyManager().claimOccupiedSegments();
            train.getSafetyManager().acquireInitialLocks();
            return train;
        }

        @Test
        @DisplayName("the run-around example works end-to-end and the route is recalculated")
        void runAround_endToEnd() {
            Train train = worldAndTrain();
            Locomotive loco = (Locomotive) train.getDirectorLinker();
            setTime(8, 0);

            List<String> errors = model.setProgram("""
                    create itinerary "runaround" {
                        add station "b" arrival 08:00,
                            uncouple backward 1,
                            stop at sensor %d speed 2,
                            reverse,
                            fork %d set curved,
                            stop at sensor %d speed 2,
                            couple forward 1,
                            reverse,
                            departure 09:00
                        add station "c"
                    }
                    assign itinerary "runaround" to train %d;
                    train %d set autopilot true;
                    """.formatted(sensor5.getId(), eastJunction.getId(), sensor6.getId(),
                    train.getId(), train.getId()));
            assertTrue(errors.isEmpty(), "unexpected errors: " + errors);

            assertEquals(1, train.getLinkers().size(), "uncouple backward must detach the wagon");
            assertFalse(train.getLinkers().contains(loco) && train.getLinkers().size() == 2,
                    "sanity");

            runUntil(() -> train.getLinkers().size() == 2, 2000);
            assertEquals(2, train.getLinkers().size(),
                    "couple forward must pick the wagon up again (loco="
                            + loco.getTrack().getPosition() + " dir=" + loco.getDir() + " entry="
                            + loco.getEntryDir() + " wagons=" + model.getWagons().size()
                            + " wagonTrack=" + (model.getWagons().isEmpty() ? "-"
                                    : model.getWagons().get(0).getTrack().getPosition()));
            assertEquals(railSensor6, headTrack(train),
                    "the loco must stop at sensor 6 to couple (other side of the wagon)");
            assertTrue(train.getLinkers().stream().anyMatch(l -> l.getTrack() == railWagon),
                    "the wagon must be part of the train after coupling");
            assertTrue(eastJunction.isUsingAlternativeRoute(),
                    "the fork action must have forced the curved route");
            assertEquals(Dir.W, loco.getDir(),
                    "the final reverse must leave the loco facing the departure direction");

            // The departure holds until 09:00 and then releases with the route recalculated.
            runUntil(() -> train.getAutopilot().mode() == AutoPilot.Mode.WAITING, 600);
            assertEquals(AutoPilot.Mode.WAITING, train.getAutopilot().mode(),
                    "the departure must hold once the maneuver is done");

            int needed = (int) model.getGameClock().ticksUntil(new letrain.time.GameTime(1, 9, 0));
            assertTrue(needed > 0, "the maneuver must end before the departure");
            runTicks(needed + 4);
            assertEquals(AutoPilot.Mode.FOLLOWING, train.getAutopilot().mode(),
                    "the departure must release the train");

            runUntil(() -> train.getStationId() == c.getId(), 4000);
            assertEquals(c.getId(), train.getStationId(),
                    "after the maneuver the route to the next waypoint is recalculated");
            assertEquals(2, train.getLinkers().size(), "the whole consist reaches C");
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

    /** Rail that turns between the two given directions (bypass corners). */
    private RailTrack corner(int x, int y, Dir a, Dir b) {
        RailTrack track = new RailTrack();
        track.setPosition(new Point(x, y));
        track.addRoute(a, b);
        track.addRoute(b, a);
        railMap().addTrack(new Point(x, y), track);
        return track;
    }

    private ForkRailTrack fork(int x, int y, Dir in, Dir out) {
        ForkRailTrack fork = new ForkRailTrack(model.nextForkId());
        fork.setPosition(new Point(x, y));
        fork.addRoute(in, out);
        railMap().addTrack(new Point(x, y), fork);
        model.addFork(fork);
        return fork;
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

    private RailTrack headTrack(Train train) {
        return (RailTrack) train.getPhysicalFront().getTrack();
    }
}
