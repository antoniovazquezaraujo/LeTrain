package letrain.mvp.impl.terminal;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import letrain.audio.AudioController;
import letrain.mvp.impl.Model;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

/**
 * Exiting must always hand the terminal back to Lanterna. Audio teardown runs after it and must
 * never be able to skip it, or the shell is left in raw mode (no echo).
 */
@DisabledIfEnvironmentVariable(named = "CI", matches = "true")
@DisplayName("2D terminal: stop() always restores the view/terminal")
class TerminalPresenterStopTest {

    @Test
    @DisplayName("view.stop() runs even when audio stop throws")
    void stop_restoresViewEvenIfAudioStopThrows() {
        TerminalPresenter presenter = new TerminalPresenter(new Model(1));
        TerminalView view = mock(TerminalView.class);
        presenter.view = view;
        AudioController audio = mock(AudioController.class);
        doThrow(new RuntimeException("audio boom")).when(audio).stop();
        presenter.audioController = audio;

        presenter.stop(); // must not propagate the audio failure

        verify(view).stop();
        verify(audio).stop();
    }
}
