package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import letrain.audio.AudioController;
import letrain.map.Point;
import letrain.mvp.impl.Model;
import letrain.mvp.input.InputEvent;
import letrain.mvp.input.KeyType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #746 (S2): portada → menú → Nueva partida in the 2D terminal. The boot menu no longer
 * auto-starts the world (New/Load start it, Save is disabled until then), the New Game dialog swaps
 * in a fresh world with its settings and the console {@code new game;} opens the same flow.
 */
@DisplayName("2D New Game flow (#746)")
class TerminalPresenterNewGameTest {

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
        if (presenter.audioController != null) {
            presenter.audioController.stop();
        }
    }

    private static InputEvent enterKey() {
        return new InputEvent(KeyType.Enter);
    }

    private void stopNewAudioController() {
        if (presenter.audioController != null) {
            presenter.audioController.stop();
        }
    }

    @Test
    @DisplayName("boot menu: no game yet; Esc there returns to the cover instead of a world")
    void bootMenu_returnsToCoverWithoutAGame() {
        presenter.beginStartup();

        assertFalse(presenter.hasActiveGame(), "the boot menu has no game to save");
        boolean started = presenter.startupMenuStep();

        assertFalse(started);
        verify(view).showFileMenu();
        assertTrue(presenter.isCoverActive(), "Esc on the boot menu goes back to the cover");
        verify(view, org.mockito.Mockito.atLeastOnce()).setCoverVisible(true);
    }

    @Test
    @DisplayName("New from the boot menu opens the dialog and starts the world with its settings")
    void newGame_startsAWorldWithTheChosenSettings() {
        presenter.beginStartup();
        Map<String, String> settings = Map.of("seed", "777", "terrain.waterPercent", "60",
                "terrain.mountainPercent", "5", "startingBalance", "500");
        when(view.showNewGameDialog(anyMap())).thenReturn(settings);

        presenter.onNewGame();
        stopNewAudioController();

        assertTrue(presenter.hasActiveGame(), "Save must be enabled once the game starts");
        assertNotSame(model, presenter.getModel(), "a fresh world replaces the boot model");
        assertEquals(777, presenter.getModel().getSeed());
        assertEquals(60, presenter.getModel().getEconomyManager().getWaterPercent());
        assertEquals(5, presenter.getModel().getEconomyManager().getMountainPercent());
        assertEquals(500f, presenter.getModel().getEconomyManager().getBalance(), 0.001f);
        verify(view).setStatusBarText(contains("777"));
        verify(view, never()).confirm(any(), any());
    }

    @Test
    @DisplayName("cancelling the dialog (null) leaves the current world untouched")
    void cancellingTheDialog_keepsTheWorld() {
        presenter.beginStartup();
        when(view.showNewGameDialog(anyMap())).thenReturn(null);

        presenter.onNewGame();

        assertSame(model, presenter.getModel());
        assertFalse(presenter.hasActiveGame());
    }

    @Test
    @DisplayName("with a game in progress New first asks for confirmation; No keeps playing")
    void inGameNew_confirmationCanBeRefused() {
        when(view.confirm(any(), any())).thenReturn(false);

        presenter.onNewGame();

        verify(view, never()).showNewGameDialog(anyMap());
        assertSame(model, presenter.getModel());
    }

    @Test
    @DisplayName("confirmed New opens the dialog; Start swaps the model")
    void inGameNew_confirmedStartsTheNewWorld() {
        when(view.confirm(any(), any())).thenReturn(true);
        when(view.showNewGameDialog(anyMap())).thenReturn(Map.of("seed", "42"));

        presenter.onNewGame();
        stopNewAudioController();

        assertEquals(42, presenter.getModel().getSeed());
        assertTrue(presenter.hasActiveGame());
    }

    @Test
    @DisplayName("console `new game;` opens the same dialog (D1)")
    void consoleCommand_opensTheDialog() {
        presenter.beginStartup();
        presenter.dismissCover();
        when(view.showNewGameDialog(anyMap())).thenReturn(Map.of("seed", "9"));
        model.setMode(Model.GameMode.COMMAND);
        model.setCommandText("new game;");

        presenter.onChar(enterKey());
        stopNewAudioController();

        verify(view).showNewGameDialog(anyMap());
        assertEquals(9, presenter.getModel().getSeed());
    }

    @Test
    @DisplayName("the dialog starts from the effective configuration values")
    void dialogDefaults_comeFromTheEffectiveConfiguration() {
        presenter.beginStartup();
        model.getEconomyManager().applyConfig(Map.of("startingBalance", "123"));
        when(view.showNewGameDialog(anyMap())).thenReturn(null);

        presenter.onNewGame();

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<Map<String, String>> captor =
                org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(view).showNewGameDialog(captor.capture());
        assertEquals("123.0", captor.getValue().get("startingBalance"));
    }

    @Test
    @DisplayName("load marks the game as started, so Save is enabled and the menu is contextual")
    void loadStartsTheGame() {
        presenter.beginStartup();
        assertFalse(presenter.hasActiveGame());

        presenter.startNewGame(Map.of("seed", "5"));
        stopNewAudioController();

        assertTrue(presenter.hasActiveGame());
    }

    @Test
    @DisplayName("the seed text resolves: number kept, random/blank drawn fresh")
    void seedResolution() {
        assertEquals(123, TerminalPresenter.resolveSeed("123"));
        assertEquals(123, TerminalPresenter.resolveSeed("  123 "));

        int random = TerminalPresenter.resolveSeed("random");
        assertTrue(random >= 1 && random <= letrain.settings.NewGameForm.MAX_SEED,
                "drawn seed: " + random);
        int blank = TerminalPresenter.resolveSeed(null);
        assertTrue(blank >= 1 && blank <= letrain.settings.NewGameForm.MAX_SEED);
    }
}
