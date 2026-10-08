package letrain.mvp.impl.terminal;

import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import letrain.mvp.impl.Model;
import letrain.mvp.input.InputEvent;
import letrain.mvp.input.KeyType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

/** Camera dead-zone startup and 'z' cycle of the 2D presenter (issue #697). */
@DisplayName("2D camera dead zone: startup and 'z' cycle")
class TerminalPresenterCameraDeadzoneTest {

    private static InputEvent charKey(char c) {
        return new InputEvent(KeyType.Character, c, false, false, false);
    }

    /** A presenter with the view and audio mocked out, so tests never draw or make sound. */
    private static TerminalPresenter presenterWith(TerminalView view) {
        TerminalPresenter presenter = new TerminalPresenter(new Model(1), view);
        presenter.audioController.stop();
        return presenter;
    }

    @Test
    @DisplayName("the view starts at the full-screen dead zone")
    void should_StartWithFullScreenDeadzone() {
        TerminalView view = mock(TerminalView.class);
        when(view.getRows()).thenReturn(60);

        presenterWith(view);

        verify(view).setCameraDeadzone(TerminalView.FULL_SCREEN_DEADZONE);
    }

    @Test
    @DisplayName("'z' cycles 999 -> 1 -> 3 -> 6 ... from the full-screen startup")
    void should_CycleFromFullScreen_toTheSmallestStep() {
        TerminalView view = mock(TerminalView.class);
        when(view.getRows()).thenReturn(60);
        TerminalPresenter presenter = presenterWith(view);

        InOrder ordered = inOrder(view);
        presenter.onChar(charKey('z'));
        presenter.onChar(charKey('z'));
        presenter.onChar(charKey('z'));

        ordered.verify(view).setCameraDeadzone(1);
        ordered.verify(view).setCameraDeadzone(3);
        ordered.verify(view).setCameraDeadzone(6);
        verify(view, atLeastOnce()).flashCameraDeadzone();
    }

    @Test
    @DisplayName("on a short map 'z' skips the steps that would not change the framing")
    void should_SkipSteps_thatDoNotFitTheMap() {
        TerminalView view = mock(TerminalView.class);
        when(view.getRows()).thenReturn(20);
        TerminalPresenter presenter = presenterWith(view);

        // maxRadius = 9: 1, 3 and 6 fit, 10+ are skipped and the cycle jumps straight to 999.
        InOrder ordered = inOrder(view);
        presenter.onChar(charKey('z'));
        presenter.onChar(charKey('z'));
        presenter.onChar(charKey('z'));
        presenter.onChar(charKey('z'));

        ordered.verify(view).setCameraDeadzone(1);
        ordered.verify(view).setCameraDeadzone(3);
        ordered.verify(view).setCameraDeadzone(6);
        ordered.verify(view).setCameraDeadzone(TerminalView.FULL_SCREEN_DEADZONE);
    }
}
