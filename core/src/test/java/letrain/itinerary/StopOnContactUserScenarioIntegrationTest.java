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
import letrain.segments.Segment;
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
 * Issue #645 follow-up: the user's run-around with a parallel variant between the two junctions.
 * The train uncouples at the station, works around its own detached wagons and must be able to
 * bypass the occupied main line through the variant (the reached waypoint no longer counts as
 * pending), touch its wagons and depart even when the scheduled departure is already past. Both
 * reviewed failures are covered: the silent block deadlock and the contact chain killing the
 * restored departure speed.
 */
@DisplayName("Issue #645 follow-up: bypass scenario (user run-around)")
class StopOnContactUserScenarioIntegrationTest {

    private Model model;

    @BeforeEach
    void setUp() {
        model = new Model(1);
        model.postLoadInit();
    }

    private static final class World {
        List<RailTrack> westTail;
        List<RailTrack> main;
        List<RailTrack> bypass;
        List<RailTrack> eastTail;
        ForkRailTrack westJunction;
        ForkRailTrack eastJunction;
        Station station3;
        Station station4;
        Sensor sensor2;
        Sensor sensor3;
    }

    /**
     * <pre>
     *  west tail (-18..-5)  F0(-4)  main (-3..10, station 4)  F1(11)  east tail (12..24, sensor 2)
     *  y=10:                 bypassWest(-4)  bypass(-3..10)  bypassEast(11)
     * </pre>
     */
    private World buildWorld() {
        World w = new World();
        w.westTail = line(-18, 14, 9);
        w.main = line(-3, 14, 9);
        w.eastTail = line(12, 13, 9);
        w.bypass = line(-3, 14, 10);
        RailTrack bypassWest = corner(-4, 10, Dir.E, Dir.N);
        RailTrack bypassEast = corner(11, 10, Dir.N, Dir.W);

        w.westJunction = fork(-4, 9, Dir.W, Dir.E);
        w.westJunction.addRoute(Dir.W, Dir.S);
        w.westJunction.setNormalRoute();
        w.eastJunction = fork(11, 9, Dir.E, Dir.W);
        w.eastJunction.addRoute(Dir.E, Dir.S);
        w.eastJunction.setNormalRoute();

        connect(w.westTail.get(13), Dir.E, w.westJunction, Dir.W);
        connect(w.westJunction, Dir.E, w.main.get(0), Dir.W);
        connect(w.main.get(13), Dir.E, w.eastJunction, Dir.W);
        connect(w.eastJunction, Dir.E, w.eastTail.get(0), Dir.W);

        w.westJunction.connect(Dir.S, bypassWest);
        bypassWest.connect(Dir.N, w.westJunction);
        connect(bypassWest, Dir.E, w.bypass.get(0), Dir.W);
        for (int i = 0; i + 1 < w.bypass.size(); i++) {
            connect(w.bypass.get(i), Dir.E, w.bypass.get(i + 1), Dir.W);
        }
        connect(w.bypass.get(13), Dir.E, bypassEast, Dir.W);
        bypassEast.connect(Dir.N, w.eastJunction);
        w.eastJunction.connect(Dir.S, bypassEast);

        w.station3 = station(w.westTail.get(3), "station3"); // x = -15
        w.sensor3 = sensor(w.westTail.get(6), "sensor3"); // x = -12
        w.station4 = station(w.main.get(5), "station4"); // x = 2
        w.sensor2 = sensor(w.eastTail.get(6), "sensor2"); // x = 18
        return w;
    }

    // ═══════════════════════════════════════════════════════════════════
    // 1. Full user run-around: both fixes together
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Full user run-around")
    class UserRunAround {

