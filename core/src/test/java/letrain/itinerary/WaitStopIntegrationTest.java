package letrain.itinerary;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.mvp.impl.Model;
import letrain.time.GameTime;
import letrain.track.Sensor;
import letrain.track.Station;
import letrain.track.rail.RailTrack;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #832: {@code wait N} stops a moving train and waits N simulation seconds before the next
 * action. Already-stopped trains start the timer immediately; {@code wait 0} keeps rolling.
 */
@DisplayName("Itinerary wait N stops the train and waits (#832)")
class WaitStopIntegrationTest {

    private Model model;

    @BeforeEach
    void setUp() {
        model = new Model();
        model.postLoadInit();
    }

    @Test
    @DisplayName("moving train brakes, waits ~N*20 ticks and resumes at the saved speed")
    void movingTrain_stopsAndResumes() {
        List<RailTrack> line = makeLine(8);
        Station a = makeStation(line.get(0), "A");
        Station b = makeStation(line.get(5), "B");
        Station c = makeStation(line.get(7), "C");
        Train train = makeTrain(line.get(0), Dir.W);
        Locomotive loco = (Locomotive) train.getDirectorLinker();

        program("""
                create itinerary "Ruta" {
                    add station %d
                    add station %d, wait 2
                    add station %d
                }
                assign itinerary "Ruta" to train %d;
                train %d set autopilot true;
                train %d set speed 3;
                """.formatted(a.getId(), b.getId(), c.getId(), train.getId(), train.getId(),
                train.getId()));

        assertTrue(runUntil(() -> train.getStationId() == b.getId(), 800),
                "the train must reach station B first");

        int stopAt = -1;
        int resumeAt = -1;
        for (int tick = 0; tick < 400; tick++) {
            runTicks(1);
            int speed = train.getSpeed();
            if (stopAt < 0 && speed == 0) {
                stopAt = tick;
            } else if (stopAt >= 0 && loco.getTargetSpeed() > 0) {
                resumeAt = tick;
                break;
            }
        }

        assertTrue(stopAt >= 0, "a moving train must stop for wait 2");
        assertTrue(resumeAt > stopAt, "the train must resume after the wait");
        int waited = resumeAt - stopAt;
        assertTrue(waited >= 30 && waited <= 50,
                "wait 2 should last about 40 ticks, was " + waited);
        assertTrue(loco.getTargetSpeed() > 0, "the saved speed must be restored after the wait");
    }

    @Test
    @DisplayName("already-stopped train starts the wait immediately")
    void alreadyStoppedTrain_waitsImmediately() {
        List<RailTrack> line = makeLine(8);
        Station a = makeStation(line.get(0), "A");
        Station b = makeStation(line.get(5), "B");
        Train train = makeTrain(line.get(5), Dir.W); // already on B, stopped
        train.setStationId(b.getId());
        Locomotive loco = (Locomotive) train.getDirectorLinker();

        program("""
                create itinerary "Ruta" {
                    add station %d, wait 2, speed 3
                    add station %d
                }
                assign itinerary "Ruta" to train %d;
                train %d set autopilot true;
                """.formatted(b.getId(), a.getId(), train.getId(), train.getId()));

        int resumeAt = -1;
        for (int tick = 0; tick < 300; tick++) {
            runTicks(1);
            if (loco.getTargetSpeed() > 0) {
                resumeAt = tick;
                break;
            }
        }

        assertTrue(resumeAt >= 0, "speed 3 after the wait must resume the train");
        assertTrue(resumeAt >= 30 && resumeAt <= 50,
                "wait 2 should last about 40 ticks, was " + resumeAt);
    }

    @Test
    @DisplayName("wait at a sensor waypoint also stops the train")
    void sensorWaypoint_stopsAndResumes() {
        List<RailTrack> line = makeLine(8);
        Station a = makeStation(line.get(0), "A");
        Station c = makeStation(line.get(7), "C");
        Sensor sensor = new Sensor(model.nextSensorId());
        sensor.setName("S");
        line.get(5).setComponent(sensor);
        model.addSensor(sensor);
        Train train = makeTrain(line.get(0), Dir.W);

        program("""
                create itinerary "Ruta" {
                    add station %d
                    add sensor "%s", wait 2
                    add station %d
                }
                assign itinerary "Ruta" to train %d;
                train %d set autopilot true;
                train %d set speed 3;
                """.formatted(a.getId(), sensor.getName(), c.getId(), train.getId(), train.getId(),
                train.getId()));

        assertTrue(
                runUntil(() -> train.getActiveSensors().stream()
                        .anyMatch(s -> s.getId() == sensor.getId()), 800),
                "the train must reach the sensor");

        int stopAt = -1;
        int resumeAt = -1;
        for (int tick = 0; tick < 400; tick++) {
            runTicks(1);
            int speed = train.getSpeed();
            Locomotive loco = (Locomotive) train.getDirectorLinker();
            if (stopAt < 0 && speed == 0) {
                stopAt = tick;
            } else if (stopAt >= 0 && loco.getTargetSpeed() > 0) {
                resumeAt = tick;
                break;
            }
        }

        assertTrue(stopAt >= 0, "the sensor waypoint wait must stop the train");
        assertTrue(resumeAt > stopAt, "the train must resume after the sensor wait");
    }

