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
import letrain.vehicle.rail.ScriptTrainEventListener;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import letrain.vehicle.rail.impl.Wagon;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Issue #645 (ADR-022 phase 2f follow-up): {@code stop on contact}, the coupling approach. The
 * train drives at the ordered speed and completes on the first physical low-speed contact with the
 * vehicle ahead, ending pressed against it, ready for {@code couple}. The canton of its own
 * detached part is entered with the #626 shunting exemption; a foreign occupant (a train with a
 * locomotive) keeps the canton: the maneuver waits at the frontier and resumes when released. At or
 * above the crash threshold the contact is a crash (normal physics).
 */
@DisplayName("Issue #645: 'stop on contact' (coupling approach)")
class StopOnContactIntegrationTest {

    private Model model;

    @BeforeEach
    void setUp() {
        model = new Model(1);
        model.postLoadInit();
    }

    // ═══════════════════════════════════════════════════════════════════
    // Acceptance 1: the run-around enters the wagons' canton
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Run-around")
    class RunAround {

        private RailTrack railWagon;
        private RailTrack railSensor6;
        private ForkRailTrack eastJunction;
        private Station c;
        private Sensor sensor5;

        /**
         * Same world as the #626 run-around test: loco at B (1,0) facing east with the wagon behind
         * at (0,0); a bypass between the junction at -2 and the junction at 2 allows the loop.
         *
         * <pre>
         *   -3 | F0(-2) | -1 sensor6 | 0 wagon | 1 B | F1(2) | 3 | 4 sensor5 | 5 | 6 C
         *   y=1:        bypassWest(-2) -1 0 1 bypassEast(2)
         * </pre>
         */
        private Train worldAndTrain() {
            RailTrack west3 = track(-3, 0);
            ForkRailTrack westJunction = fork(-2, 0, Dir.S, Dir.E);
            westJunction.addRoute(Dir.S, Dir.W);
            westJunction.setNormalRoute();
            railSensor6 = track(-1, 0);
            railWagon = track(0, 0);
            RailTrack railB = track(1, 0);
            eastJunction = fork(2, 0, Dir.E, Dir.W);
            eastJunction.addRoute(Dir.E, Dir.S);
            eastJunction.setNormalRoute();
            RailTrack railEast3 = track(3, 0);
            RailTrack railEast4 = track(4, 0);
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

            Station b = station(railB, "b");
            c = station(railEast6, "c");
            sensor5 = sensor(railEast4, "s5");
            sensor(railSensor6, "s6");

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
        @DisplayName("'stop on contact' enters the wagons' canton, touches them and couples")
        void stopOnContact_completesTheRunAround() {
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
                            stop on contact speed 2,
                            couple forward 1,
                            reverse,
                            departure 09:00
                        add station "c"
                    }
                    assign itinerary "runaround" to train %d;
                    train %d set autopilot true;
                    """.formatted(sensor5.getId(), eastJunction.getId(), train.getId(),
                    train.getId()));
            assertTrue(errors.isEmpty(), "unexpected errors: " + errors);
            assertEquals(1, train.getLinkers().size(), "uncouple backward must detach the wagon");

            runUntil(() -> train.getLinkers().size() == 2, 2000);
            assertEquals(2, train.getLinkers().size(),
                    "couple forward must pick the wagon up again (loco="
                            + loco.getTrack().getPosition() + " wagonTrack="
                            + (model.getWagons().isEmpty() ? "-"
                                    : model.getWagons().get(0).getTrack().getPosition()));
            assertEquals(railSensor6, headTrack(train),
                    "the contact approach must stop the loco pressed against the wagon");
            assertTrue(train.getLinkers().stream().anyMatch(l -> l.getTrack() == railWagon),
                    "the wagon must be part of the train after coupling");
            assertEquals(TrainMission.State.COMPLETED,
                    train.getAutopilot().mission().orElseThrow().state(),
                    "the contact must complete the mission (silent log success)");
            assertEquals(0, train.getSpeed());
            assertFalse(train.isStalled(), "a low-speed contact is not a crash");
            assertEquals(Dir.W, loco.getDir(),
                    "the final reverse must leave the loco facing the departure direction");
            assertEquals(List.of(train),
                    model.getBlockManager()
                            .getOwners(model.getRailwayGraph().getSegment(railWagon)),
                    "the coupled wagon part must release its share of the canton");

            // The departure holds after the maneuver and then releases with the route recalculated.
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
    // Acceptance 2: foreign occupant keeps the canton
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Foreign occupant in the canton")
    class ForeignOccupant {

        @Test
        @DisplayName("the maneuver waits at the frontier and resumes when the foreign train leaves")
        void waitsAtFrontier_andResumes() {
            List<RailTrack> approach = line(0, 3, 0); // x = 0..2
            ForkRailTrack entry = fork(3, 0, Dir.W, Dir.E);
            List<RailTrack> target = line(4, 6, 0); // x = 4..9
            ForkRailTrack exit = fork(10, 0, Dir.W, Dir.E);
            List<RailTrack> far = line(11, 4, 0); // x = 11..14
            connect(approach.get(2), Dir.E, entry, Dir.W);
            connect(entry, Dir.E, target.get(0), Dir.W);
            connect(target.get(5), Dir.E, exit, Dir.W);
            connect(exit, Dir.E, far.get(0), Dir.W);

            Station a = station(approach.get(0), "a");
            station(approach.get(2), "b");
            Train subject = placeTrain(approach.get(0), Dir.W);
            subject.setStationId(a.getId());
            Train foreign = placeTrain(target.get(3), Dir.W); // x = 7, a train with a locomotive
            setTime(8, 0);

            List<String> errors = model.setProgram("""
                    create itinerary "maneuver" {
                        add station "a" arrival 08:00,
                            stop on contact speed 2,
                            departure 09:00
                        add station "b"
                    }
                    assign itinerary "maneuver" to train %d;
                    train %d set autopilot true;
                    """.formatted(subject.getId(), subject.getId()));
            assertTrue(errors.isEmpty(), "unexpected errors: " + errors);

            runUntil(() -> subject.getSafetyManager().isWaitingForBlock(), 600);
            runTicks(200);

            assertEquals(0, subject.getSpeed(), "the maneuver must wait for the foreign train");
            assertFalse(subject.isPendingManualMode(),
                    "a foreign train must keep the canton: no shunting exemption");
            assertTrue(headX(subject) < 3, "the subject must stop before the blocked canton");
            assertFalse(subject.isStalled(), "no contact");
            assertTrue(subject.getAutopilot().mission().orElseThrow().isActive(),
                    "the mission must survive the wait");

            // The foreign train leaves the canton: the block is released and the approach resumes
            // into the canton it could not enter while the foreign train was there.
            letrain.segments.Segment targetSegment =
                    model.getRailwayGraph().getSegment(target.get(0));
            foreign.setSpeed(3);
            runUntil(() -> headX(subject) >= 4, 2000);

            assertEquals(targetSegment, subject.getSafetyManager().getCurrentSegment(),
                    "the release must wake the waiting maneuver into the freed canton");
            assertTrue(headX(subject) >= 4, "the head must be inside the target canton");
            assertFalse(subject.isPendingManualMode(), "the resume must not switch to manual");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Loose order (console/script)
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Loose order")
    class LooseOrder {

        @Test
        @DisplayName("a loose 'stop on contact' enters the canton of the loco-less part and couples")
        void looseOrder_entersOwnPartCanton() {
            List<RailTrack> east = line(3, 2, 0); // x = 3..4
            ForkRailTrack boundary = fork(2, 0, Dir.W, Dir.E);
            List<RailTrack> west = line(0, 2, 0); // x = 0..1
            connect(west.get(1), Dir.E, boundary, Dir.W);
            connect(boundary, Dir.E, east.get(0), Dir.W);
            Train subject = placeTrain(east.get(1), Dir.E); // faces west
            Locomotive loco = (Locomotive) subject.getDirectorLinker();
            Wagon wagon = wagonOnlyTrain(west.get(0), Dir.W);
            List<String> messages = new ArrayList<>();

            String error = PlayerCommandExecutor.execute(
                    "train " + loco.getId() + " stop on contact speed 2;", model, null, null, null,
                    (title, text) -> messages.add(text), null, null, null);
            assertNull(error, error);

            runUntil(() -> missionFinished(subject), 2000);

            assertEquals(TrainMission.State.COMPLETED,
                    subject.getAutopilot().mission().orElseThrow().state());
            assertEquals(west.get(1), headTrack(subject),
                    "the loco must stop pressed against the wagon (one rail before it)");
            assertFalse(subject.isStalled(), "a low-speed contact is not a crash");
            assertTrue(messages.isEmpty(),
                    "success must be silent on the console (log only): " + messages);

            // Ready for the coupling: the loose order is the console form of the run-around.
            error = PlayerCommandExecutor.execute("train " + loco.getId() + " couple forward 1;",
                    model);
            assertNull(error, error);
            assertEquals(2, subject.getLinkers().size(),
                    "after touching, couple must really join the own wagon");
            assertTrue(subject.getLinkers().contains(wagon));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Edges
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Edges")
    class Edges {

        @Test
        @DisplayName("a low-speed contact with the end of track also completes (no vehicle)")
        void deadEndContact_completes() {
            List<RailTrack> rails = line(0, 4, 0);
            Train subject = placeTrain(rails.get(0), Dir.W);
            Locomotive loco = (Locomotive) subject.getDirectorLinker();

            String error = PlayerCommandExecutor
                    .execute("train " + loco.getId() + " stop on contact speed 2;", model);
            assertNull(error, error);

            runUntil(() -> missionFinished(subject), 600);

            assertEquals(TrainMission.State.COMPLETED,
                    subject.getAutopilot().mission().orElseThrow().state(),
                    "touching the buffer ahead also satisfies the order");
            assertEquals(rails.get(3), headTrack(subject));
            assertEquals(0, subject.getSpeed());
            assertFalse(subject.isStalled(), "a low-speed contact is not a crash");
        }

        @Test
        @DisplayName("contact at or above the crash threshold is a crash (normal physics)")
        void highSpeedContact_crashes_andFailsTheMission() {
            List<RailTrack> rails = line(0, 40, 0); // long run-up: speed >= threshold at the buffer
            Train subject = placeTrain(rails.get(0), Dir.W);
            Locomotive loco = (Locomotive) subject.getDirectorLinker();
            List<String> messages = new ArrayList<>();
            List<Integer> crashSpeeds = new ArrayList<>();
            subject.addScriptTrainEventListener(new ScriptTrainEventListener() {
                @Override
                public void onCrash(Train train, Point pos, int speed) {
                    crashSpeeds.add(speed);
                }
            });

            String error = PlayerCommandExecutor.execute(
                    "train " + loco.getId() + " stop on contact speed 8;", model, null, null, null,
                    (title, text) -> messages.add(text), null, null, null);
            assertNull(error, error);

            runUntil(() -> subject.isStalled(), 2000);

            assertTrue(subject.isStalled(), "the high-speed contact must destroy the train");
            assertFalse(crashSpeeds.isEmpty(), "the crash event must fire");
            assertTrue(crashSpeeds.stream().allMatch(s -> s >= 5),
                    "a crash only happens at or above the threshold: " + crashSpeeds);
            assertEquals(TrainMission.State.FAILED,
                    subject.getAutopilot().mission().orElseThrow().state(),
                    "a crash fails the approach (problems warn)");
            assertTrue(messages.stream().anyMatch(m -> m.contains("crashed")),
                    "the crash must warn: " + messages);
        }

        @Test
        @DisplayName("M1: a high speed ordered while already touching reports the real speed")
        void alreadyTouching_highSpeedOrder_isAnHonestLowSpeedContact() {
            List<RailTrack> west = line(0, 2, 0);
            ForkRailTrack boundary = fork(2, 0, Dir.W, Dir.E);
            List<RailTrack> east = line(3, 3, 0);
            connect(west.get(1), Dir.E, boundary, Dir.W);
            connect(boundary, Dir.E, east.get(0), Dir.W);
            Train subject = placeTrain(east.get(2), Dir.E); // faces west
            Locomotive loco = (Locomotive) subject.getDirectorLinker();
            wagonOnlyTrain(west.get(0), Dir.W);
            List<String> events = new ArrayList<>();
            subject.addScriptTrainEventListener(new ScriptTrainEventListener() {
                @Override
                public void onContact(Train train, Point pos, int speed) {
                    events.add("contact:" + speed);
                }

                @Override
                public void onCrash(Train train, Point pos, int speed) {
                    events.add("crash:" + speed);
                }
            });

            String error = PlayerCommandExecutor
                    .execute("train " + loco.getId() + " stop on contact speed 2;", model);
            assertNull(error, error);
            runUntil(() -> missionFinished(subject), 2000);
            assertEquals(TrainMission.State.COMPLETED,
                    subject.getAutopilot().mission().orElseThrow().state(),
                    "the first approach must complete pressed against the wagon");

            // Already pressed: the ordered 8 can never become impact energy (the train does not
            // move), so the instant contact check reports the real speed 0 and the order completes
            // again. The event must not lie with the ordered target.
            error = PlayerCommandExecutor
                    .execute("train " + loco.getId() + " stop on contact speed 8;", model);
            assertNull(error, error);
            runTicks(200);

            assertEquals(TrainMission.State.COMPLETED,
                    subject.getAutopilot().mission().orElseThrow().state(),
                    "already touching at real speed 0 is a contact, not a crash");
            assertEquals(List.of("contact:2", "contact:0"), events,
                    "the already-touching contact must report the real speed, not the ordered 8");
            assertFalse(subject.isStalled(), "a stationary train cannot crash");
            assertEquals(0, subject.getSpeed());
        }

        @Test
        @DisplayName("M2: repeating 'stop on contact' already pressed against the buffer completes")
        void alreadyPressedAgainstBuffer_repeatingTheOrderCompletes() {
            List<RailTrack> rails = line(0, 3, 0);
            Train subject = placeTrain(rails.get(0), Dir.W);
            Locomotive loco = (Locomotive) subject.getDirectorLinker();
            List<String> messages = new ArrayList<>();

            String error = PlayerCommandExecutor.execute(
                    "train " + loco.getId() + " stop on contact speed 2;", model, null, null, null,
                    (title, text) -> messages.add(text), null, null, null);
            assertNull(error, error);
            runUntil(() -> missionFinished(subject), 600);
            assertEquals(TrainMission.State.COMPLETED,
                    subject.getAutopilot().mission().orElseThrow().state());
            assertEquals(rails.get(2), headTrack(subject));

            // Pressed against the buffer there is no rail ahead and no contact event will ever
            // fire: the repeated order must complete in place instead of staying active forever.
            error = PlayerCommandExecutor.execute(
                    "train " + loco.getId() + " stop on contact speed 2;", model, null, null, null,
                    (title, text) -> messages.add(text), null, null, null);
            assertNull(error, error);
            runTicks(1500); // beyond the stall guard window

            assertEquals(TrainMission.State.COMPLETED,
                    subject.getAutopilot().mission().orElseThrow().state(),
                    "already at the buffer must complete the order in place");
            assertEquals(0, subject.getSpeed());
            assertFalse(subject.isStalled());
            assertTrue(messages.isEmpty(), "completing in place must stay silent: " + messages);
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

    /** A part without a locomotive (like the wagons left behind by {@code uncouple}). */
    private Wagon wagonOnlyTrain(RailTrack rail, Dir entryFrom) {
        Wagon wagon = new Wagon("w");
        Train wagonTrain = new Train(model.nextTrainId());
        wagonTrain.setModel(model);
        wagonTrain.pushBack(wagon);
        model.addWagon(wagon);
        rail.enterLinkerFromDir(entryFrom, wagon);
        wagonTrain.getSafetyManager().claimOccupiedSegments();
        return wagon;
    }

    private boolean missionFinished(Train train) {
        return train.getAutopilot().mission().map(m -> !m.isActive()).orElse(false);
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
