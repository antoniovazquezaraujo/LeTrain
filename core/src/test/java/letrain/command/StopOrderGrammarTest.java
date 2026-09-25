package letrain.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import letrain.mvp.impl.Model;
import letrain.track.Sensor;
import letrain.track.Station;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #619: grammar of the one-shot {@code stop at ...} / {@code stop when blocked} train orders.
 * The destination and the speed belong to the same order; the speed is optional.
 */
@DisplayName("Issue #619: 'stop at / stop when blocked' order grammar")
class StopOrderGrammarTest {

    private Model model;

    @BeforeEach
    void setUp() {
        model = new Model(1);
        model.postLoadInit();
        Station station = new Station(1);
        station.setName("A");
        model.addStation(station);
        Sensor sensor = new Sensor(1);
        sensor.setName("S1");
        model.addSensor(sensor);
        Locomotive loco = new Locomotive(1, "A");
        Train train = new Train(1);
        train.pushBack(loco);
        model.addLocomotive(loco);
    }

    private List<String> run(String text) {
        return model.setProgram(text);
    }

    @Test
    @DisplayName("accepts station, sensor, end of track and blocked, with and without speed")
    void acceptsTheFourForms() {
        assertTrue(run("train 1 stop at sensor 1 speed 2;\n").isEmpty());
        assertTrue(run("train 1 stop at station \"A\" speed 4;\n").isEmpty());
        assertTrue(run("train 1 stop at end speed 2;\n").isEmpty());
        assertTrue(run("train 1 stop when blocked speed 2;\n").isEmpty());
        assertTrue(run("train 1 stop at sensor \"S1\";\n").isEmpty());
        assertTrue(run("train 1 stop at station 1;\n").isEmpty());
        assertTrue(run("train 1 stop at end;\n").isEmpty());
        assertTrue(run("train 1 stop when blocked;\n").isEmpty());
    }

    @Test
    @DisplayName("rejects incomplete destinations and malformed speeds")
    void rejectsMalformedOrders() {
        assertFalse(run("train 1 stop;\n").isEmpty(), "bare stop is not an order");
        assertFalse(run("train 1 stop at;\n").isEmpty(), "missing destination");
        assertFalse(run("train 1 stop at sensor;\n").isEmpty(), "missing sensor id");
        assertFalse(run("train 1 stop at end of track;\n").isEmpty(), "extra words");
        assertFalse(run("train 1 stop when;\n").isEmpty(), "missing blocked");
        assertFalse(run("train 1 stop when blocked 2;\n").isEmpty(), "speed needs the keyword");
        assertFalse(run("train 1 stop at end speed;\n").isEmpty(), "speed without value");
        assertFalse(run("train 1 stop at sensor 1 speed fast;\n").isEmpty(),
                "speed must be numeric");
    }
}
