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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Scenario exporter: initial conditions (on start)")
class ScenarioExporterTest {

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
}
