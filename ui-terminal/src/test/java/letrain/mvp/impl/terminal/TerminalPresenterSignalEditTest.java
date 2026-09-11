package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import letrain.map.Dir;
import letrain.map.Point;
import letrain.mvp.impl.Model;
import letrain.mvp.input.InputEvent;
import letrain.mvp.input.KeyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

/**
 * Keyboard signal toggles (invert/mode/limit) must be recorded into the journal exactly like console
 * commands, so they show up in the exported scenario and can be undone.
 */
@DisabledIfEnvironmentVariable(named = "CI", matches = "true")
@DisplayName("2D terminal: keyboard signal toggles are recorded")
class TerminalPresenterSignalEditTest {

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

    @Test
    @DisplayName("signal invert/mode/limit from the keyboard are journaled as canonical commands")
    void keyboardSignalToggles_areJournaled() {
        Model model = new Model();
        model.updateGroundMap(new Point(-30, -30), 60, 60);
        model.getCursor().setPosition(new Point(7, 0));
        model.getCursor().setDir(Dir.E);
        model.setMode(Model.GameMode.RAILS);

        TerminalPresenter presenter = new TerminalPresenter(model);
        presenter.view = mock(TerminalView.class);

        // Build a tile and a speed signal through the console (default limit 3, mode max, facing E).
        console(presenter, "go 7,0; face e; write 1;");
        console(presenter, "go 7,0; face e; new sg;");
        assertEquals("", model.getCommandError());
        assertEquals(1, model.getSpeedSignals().size());

        // Enter Record/edit mode with 'R': recording starts and undo begins.
        presenter.onChar(charKey('R'));
        assertEquals(0, model.getCommandJournal().size());

        letrain.track.SpeedSignal sig = model.getSpeedSignals().get(0);
        model.selectSpeedSignal(sig.getId());
        model.setMode(Model.GameMode.SPEED_SIGNALS);
        model.getCursor().setPosition(new Point(7, 0));
        model.getCursor().setDir(Dir.E);

        presenter.onChar(charKey(' ')); // invert
        presenter.onChar(charKey('m')); // mode max -> min
        presenter.onChar(charKey('7')); // limit -> 7
        presenter.onChar(charKey('4')); // limit -> 4 (collapses into the previous one)

        assertEquals(3, model.getCommandJournal().size(),
                "consecutive limit tweaks must collapse into a single command");
        assertEquals("go 7,0; face e; signal " + sig.getId() + " invert;",
                model.getCommandJournal().entries().get(0));
        assertEquals("go 7,0; face e; signal " + sig.getId() + " set mode min;",
                model.getCommandJournal().entries().get(1));
        assertEquals("go 7,0; face e; signal " + sig.getId() + " set limit 4;",
                model.getCommandJournal().entries().get(2));
    }
}
