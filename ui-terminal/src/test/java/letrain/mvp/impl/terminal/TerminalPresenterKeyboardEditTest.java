package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import letrain.audio.AudioController;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.mvp.impl.Model;
import letrain.mvp.input.InputEvent;
import letrain.mvp.input.KeyType;
import letrain.track.RailSemaphore;
import letrain.track.Sensor;
import letrain.track.Station;
import letrain.track.rail.RailTrack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Keyboard state toggles (signal invert/mode/limit, fork route, station/sensor/semaphore invert) must
 * be recorded into the journal exactly like console commands, so they show up in the exported
 * scenario and can be undone.
 */
@DisplayName("2D terminal: keyboard state toggles are recorded")
class TerminalPresenterKeyboardEditTest {

    private static InputEvent charKey(char c) {
        return new InputEvent(KeyType.Character, c, false, false, false);
    }

    /** A presenter with the view and audio mocked out, so tests never draw or make sound. */
    private static TerminalPresenter silentPresenter(Model model) {
        TerminalPresenter presenter = new TerminalPresenter(model, mock(TerminalView.class));
        AudioController realAudio = presenter.audioController;
        presenter.audioController = mock(AudioController.class);
        if (realAudio != null) {
            realAudio.stop();
        }
        return presenter;
    }

    private static void console(TerminalPresenter presenter, String cmd) {
        presenter.onChar(charKey(':'));
        for (int i = 0; i < cmd.length(); i++) {
            presenter.onChar(charKey(cmd.charAt(i)));
        }
        presenter.onChar(new InputEvent(KeyType.Enter));
    }

    /** A fork splitting an incoming west line into east (normal) and south (alternative). */
    private static letrain.track.rail.ForkRailTrack addFork(Model model, int x, int y) {
        letrain.track.rail.ForkRailTrack fork =
                new letrain.track.rail.ForkRailTrack(model.nextForkId());
        fork.setPosition(new Point(x, y));
        fork.setCreationDir(Dir.E);
        fork.addRoute(Dir.W, Dir.E);
        fork.addRoute(Dir.E, Dir.W);
        fork.addRoute(Dir.W, Dir.S);
        fork.addRoute(Dir.S, Dir.W);
        fork.setNormalRoute();
        model.getRailMap().addTrack(fork.getPosition(), fork);
        model.addFork(fork);
        return fork;
    }

    @Test
    @DisplayName("signal invert/mode/limit from the keyboard are journaled as canonical commands")
    void keyboardSignalToggles_areJournaled() {
        Model model = new Model();
        model.updateGroundMap(new Point(-30, -30), 60, 60);
        model.getCursor().setPosition(new Point(7, 0));
        model.getCursor().setDir(Dir.E);
        model.setMode(Model.GameMode.RAILS);

        TerminalPresenter presenter = silentPresenter(model);

        // Build a tile and a speed signal through the console (default limit 3, mode max, facing E).
        console(presenter, "go 7,0; face e; write 1;");
        console(presenter, "go 7,0; face e; new sg;");
        assertEquals("", model.getCommandError());
        assertEquals(1, model.getSpeedSignals().size());

        // Enter Record/edit mode with 'R': recording starts and undo begins.
        presenter.onChar(charKey('R'));
        assertEquals(0, model.getCommandJournal().size());

        letrain.track.SpeedSignal sig = model.getSpeedSignals().get(0);
        model.selectSpeedSignal(sig.getId());
        model.setMode(Model.GameMode.SPEED_SIGNALS);
        model.getCursor().setPosition(new Point(7, 0));
        model.getCursor().setDir(Dir.E);

        presenter.onChar(charKey(' ')); // invert
        presenter.onChar(charKey('m')); // mode max -> min
        presenter.onChar(charKey('7')); // limit -> 7
        presenter.onChar(charKey('4')); // limit -> 4 (collapses into the previous one)

        assertEquals(3, model.getCommandJournal().size(),
                "consecutive limit tweaks must collapse into a single command");
        assertEquals("go 7,0; face e; signal " + sig.getId() + " invert;",
                model.getCommandJournal().entries().get(0));
        assertEquals("go 7,0; face e; signal " + sig.getId() + " set mode min;",
                model.getCommandJournal().entries().get(1));
        assertEquals("go 7,0; face e; signal " + sig.getId() + " set limit 4;",
                model.getCommandJournal().entries().get(2));
    }

    /** An east-west rail tile at (x,y) with no component yet. */
    private static RailTrack trackAt(Model model, int x, int y) {
        RailTrack track = new RailTrack();
        track.addRoute(Dir.E, Dir.W);
        track.addRoute(Dir.W, Dir.E);
        track.setPosition(new Point(x, y));
        model.getRailMap().addTrack(track.getPosition(), track);
        return track;
    }

