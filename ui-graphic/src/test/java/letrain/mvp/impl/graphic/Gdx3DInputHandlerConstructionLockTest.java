package letrain.mvp.impl.graphic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import letrain.map.Point;
import letrain.mvp.impl.Model;
import letrain.mvp.impl.RailTrackMaker;
import letrain.mvp.input.InputEvent;
import letrain.mvp.input.KeyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Manual-edit lock while the shared construction service is laying a planned work. */
@DisplayName("3D input: construction edit lock")
class Gdx3DInputHandlerConstructionLockTest {

    private Model model;
    private GraphicPresenter view;
    private RailTrackMaker trackMaker;
    private Gdx3DInputHandler handler;

    @BeforeEach
    void setUp() {
        model = new Model();
        model.updateGroundMap(new Point(-30, -30), 60, 60);
        model.setMode(letrain.mvp.Model.GameMode.RAILS);

        view = mock(GraphicPresenter.class);
        when(view.getModel()).thenReturn(model);
        when(view.isConstructionActive()).thenReturn(true);

        trackMaker = mock(RailTrackMaker.class);
        handler = new Gdx3DInputHandler(model, view, new CameraController(model), trackMaker, null);
    }

    private static InputEvent charKey(char c, boolean shift) {
        return new InputEvent(KeyType.Character, c, false, false, shift);
    }

    @Test
    @DisplayName("build/erase keys never reach the track maker while a work runs")
    void should_BlockTrackEditing() {
        handler.onChar(charKey('H', true));
        handler.onChar(new InputEvent(KeyType.ArrowUp, null, true, false, false));
        handler.onChar(charKey('w', false));

        verifyNoInteractions(trackMaker);
    }

    @Test
    @DisplayName("plain movement and quantifiers still work while a work runs")
    void should_AllowNavigationOnly() {
        handler.onChar(new InputEvent(KeyType.ArrowUp));
        handler.onChar(charKey('j', false));
        handler.onChar(charKey('7', false));
        handler.onChar(charKey(' ', false));

        verify(trackMaker, org.mockito.Mockito.times(4)).onChar(any());
    }

    @Test
    @DisplayName("console, repeat and program/experiment keys are blocked")
    void should_BlockEditShortcuts() {
        handler.onChar(charKey(':', false));
        handler.onChar(charKey('.', false));
        handler.onChar(charKey('p', false));
        handler.onChar(charKey('X', true));
        handler.onChar(charKey('R', true));

        assertEquals(letrain.mvp.Model.GameMode.RAILS, model.getMode());
        assertFalse(model.isPauseEditing());
        verify(view, never()).getCommandHistory();
        verify(view, never()).toggleExperimentMode();
        verify(view, never()).getExperimentSession();
    }

    @Test
    @DisplayName("Planning and mode changes remain available")
    void should_AllowPlanningAndModeChanges() {
        handler.onChar(charKey('b', false));
        assertEquals(letrain.mvp.Model.GameMode.PLANNED_WORKS, model.getMode());
        verify(view).showPlanningWindow();

        model.setMode(letrain.mvp.Model.GameMode.RAILS);
        handler.onChar(charKey('a', false));
        assertEquals(letrain.mvp.Model.GameMode.ADD, model.getMode());
    }
}
