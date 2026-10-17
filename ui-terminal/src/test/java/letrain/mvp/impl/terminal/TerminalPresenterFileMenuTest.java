package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import letrain.audio.AudioController;
import letrain.mvp.impl.Model;
import letrain.mvp.input.InputEvent;
import letrain.mvp.input.KeyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #743: Esc opens the File menu in the 2D terminal (owner decision D11). Quit lives inside
 * the menu, so Esc no longer ends the game directly.
 */
@DisplayName("2D File menu: Esc wiring")
class TerminalPresenterFileMenuTest {

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
    @DisplayName("Esc opens the File menu")
    void should_OpenTheFileMenuOnEscape() {
        presenter.onChar(new InputEvent(KeyType.Escape));

        verify(view).showFileMenu();
    }

    @Test
    @DisplayName("Esc inside the console still just leaves the console")
    void consoleEscape_leavesTheConsole() {
        model.setMode(Model.GameMode.DRIVE);
        presenter.onChar(charKey(':'));

        presenter.onChar(new InputEvent(KeyType.Escape));

        assertEquals(Model.GameMode.DRIVE, model.getMode());
        verify(view, never()).showFileMenu();
    }
}
