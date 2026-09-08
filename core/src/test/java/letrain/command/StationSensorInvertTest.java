package letrain.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import letrain.map.Dir;
import letrain.map.Point;
import letrain.mvp.impl.Model;
import letrain.track.Station;
import letrain.track.rail.RailTrack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contract tests for the {@code station N invert;} / {@code sensor N invert;} DSL commands
 * (inverting an element orientation on the rail, the same action the UI performs with Space in the
 * STATIONS / SENSORS edit modes).
 */
@DisplayName("CLI station/sensor invert command")
class StationSensorInvertTest {

    private Model model;

    @BeforeEach
    void setUp() {
        model = new Model();
    }

    // ------------------------------------------------------------------
    // World helpers: an E-W straight made of n connected tracks.
    // ------------------------------------------------------------------

    private RailTrack straightAt(int x, int y) {
        RailTrack track = new RailTrack();
        track.addRoute(Dir.E, Dir.W);
        track.addRoute(Dir.W, Dir.E);
        track.setPosition(new Point(x, y));
        model.getRailMap().addTrack(track.getPosition(), track);
        return track;
    }

    private void buildEastWestLine(int y, int n) {
        RailTrack prev = null;
        for (int x = 0; x < n; x++) {
            RailTrack track = straightAt(x, y);
            if (prev != null) {
                prev.connect(Dir.E, track);
                track.connect(Dir.W, prev);
            }
            prev = track;
        }
        model.getCursor().getPosition().setX(0);
        model.getCursor().getPosition().setY(y);
        model.getCursor().setDir(Dir.E);
    }

    /** Runs a full player script and asserts it produced no error. */
    private void run(String script) {
        String error = PlayerCommandExecutor.execute(script, model, null, null, null);
        if (error != null) {
            throw new AssertionError("Script failed: " + error + "\nscript: " + script);
        }
    }

    // ------------------------------------------------------------------
    // Station invert
    // ------------------------------------------------------------------

    @Test
    @DisplayName("station 1 invert flips creationDir and recomputes sideDir")
    void station_invert_flipsOrientationAndSide() {
        // Arrange
        buildEastWestLine(0, 3);
        run("new st;");
        Station station = model.getStations().get(0);
        assertEquals(Dir.E, station.getCreationDir(), "cursor faced E at creation");
        Dir originalSide = station.getSideDir();

        // Act
        run("station 1 invert;");

        // Assert
        assertEquals(Dir.W, station.getCreationDir(), "station should now face west");
        assertEquals(originalSide.inverse(), station.getSideDir(),
                "platform side should mirror to the opposite rail side");
        assertSame(station, model.getStation(1), "invert must keep the same station identity");
    }

    @Test
    @DisplayName("station 1 invert twice restores the original orientation")
    void station_invert_twice_restoresOriginal() {
        // Arrange
        buildEastWestLine(0, 3);
        run("new st;");
        Station station = model.getStations().get(0);
        Dir originalDir = station.getCreationDir();
        Dir originalSide = station.getSideDir();

        // Act
        run("station 1 invert; station 1 invert;");

        // Assert
        assertEquals(originalDir, station.getCreationDir());
        assertEquals(originalSide, station.getSideDir());
    }

    // ------------------------------------------------------------------
    // Sensor invert
    // ------------------------------------------------------------------

    @Test
    @DisplayName("sensor 1 invert flips the sensor detection direction")
    void sensor_invert_flipsCreationDir() {
        // Arrange
        buildEastWestLine(0, 3);
        run("new sn;");
        assertEquals(1, model.getSensors().size());
        letrain.track.Sensor sensor = model.getSensors().get(0);
        assertEquals(Dir.E, sensor.getCreationDir(), "cursor faced E at creation");

        // Act
        run("sensor 1 invert;");

        // Assert
        assertEquals(Dir.W, sensor.getCreationDir(), "sensor should now face west");
    }

    @Test
    @DisplayName("sensor 1 invert keeps identity and plain-sensor registration")
    void sensor_invert_keepsIdentity() {
        // Arrange
        buildEastWestLine(0, 3);
        run("new sn;");
        letrain.track.Sensor sensor = model.getSensors().get(0);

        // Act
        run("sensor 1 invert;");

        // Assert
        assertSame(sensor, model.getSensor(1), "invert must keep the same sensor identity");
        assertEquals(1, model.getSensors().size(), "still exactly one plain sensor");
    }

    @Test
    @DisplayName("sensor invert ignores a speed signal sharing a numeric id")
    void sensor_invert_doesNotTouchSpeedSignalWithSameId() {
        // Arrange: plain sensor (id 1) and speed signal (id 1, separate counters) both exist.
        buildEastWestLine(0, 4);
        run("new sn; go 1,0; face e; new sg;");
        letrain.track.SpeedSignal signal = model.getSpeedSignals().get(0);
        assertEquals(1, signal.getId());
        Dir originalDir = signal.getCreationDir();
        assertEquals(2, model.getSensors().size());

        // Act: sensor 1 must resolve to the plain sensor, not the speed signal.
        run("sensor 1 invert;");

        // Assert: the speed signal orientation is untouched, the plain sensor flipped.
        assertEquals(originalDir, signal.getCreationDir());
        letrain.track.Sensor plain = model.getSensors().stream()
                .filter(s -> s.getClass() == letrain.track.Sensor.class)
                .findFirst().orElseThrow();
        assertEquals(Dir.W, plain.getCreationDir());
    }
}
