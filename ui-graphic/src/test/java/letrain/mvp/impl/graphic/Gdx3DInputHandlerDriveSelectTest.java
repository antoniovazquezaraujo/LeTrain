package letrain.mvp.impl.graphic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import letrain.mvp.Presenter;
import letrain.mvp.View;
import letrain.mvp.impl.Model;
import letrain.mvp.impl.RailTrackMaker;
import letrain.mvp.input.InputEvent;
import letrain.mvp.input.KeyType;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #700: entering Drive with no locomotive selected auto-selects the first available one, so
 * the HUD data and the camera follow start working immediately.
 */
@DisplayName("3D: Drive mode auto-selection")
class Gdx3DInputHandlerDriveSelectTest {

    private Model model;
    private Gdx3DInputHandler handler;

    @BeforeEach
    void setUp() {
        model = new Model();
        model.setMode(Model.GameMode.RAILS);

        GraphicPresenter view = mock(GraphicPresenter.class);
        when(view.getModel()).thenReturn(model);

        Presenter makerPresenter = mock(Presenter.class);
        when(makerPresenter.getModel()).thenReturn(model);
        when(makerPresenter.getView()).thenReturn(mock(View.class));
        when(makerPresenter.getAudioController()).thenReturn(null);
        when(makerPresenter.getUndoRedoHistory()).thenReturn(null);
        RailTrackMaker trackMaker = new RailTrackMaker(makerPresenter);

        handler = new Gdx3DInputHandler(model, view, new CameraController(model), trackMaker, null);
    }

    private static InputEvent charKey(char c) {
        return new InputEvent(KeyType.Character, c, false, false, false);
    }

    private static Locomotive addDrivableLocomotive(Model model, int id, char aspect) {
        Train train = new Train(model.nextTrainId());
        Locomotive locomotive = new Locomotive(id, aspect);
        train.pushBack(locomotive);
        train.setDirectorLinker(locomotive);
        train.rebind();
        model.addLocomotive(locomotive);
        return locomotive;
    }

    @Test
    @DisplayName("'d' with nothing selected lands in DRIVE on the first locomotive")
    void driveKey_selectsFirstLocomotive() {
        Locomotive first = addDrivableLocomotive(model, 1, 'A');
        addDrivableLocomotive(model, 2, 'B');

        handler.onChar(charKey('d'));

        assertEquals(Model.GameMode.DRIVE, model.getMode());
        assertEquals(first, model.getSelectedLocomotive(),
                "Drive must land on the first locomotive of the list");
        assertEquals(0, model.getSelectedLocomotiveIndex());
    }

    @Test
    @DisplayName("'d' keeps the locomotive the player already had selected")
    void driveKey_keepsExistingSelection() {
        addDrivableLocomotive(model, 1, 'A');
        Locomotive second = addDrivableLocomotive(model, 2, 'B');
        model.selectLocomotive(2);

        handler.onChar(charKey('d'));

        assertEquals(Model.GameMode.DRIVE, model.getMode());
        assertEquals(second, model.getSelectedLocomotive());
    }
}
