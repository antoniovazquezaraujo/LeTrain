package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import letrain.audio.AudioController;
import letrain.map.Point;
import letrain.mvp.impl.Model;
import letrain.mvp.input.InputEvent;
import letrain.mvp.input.KeyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * D1 contextual channel in the 2D console: a typed command's short warning stays on the command
 * line (the console stays open), while a long warning opens the panel instead.
 */
@DisplayName("2D console: contextual notice channel")
class TerminalPresenterCommandNoticeTest {

    private static InputEvent charKey(char c) {
        return new InputEvent(KeyType.Character, c, false, false, false);
    }

    private static void console(TerminalPresenter presenter, String cmd) {
        presenter.onChar(charKey(':'));
        for (int i = 0; i < cmd.length(); i++) {
            presenter.onChar(charKey(cmd.charAt(i)));
        }
        presenter.onChar(new InputEvent(KeyType.Enter));
    }

    /**
     * A presenter over the mocked view, with the real audio controller stopped and replaced so the
     * test never opens a sound line.
     */
    private static TerminalPresenter presenter(Model model, TerminalView view) {
        TerminalPresenter presenter = new TerminalPresenter(model, view);
        AudioController realAudio = presenter.audioController;
        presenter.audioController = mock(AudioController.class);
        if (realAudio != null) {
            realAudio.stop();
        }
        return presenter;
    }

    private static Model model() {
        Model model = new Model(1);
        model.postLoadInit();
        model.updateGroundMap(new Point(-30, -30), 60, 60);
        return model;
    }

    @Test
    @DisplayName("a short typed warning stays on the command line and keeps the console open")
    void shortNotice_keepsConsoleOpen() {
        Model model = model();
        TerminalView view = mock(TerminalView.class);
        TerminalPresenter presenter = presenter(model, view);

        console(presenter, "train 99 set speed 3;");

        assertEquals("Train 99 not found; order ignored", model.getCommandNotice());
        assertEquals(Model.GameMode.COMMAND, model.getMode(),
                "the console must stay open so the notice is readable");
        verify(view, never()).showMessage(anyString(), anyString());
    }

    @Test
    @DisplayName("a long typed warning opens the panel and not the command line")
    void longNotice_opensPanel() {
        Model model = model();
        TerminalView view = mock(TerminalView.class);
        TerminalPresenter presenter = presenter(model, view);

        console(presenter, "time set 25:99;");

        assertTrue(model.getCommandNotice().isEmpty(),
                "a long notice must not duplicate on the line: " + model.getCommandNotice());
        verify(view).showMessage(eq("Command notice"), contains("Invalid time 25:99"));
        assertEquals(Model.GameMode.RAILS, model.getMode(),
                "without a line notice the console closes as usual");
    }
}
