package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import letrain.audio.AudioController;
import letrain.construction.ConstructionState;
import letrain.ground.GroundMap;
import letrain.map.Point;
import letrain.mvp.impl.Model;
import letrain.mvp.input.InputEvent;
import letrain.mvp.input.KeyType;
import letrain.planner.RoutePreviewSession;
import letrain.vehicle.rail.impl.Locomotive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("2D construction queue: presenter wiring")
class TerminalPresenterConstructionTest {

    private Model model;
    private TerminalView view;
    private TerminalPresenter presenter;

    private record Harness(Model model, TerminalPresenter presenter, TerminalView view) {
    }

    private static InputEvent charKey(char c) {
        return new InputEvent(KeyType.Character, c, false, false, false);
    }

    private static InputEvent shiftC() {
        return new InputEvent(KeyType.Character, 'C', false, false, true);
    }

    private static InputEvent enter() {
        return new InputEvent(KeyType.Enter);
    }

    private static InputEvent escape() {
        return new InputEvent(KeyType.Escape);
    }

    private static InputEvent shiftArrowUp() {
        return new InputEvent(KeyType.ArrowUp, null, false, false, true);
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
        for (int x = 0; x <= 6; x++) {
            model.getGroundMap().setValueAt(x, 0, GroundMap.GROUND);
        }
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

    /** Drives CONNECT from A=(0,0) to B=(4,0) through the real asynchronous planner. */
    private void selectStraightRoute() {
        model.setMode(Model.GameMode.RAILS);
        model.getCursor().setPosition(new Point(0, 0));
        presenter.onChar(shiftC());
        presenter.onChar(enter());
        model.getCursor().setPosition(new Point(4, 0));
        presenter.onChar(enter());
        awaitPreview();
        presenter.onChar(enter());
    }

    private void awaitPreview() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            presenter.pollRoutePreview();
            RoutePreviewSession session = presenter.getRoutePreviewSession();
            assertNotNull(session, "preview session disappeared while planning");
            if (session.state() != RoutePreviewSession.State.PLANNING) {
                assertEquals(RoutePreviewSession.State.PREVIEW, session.state());
                return;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
        fail("route preview did not leave PLANNING before the timeout");
    }

    private int trackCount() {
        int[] count = {0};
        model.getRailMap().forEach(track -> count[0]++);
        return count[0];
    }

    private void tickUntilDone() {
        int guard = 0;
        while (presenter.isConstructionActive() && guard++ < 100) {
            presenter.tickConstruction();
        }
        assertFalse(presenter.isConstructionActive(), "construction did not finish");
    }

    @Test
    @DisplayName("selecting a route starts the queue, ticks build rails and the remaining route ghosts")
    void selection_startsQueue_andTickBuildsRealRails() {
        selectStraightRoute();

        assertTrue(presenter.isConstructionActive());
        assertEquals(0, trackCount(), "selection alone must not materialise cells");

        presenter.tickConstruction();

        assertEquals(1, presenter.getConstructionProgress().builtSteps());
        assertEquals(1, trackCount(), "one tick builds exactly one real rail cell");

        presenter.drawConstructionOverlay();
        ArgumentCaptor<String> status = ArgumentCaptor.forClass(String.class);
        verify(view, atLeastOnce()).drawRoutePreviewStatus(status.capture());
        assertTrue(
                status.getAllValues().stream()
                        .anyMatch(text -> text.startsWith("Building · 1/") && text.contains("$")
                                && text.endsWith("Esc cancel")),
                "status must report built/total, plan cost and the Esc hint");
        verify(view, atLeastOnce()).drawRoutePreviewCell(eq(1), eq(0),
                eq(RoutePreviewOverlay.ROUTE_GLYPH), eq(false), any());
        verify(view, never()).drawRoutePreviewCell(eq(0), eq(0),
                eq(RoutePreviewOverlay.ROUTE_GLYPH), eq(false), any());
    }

    @Test
    @DisplayName("first Esc pauses and arms; any other key resumes; second Esc cancels and keeps partial rail")
    void escapeConfirmation_pausesResumesAndCancels() {
        selectStraightRoute();
        presenter.tickConstruction();
        int builtBefore = presenter.getConstructionProgress().builtSteps();

        presenter.onChar(escape());
        assertTrue(presenter.isConstructionCancelArmed());
        presenter.drawConstructionOverlay();
        ArgumentCaptor<String> armedStatus = ArgumentCaptor.forClass(String.class);
        verify(view, atLeastOnce()).drawRoutePreviewStatus(armedStatus.capture());
        assertTrue(armedStatus.getAllValues().stream()
                .anyMatch(text -> text.equals(ConstructionStatus.CONFIRM_CANCEL)));
        presenter.tickConstruction();
        assertEquals(builtBefore, presenter.getConstructionProgress().builtSteps(),
                "the queue must be paused while the cancel confirmation is armed");
        verify(view, never()).showFileMenu();

        presenter.onChar(charKey('k'));
        assertFalse(presenter.isConstructionCancelArmed());
        presenter.tickConstruction();
        assertEquals(builtBefore + 1, presenter.getConstructionProgress().builtSteps(),
                "any other key resumes the queue");

        presenter.onChar(escape());
        assertTrue(presenter.isConstructionCancelArmed());
        presenter.onChar(escape());
        assertFalse(presenter.isConstructionActive());
        assertEquals(ConstructionState.CANCELLED, presenter.getConstructionProgress().state());
        int partial = trackCount();
        assertTrue(partial > 0, "the cells built before cancelling stay in the world");

        presenter.drawConstructionOverlay();
        ArgumentCaptor<String> cancelledStatus = ArgumentCaptor.forClass(String.class);
        verify(view, atLeastOnce()).drawRoutePreviewStatus(cancelledStatus.capture());
        assertTrue(cancelledStatus.getAllValues().stream()
                .anyMatch(text -> text.contains("partial track remains")));
        verify(view, never()).showFileMenu();

        presenter.onChar(escape());
        verify(view).showFileMenu();
    }

    @Test
    @DisplayName("Drive and mode switches stay available while construction runs")
    void modeChanges_remainAvailable_andConstructionKeepsTickingInDrive() {
        selectStraightRoute();
        Locomotive locomotive = new Locomotive(1, 'A');
        model.addLocomotive(locomotive);

        presenter.onChar(charKey('d'));

        assertEquals(Model.GameMode.DRIVE, model.getMode());
        int built = presenter.getConstructionProgress().builtSteps();
        presenter.tickConstruction();
        assertEquals(built + 1, presenter.getConstructionProgress().builtSteps(),
                "Drive must not pause the construction queue");

        presenter.onChar(charKey('r'));
        assertEquals(Model.GameMode.RAILS, model.getMode());
    }

    @Test
    @DisplayName("manual rail keys are ignored while construction is active")
    void manualEditing_isBlockedWhileActive() {
        selectStraightRoute();
        int before = trackCount();

        presenter.onChar(shiftArrowUp());
        presenter.onChar(charKey('w'));
        presenter.onChar(new InputEvent(KeyType.Insert));

        assertEquals(before, trackCount(),
                "build/erase/infrastructure keys must be swallowed by the edit lock");
    }

    @Test
    @DisplayName("console edit commands are rejected while construction is active")
    void consoleEditing_isBlockedWhileActive() {
        selectStraightRoute();
        int before = trackCount();

        presenter.onChar(charKey(':'));
        assertEquals(Model.GameMode.COMMAND, model.getMode());
        model.setCommandText("del 0,0");
        presenter.onChar(enter());

        assertEquals(Model.GameMode.COMMAND, model.getMode(),
                "a blocked command keeps the console open");
        assertEquals(ConstructionStatus.BLOCKED_COMMAND_MESSAGE, model.getCommandError());
        assertEquals(before, trackCount());

        model.setCommandText("go 1,1");
        presenter.onChar(enter());
        assertEquals(Model.GameMode.RAILS, model.getMode(),
                "a navigation command still works and returns to the previous mode");
    }

    @Test
    @DisplayName("completion unlocks editing, clears the HUD and Esc opens the File menu again")
    void completion_unlocksAndClearsHud() {
        selectStraightRoute();
        tickUntilDone();

        assertEquals(ConstructionState.DONE, presenter.getConstructionProgress().state());
        int total = presenter.getConstructionProgress().totalSteps();
        assertEquals(total, presenter.getConstructionProgress().builtSteps());
        assertEquals(total, trackCount(), "every planned cell is a real rail");
        assertFalse(presenter.getConstructionQueue().isEditingLocked());

        presenter.drawConstructionOverlay();
        ArgumentCaptor<String> status = ArgumentCaptor.forClass(String.class);
        verify(view, atLeastOnce()).drawRoutePreviewStatus(status.capture());
        assertTrue(status.getAllValues().stream()
                .anyMatch(text -> text.equals(ConstructionStatus.COMPLETE)));

        presenter.onChar(escape());
        verify(view).showFileMenu();
    }

    @Test
    @DisplayName("a rejected preflight never starts building and shows a clear notice")
    void rejection_doesNotStartOrBuild() {
        model.getRailMap().addTrack(new Point(2, 0), new letrain.track.rail.RailTrack());
        selectStraightRoute();

        assertFalse(presenter.isConstructionActive());
        assertEquals(ConstructionState.REJECTED, presenter.getConstructionProgress().state());
        assertEquals(1, trackCount(), "only the pre-existing rail remains");

        presenter.drawConstructionOverlay();
        ArgumentCaptor<String> status = ArgumentCaptor.forClass(String.class);
        verify(view, atLeastOnce()).drawRoutePreviewStatus(status.capture());
        assertTrue(status.getAllValues().stream().anyMatch(
                text -> text.startsWith("Construction rejected · ") && text.contains("occupied")));
    }
}
