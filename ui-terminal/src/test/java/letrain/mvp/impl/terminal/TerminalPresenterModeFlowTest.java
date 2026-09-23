package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import letrain.audio.AudioController;
import letrain.mvp.impl.Model;
import letrain.mvp.input.InputEvent;
import letrain.mvp.input.KeyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Menu/mode-flow regressions: the game must start with the full menu visible, finishing a train
 * must return to the editing mode, and closing the IDE must leave PROGRAM.
 */
@DisplayName("2D terminal: menu and mode transitions")
class TerminalPresenterModeFlowTest {

    private static InputEvent charKey(char c) {
        return new InputEvent(KeyType.Character, c, false, false, false);
    }

    private static TerminalPresenter presenterWith(Model model, TerminalView view) {
        TerminalPresenter presenter = new TerminalPresenter(model, view);
        AudioController realAudio = presenter.audioController;
        presenter.audioController = mock(AudioController.class);
        if (realAudio != null) {
            realAudio.stop();
        }
        return presenter;
    }

    @Test
    @DisplayName("starts with the full menu/help level so the controls are visible")
    void starts_withFullHelp() {
        Model model = new Model(1);
        TerminalView view = mock(TerminalView.class);

        presenterWith(model, view);

        assertEquals(2, model.getHelpLevel(), "the model must start at the full help level");
        verify(view).setHelpLevel(2);
    }

    @Test
    @DisplayName("finishing a train (Enter in TRAINS) returns to RAILS, not the empty menu")
    void enterInTrains_returnsToRails() {
        Model model = new Model(1);
        TerminalPresenter presenter = presenterWith(model, mock(TerminalView.class));
        model.setMode(Model.GameMode.TRAINS);

        presenter.onChar(new InputEvent(KeyType.Enter));

        assertEquals(Model.GameMode.RAILS, model.getMode());
    }

    @Test
    @DisplayName("Enter in a non-train mode still opens the main menu (MENU)")
    void enterInOtherMode_opensMenu() {
        Model model = new Model(1);
        TerminalPresenter presenter = presenterWith(model, mock(TerminalView.class));
        model.setMode(Model.GameMode.STATIONS);

        presenter.onChar(new InputEvent(KeyType.Enter));

        assertEquals(Model.GameMode.MENU, model.getMode());
    }

    @Test
    @DisplayName("closing the IDE leaves PROGRAM and returns to RAILS")
    void programExit_returnsToRails() {
        Model model = new Model(1);
        TerminalView view = mock(TerminalView.class);
        TerminalPresenter presenter = presenterWith(model, view);
        model.setMode(Model.GameMode.RAILS);

        presenter.onChar(charKey('p'));

        verify(view).showIDE();
        assertEquals(Model.GameMode.RAILS, model.getMode(),
                "after the IDE closes the highlighted PROGRAM option must be cleared");
    }

    @Test
    @DisplayName("leaving the console with Esc returns to the mode the player was in")
    void consoleExit_returnsToPreviousMode() {
        Model model = new Model(1);
        TerminalPresenter presenter = presenterWith(model, mock(TerminalView.class));
        model.setMode(Model.GameMode.DRIVE);

        presenter.onChar(charKey(':'));
        assertEquals(Model.GameMode.COMMAND, model.getMode(), "':' opens the console");

        presenter.onChar(new InputEvent(KeyType.Escape));

        assertEquals(Model.GameMode.DRIVE, model.getMode());
        assertEquals("", model.getCommandText());
    }

    @Test
    @DisplayName("running a command from the console also returns to the previous mode")
    void consoleCommand_returnsToPreviousMode() {
        Model model = new Model(1);
        TerminalPresenter presenter = presenterWith(model, mock(TerminalView.class));
        model.setMode(Model.GameMode.TRAINS);

        presenter.onChar(charKey(':'));
        for (char c : "help".toCharArray()) {
            presenter.onChar(charKey(c));
        }
        presenter.onChar(new InputEvent(KeyType.Enter));

        assertEquals(Model.GameMode.TRAINS, model.getMode());
    }

    @Test
    @DisplayName("leaving the console from RAILS still lands in RAILS")
    void consoleExit_fromRails_staysInRails() {
        Model model = new Model(1);
        TerminalPresenter presenter = presenterWith(model, mock(TerminalView.class));
        model.setMode(Model.GameMode.RAILS);

        presenter.onChar(charKey(':'));
        presenter.onChar(new InputEvent(KeyType.Escape));

        assertEquals(Model.GameMode.RAILS, model.getMode());
    }
}
