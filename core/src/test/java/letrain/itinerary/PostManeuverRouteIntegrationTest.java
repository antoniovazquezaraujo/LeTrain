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
import org.junit.jupiter.api.Test;

/**
 * Issue #649: when the post-maneuver route to the next waypoint cannot be computed (e.g. the
 * itinerary is missing the final {@code reverse} and the train faces the wrong sense), the
 * autopilot used to log only and let the train roll to the dead end with {@code route=[]}. It must
 * warn visibly and hold the train instead.
 */
@DisplayName("Issue #649: no route after a maneuver warns and holds the train")
class PostManeuverRouteIntegrationTest {

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

    /** Same world as the user run-around test: main line plus a parallel bypass. */
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

        w.station3 = station(w.westTail.get(3), "station3");
        w.sensor3 = sensor(w.westTail.get(6), "sensor3");
        w.station4 = station(w.main.get(5), "station4");
        w.sensor2 = sensor(w.eastTail.get(6), "sensor2");
        return w;
    }

    @Test
    @DisplayName("the missing final reverse warns and the train is held instead of rolling away")
    void noRouteAfterManeuver_warnsAndHoldsTheTrain() {
        World w = buildWorld();
        Train subject = placeConsist(w.westTail, 6, 2);
        Locomotive loco = (Locomotive) subject.getDirectorLinker();
        int locoId = loco.getId();
        setTime(12, 45);
        List<String> messages = new ArrayList<>();
        model.setUserMessageSink((title, text) -> messages.add(text));

        // The user's run-around without the final `reverse` after the couple: the train ends
        // facing the wrong sense for station 3 and the route cannot be planned.
        List<String> errors = model.setProgram("""
                create itinerary "c" {
                    add station %d, arrival 12:00,
                        uncouple backward all,
                        stop at sensor %d speed 3,
                        reverse,
                        stop at sensor %d speed 3,
                        reverse,
                        stop on contact speed 3,
                        couple forward all,
                        departure 12:30;
                    add station %d, arrival 14:00, park
                }
                assign itinerary "c" to train %d;
                train %d set autopilot true;
                train %d set speed 3;
                """.formatted(w.station4.getId(), w.sensor2.getId(), w.sensor3.getId(),
                w.station3.getId(), locoId, locoId, locoId));
        assertTrue(errors.isEmpty(), "unexpected errors: " + errors);

        // The departure is already past: once the maneuver ends the flow advances to station 3 and
        // tries to plan the route from the final position/sense.
        runUntil(() -> subject.getAutopilot().mode() == AutoPilot.Mode.FOLLOWING
                && subject.getAutopilot().currentWaypointIndex() == 1, 12000);

        assertTrue(messages.stream().anyMatch(m -> m.contains("no route")),
                "the silent no-route must warn visibly: " + messages);
        assertTrue(subject.getAutopilot().currentRoute().isEmpty(),
                "the scenario must reproduce the empty route: "
                        + subject.getAutopilot().currentRoute());
        assertEquals(0, loco.getTargetSpeed(), "the train must be held without a route");

        int heldX = loco.getPosition().getX();
        runTicks(600);

        assertEquals(0, loco.getSpeed(), "the held train must stay stopped");
        assertEquals(heldX, loco.getPosition().getX(),
                "the train must not roll to the dead end after the no-route warning");
        assertFalse(subject.isPendingManualMode(), "holding must not switch the train to manual");
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
