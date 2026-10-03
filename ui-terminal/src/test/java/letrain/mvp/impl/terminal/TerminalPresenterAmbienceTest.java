package letrain.mvp.impl.terminal;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import letrain.audio.AudioController;
import letrain.mvp.impl.Model;
import letrain.game.audio.SoundscapeAmbience;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("2D terminal: soundscape glue wiring")
class TerminalPresenterAmbienceTest {

    @Test
    @DisplayName("with the glue wired, the zone focus is pushed and the legacy ambient is skipped")
    void should_PushGlue_AndSkipLegacyAmbient() {
        TerminalView view = mock(TerminalView.class);
        when(view.getCols()).thenReturn(80);
        when(view.getRows()).thenReturn(25);
        Model model = new Model(1);
        SoundscapeAmbience ambience = mock(SoundscapeAmbience.class);
        TerminalPresenter presenter = new TerminalPresenter(model, view, ambience);
        AudioController audio = mock(AudioController.class);
        presenter.audioController = audio;

        presenter.updateAmbientAudio();

        verify(ambience).update(eq(model), anyFloat());
        verify(audio, never()).updateAmbient(anyBoolean(), anyFloat(), anyFloat(), anyFloat(),
                anyFloat());
        verify(audio).update();
    }

    @Test
    @DisplayName("the console over DRIVE keeps the listener on the locomotive, not the cursor")
    void should_KeepListenerOnLocomotive_When_ConsoleOpen() {
        TerminalView view = mock(TerminalView.class);
        when(view.getCols()).thenReturn(80);
        when(view.getRows()).thenReturn(25);
        Model model = new Model(1);
        letrain.vehicle.rail.impl.Locomotive loco =
                new letrain.vehicle.rail.impl.Locomotive(1, "A");
        loco.setPosition(new letrain.map.Point(33, 44));
        model.addLocomotive(loco);
        model.setSelectedLocomotive(loco);
        model.getCursor().setPosition(new letrain.map.Point(3, 4));
        model.setMode(Model.GameMode.DRIVE);
        model.setMode(Model.GameMode.COMMAND);

        TerminalPresenter presenter = new TerminalPresenter(model, view);
        AudioController audio = mock(AudioController.class);
        presenter.audioController = audio;

        presenter.updateAmbientAudio();

        verify(audio).setListenerPosition(33f, 44f, 0f, 0);
    }

    @Test
    @DisplayName("without the glue, the legacy ambient keeps playing")
    void should_KeepLegacyAmbient_WithoutGlue() {
        TerminalView view = mock(TerminalView.class);
        when(view.getCols()).thenReturn(80);
        when(view.getRows()).thenReturn(25);
        Model model = new Model(1);
        TerminalPresenter presenter = new TerminalPresenter(model, view);
        AudioController audio = mock(AudioController.class);
        presenter.audioController = audio;

        presenter.updateAmbientAudio();

        verify(audio).updateAmbient(anyBoolean(), anyFloat(), anyFloat(), anyFloat(), anyFloat());
    }
}