    @Test
    @DisplayName("wait 0 does not brake: the train keeps rolling")
    void waitZero_doesNotBrake() {
        List<RailTrack> line = makeLine(8);
        Station a = makeStation(line.get(0), "A");
        Station b = makeStation(line.get(3), "B");
        Train train = makeTrain(line.get(0), Dir.W);

        program("""
                create itinerary "Ruta" {
                    add station %d
                    add station %d, wait 0, speed 3
                }
                assign itinerary "Ruta" to train %d;
                train %d set autopilot true;
                train %d set speed 3;
                """.formatted(a.getId(), b.getId(), train.getId(), train.getId(), train.getId()));

        assertTrue(runUntil(() -> train.getStationId() == b.getId(), 800),
                "the train must reach station B first");
        for (int tick = 0; tick < 20; tick++) {
            runTicks(1);
            assertTrue(train.getSpeed() > 0, "wait 0 must not brake the train (tick " + tick + ")");
        }
    }

    @Test
    @DisplayName("wait combined with departure still holds the train until the scheduled time")
    void waitWithDeparture_keepsRetention() {
        List<RailTrack> line = makeLine(8);
        Station a = makeStation(line.get(0), "A");
        Station b = makeStation(line.get(5), "B");
        Station c = makeStation(line.get(7), "C");
        Train train = makeTrain(line.get(0), Dir.W);
        Locomotive loco = (Locomotive) train.getDirectorLinker();
        setTime(8, 0);

        program("""
                create itinerary "Ruta" {
                    add station %d
                    add station %d, wait 1, departure 09:00
                    add station %d
                }
                assign itinerary "Ruta" to train %d;
                train %d set autopilot true;
                train %d set speed 3;
                """.formatted(a.getId(), b.getId(), c.getId(), train.getId(), train.getId(),
                train.getId()));

        assertTrue(runUntil(() -> train.getStationId() == b.getId(), 800),
                "the train must reach station B first");
        // Let the brake finish (the wait starts when the train is fully stopped).
        assertTrue(runUntil(() -> train.getSpeed() == 0, 400),
                "the train must stop before the wait starts");
        runTicks(2 * 20 + 10); // the 1 s wait must be over; the 09:00 departure still holds

        assertEquals(AutoPilot.Mode.WAITING, train.getAutopilot().mode(),
                "the scheduled departure must keep the train held after the wait");
        assertEquals(0, train.getSpeed(), "the train must stay stopped at the waypoint");

        runTicks((int) model.getGameClock().ticksUntil(new GameTime(1, 9, 0)) + 2);
        assertEquals(AutoPilot.Mode.FOLLOWING, train.getAutopilot().mode(),
                "the departure must release the hold");
        assertTrue(loco.getTargetSpeed() > 0, "the departure must restore the cruise");
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private void program(String script) {
        List<String> errors = model.setProgram(script);
        assertTrue(errors.isEmpty(), "unexpected program errors: " + errors);
    }

    private List<RailTrack> makeLine(int length) {
        List<RailTrack> tracks = new ArrayList<>();
        for (int x = 0; x < length; x++) {
            RailTrack track = new RailTrack();
            track.setPosition(new Point(x, 0));
            track.addRoute(Dir.E, Dir.W);
            track.addRoute(Dir.W, Dir.E);
            model.getRailMap().addTrack(new Point(x, 0), track);
            tracks.add(track);
        }
        for (int x = 0; x + 1 < length; x++) {
            tracks.get(x).connect(Dir.E, tracks.get(x + 1));
            tracks.get(x + 1).connect(Dir.W, tracks.get(x));
        }
        return tracks;
    }

    private Station makeStation(RailTrack track, String name) {
        Station station = new Station(model.nextStationId());
        station.setName(name);
        station.setTrack(track);
        track.setComponent(station);
        model.addStation(station);
        return station;
    }

    private Train makeTrain(RailTrack track, Dir entryFrom) {
        Locomotive loco = new Locomotive(model.nextLocomotiveId(), "A");
        loco.setEngineOn(true);
        loco.setTargetSpeed(0);
        Train train = new Train(model.nextTrainId());
        train.setModel(model);
        train.pushBack(loco);
        train.setDirectorLinker(loco);
        model.addLocomotive(loco);
        track.enterLinkerFromDir(entryFrom, loco);
        if (track.getComponent() instanceof Station station) {
            train.setStationId(station.getId());
        }
        return train;
    }

    private void setTime(int hour, int minute) {
        String error = letrain.command.PlayerCommandExecutor
                .execute("time set " + hour + ":" + minute + ";", model);
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

    private boolean runUntil(java.util.function.BooleanSupplier condition, int maxTicks) {
        for (int i = 0; i < maxTicks; i++) {
            if (condition.getAsBoolean()) {
                return true;
            }
            runTicks(1);
        }
        return condition.getAsBoolean();
    }
}
