package letrain.mvp.impl.graphic;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.badlogic.gdx.Input;
import letrain.map.Point;
import letrain.mvp.Presenter;
import letrain.mvp.View;
import letrain.mvp.impl.Model;
import letrain.mvp.impl.RailTrackMaker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #743: the 3D title cover consumes the key that dismisses it (and its typed character), and
 * Esc opens the File menu once the game is running.
 */
@DisplayName("3D title cover: input handling")
class Gdx3DInputHandlerCoverTest {

    private Model model;
    private GraphicPresenter view;
    private Gdx3DInputHandler handler;
    private com.badlogic.gdx.Input previousInput;

    @BeforeEach
    void setUp() {
        model = new Model(1);
        model.updateGroundMap(new Point(-30, -30), 60, 60);
        model.setMode(Model.GameMode.RAILS);

        view = mock(GraphicPresenter.class);
        when(view.getModel()).thenReturn(model);
        when(view.getCommandHistory()).thenReturn(new CommandHistory());

        Presenter makerPresenter = mock(Presenter.class);
        when(makerPresenter.getModel()).thenReturn(model);
        when(makerPresenter.getView()).thenReturn(mock(View.class));
        when(makerPresenter.getAudioController()).thenReturn(null);
        when(makerPresenter.getUndoRedoHistory()).thenReturn(null);
        RailTrackMaker trackMaker = new RailTrackMaker(makerPresenter);

        handler = new Gdx3DInputHandler(model, view, new CameraController(model), trackMaker,
                mock(letrain.audio.AudioController.class));

        previousInput = com.badlogic.gdx.Gdx.input;
        com.badlogic.gdx.Gdx.input = mock(com.badlogic.gdx.Input.class);
    }

    @AfterEach
    void restoreGdxInput() {
        com.badlogic.gdx.Gdx.input = previousInput;
    }

    /** The real presenter delegates onChar to the handler; the mock bridges to it. */
    private void installTypedKeyBridge() {
        doAnswer(invocation -> {
            handler.onChar(invocation.getArgument(0));
            return null;
        }).when(view).onChar(any());
    }

    @Test
    @DisplayName("the key that dismisses the cover is consumed, typed character included")
    void coverKey_isConsumed() {
        when(view.isCoverActive()).thenReturn(true);

        assertTrue(handler.keyDown(Input.Keys.T));
        verify(view).dismissCover();
        assertTrue(handler.keyTyped('t'), "the typed char of the dismissing key must be swallowed");
        verify(view, never()).onChar(any());
    }

    @Test
    @DisplayName("a non-typing key does not swallow the next typed character")
    void nonTypingCoverKey_doesNotSwallowTheNextTypedChar() {
        when(view.isCoverActive()).thenReturn(true);
        handler.keyDown(Input.Keys.ESCAPE);
        handler.keyUp(Input.Keys.ESCAPE);

        handler.keyTyped(':');

        verify(view).onChar(any());
    }

    @Test
    @DisplayName("Esc opens the File menu once the cover is gone")
    void escape_opensTheFileMenu() {
        installTypedKeyBridge();

        handler.keyDown(Input.Keys.ESCAPE);

        verify(view).showFileMenu();
    }
}
