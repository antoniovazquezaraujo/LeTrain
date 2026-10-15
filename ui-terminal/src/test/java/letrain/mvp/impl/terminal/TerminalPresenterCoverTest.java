package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import letrain.audio.AudioController;
import letrain.map.Point;
import letrain.mvp.impl.Model;
import letrain.mvp.input.InputEvent;
import letrain.mvp.input.KeyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #743: the 2D title cover. Any key continues; the dismissing keystroke is consumed and never
 * reaches the game.
 */
@DisplayName("2D title cover")
class TerminalPresenterCoverTest {

    private Model model;
    private TerminalView view;
    private TerminalPresenter presenter;

    private static InputEvent charKey(char c) {
        return new InputEvent(KeyType.Character, c, false, false, false);
    }

    @BeforeEach
    void setUp() {
        model = new Model(1);
        model.setMode(Model.GameMode.RAILS);
        view = mock(TerminalView.class);
        presenter = new TerminalPresenter(model, view);
        AudioController real = presenter.audioController;
        presenter.audioController = mock(AudioController.class);
        if (real != null) {
            real.stop();
        }
    }

    @Test
    @DisplayName("showing the cover flags the view and waits for any key")
    void should_ShowTheCover() {
        presenter.showCover();

        assertTrue(presenter.isCoverActive());
        verify(view).setCoverVisible(true);
    }

    @Test
    @DisplayName("any key dismisses the cover and is consumed (never an action)")
    void should_DismissWithAnyKey_andConsumeIt() {
        presenter.showCover();

        presenter.onChar(charKey(':')); // would open the console if it leaked

        assertFalse(presenter.isCoverActive());
        assertEquals(Model.GameMode.RAILS, model.getMode(),
                "':' must not open the console from the cover");
        assertEquals("", model.getCommandText());
        verify(view).setCoverVisible(false);
    }

    @Test
    @DisplayName("the next key after the cover works normally")
    void should_ActNormallyAfterTheCover() {
        presenter.showCover();
        presenter.onChar(charKey(':'));

        presenter.onChar(charKey(':'));

        assertEquals(Model.GameMode.COMMAND, model.getMode());
    }

    @Test
    @DisplayName("Esc on the cover dismisses it instead of opening the File menu")
    void escOnCover_doesNotOpenTheMenu() {
        presenter.showCover();

        presenter.onChar(new InputEvent(KeyType.Escape));

        assertFalse(presenter.isCoverActive());
        verify(view, never()).showFileMenu();
    }

    @Test
    @DisplayName("dismissing the cover always hands the screen back to the game")
    void dismissCover_hidesTheCover() {
        presenter.showCover();

        presenter.dismissCover();

        assertFalse(presenter.isCoverActive());
        verify(view).setCoverVisible(false);
    }

    /** Stubs a view whose very first key read ends the game, so {@code start()} returns at once. */
    private void stubImmediateExit() {
        when(view.readKey()).thenReturn(null);
        when(view.isEndOfGame(any())).thenReturn(true);
        when(view.getScrollOffset()).thenReturn(new Point(0, 0));
    }

    @Test
    @DisplayName("the default boot shows the cover before the world")
    void coverEnabled_showsTheCover() {
        stubImmediateExit();

        presenter.start();

        verify(view).setCoverVisible(true);
    }

    @Test
    @DisplayName("a headless or direct-scenario run (cover disabled) never paints the cover")
    void coverDisabled_neverShowsTheCover() {
        presenter.setCoverEnabled(false);
        stubImmediateExit();

        presenter.start();

        verify(view, never()).setCoverVisible(true);
        assertFalse(presenter.isCoverActive(), "the world must start without waiting on the cover");
    }
}
