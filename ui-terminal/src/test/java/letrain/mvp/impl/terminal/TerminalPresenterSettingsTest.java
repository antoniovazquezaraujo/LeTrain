package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anySet;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import letrain.audio.AudioController;
import letrain.map.Point;
import letrain.mvp.impl.Model;
import letrain.settings.SettingsApplyResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #764 (S3b): File → Settings in the 2D terminal. The presenter opens the dialog with the
 * running game's effective values and its scenario-locked keys, the dialog's port applies to the
 * model (S3a), the new-game shortcut confirms and re-enters the New Game flow prefilled, and Save
 * as defaults writes the game's {@code letrain.cfg} (D4).
 */
@DisplayName("2D in-game Settings flow (#764)")
class TerminalPresenterSettingsTest {

    private Model model;
    private TerminalView view;
    private TerminalPresenter presenter;

    @BeforeEach
    void setUp() {
        model = new Model(1);
        model.setMode(Model.GameMode.RAILS);
        view = mock(TerminalView.class);
        when(view.getScrollOffset()).thenReturn(new Point(0, 0));
        when(view.getCols()).thenReturn(80);
        when(view.getRows()).thenReturn(24);
        presenter = new TerminalPresenter(model, view);
        AudioController real = presenter.audioController;
        presenter.audioController = mock(AudioController.class);
        if (real != null) {
            real.stop();
        }
    }

    @AfterEach
    void tearDown() {
        stopNewAudioController();
    }

    private void stopNewAudioController() {
        if (presenter.audioController != null) {
            presenter.audioController.stop();
        }
    }

    private SettingsDialog.SettingsPort capturePort(AtomicReference<Map<String, String>> current,
            AtomicReference<Set<String>> scenario) {
        AtomicReference<SettingsDialog.SettingsPort> port = new AtomicReference<>();
        when(view.showSettingsDialog(anyMap(), anySet(), any())).thenAnswer(invocation -> {
            current.set(invocation.getArgument(0));
            scenario.set(invocation.getArgument(1));
            port.set(invocation.getArgument(2));
            return new SettingsDialog.Action.Close();
        });
        presenter.onSettings();
        return port.get();
    }

    @Test
    @DisplayName("the dialog gets the effective values (seed included) and the port applies to the model")
    void settingsDialog_usesTheEffectiveValues() {
        presenter.beginStartup();
        presenter.startNewGame(Map.of("seed", "5", "startingBalance", "100"));
        stopNewAudioController();

        AtomicReference<Map<String, String>> current = new AtomicReference<>();
        AtomicReference<Set<String>> scenario = new AtomicReference<>();
        SettingsDialog.SettingsPort port = capturePort(current, scenario);

        assertEquals("5", current.get().get("seed"), "the running world seed is shown");
        assertEquals("100.0", current.get().get("startingBalance"));
        assertTrue(scenario.get().isEmpty());

        SettingsApplyResult outcome = port.apply(Map.of("startingBalance", "250.0"));
        assertFalse(outcome.hasRejections(), String.valueOf(outcome.rejections()));
        assertTrue(outcome.applied().contains("startingBalance"));
    }

    @Test
    @DisplayName("the port mirrors S3a: NEW_GAME and scenario keys come back with their reason")
    void settingsPort_mirrorsApplyStates() {
        presenter.beginStartup();
        presenter.startNewGame(Map.of("seed", "5"));
        stopNewAudioController();
        SettingsDialog.SettingsPort port =
                capturePort(new AtomicReference<>(), new AtomicReference<>());

        assertEquals(SettingsApplyResult.REQUIRES_NEW_GAME,
                port.apply(Map.of("seed", "7")).rejections().get("seed"));

        presenter.getModel().applyScenarioConfiguration(Map.of("startingBalance", "55"));
        assertEquals(SettingsApplyResult.DEFINED_BY_SCENARIO,
                port.apply(Map.of("startingBalance", "60")).rejections().get("startingBalance"));
    }

    @Test
    @DisplayName("'New game' confirms and opens the New Game form prefilled (#767)")
    void newGameShortcut_prefillsAndConfirms() {
        presenter.beginStartup();
        presenter.startNewGame(Map.of("seed", "5"));
        stopNewAudioController();

        Map<String, String> prefill = Map.of("seed", "77", "startingBalance", "500");
        when(view.showSettingsDialog(anyMap(), anySet(), any()))
                .thenReturn(new SettingsDialog.Action.StartGame(prefill));
        when(view.confirm(any(), any())).thenReturn(false);
        presenter.onSettings();

        verify(view, never()).showNewGameDialog(anyMap());
        assertEquals(5, presenter.getModel().getSeed(), "refused: the running game stays");

        when(view.confirm(any(), any())).thenReturn(true);
        when(view.showNewGameDialog(anyMap())).thenReturn(Map.of("seed", "77"));
        presenter.onSettings();
        stopNewAudioController();

        verify(view).showNewGameDialog(prefill);
        assertEquals(77, presenter.getModel().getSeed(),
                "the prefilled form started the new world");
    }

    @Test
    @DisplayName("Save as defaults writes the form values into letrain.cfg or reports the failure (D4)")
    void saveAsDefaults_writesTheCfg() throws Exception {
        Path dir = Files.createTempDirectory("s3b-defaults");
        File cfg = dir.resolve("letrain.cfg").toFile();

        assertNull(TerminalPresenter.writeDefaults(cfg, Map.of("startingBalance", "12.5")),
                "a writable file reports success");
        assertTrue(Files.readString(cfg.toPath()).contains("startingBalance=12.5"));

        assertNotNull(
                TerminalPresenter.writeDefaults(dir.toFile(), Map.of("startingBalance", "12.5")),
                "an unwritable target comes back as a message for the dialog");
        Files.deleteIfExists(cfg.toPath());
        Files.deleteIfExists(dir);
    }
}
