package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

@DisplayName("2D marks menu: open, go and delete")
class TerminalPresenterMarksTest {

    private Model model;
    private TerminalView view;
    private TerminalPresenter presenter;

    private record Harness(Model model, TerminalPresenter presenter, TerminalView view) {
    }

    private static InputEvent charKey(char c) {
        return new InputEvent(KeyType.Character, c, false, false, false);
    }

    @BeforeEach
    void setUp() {
        Harness harness = harness();
        model = harness.model();
        presenter = harness.presenter();
        view = harness.view();
    }

    private static Harness harness() {
        Model model = new Model(1);
        model.updateGroundMap(new Point(-10, -10), 30, 30);
        TerminalView view = mock(TerminalView.class);
        when(view.getScrollOffset()).thenReturn(new Point(0, 0));
        when(view.getCols()).thenReturn(80);
        when(view.getRows()).thenReturn(25);
        when(view.isCameraPagination()).thenReturn(false);
        TerminalPresenter presenter = new TerminalPresenter(model, view);
        AudioController real = presenter.audioController;
        presenter.audioController = mock(AudioController.class);
        if (real != null) {
            real.stop();
        }
        return new Harness(model, presenter, view);
    }

    @Test
    @DisplayName("m opens the marks list and Go moves and centres the cursor")
    void go_movesCursor_andCloses() {
        model.setMark("base", new Point(10, 20));
        when(view.showMarksDialog(anyList(), anyInt()))
                .thenReturn(new MarksDialog.Result(MarksDialog.Action.GO, 0));

        presenter.onChar(charKey('m'));

        assertEquals(new Point(10, 20), model.getCursor().getPosition());
        verify(view).centerOn(10, 20);
        verify(view, times(1)).showMarksDialog(anyList(), anyInt());
    }

    @Test
    @DisplayName("Delete removes the selected mark and refreshes the list")
    void delete_removesMark_andRefreshes() {
        model.setMark("base", new Point(10, 20));
        model.setMark("Way-1-start", new Point(30, 40));
        when(view.showMarksDialog(anyList(), anyInt()))
                .thenReturn(new MarksDialog.Result(MarksDialog.Action.DELETE, 0))
                .thenReturn(new MarksDialog.Result(MarksDialog.Action.CLOSE, 0));

        presenter.onChar(charKey('m'));

        assertTrue(model.getMark("base") == null, "the first sorted mark must be removed");
        assertEquals(new Point(30, 40), model.getMark("Way-1-start"));
        verify(view, times(2)).showMarksDialog(anyList(), anyInt());
    }

    @Test
    @DisplayName("m in an editing mode with its own m binding does not open the marks menu")
    void m_inSemaphoresMode_doesNotOpenMarks() {
        model.setMode(Model.GameMode.SEMAPHORES);

        presenter.onChar(charKey('m'));

        verify(view, never()).showMarksDialog(anyList(), anyInt());
    }
}
