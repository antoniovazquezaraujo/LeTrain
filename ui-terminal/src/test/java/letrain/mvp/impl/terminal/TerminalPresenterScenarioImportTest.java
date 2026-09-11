package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import letrain.audio.AudioController;
import letrain.map.Point;
import letrain.mvp.impl.Model;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("2D terminal: scenario import installs the program")
class TerminalPresenterScenarioImportTest {

    @Test
    @DisplayName("a scenario's program section is installed on import")
    void importScenario_installsProgram() throws Exception {
        Model model = new Model(1);
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

        java.nio.file.Path file = java.nio.file.Files.createTempFile("scenario", ".ltr");
        java.nio.file.Files.writeString(file,
                "# LeTrain scenario v1\n"
                        + "seed 1\n"
                        + "on build {\n"
                        + "go 0,0; face e; write 3;\n"
                        + "}\n"
                        + "program {\n"
                        + "sensor 1 on train enter { semaphore 1 open; }\n"
                        + "}\n");
        try {
            presenter.onImportScenario(file.toFile());
            assertTrue(presenter.getModel().getProgram().contains("sensor 1 on train enter"),
                    "the program must be installed: " + presenter.getModel().getProgram());
        } finally {
            java.nio.file.Files.deleteIfExists(file);
        }
    }
}
