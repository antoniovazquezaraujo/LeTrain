package letrain.mvp.impl.terminal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
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

@DisplayName("2D overlay: vim-style pager keys")
class TerminalOverlayKeysTest {

    private TerminalPresenter presenter;
    private TerminalView view;

    private static InputEvent key(char c, boolean ctrl) {
        return new InputEvent(KeyType.Character, c, ctrl, false, false);
    }

    @BeforeEach
    void setUp() {
        Model model = new Model(1);
        view = mock(TerminalView.class);
        when(view.getScrollOffset()).thenReturn(new Point(0, 0));
        when(view.getCols()).thenReturn(80);
        when(view.getRows()).thenReturn(24);
        when(view.isShowingOverlay()).thenReturn(true);
        presenter = new TerminalPresenter(model, view);
        AudioController real = presenter.audioController;
        presenter.audioController = mock(AudioController.class);
        if (real != null) {
            real.stop();
        }
    }

    @Test
    @DisplayName("j / k scroll one line")
    void scrollKeys() {
        presenter.onChar(key('j', false));
        verify(view).scrollOverlay(1);
        presenter.onChar(key('k', false));
        verify(view).scrollOverlay(-1);
    }

    @Test
    @DisplayName("PageUp / PageDown and Ctrl+P / Ctrl+N scroll a page")
    void pageKeys() {
        presenter.onChar(new InputEvent(KeyType.PageDown));
        presenter.onChar(key('n', true));
        verify(view, times(2)).scrollOverlayPage(1);

        presenter.onChar(new InputEvent(KeyType.PageUp));
        presenter.onChar(key('p', true));
        verify(view, times(2)).scrollOverlayPage(-1);
    }

    @Test
    @DisplayName("h / left grow the panel width, l / right shrink it")
    void widthKeys() {
        presenter.onChar(key('h', false));
        verify(view).resizeOverlay(4);
        presenter.onChar(key('l', false));
        verify(view).resizeOverlay(-4);
    }

    @Test
    @DisplayName("F maximizes and Esc closes")
    void miscKeys() {
        presenter.onChar(key('f', false));
        verify(view).toggleOverlayMaximize();
        presenter.onChar(new InputEvent(KeyType.Escape));
        verify(view).clearOverlay();
    }
}
