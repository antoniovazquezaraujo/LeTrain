package letrain.mvp.impl.terminal;

import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayDeque;
import java.util.Deque;
import letrain.audio.AudioController;
import letrain.map.Point;
import letrain.mvp.impl.Model;
import letrain.vehicle.rail.Linker;
import letrain.vehicle.rail.impl.Train;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

/**
 * Importing an exported (linked) train must not play the coupling "link" sound: the replayed command
 * represents a past user action, not a live one. Same for undo/redo replays.
 */
@DisabledIfEnvironmentVariable(named = "CI", matches = "true")
@DisplayName("2D terminal: train-event sounds are muted during programmatic replay")
class TerminalPresenterEventSoundTest {

    private static Train trainWithLinkerAt(int x, int y) {
        Train train = mock(Train.class);
        Linker linker = mock(Linker.class);
        when(linker.getPosition()).thenReturn(new Point(x, y));
        Deque<Linker> linkers = new ArrayDeque<>();
        linkers.add(linker);
        when(train.getLinkers()).thenReturn(linkers);
        return train;
    }

    @Test
    @DisplayName("onLink plays the link sound, but stays silent inside runWithoutEventSounds")
    void linkSound_isMutedDuringReplay() {
        TerminalPresenter presenter = new TerminalPresenter(new Model(1));
        AudioController audio = mock(AudioController.class);
        presenter.audioController = audio;
        Train train = trainWithLinkerAt(3, 4);

        presenter.onLink(train);
        verify(audio).playOneShot(eq("link"), anyFloat(), anyFloat());

        reset(audio);
        presenter.runWithoutEventSounds(() -> presenter.onLink(train));
        verify(audio, never()).playOneShot(eq("link"), anyFloat(), anyFloat());

        // The suppression is scoped: it must not leak past the replay.
        presenter.onLink(train);
        verify(audio).playOneShot(eq("link"), anyFloat(), anyFloat());
    }
}
