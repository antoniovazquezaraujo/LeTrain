package letrain.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import letrain.map.Dir;
import letrain.map.Point;
import letrain.mvp.Model;
import letrain.mvp.Presenter;
import letrain.mvp.View;
import letrain.mvp.impl.RailTrackMaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Coupling DSL: train <locoId> couple/uncouple (ADR-020 item 3 phase 2)")
class CouplingCommandTest {

    private letrain.mvp.impl.Model model;

    private static RailTrackMaker headlessMaker(letrain.mvp.impl.Model model) {
        Presenter presenter = org.mockito.Mockito.mock(Presenter.class);
        org.mockito.Mockito.when(presenter.getModel()).thenReturn(model);
        org.mockito.Mockito.when(presenter.getView())
                .thenReturn(org.mockito.Mockito.mock(View.class));
        org.mockito.Mockito.when(presenter.getAudioController()).thenReturn(null);
        return new RailTrackMaker(presenter);
    }

    private String run(String script) {
        return PlayerCommandExecutor.execute(script, model, null, null,
                new TurtleBuilder(model, headlessMaker(model)));
    }

    @BeforeEach
    void setUp() {
        model = new letrain.mvp.impl.Model();
        model.updateGroundMap(new Point(-30, -30), 60, 60);
        model.getCursor().setPosition(new Point(0, 0));
        model.getCursor().setDir(Dir.E);
        model.setMode(Model.GameMode.RAILS);
        assertEquals(null, run("go 0,0; face e; write 3;"));
        assertEquals(null, run("go 0,0; face e; new locomotive A red;"));
        assertEquals(null, run("go 1,0; face e; new wagon b coal;"));
        assertEquals(1, model.getLocomotives().size());
        assertEquals(1, model.getWagons().size());
    }

    @Test
    @DisplayName("optimized straight writes replay identically to the per-tile commands")
    void optimize_replayEquivalent() {
        java.util.List<String> original = java.util.List.of(
                "go 0,0; face e; write 1;",
                "go 1,0; face e; write 1;",
                "go 2,0; face e; write 1;",
                "go 3,0; face e; write 1;");
        java.util.List<String> optimized = ScenarioFile.optimize(original);

        letrain.mvp.impl.Model a = freshWorld();
        letrain.mvp.impl.Model b = freshWorld();
        for (String cmd : original) {
            assertEquals(null, PlayerCommandExecutor.execute(cmd, a, null, null,
                    new TurtleBuilder(a, headlessMaker(a))));
        }
        for (String cmd : optimized) {
            assertEquals(null, PlayerCommandExecutor.execute(cmd, b, null, null,
                    new TurtleBuilder(b, headlessMaker(b))));
        }
        assertEquals(semanticKey(a), semanticKey(b),
                "optimized scenario must replay to the same tracks/cursor");
    }

    private static letrain.mvp.impl.Model freshWorld() {
        letrain.mvp.impl.Model m = new letrain.mvp.impl.Model();
        m.updateGroundMap(new Point(-30, -30), 60, 60);
        m.getCursor().setPosition(new Point(0, 0));
        m.getCursor().setDir(Dir.E);
        m.setMode(Model.GameMode.RAILS);
        return m;
    }

    private static String semanticKey(letrain.mvp.impl.Model m) {
        java.util.TreeSet<String> tiles = new java.util.TreeSet<>();
        m.getRailMap().forEach(t -> tiles.add(t.getPosition().getX() + "," + t.getPosition().getY()));
        return m.getCursor().getPosition() + " " + m.getCursor().getDir() + " " + tiles;
    }

    @Test
    @DisplayName("bright palette colors are valid DSL colors (exact scenario replay)")
    void brightPaletteColor_isValid() {
        assertEquals(null, run("go 10,0; face e; write 3;"));
        assertEquals(null, run("go 10,0; face e; new locomotive C blue_bright;"));
        assertEquals(2, model.getLocomotives().size());
        assertEquals("BLUE_BRIGHT", model.getLocomotives().get(1).getColor());
    }

    @Test
    @DisplayName("couple joins the adjacent wagon and uncouple splits it back")
    void coupleUncouple_roundTrip() {
        int locoId = model.getLocomotives().get(0).getId();
        assertNotNull(model.getTrainFromLocomotiveId(locoId));
        assertEquals(1, model.getTrainFromLocomotiveId(locoId).getLinkers().size());

        String couple = "train " + locoId + " couple forward 1;";
        assertEquals(null, run(couple), "couple must run");
        var train = model.getTrainFromLocomotiveId(locoId);
        assertEquals(2, train.getLinkers().size(), "train must have loco + wagon after couple");

        String uncouple = "train " + locoId + " uncouple forward 1;";
        assertEquals(null, run(uncouple), "uncouple must run");
        assertEquals(1, model.getTrainFromLocomotiveId(locoId).getLinkers().size(),
                "train must have only the loco after uncouple");
    }
}
