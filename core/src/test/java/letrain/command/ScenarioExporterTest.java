package letrain.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
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

    @Test
    @DisplayName("the exported configuration comes from the catalogue, not the raw snapshot (#782)")
    void configuration_comesFromCatalogue() {
        letrain.mvp.impl.Model model = new letrain.mvp.impl.Model(1);

        String text = ScenarioExporter.render(model, List.of("go 0,0; face e; write 1;"));

        assertTrue(text.contains("configuration {"), text);
        assertTrue(text.contains("terrain.waterPercent=25"), text);
        assertTrue(text.contains("world.latitude=40.0"), text);
        assertFalse(text.contains("threshold."), "legacy thresholds must be gone: " + text);
    }

    @Test
    @DisplayName("an exported configuration round-trips into the rebuilt world's terrain (#782)")
    void configuration_roundTripsIntoModel() {
        letrain.mvp.impl.Model source = new letrain.mvp.impl.Model(1);
        source.getEconomyManager()
                .applyConfig(Map.of("terrain.waterPercent", "45", "terrain.mountainPercent", "5"));

        String text = ScenarioExporter.render(source, List.of("go 0,0; face e; write 1;"));

        ScenarioFile.Scenario scenario = ScenarioFile.parse(text);
        letrain.mvp.impl.Model rebuilt = new letrain.mvp.impl.Model(scenario.seed());
        rebuilt.applyScenarioConfiguration(scenario.configuration());

        assertTrue(rebuilt.getEconomyManager().isTerrainPercentEnabled());
        assertEquals(45, rebuilt.getEconomyManager().getWaterPercent());
        assertEquals(5, rebuilt.getEconomyManager().getMountainPercent());
    }

    @Test
    @DisplayName("world and economy keys survive the generate -> parse -> replay round-trip (#782)")
    void configuration_roundTripsWorldAndEconomyKeys() {
        letrain.mvp.impl.Model source = new letrain.mvp.impl.Model(1);
        source.getEconomyManager().applyConfig(Map.of("world.latitude", "12.5",
                "time.dayDurationSeconds", "600", "startingBalance", "750.0"));

        String text = ScenarioExporter.render(source, List.of("go 0,0; face e; write 1;"));

        ScenarioFile.Scenario scenario = ScenarioFile.parse(text);
        letrain.mvp.impl.Model rebuilt = new letrain.mvp.impl.Model(scenario.seed());
        rebuilt.applyScenarioConfiguration(scenario.configuration());

        assertEquals(12.5, rebuilt.getEconomyManager().getLatitude(), 1e-9);
        assertEquals(600, rebuilt.getEconomyManager().getDayDurationSeconds());
        assertEquals(750.0f, rebuilt.getEconomyManager().getBalance(), 0.001f);
    }

    @Test
    @DisplayName("a legacy terrain model exports the catalogue defaults, not its legacy thresholds (#782)")
    void configuration_legacyTerrainModelExportsCatalogueDefaults() {
        letrain.mvp.impl.Model source = new letrain.mvp.impl.Model(1);
        source.getEconomyManager()
                .applyConfig(Map.of("threshold.WATER", "130", "threshold.ROCK", "180"));
        assertFalse(source.getEconomyManager().isTerrainPercentEnabled(),
                "legacy thresholds switch the terrain to calibration mode");

        String text = ScenarioExporter.render(source, List.of("go 0,0; face e; write 1;"));

        assertTrue(text.contains("terrain.waterPercent=25"),
                "a legacy snapshot has no percent keys, so the catalogue default fills in: "
                        + text);
        assertTrue(text.contains("terrain.mountainPercent=10"), text);
        assertFalse(text.contains("threshold."),
                "legacy thresholds are no longer exported: " + text);

        ScenarioFile.Scenario scenario = ScenarioFile.parse(text);
        letrain.mvp.impl.Model rebuilt = new letrain.mvp.impl.Model(scenario.seed());
        rebuilt.applyScenarioConfiguration(scenario.configuration());

        assertTrue(rebuilt.getEconomyManager().isTerrainPercentEnabled(),
                "replaying a generated block always turns percent mode back on");
        assertEquals(25, rebuilt.getEconomyManager().getWaterPercent());
        assertEquals(10, rebuilt.getEconomyManager().getMountainPercent());
    }
}