    @Test
    @DisplayName("inverting a plain sensor from the keyboard is journaled")
    void keyboardSensorInvert_isJournaled() {
        Model model = new Model(1);
        TerminalPresenter presenter = silentPresenter(model);
        RailTrack track = trackAt(model, 0, 0);
        Sensor sensor = new Sensor(model.nextSensorId());
        sensor.setTrack(track);
        sensor.setCreationDir(Dir.E);
        track.setComponent(sensor);
        model.addSensor(sensor);

        presenter.onChar(charKey('R')); // Record/edit mode: recording starts
        model.selectSensor(sensor.getId());
        model.setMode(Model.GameMode.SENSORS);

        presenter.onChar(charKey(' '));

        assertEquals(1, model.getCommandJournal().size());
        assertEquals("sensor " + sensor.getId() + " invert;",
                model.getCommandJournal().entries().get(0));
    }

    @Test
    @DisplayName("inverting a station from the keyboard is journaled")
    void keyboardStationInvert_isJournaled() {
        Model model = new Model(1);
        TerminalPresenter presenter = silentPresenter(model);
        RailTrack track = trackAt(model, 0, 0);
        Station station = new Station(model.nextStationId());
        station.setTrack(track);
        station.setCreationDir(Dir.E);
        station.setSideDir(Dir.E.turnRight().turnRight());
        track.setComponent(station);
        model.addStation(station);

        presenter.onChar(charKey('R'));
        model.selectStation(station.getId());
        model.setMode(Model.GameMode.STATIONS);

        presenter.onChar(charKey(' '));

        assertEquals(1, model.getCommandJournal().size());
        assertEquals("station " + station.getId() + " invert;",
                model.getCommandJournal().entries().get(0));
    }

    @Test
    @DisplayName("inverting a semaphore from the keyboard is journaled")
    void keyboardSemaphoreInvert_isJournaled() {
        Model model = new Model(1);
        TerminalPresenter presenter = silentPresenter(model);
        RailTrack track = trackAt(model, 0, 0);
        RailSemaphore semaphore = new RailSemaphore(model.nextSemaphoreId());
        semaphore.setTrack(track);
        semaphore.setCreationDir(Dir.E);
        track.setComponent(semaphore);
        model.addSemaphore(semaphore);

        presenter.onChar(charKey('R'));
        model.selectSemaphore(semaphore.getId());
        model.setMode(Model.GameMode.SEMAPHORES);

        presenter.onChar(charKey(' ')); // Space inverts the semaphore direction

        assertEquals(1, model.getCommandJournal().size());
        assertEquals("semaphore " + semaphore.getId() + " invert;",
                model.getCommandJournal().entries().get(0));
    }

    @Test
    @DisplayName("flipping a fork from the keyboard is journaled as an absolute route command")
    void keyboardForkFlip_isJournaled() {
        Model model = new Model(1);
        model.updateGroundMap(new Point(-30, -30), 60, 60);
        model.setMode(Model.GameMode.RAILS);

        TerminalPresenter presenter = silentPresenter(model);

        letrain.track.rail.ForkRailTrack fork = addFork(model, 0, 0);

        presenter.onChar(charKey('R')); // Record/edit mode: recording starts
        model.selectFork(fork.getId());
        model.setMode(Model.GameMode.FORKS);

        presenter.onChar(charKey(' ')); // straight -> curved
        assertEquals(1, model.getCommandJournal().size());
        assertEquals("fork " + fork.getId() + " set curved;",
                model.getCommandJournal().entries().get(0));

        presenter.onChar(charKey(' ')); // curved -> straight (collapses into the previous one)
        assertEquals(1, model.getCommandJournal().size(),
                "consecutive route flips must collapse into a single command");
        assertEquals("fork " + fork.getId() + " set straight;",
                model.getCommandJournal().entries().get(0));
    }

    @Test
    @DisplayName("a console line mixing navigation and an edit is journaled; pure navigation is not")
    void consoleLineWithNavigationAndEdit_isJournaled() {
        Model model = new Model(1);
        model.updateGroundMap(new Point(-30, -30), 60, 60);
        model.getCursor().setPosition(new Point(7, 0));
        model.getCursor().setDir(Dir.E);
        model.setMode(Model.GameMode.RAILS);

        TerminalPresenter presenter = silentPresenter(model);
        presenter.onChar(charKey('R')); // Record/edit mode: recording starts

        // Full self-positioned edit line: must be recorded (it used to be dropped for starting 'go').
        console(presenter, "go 7,0; face e; write 1;");
        assertEquals(1, model.getCommandJournal().size(),
                "a line with navigation + an edit must be recorded");

        // Pure navigation must still be ignored.
        console(presenter, "go 1,1;");
        assertEquals(1, model.getCommandJournal().size(),
                "pure navigation must not be recorded");
    }
}
