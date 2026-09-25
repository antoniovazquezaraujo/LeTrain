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
                            stop at sensor %d speed 2,
                            reverse
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
            assertFalse(((Locomotive) train.getDirectorLinker()).isReversed(),
                    "a rejected maneuver must abort the remaining waypoint actions (review)");
        }

        @Test
        @DisplayName("a mission already satisfied does not block the action list")
        void alreadyAtTarget_doesNotBlockTheList() {
            List<RailTrack> line = line(0, 8, 0);
            Station a = station(line.get(0), "a");
            station(line.get(7), "b");
            Train train = placeTrain(line.get(0), Dir.W);
            train.setStationId(a.getId());
            setTime(8, 0);

            List<String> errors = model.setProgram("""
                    create itinerary "maneuver" {
                        add station "a" arrival 08:00,
                            stop at station "a" speed 2,
                            reverse,
                            departure 09:00
                        add station "b"
                    }
                    assign itinerary "maneuver" to train %d;
                    train %d set autopilot true;
                    """.formatted(train.getId(), train.getId()));
            assertTrue(errors.isEmpty(), "unexpected errors: " + errors);
            runTicks(40);

            assertTrue(((Locomotive) train.getDirectorLinker()).isReversed(),
                    "the action after an already-satisfied mission must run (review M1)");
            assertEquals(line.get(0), headTrack(train), "the train must not move");
            assertEquals(AutoPilot.Mode.WAITING, train.getAutopilot().mode(),
                    "the departure must hold after the maneuver");
        }

        @Test
        @DisplayName("a chain of repeated waypoints advances to the next distinct stop")
        void repeatedWaypointChain_advancesToNextStop() {
            List<RailTrack> line = line(0, 3, 0);
            ForkRailTrack fork = fork(3, 0, Dir.W, Dir.E);
            fork.addRoute(Dir.W, Dir.S);
            RailTrack stub = track(3, 1);
            List<RailTrack> far = line(4, 3, 0); // x = 4..6
            connect(line.get(2), Dir.E, fork, Dir.W);
            connect(fork, Dir.E, far.get(0), Dir.W);
            fork.connect(Dir.S, stub);
            stub.connect(Dir.N, fork);
            Station a = station(line.get(0), "a");
            Station b = station(far.get(2), "b");
            Train train = placeTrain(line.get(0), Dir.W); // faces b
            train.setStationId(a.getId());

            List<String> errors = model.setProgram("""
                    create itinerary "chain" {
                        add station "a" reverse
                        add station "a"
                        add station "a" reverse
                        add station "b"
                    }
                    assign itinerary "chain" to train %d;
                    train %d set autopilot true;
                    train %d set speed 3;
                    """.formatted(train.getId(), train.getId(), train.getId()));
            assertTrue(errors.isEmpty(), "unexpected errors: " + errors);

            runUntil(() -> train.getStationId() == b.getId(), 900);

            assertEquals(b.getId(), train.getStationId(),
                    "the chain of repeated stops must advance to the next distinct stop (review)");
            assertFalse(((Locomotive) train.getDirectorLinker()).isReversed(),
                    "the reverses of the first and third waypoints must both run");
        }

        @Test
        @DisplayName("a missing A* route with the destination ahead does not blame the sense")
        void aheadButNoRoute_doesNotBlameTheSense() {
            List<RailTrack> line = line(0, 6, 0);
            RailTrack stub = track(3, 1);
            ForkRailTrack fork = fork(3, 0, Dir.W, Dir.E);
            fork.addRoute(Dir.W, Dir.S);
            connect(line.get(2), Dir.E, fork, Dir.W);
            connect(fork, Dir.E, line.get(4), Dir.W);
            fork.connect(Dir.S, stub);
            stub.connect(Dir.N, fork);
            fork.setAlternativeRoute(); // the physical walk diverges into the dead-end stub
            Sensor sensor = sensor(line.get(4), "s4");
            Train train = placeTrain(line.get(0), Dir.W);
            AutoPilot autopilot = train.getAutopilot();
            autopilot.setPathfinder((from, to, entryDir) -> List.of());
            List<String> messages = new ArrayList<>();
            autopilot.setMissionNotifier(messages::add);

            boolean accepted = autopilot.startMission(
                    TrainMission.forItinerary(TrainMission.Kind.SENSOR, sensor.getId(), 2));

            assertFalse(accepted, "without any route the maneuver must be rejected");
            assertTrue(messages.stream().anyMatch(m -> m.contains("no route to sensor")),
                    "expected the no-route warning, got: " + messages);
            assertFalse(messages.stream().anyMatch(m -> m.contains("add 'reverse'")),
                    "the reverse hint must only appear when the destination is behind");
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
    // Shunting and blocks
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Shunting and blocks")
    class ShuntingAndBlocks {

        @Test
        @DisplayName("a destination canton occupied by an unrelated train waits instead of invading")
        void targetCantonWithIntruder_waitsAndResumes() {
            List<RailTrack> approach = line(0, 3, 0);
            ForkRailTrack fork = fork(3, 0, Dir.W, Dir.E);
            List<RailTrack> target = line(4, 6, 0); // x = 4..9
            ForkRailTrack exit = fork(10, 0, Dir.W, Dir.E);
            List<RailTrack> far = line(11, 4, 0); // x = 11..14, where the intruder parks
            connect(approach.get(2), Dir.E, fork, Dir.W);
            connect(fork, Dir.E, target.get(0), Dir.W);
            connect(target.get(5), Dir.E, exit, Dir.W);
            connect(exit, Dir.E, far.get(0), Dir.W);
            Station a = station(approach.get(0), "a");
            Station b = station(approach.get(2), "b");
            Sensor sensor = sensor(target.get(2), "s6");
            Train subject = placeTrain(approach.get(0), Dir.W);
            subject.setStationId(a.getId());
            Train intruder = placeTrain(target.get(1), Dir.W); // parked in the target canton

            List<String> errors = model.setProgram("""
                    create itinerary "maneuver" {
                        add station "a"
                            stop at sensor %d speed 2
                        add station "b"
                    }
                    assign itinerary "maneuver" to train %d;
                    train %d set autopilot true;
                    """.formatted(sensor.getId(), subject.getId(), subject.getId()));
            assertTrue(errors.isEmpty(), "unexpected errors: " + errors);

            runUntil(() -> subject.getSafetyManager().isWaitingForBlock(), 600);
            runTicks(200);

            assertEquals(0, subject.getSpeed(), "the mission must wait for the intruder");
            assertFalse(subject.isPendingManualMode(),
                    "an unrelated train must not trigger the shunting exemption (review M2)");
            assertTrue(headX(subject) < 4, "the subject must stay before the blocked canton");
            assertFalse(subject.isStalled(), "no contact");
            assertTrue(subject.getAutopilot().mission().orElseThrow().isActive(),
                    "the mission must survive the wait");

            // The intruder leaves the canton: the mission resumes and reaches the sensor.
            intruder.setSpeed(3);
            runUntil(() -> !subject.getSafetyManager().isWaitingForBlock(), 1200);
            assertFalse(subject.getSafetyManager().isWaitingForBlock(),
                    "the release must wake the waiting train");

            runUntil(() -> subject.getAutopilot().mission().map(m -> !m.isActive()).orElse(false),
                    1200);
            assertEquals(TrainMission.State.COMPLETED,
                    subject.getAutopilot().mission().orElseThrow().state());
            assertEquals(target.get(2), headTrack(subject));
            assertFalse(subject.isStalled(), "no contact");
        }

        @Test
        @DisplayName("a wagon-only train keeps its canton after a reload")
        void wagonOnlyTrain_claimsItsCantonAfterLoad() {
            List<RailTrack> line = line(0, 4, 0);
            RailTrack wagonRail = line.get(1);
            Wagon wagon = new Wagon("b");
            Train wagonTrain = new Train(model.nextTrainId());
            wagonTrain.setModel(model);
            wagonTrain.pushBack(wagon);
            wagon.setTrain(wagonTrain);
            model.addWagon(wagon);
            wagonRail.enterLinkerFromDir(Dir.W, wagon);

            // Simulate a reload: the block manager is recreated and the trains are re-claimed
            // from their linkers (locomotives and wagon-only parts).
            model.getBlockManager().clearAll();
            model.postLoadInit();

            letrain.segments.Segment segment = model.getRailwayGraph().getSegment(wagonRail);
            assertTrue(model.getBlockManager().getOwners(segment).contains(wagonTrain),
                    "the wagon-only part must claim its canton after a load (review minor)");
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
            assertEquals(List.of(train),
                    model.getBlockManager()
                            .getOwners(model.getRailwayGraph().getSegment(railWagon)),
                    "the vanished wagon part must release its share of the canton");
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
    // Curved-fork map (user report: simple.json)
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Curved-fork map (user report)")
    class CurvedForkMap {

        private List<RailTrack> east;
        private List<RailTrack> middle;
        private ForkRailTrack fork1;
        private Station station;

        /**
         * Equivalent of the user's simple.json: a main line with a station in the middle, two forks
         * whose <b>normal route curves away</b> to a bypass, and a loose locomotive east of the
         * station facing west.
         *
         * <pre>
         *   y=0:  loco(-1..-9)  F1(-10)  middle(-11..-27, station at -16)  F2(-28)  west stub(-29..-31)
         *   y=1:                 bypass(-10..-28)
         * </pre>
         */
        private Train worldAndTrain() {
            east = new ArrayList<>();
            for (int x = -1; x >= -9; x--) {
                east.add(track(x, 0));
            }
            fork1 = fork(-10, 0, Dir.E, Dir.S);
            fork1.addRoute(Dir.E, Dir.W); // alternative: straight to the station
            fork1.setNormalRoute(); // normal: curves south to the bypass
            middle = new ArrayList<>();
            for (int x = -11; x >= -27; x--) {
                middle.add(track(x, 0));
            }
            ForkRailTrack fork2 = fork(-28, 0, Dir.W, Dir.S);
            fork2.addRoute(Dir.W, Dir.E);
            fork2.setNormalRoute();
            List<RailTrack> west = new ArrayList<>();
            for (int x = -29; x >= -31; x--) {
                west.add(track(x, 0));
            }
            RailTrack bypassWest = corner(-10, 1, Dir.N, Dir.W);
            RailTrack bypassEast = corner(-28, 1, Dir.N, Dir.E);
            List<RailTrack> bypass = new ArrayList<>();
            for (int x = -11; x >= -27; x--) {
                bypass.add(track(x, 1));
            }

            for (int i = 0; i + 1 < east.size(); i++) {
                connect(east.get(i), Dir.W, east.get(i + 1), Dir.E);
            }
            connect(east.get(east.size() - 1), Dir.W, fork1, Dir.E);
            connect(fork1, Dir.W, middle.get(0), Dir.E);
            for (int i = 0; i + 1 < middle.size(); i++) {
                connect(middle.get(i), Dir.W, middle.get(i + 1), Dir.E);
            }
            connect(middle.get(middle.size() - 1), Dir.W, fork2, Dir.E);
            connect(fork2, Dir.W, west.get(0), Dir.E);
            for (int i = 0; i + 1 < west.size(); i++) {
                connect(west.get(i), Dir.W, west.get(i + 1), Dir.E);
            }
            fork1.connect(Dir.S, bypassWest);
            bypassWest.connect(Dir.N, fork1);
            connect(bypassWest, Dir.W, bypass.get(0), Dir.E);
            for (int i = 0; i + 1 < bypass.size(); i++) {
                connect(bypass.get(i), Dir.W, bypass.get(i + 1), Dir.E);
            }
            connect(bypass.get(bypass.size() - 1), Dir.W, bypassEast, Dir.E);
            fork2.connect(Dir.S, bypassEast);
            bypassEast.connect(Dir.N, fork2);

            station = station(middle.get(5), "s"); // x = -16
            return placeTrain(east.get(0), Dir.E); // facing west
        }

        @Test
        @DisplayName("a loose stop-at order orients the normal-curved fork and reaches the station")
        void looseStopAt_reachesStation() {
            Train train = worldAndTrain();
            assertFalse(fork1.isUsingAlternativeRoute(), "fork 1 starts on its curved route");

            String error = PlayerCommandExecutor.execute(
                    "train " + train.getId() + " stop at station " + station.getId() + " speed 4;",
                    model);
            assertNull(error, error);
            assertTrue(fork1.isUsingAlternativeRoute(),
                    "the route to the station must orient the curved fork straight");

            runUntil(() -> train.getStationId() == station.getId(), 900);
            assertEquals(station.getId(), train.getStationId());
            assertEquals(0, train.getSpeed());
        }

        @Test
        @DisplayName("an itinerary repeating the same station works (no recursion)")
        void repeatedWaypointItinerary_doesNotRecurse() {
            Train train = worldAndTrain();
            List<String> errors = model.setProgram("""
                    create itinerary "loop" {
                        add station "s"
                        add station "s"
                    }
                    assign itinerary "loop" to train %d;
                    train %d set autopilot true;
                    train %d set speed 4;
                    """.formatted(train.getId(), train.getId(), train.getId()));
            assertTrue(errors.isEmpty(), "unexpected errors: " + errors);
            assertTrue(train.isAutoMode(), "the itinerary must activate the autopilot");

            runUntil(() -> train.getStationId() == station.getId(), 900);
            assertEquals(station.getId(), train.getStationId(),
                    "the repeated-waypoint loop must run without blowing the stack");
        }

        @Test
        @DisplayName("autopilot on without a valid itinerary warns on the console")
        void autopilotWithoutItinerary_warns() {
            Train train = worldAndTrain();
            List<String> messages = new ArrayList<>();
            String error = PlayerCommandExecutor.execute(
                    "train " + train.getId() + " set autopilot true;", model, null, null, null,
                    (title, text) -> messages.add(text), null, null, null);
            assertNull(error, error);
            assertTrue(messages.stream().anyMatch(m -> m.contains("has no itinerary assigned")),
                    "expected the no-itinerary warning, got: " + messages);

            messages.clear();
            PlayerCommandExecutor.execute("create itinerary \"x\" { add station \"s\" }", model,
                    null, null, null, (title, text) -> messages.add(text), null, null, null);
            assertTrue(messages.stream().anyMatch(m -> m.contains("at least 2 waypoints")),
                    "expected the invalid-itinerary warning, got: " + messages);
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

    private int headX(Train train) {
        return headTrack(train).getPosition().getX();
    }
}
