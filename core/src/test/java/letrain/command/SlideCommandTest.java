package letrain.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import letrain.map.Dir;
import letrain.map.Point;
import letrain.mvp.impl.Model;
import letrain.track.Sensor;
import letrain.track.Station;
import letrain.track.rail.RailTrack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contract tests for the {@code slide} player command (issue #487 PoC): sliding a track element
 * (sensor/station/semaphore/speed signal) along the rail through the exact same DSL the console
 * uses. Slides reuse the engine's {@code moveSensorForward/Backward} machinery, so every rule of
 * {@code TrackElementMoveTest} (one resting cell per hop, jump over other components, stop before
 * trains, follow curves/forks) applies.
 */
@DisplayName("CLI slide command")
class SlideCommandTest {

    private Model model;

    @BeforeEach
    void setUp() {
        model = new Model();
    }

    // ------------------------------------------------------------------
    // World helpers: an E-W straight made of n tracks with a free E tail.
    // ------------------------------------------------------------------

    private RailTrack straightAt(int x, int y) {
        RailTrack track = new RailTrack();
        track.addRoute(Dir.E, Dir.W);
        track.addRoute(Dir.W, Dir.E);
        track.setPosition(new Point(x, y));
        model.getRailMap().addTrack(track.getPosition(), track);
        return track;
    }

    private void connect(RailTrack a, RailTrack b) {
        a.connect(Dir.E, b);
        b.connect(Dir.W, a);
    }

    /** Builds tracks at (x,y) for x in [0, n-1] connected E-W and parks the cursor at (0,y). */
    private void buildEastWestLine(int y, int n) {
        RailTrack prev = null;
        for (int x = 0; x < n; x++) {
            RailTrack track = straightAt(x, y);
            if (prev != null) {
                connect(prev, track);
            }
            prev = track;
        }
        model.getCursor().getPosition().setX(0);
        model.getCursor().getPosition().setY(y);
        model.getCursor().setDir(Dir.E);
    }

    /** Runs a full player script and asserts it produced no error. */
    private String run(String script) {
        String error = PlayerCommandExecutor.execute(script, model, null, null, null);
        if (error != null) {
            throw new AssertionError("Script failed: " + error + "\nscript: " + script);
        }
        return error;
    }

    private Sensor onlySensor() {
        assertEquals(1, model.getSensors().size(), "expected exactly one plain sensor");
        return model.getSensors().get(0);
    }

    // ------------------------------------------------------------------
    // Sensor sliding
    // ------------------------------------------------------------------

    @Test
    @DisplayName("slide sn fw moves the sensor one resting cell east")
    void sensor_slidesForward_oneCell() {
        // Arrange
        buildEastWestLine(0, 4);
        run("new sn; slide sn 1 fw;");

        // Assert
        Sensor sensor = onlySensor();
        assertEquals(1, sensor.getTrack().getPosition().getX(), "sensor should rest on x=1");
    }

    @Test
    @DisplayName("slide sn 1 fw 2 hops two resting cells east")
    void sensor_slidesForward_twoCells() {
        // Arrange
        buildEastWestLine(0, 5);
        run("new sn; slide sn 1 fw 2;");

        // Assert
        Sensor sensor = onlySensor();
        assertEquals(2, sensor.getTrack().getPosition().getX(), "sensor should rest on x=2");
    }

    @Test
    @DisplayName("slide sn 1 bw 1 moves the sensor back west")
    void sensor_slidesBackward_oneCell() {
        // Arrange
        buildEastWestLine(0, 5);
        run("new sn; slide sn 1 fw 2; slide sn 1 bw 1;");

        // Assert
        Sensor sensor = onlySensor();
        assertEquals(1, sensor.getTrack().getPosition().getX(), "sensor should rest on x=1");
    }

    @Test
    @DisplayName("slide default (no sense) slides forward one cell")
    void sensor_slidesForward_byDefault() {
        // Arrange
        buildEastWestLine(0, 4);
        run("new sn; slide sn 1;");

        // Assert
        Sensor sensor = onlySensor();
        assertEquals(1, sensor.getTrack().getPosition().getX());
    }

    @Test
    @DisplayName("slide blocked at the end of the line reports an error")
    void sensor_slide_blockedAtLineEnd_returnsError() {
        // Arrange: sensor placed at x=0 of a 1-track line: nothing east to rest on.
        buildEastWestLine(0, 1);

        // Act
        String error = PlayerCommandExecutor.execute("new sn; slide sn 1 fw;", model);

        // Assert
        assertNotNull(error, "a blocked slide should report an error");
        assertTrue(error.contains("blocked"), "unexpected error: " + error);
        assertEquals(0, onlySensor().getTrack().getPosition().getX(),
                "blocked sensor must not move");
    }

    @Test
    @DisplayName("slide to an unknown sensor id reports an error")
    void sensor_slide_unknownId_returnsError() {
        // Arrange
        buildEastWestLine(0, 4);

        // Act
        String error = PlayerCommandExecutor.execute("slide sn 99 fw;", model);

        // Assert
        assertNotNull(error, "unknown sensor should report an error");
        assertTrue(error.contains("Sensor not found"), "unexpected error: " + error);
    }

    // ------------------------------------------------------------------
    // Sliding every track element type
    // ------------------------------------------------------------------

    @Test
    @DisplayName("slide st 1 fw moves a station and keeps its identity")
    void station_slidesForward_keepsIdentity() {
        // Arrange
        buildEastWestLine(0, 4);
        run("new st; slide st 1 fw;");

        // Assert
        assertEquals(1, model.getStations().size());
        Station station = model.getStations().get(0);
        assertEquals(1, station.getTrack().getPosition().getX(), "station should rest on x=1");
        assertSame(station, model.getStation(station.getId()));
        assertTrue(model.getStations().contains(station));
    }

    @Test
    @DisplayName("slide sm 1 fw moves a semaphore")
    void semaphore_slidesForward() {
        // Arrange
        buildEastWestLine(0, 4);
        run("new sm; slide sm 1 fw;");

        // Assert
        assertEquals(1, model.getSemaphores().size());
        assertEquals(1, model.getSemaphores().get(0).getTrack().getPosition().getX());
    }

    @Test
    @DisplayName("slide sg 1 fw moves a speed signal")
    void speedSignal_slidesForward() {
        // Arrange
        buildEastWestLine(0, 4);
        run("new sg; slide sg 1 fw;");

        // Assert
        assertEquals(1, model.getSpeedSignals().size());
        assertEquals(1, model.getSpeedSignals().get(0).getTrack().getPosition().getX());
    }
}
