package letrain.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.mvp.Model;
import letrain.mvp.Presenter;
import letrain.mvp.View;
import letrain.mvp.impl.RailTrackMaker;
import letrain.track.SpeedSignal;
import letrain.track.rail.ForkRailTrack;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Scenario exporter: initial conditions (on start)")
class ScenarioExporterTest {

    /** A fork splitting an incoming west line into east (normal) and south (alternative). */
    private static ForkRailTrack addFork(letrain.mvp.impl.Model model, int x, int y) {
        ForkRailTrack fork = new ForkRailTrack(model.nextForkId());
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

    private static RailTrackMaker headlessMaker(letrain.mvp.impl.Model model) {
        Presenter presenter = org.mockito.Mockito.mock(Presenter.class);
        org.mockito.Mockito.when(presenter.getModel()).thenReturn(model);
        org.mockito.Mockito.when(presenter.getView())
                .thenReturn(org.mockito.Mockito.mock(View.class));
        org.mockito.Mockito.when(presenter.getAudioController()).thenReturn(null);
        return new RailTrackMaker(presenter);
    }

    private static String run(letrain.mvp.impl.Model model, String script) {
        return PlayerCommandExecutor.execute(script, model, null, null,
                new TurtleBuilder(model, headlessMaker(model)));
    }

    @Test
    @DisplayName("semaphore state is exported into on start")
    void semaphoreState_exported() {
        letrain.mvp.impl.Model model = new letrain.mvp.impl.Model();
        model.updateGroundMap(new Point(-30, -30), 60, 60);
        model.getCursor().setPosition(new Point(0, 0));
        model.getCursor().setDir(Dir.E);
        model.setMode(Model.GameMode.RAILS);
        assertEquals(null, run(model, "go 0,0; face e; write 3;"), "build must run");
        assertEquals(null, run(model, "go 2,0; face e; new sm;"), "new sm must run");
        assertEquals(1, model.getSemaphores().size(), "semaphore must exist");

        var semaphore = model.getSemaphores().get(0);
        int id = semaphore.getId();

        semaphore.setOpen(false);
        assertEquals(List.of("semaphore " + id + " close;"),
                ScenarioExporter.initialConditions(model));

        semaphore.setOpen(true);
        assertEquals(List.of("semaphore " + id + " open;"),
                ScenarioExporter.initialConditions(model));

        String text = ScenarioExporter.render(model, List.of("go 0,0; face e; write 3;"));
        assertTrue(text.contains("on start {"), text);
        assertTrue(text.contains("semaphore " + id + " open;"), text);
    }

    @Test
    @DisplayName("fork route is exported into on start (straight and curved)")
    void forkRoute_exported() {
        letrain.mvp.impl.Model model = new letrain.mvp.impl.Model(1);
        ForkRailTrack fork = addFork(model, 0, 0);
        int id = fork.getId();

        fork.setNormalRoute();
        assertEquals(List.of("fork " + id + " set straight;"),
                ScenarioExporter.initialConditions(model));

        fork.setAlternativeRoute();
        assertEquals(List.of("fork " + id + " set curved;"),
                ScenarioExporter.initialConditions(model));
    }

    @Test
    @DisplayName("speed-signal mode and limit are exported into on start")
    void signalState_exported() {
        letrain.mvp.impl.Model model = new letrain.mvp.impl.Model(1);
        SpeedSignal signal = new SpeedSignal(model.nextSpeedSignalId(), Dir.E, 40, false);
        model.addSensor(signal);
        int id = signal.getId();

        assertEquals(List.of("signal " + id + " set mode min;", "signal " + id + " set limit 40;"),
                ScenarioExporter.initialConditions(model));

        signal.setMax(true);
        signal.setLimit(80);
        assertEquals(List.of("signal " + id + " set mode max;", "signal " + id + " set limit 80;"),
                ScenarioExporter.initialConditions(model));
    }

    @Test
    @DisplayName("the automation program is exported into the program section")
    void program_exported() {
        letrain.mvp.impl.Model model = new letrain.mvp.impl.Model(1);
        String program = "sensor 1 on train enter { semaphore 1 open; }";
        model.setProgram(program);

        String text = ScenarioExporter.render(model, List.of("go 0,0; face e; write 3;"));

        assertTrue(text.contains("program {"), text);
        assertTrue(text.contains(program), text);
    }
}