        @Test
        @DisplayName("the train bypasses its wagons' canton, touches them, couples and departs late")
        void runAround_withVariant_completes() {
            World w = buildWorld();
            Train subject = placeConsist(w.westTail, 6, 2);
            Locomotive loco = (Locomotive) subject.getDirectorLinker();
            int locoId = loco.getId();
            setTime(12, 45);

            List<String> errors = model.setProgram("""
                    create itinerary "c" {
                        add station %d arrival 12:00,
                            uncouple backward all,
                            stop at sensor %d speed 3,
                            reverse,
                            stop at sensor %d speed 3,
                            reverse,
                            stop on contact speed 3,
                            couple forward all,
                            reverse,
                            departure 12:30;
                        add station %d arrival 14:00, park
                    }
                    assign itinerary "c" to train %d;
                    train %d set autopilot true;
                    train %d set speed 3;
                    """.formatted(w.station4.getId(), w.sensor2.getId(), w.sensor3.getId(),
                    w.station3.getId(), locoId, locoId, locoId));
            assertTrue(errors.isEmpty(), "unexpected errors: " + errors);

            Segment variant = model.getRailwayGraph().getSegment(w.bypass.get(0));
            boolean[] usedVariant = {false};
            boolean[] reachedStation3 = {false};
            runUntil(() -> {
                Segment current = subject.getSafetyManager().getCurrentSegment();
                if (variant.equals(current)) {
                    usedVariant[0] = true;
                }
                if (subject.getStationId() == w.station3.getId()) {
                    reachedStation3[0] = true;
                }
                return !loco.isEngineOn();
            }, 12000);

            assertTrue(usedVariant[0],
                    "the mission to sensor 3 must bypass the occupied main via the variant");
            assertFalse(subject.isPendingManualMode(), "the bypass must not switch to manual");
            assertFalse(subject.isStalled(), "the maneuver must not crash");
            assertEquals(3, subject.getLinkers().size(),
                    "couple forward all must rejoin the two wagons after the contact");
            assertEquals(TrainMission.State.COMPLETED,
                    subject.getAutopilot().mission().orElseThrow().state(),
                    "every action of the waypoint must complete");
            assertTrue(reachedStation3[0],
                    "the late departure must release the train to station 3");
            assertEquals(0, subject.getSpeed());
            assertFalse(loco.isEngineOn(), "park must switch the engine off at station 3");
            Punctuality punctuality = subject.getAutopilot().punctuality().orElseThrow();
            assertTrue(
                    punctuality.stops().stream().anyMatch(
                            s -> s.departure().isPresent() && s.departure().getAsInt() > 0),
                    "the late departure must be measured: " + punctuality.describe());
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // 2. segmentHasPendingWaypoints: reached vs pending
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Pending waypoints")
    class PendingWaypoints {

        @Test
        @DisplayName("a reached waypoint frees its segment; a genuinely pending stop still blocks it")
        void reachedWaypoint_allowsBypass_pendingDoesNot() {
            World w = buildWorld();
            Train subject = placeConsist(w.westTail, 6, 1);
            Locomotive loco = (Locomotive) subject.getDirectorLinker();
            wagonOnlyTrain(w.main.get(3), Dir.W); // blocks the main segment
            setTime(12, 0);

            List<String> errors = model.setProgram("""
                    create itinerary "p" {
                        add station %d
                        add station %d
                    }
                    assign itinerary "p" to train %d;
                    train %d set autopilot true;
                    train %d set speed 2;
                    """.formatted(w.station4.getId(), w.station3.getId(), loco.getId(),
                    loco.getId(), loco.getId()));
            assertTrue(errors.isEmpty(), "unexpected errors: " + errors);

            Segment main = model.getRailwayGraph().getSegment(w.main.get(0));
            Segment variant = model.getRailwayGraph().getSegment(w.bypass.get(0));

            // The current waypoint (station 4, in the blocked main) is not reached yet: the
            // safety layer must refuse to bypass it.
            assertEquals(main, subject.getSafetyManager().getNextSegment(),
                    "a genuinely pending waypoint must keep its segment blocking the bypass");
            assertTrue(subject.getSafetyManager().isWaitingForBlock(),
                    "the train must wait for the blocked main");

            // The waypoint is reached (its actions are running): the bypass is allowed now.
            subject.getAutopilot().markCurrentWaypointReached();
            subject.getSafetyManager().acquireInitialLocks();

            assertEquals(variant, subject.getSafetyManager().getNextSegment(),
                    "a reached waypoint must not block the parallel bypass");
            assertTrue(subject.getAutopilot().currentRoute().contains(variant),
                    "the route must be replaced with the variant: "
                            + subject.getAutopilot().currentRoute());
            assertFalse(subject.getAutopilot().currentRoute().contains(main),
                    "the occupied main must leave the route");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Fixture
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

    /** Contiguous consist: head at {@code rails.get(headIndex)} with wagons trailing west. */
    private Train placeConsist(List<RailTrack> rails, int headIndex, int wagons) {
        Locomotive loco = new Locomotive(model.nextLocomotiveId(), "L");
        loco.setEngineOn(true);
        List<Wagon> wagonList = new ArrayList<>();
        for (int i = 0; i < wagons; i++) {
            wagonList.add(new Wagon("w" + i));
        }
        Train train = new Train(model.nextTrainId());
        train.setModel(model);
        train.pushBack(loco);
        for (Wagon wagon : wagonList) {
            train.pushBack(wagon);
        }
        train.setDirectorLinker(loco);
        model.addLocomotive(loco);
        for (Wagon wagon : wagonList) {
            model.addWagon(wagon);
        }
        rails.get(headIndex).enterLinkerFromDir(Dir.W, loco);
        for (int i = 0; i < wagons; i++) {
            rails.get(headIndex - 1 - i).enterLinkerFromDir(Dir.W, wagonList.get(i));
        }
        train.rebind();
        train.getMovementManager().refreshLinkersDirection();
        return train;
    }

    /** A wagon-only train (like the part left behind by {@code uncouple}) placed in the way. */
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
