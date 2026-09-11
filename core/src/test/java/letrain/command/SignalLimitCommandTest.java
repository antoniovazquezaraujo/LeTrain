package letrain.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import letrain.map.Dir;
import letrain.map.Point;
import letrain.mvp.impl.Model;
import letrain.track.Sensor;
import letrain.track.SpeedSignal;
import letrain.track.rail.RailTrack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Plain sensors and speed signals have separate id counters, so a numeric id can name both at once.
 * Direct signal commands must resolve the <b>signal</b>, never a same-id plain sensor, or the command
 * is silently dropped (this is what made an imported scenario keep the signal at its default 3).
 */
@DisplayName("Direct signal command resolves the speed signal, not a same-id plain sensor")
class SignalLimitCommandTest {

    private static RailTrack trackAt(Model model, int x, int y) {
        RailTrack track = new RailTrack();
        track.addRoute(Dir.E, Dir.W);
        track.addRoute(Dir.W, Dir.E);
        track.setPosition(new Point(x, y));
        model.getRailMap().addTrack(track.getPosition(), track);
        return track;
    }

    @Test
    @DisplayName("signal 1 set limit reaches the signal even when a plain sensor also has id 1")
    void setLimit_withIdCollision() {
        Model model = new Model(1);

        // A plain sensor created first, so it is the one getSensor(1) would find.
        RailTrack plainTrack = trackAt(model, 0, 0);
        Sensor plain = new Sensor(model.nextSensorId());
        plain.setTrack(plainTrack);
        plain.setCreationDir(Dir.E);
        plainTrack.setComponent(plain);
        model.addSensor(plain);

        RailTrack signalTrack = trackAt(model, 4, 0);
        SpeedSignal signal = new SpeedSignal(model.nextSpeedSignalId(), Dir.E, 3, true);
        signal.setTrack(signalTrack);
        signalTrack.setComponent(signal);
        model.addSensor(signal);

        assertEquals(1, plain.getId());
        assertEquals(1, signal.getId(), "both share id 1 on purpose");

        String error = PlayerCommandExecutor.execute("go 4,0; face e; signal 1 set limit 4;",
                model, null, null, null);

        assertNull(error, error);
        assertEquals(4, signal.getLimit(), "the speed signal must receive the limit");
        assertEquals(Dir.E, plain.getCreationDir(), "the plain sensor must be untouched");
    }
}
