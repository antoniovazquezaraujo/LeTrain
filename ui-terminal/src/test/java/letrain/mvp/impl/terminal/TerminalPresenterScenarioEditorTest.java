package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import letrain.audio.AudioController;
import letrain.map.Point;
import letrain.mvp.impl.Model;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("2D terminal: scenario editor backend")
class TerminalPresenterScenarioEditorTest {

    private static TerminalPresenter presenterWith(Model model) {
        TerminalView view = mock(TerminalView.class);
        when(view.getScrollOffset()).thenReturn(new Point(0, 0));
        when(view.getCols()).thenReturn(80);
        when(view.getRows()).thenReturn(24);
        TerminalPresenter presenter = new TerminalPresenter(model, view);
        AudioController realAudio = presenter.audioController;
        presenter.audioController = mock(AudioController.class);
        if (realAudio != null) {
            realAudio.stop();
        }
        return presenter;
    }

    @Test
    @DisplayName("getScenarioText returns the world recipe (journal + on start) without the program")
    void getScenarioText_isRecipeWithoutProgram() {
        Model model = new Model(1);
        model.setProgram("sensor 1 on train enter { semaphore 1 open; }");
        TerminalPresenter presenter = presenterWith(model);
        presenter.getModel().getCommandJournal().record("go 0,0; face e; write 1;");

        String text = presenter.getScenarioText();

        assertTrue(text.contains("seed 1"), text);
        assertTrue(text.contains("go 0,0; face e; write 1;"), text);
        assertFalse(text.contains("program {"), "the recipe must not carry the program: " + text);
        assertFalse(text.contains("sensor 1 on train enter"), text);
    }

    @Test
    @DisplayName("onExportScenarioText writes the given text verbatim")
    void exportText_writesFile() throws Exception {
        Model model = new Model(1);
        TerminalPresenter presenter = presenterWith(model);
        java.nio.file.Path file = java.nio.file.Files.createTempFile("exp", ".ltr");
        String text = "# LeTrain scenario v1\nseed 1\non build {\n}\n";
        try {
            presenter.onExportScenarioText(file.toFile(), text);
            assertEquals(text, java.nio.file.Files.readString(file));
        } finally {
            java.nio.file.Files.deleteIfExists(file);
        }
    }

    @Test
    @DisplayName("onPlayScenarioText rebuilds the world and installs the program")
    void playText_rebuildsWorld() {
        Model model = new Model(1);
        TerminalPresenter presenter = presenterWith(model);
        String text = "# LeTrain scenario v1\n"
                + "seed 1\n"
                + "on build {\n"
                + "go 0,0; face e; write 3;\n"
                + "}\n"
                + "program {\n"
                + "sensor 1 on train enter { semaphore 1 open; }\n"
                + "}\n";

        presenter.onPlayScenarioText(text);

        assertFalse(presenter.getModel().getRailMap().getRails().isEmpty(), "rails must exist");
        assertTrue(presenter.getModel().getProgram().contains("sensor 1 on train enter"),
                presenter.getModel().getProgram());
    }

    @Test
    @DisplayName("onPlayScenarioText applies the scenario configuration (wins over the local file)")
    void playText_appliesConfiguration() {
        Model model = new Model(1);
        TerminalPresenter presenter = presenterWith(model);
        String text = "# LeTrain scenario v1\n"
                + "seed 1\n"
                + "configuration {\n"
                + "threshold.WATER=99.5\n"
                + "threshold.ROCK=199.5\n"
                + "}\n"
                + "on build {\n"
                + "go 0,0; face e; write 3;\n"
                + "}\n";

        presenter.onPlayScenarioText(text);

        assertEquals(99.5f, presenter.getModel().getEconomyManager().getWaterThreshold(), 0.001f);
        assertEquals(199.5f, presenter.getModel().getEconomyManager().getRockThreshold(), 0.001f);
    }
}
