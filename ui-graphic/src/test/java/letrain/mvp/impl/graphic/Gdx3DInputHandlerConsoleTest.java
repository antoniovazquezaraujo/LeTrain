package letrain.mvp.impl.graphic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import letrain.map.Dir;
import letrain.map.Point;
import letrain.mvp.Presenter;
import letrain.mvp.View;
import letrain.mvp.impl.Model;
import letrain.mvp.impl.RailTrackMaker;
import letrain.mvp.input.InputEvent;
import letrain.mvp.input.KeyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Drives {@link Gdx3DInputHandler#onChar} directly (no GL) to verify the ':' console history
 * (arrows) and '.' repeat behave like the terminal presenter.
 */
@DisplayName("3D console: command history and '.' repeat")
class Gdx3DInputHandlerConsoleTest {

    private Model model;
    private GraphicPresenter view;
    private Gdx3DInputHandler handler;
    private RailTrackMaker trackMaker;

    private static InputEvent key(KeyType type) {
        return new InputEvent(type);
    }

    private static InputEvent charKey(char c) {
        return new InputEvent(KeyType.Character, c, false, false, false);
    }

    @BeforeEach
    void setUp() {
        model = new Model();
        model.updateGroundMap(new Point(-30, -30), 60, 60);
        model.getCursor().setPosition(new Point(0, 0));
        model.getCursor().setDir(Dir.E);
        model.setMode(Model.GameMode.RAILS);

        view = mock(GraphicPresenter.class);
        when(view.getModel()).thenReturn(model);
        when(view.getView()).thenReturn(mock(View.class));
        when(view.getUndoRedoHistory()).thenReturn(null);
        when(view.getCommandHistory()).thenReturn(new CommandHistory());

        Presenter makerPresenter = mock(Presenter.class);
        when(makerPresenter.getModel()).thenReturn(model);
        when(makerPresenter.getView()).thenReturn(mock(View.class));
        when(makerPresenter.getAudioController()).thenReturn(null);
        when(makerPresenter.getUndoRedoHistory()).thenReturn(null);
        trackMaker = new RailTrackMaker(makerPresenter);

        handler = new Gdx3DInputHandler(model, view, new CameraController(model), trackMaker,
                mock(letrain.audio.AudioController.class));
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

    private void typeCommand(String cmd) {
        for (int i = 0; i < cmd.length(); i++) {
            handler.onChar(charKey(cmd.charAt(i)));
        }
    }

    private String executeInConsole(String cmd) {
        handler.onChar(charKey(':'));
        assertEquals(Model.GameMode.COMMAND, model.getMode());
        typeCommand(cmd);
        handler.onChar(key(KeyType.Enter));
        return model.getCommandText();
    }

    @Test
    @DisplayName("commands typed in ':' are stored and navigable with the arrows")
    void executedCommands_areNavigableWithArrows() {
        // First command executes and exits to RAILS.
        executeInConsole("go 5,0; face e; write 1;");
        assertEquals(Model.GameMode.RAILS, model.getMode());
        // Second (different) command.
        executeInConsole("go 8,0; face e; write 1;");
        assertEquals(Model.GameMode.RAILS, model.getMode());

        // Open ':' again: ArrowUp walks backwards to the previous commands.
        handler.onChar(charKey(':'));
        handler.onChar(key(KeyType.ArrowUp));
        assertEquals("go 8,0; face e; write 1;", model.getCommandText());
        handler.onChar(key(KeyType.ArrowUp));
        assertEquals("go 5,0; face e; write 1;", model.getCommandText());
        // ArrowDown walks forward again (past the last entry clears to empty).
        handler.onChar(key(KeyType.ArrowDown));
        assertEquals("go 8,0; face e; write 1;", model.getCommandText());
        handler.onChar(key(KeyType.ArrowDown));
        assertEquals("", model.getCommandText());
    }

    @Test
    @DisplayName("typing the same command twice does not duplicate it in the history")
    void repeatedCommand_isNotDuplicated() {
        executeInConsole("go 3,0; face e; write 1;");
        executeInConsole("go 3,0; face e; write 1;");

        handler.onChar(charKey(':'));
        handler.onChar(key(KeyType.ArrowUp));
        assertEquals("go 3,0; face e; write 1;", model.getCommandText());
        handler.onChar(key(KeyType.ArrowUp)); // no older entry -> stays
        assertEquals("go 3,0; face e; write 1;", model.getCommandText());
    }

    @Test
    @DisplayName("Shift+X triggers experiment mode toggle")
    void shiftX_togglesExperimentMode() {
        handler.onChar(charKey('X'));
        org.mockito.Mockito.verify(view).toggleExperimentMode();
    }

    @Test
    @DisplayName("console commands journal the canonical self-positioned form while recording")
    void consoleCommand_recordsCanonicalJournal() {
        model.setPauseEditing(true); // recording only while frozen in edit mode
        model.getCommandJournal().startRecording();
        executeInConsole("write 1;");
        assertEquals(1, model.getCommandJournal().size());
        assertEquals("go 0,0; face e; write 1;", model.getCommandJournal().entries().get(0));
    }

    @Test
    @DisplayName("keyboard signal toggles are journaled as canonical commands")
    void keyboardSignalToggles_areJournaled() {
        model.setPauseEditing(true);
        model.getCommandJournal().startRecording();
        // Build a tile and a speed signal through the console (default limit 3, mode max, facing E).
        executeInConsole("go 7,0; face e; write 1;");
        executeInConsole("go 7,0; face e; new sg;");
        assertEquals("", model.getCommandError());
        assertEquals(1, model.getSpeedSignals().size());
        model.getCommandJournal().clear();
        model.getCommandJournal().startRecording();

        letrain.track.SpeedSignal sig = model.getSpeedSignals().get(0);
        model.selectSpeedSignal(sig.getId());
        model.setMode(Model.GameMode.SPEED_SIGNALS);
        model.getCursor().setPosition(new Point(7, 0));
        model.getCursor().setDir(Dir.E);

        handler.onChar(charKey(' ')); // invert
        handler.onChar(charKey('m')); // mode max -> min
        handler.onChar(charKey('7')); // limit -> 7
        handler.onChar(charKey('4')); // limit -> 4 (collapses into the previous one)

        assertEquals(3, model.getCommandJournal().size(),
                "consecutive limit tweaks must collapse into a single command");
        assertEquals("go 7,0; face e; signal " + sig.getId() + " invert;",
                model.getCommandJournal().entries().get(0));
        assertEquals("go 7,0; face e; signal " + sig.getId() + " set mode min;",
                model.getCommandJournal().entries().get(1));
        assertEquals("go 7,0; face e; signal " + sig.getId() + " set limit 4;",
                model.getCommandJournal().entries().get(2));
    }

    @Test
    @DisplayName("flipping a fork from the keyboard journals an absolute route command")
    void keyboardForkFlip_isJournaled() {
        letrain.track.rail.ForkRailTrack fork = addFork(model, 0, 0);
        model.selectFork(fork.getId());
        model.setMode(Model.GameMode.FORKS);

        handler.onChar(charKey(' ')); // straight -> curved

        org.mockito.Mockito.verify(view)
                .journalEditingCommand("fork " + fork.getId() + " set curved;");
    }

    /** An east-west rail tile at (x,y) with no component yet. */
    private static letrain.track.rail.RailTrack trackAt(Model model, int x, int y) {
        letrain.track.rail.RailTrack track = new letrain.track.rail.RailTrack();
        track.addRoute(Dir.E, Dir.W);
        track.addRoute(Dir.W, Dir.E);
        track.setPosition(new Point(x, y));
        model.getRailMap().addTrack(track.getPosition(), track);
        return track;
    }

    @Test
    @DisplayName("inverting station/sensor/semaphore from the keyboard journals the commands")
    void keyboardElementToggles_areJournaled() {
        letrain.track.rail.RailTrack sensorTrack = trackAt(model, 0, 0);
        letrain.track.Sensor sensor = new letrain.track.Sensor(model.nextSensorId());
        sensor.setTrack(sensorTrack);
        sensor.setCreationDir(Dir.E);
        sensorTrack.setComponent(sensor);
        model.addSensor(sensor);
        model.selectSensor(sensor.getId());
        model.setMode(Model.GameMode.SENSORS);
        handler.onChar(charKey(' '));
        org.mockito.Mockito.verify(view)
                .journalEditingCommand("sensor " + sensor.getId() + " invert;");

        letrain.track.rail.RailTrack semaphoreTrack = trackAt(model, 2, 0);
        letrain.track.RailSemaphore semaphore =
                new letrain.track.RailSemaphore(model.nextSemaphoreId());
        semaphore.setTrack(semaphoreTrack);
        semaphore.setCreationDir(Dir.E);
        semaphoreTrack.setComponent(semaphore);
        model.addSemaphore(semaphore);
        model.selectSemaphore(semaphore.getId());
        model.setMode(Model.GameMode.SEMAPHORES);
        handler.onChar(charKey(' '));
        org.mockito.Mockito.verify(view)
                .journalEditingCommand("semaphore " + semaphore.getId() + " invert;");

        letrain.track.rail.RailTrack stationTrack = trackAt(model, 4, 0);
        letrain.track.Station station = new letrain.track.Station(model.nextStationId());
        station.setTrack(stationTrack);
        station.setCreationDir(Dir.E);
        station.setSideDir(Dir.E.turnRight().turnRight());
        stationTrack.setComponent(station);
        model.addStation(station);
        model.selectStation(station.getId());
        model.setMode(Model.GameMode.STATIONS);
        handler.onChar(charKey(' '));
        org.mockito.Mockito.verify(view)
                .journalEditingCommand("station " + station.getId() + " invert;");
    }

    @Test
    @DisplayName("finishing a train (Enter in TRAINS) returns to RAILS, not the empty menu")
    void enterInTrains_returnsToRails() {
        model.setMode(Model.GameMode.TRAINS);
        handler.onChar(key(KeyType.Enter));
        assertEquals(Model.GameMode.RAILS, model.getMode());
    }

    @Test
    @DisplayName("'.' outside COMMAND repeats the last executed command")
    void dotRepeatsLastCommand() {
        executeInConsole("go 7,0; face e; write 1;"); // build a tile at (7,0)
        executeInConsole("go 7,0; face e; new sn;"); // last command: place a sensor there
        assertEquals(1, model.getSensors().size());
        assertEquals("", model.getCommandError());
        assertEquals(Model.GameMode.RAILS, model.getMode());

        // '.' re-runs "new sn" on a tile that already has a sensor -> the repeat must error, which
        // proves the command was actually executed a second time.
        handler.onChar(charKey('.'));
        assertEquals(Model.GameMode.RAILS, model.getMode());
        assertEquals(1, model.getSensors().size());
        assertEquals("Cannot place sensor: Track already has a component.", model.getCommandError());
    }

    @Test
    @DisplayName("history survives a model/handler swap like the one an undo performs")
    void history_survivesHandlerRecreation() {
        executeInConsole("go 5,0; face e; write 1;");
        assertEquals(Model.GameMode.RAILS, model.getMode());

        // An undo swaps the model and recreates the input handler (applyModel); the command history
        // lives in the presenter and must still be navigable from the brand-new handler.
        handler = new Gdx3DInputHandler(model, view, new CameraController(model), trackMaker, null);
        handler.onChar(charKey(':'));
        handler.onChar(key(KeyType.ArrowUp));
        assertEquals("go 5,0; face e; write 1;", model.getCommandText());
    }
}
