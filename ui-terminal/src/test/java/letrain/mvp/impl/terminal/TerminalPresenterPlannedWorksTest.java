package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.List;
import letrain.audio.AudioController;
import letrain.ground.GroundMap;
import letrain.map.Point;
import letrain.mvp.impl.Model;
import letrain.mvp.input.InputEvent;
import letrain.mvp.input.KeyType;
import letrain.planner.RoutePreviewSession;
import letrain.planner.RoutePolicy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("2D planned works: save-to-list and list actions")
class TerminalPresenterPlannedWorksTest {

    private Model model;
    private TerminalView view;
    private TerminalPresenter presenter;

    private record Harness(Model model, TerminalPresenter presenter, TerminalView view) {
    }

    private static InputEvent charKey(char c) {
        return new InputEvent(KeyType.Character, c, false, false, false);
    }

    private static InputEvent enter() {
        return new InputEvent(KeyType.Enter);
    }

    private static InputEvent escape() {
        return new InputEvent(KeyType.Escape);
    }

    private static InputEvent shiftC() {
        return new InputEvent(KeyType.Character, 'C', false, false, true);
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

    /** Opens the works list, chooses Add, plans 0,0 -> 4,0 and accepts the preview. */
    private void addStraightRouteThroughList() {
        when(view.showConstructionPlansDialog(anyList(), anyInt(), anyString())).thenReturn(
                new ConstructionPlansDialog.Result(ConstructionPlansDialog.Action.ADD, -1));
        model.setMode(Model.GameMode.RAILS);
        model.getCursor().setPosition(new Point(0, 0));
        presenter.onChar(charKey('b'));

        assertEquals(Model.GameMode.CONNECT, model.getMode());
        presenter.onChar(enter()); // A
        model.getCursor().setPosition(new Point(4, 0));
        presenter.onChar(enter()); // B; planning
        awaitPreview();
        presenter.onChar(enter()); // save to list, not build
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

    @Test
    @DisplayName("Add hides the normal menu, saves a draft and does not start construction")
    void add_savesDraft_withoutBuildingOrReopeningTheList() {
        addStraightRouteThroughList();

        assertEquals(1, presenter.getPlannedWorks().size());
        assertEquals(RoutePolicy.CHEAPEST, presenter.getPlannedWorks().works().get(0).policy());
        assertEquals(new Point(0, 0), presenter.getPlannedWorks().works().get(0).start());
        assertEquals(new Point(4, 0), presenter.getPlannedWorks().works().get(0).end());
        assertEquals(new Point(0, 0), model.getMark("Way-1-start"));
        assertEquals(new Point(4, 0), model.getMark("Way-1-end"));
        assertEquals("Way-24-end", TerminalPresenter.wayMarkName(24, "end"));
        assertFalse(presenter.isConstructionActive(), "Add must never start the queue");
        assertEquals(Model.GameMode.RAILS, model.getMode());
        assertFalse(presenter.isRoutePreviewSaveToList());

        verify(view).setMenuVisible(false);
        verify(view).setMenuVisible(true);
        verify(view, times(1)).showConstructionPlansDialog(anyList(), anyInt(), anyString());
        presenter.drawConstructionOverlay();
        verify(view, atLeastOnce())
                .drawRoutePreviewStatus(org.mockito.ArgumentMatchers.contains("added to works"));
    }

    @Test
    @DisplayName("Add can save a budget that overlaps existing track; it never starts the queue")
    void add_savesUnbuildablePlan_withoutBuilding() {
        model.addTrack(new Point(2, 0), new letrain.track.rail.RailTrack());

        addStraightRouteThroughList();

        assertEquals(1, presenter.getPlannedWorks().size());
        assertFalse(presenter.isConstructionActive(),
                "an Add budget must not be sent to the construction preflight");
    }

    @Test
    @DisplayName("cancelling Add leaves the list empty and restores the normal menu")
    void cancellingAdd_leavesListEmpty_andRestoresMenu() {
        when(view.showConstructionPlansDialog(anyList(), anyInt(), anyString())).thenReturn(
                new ConstructionPlansDialog.Result(ConstructionPlansDialog.Action.ADD, -1));
        model.setMode(Model.GameMode.RAILS);
        model.getCursor().setPosition(new Point(0, 0));
        presenter.onChar(charKey('b'));
        presenter.onChar(enter()); // A
        model.getCursor().setPosition(new Point(4, 0));
        presenter.onChar(enter()); // planning
        awaitPreview();
        presenter.onChar(escape());

        assertTrue(presenter.getPlannedWorks().isEmpty());
        assertFalse(presenter.isConstructionActive());
        assertEquals(Model.GameMode.RAILS, model.getMode());
        verify(view).setMenuVisible(false);
        verify(view).setMenuVisible(true);
        verify(view, times(1)).showConstructionPlansDialog(anyList(), anyInt(), anyString());
    }

    @Test
    @DisplayName("the Add planning mode uses its own status wording")
    void addPlanning_usesAddRouteStatus() {
        when(view.showConstructionPlansDialog(anyList(), anyInt(), anyString())).thenReturn(
                new ConstructionPlansDialog.Result(ConstructionPlansDialog.Action.ADD, -1));
        model.setMode(Model.GameMode.RAILS);
        model.getCursor().setPosition(new Point(0, 0));
        presenter.onChar(charKey('b'));

        presenter.drawRoutePreviewOverlay();

        org.mockito.ArgumentCaptor<String> status =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(view, atLeastOnce()).drawRoutePreviewStatus(status.capture());
        assertTrue(
                status.getAllValues().stream()
                        .anyMatch(text -> text.startsWith("Add route · PICK_START")),
                "the Add flow must not look like a normal Connect preview");
        presenter.onChar(escape());
    }

    @Test
    @DisplayName("reopening the list preserves the selected row and the exact total line")
    void reopening_preservesSelectionAndTotal() {
        addStraightRouteThroughList();
        when(view.showConstructionPlansDialog(anyList(), anyInt(), anyString())).thenReturn(
                new ConstructionPlansDialog.Result(ConstructionPlansDialog.Action.CLOSE, 0));

        presenter.onChar(charKey('b'));

        org.mockito.ArgumentCaptor<List<ConstructionPlansDialog.Row>> rows =
                org.mockito.ArgumentCaptor.forClass(List.class);
        org.mockito.ArgumentCaptor<Integer> index =
                org.mockito.ArgumentCaptor.forClass(Integer.class);
        org.mockito.ArgumentCaptor<String> total =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(view, times(2)).showConstructionPlansDialog(rows.capture(), index.capture(),
                total.capture());
        assertEquals(1, rows.getAllValues().get(1).size());
        assertEquals(0, index.getAllValues().get(1));
        assertTrue(total.getAllValues().get(1).startsWith("Total: 1 work"), "exact total line");
    }

    @Test
    @DisplayName("Delete of the last row carries -1 as the next selection")
    void deleteLastRow_preservesEmptySelection() {
        addStraightRouteThroughList();
        when(view.showConstructionPlansDialog(anyList(), anyInt(), anyString()))
                .thenReturn(new ConstructionPlansDialog.Result(
                        ConstructionPlansDialog.Action.DELETE, 0))
                .thenReturn(new ConstructionPlansDialog.Result(ConstructionPlansDialog.Action.CLOSE,
                        -1));

        presenter.onChar(charKey('b'));

        org.mockito.ArgumentCaptor<Integer> index =
                org.mockito.ArgumentCaptor.forClass(Integer.class);
        verify(view, times(3)).showConstructionPlansDialog(anyList(), index.capture(), anyString());
        assertEquals(-1, index.getAllValues().get(2));
        assertTrue(presenter.getPlannedWorks().isEmpty());
    }

    @Test
    @DisplayName("Shift+C also cancels the Add planning flow")
    void shiftC_cancelsAddPlanning() {
        when(view.showConstructionPlansDialog(anyList(), anyInt(), anyString())).thenReturn(
                new ConstructionPlansDialog.Result(ConstructionPlansDialog.Action.ADD, -1));
        model.setMode(Model.GameMode.RAILS);
        model.getCursor().setPosition(new Point(0, 0));
        presenter.onChar(charKey('b'));
        presenter.onChar(enter());
        model.getCursor().setPosition(new Point(4, 0));
        presenter.onChar(enter());
        awaitPreview();
        presenter.onChar(shiftC());

        assertTrue(presenter.getPlannedWorks().isEmpty());
        assertEquals(Model.GameMode.RAILS, model.getMode());
        verify(view).setMenuVisible(true);
    }

    @Test
    @DisplayName("Delete removes the selected draft and keeps the list loop open")
    void delete_removesSelectedWork() {
        addStraightRouteThroughList();
        when(view.showConstructionPlansDialog(anyList(), anyInt(), anyString()))
                .thenReturn(new ConstructionPlansDialog.Result(
                        ConstructionPlansDialog.Action.DELETE, 0))
                .thenReturn(new ConstructionPlansDialog.Result(ConstructionPlansDialog.Action.CLOSE,
                        -1));

        presenter.onChar(charKey('b'));

        assertTrue(presenter.getPlannedWorks().isEmpty());
        verify(view, times(3)).showConstructionPlansDialog(anyList(), anyInt(), anyString());
    }

    @Test
    @DisplayName("stored drafts are painted on the map and disappear when deleted")
    void storedDrafts_arePaintedUntilDeleted() {
        addStraightRouteThroughList();
        org.mockito.Mockito.clearInvocations(view);
        presenter.drawPlannedWorksOverlay();

        verify(view, atLeastOnce()).drawRoutePreviewCell(eq(0), eq(0), eq("A"), eq(true), any());
        verify(view, atLeastOnce()).drawRoutePreviewCell(eq(1), eq(0),
                eq(RoutePreviewOverlay.ROUTE_GLYPH), eq(false), any());
        verify(view, atLeastOnce()).drawRoutePreviewCell(eq(4), eq(0), eq("B"), eq(true), any());

        presenter.getPlannedWorks().remove(presenter.getPlannedWorks().works().get(0).id());
        org.mockito.Mockito.clearInvocations(view);
        presenter.drawPlannedWorksOverlay();

        verify(view, never()).drawRoutePreviewCell(anyInt(), anyInt(), anyString(), anyBoolean(),
                any());
    }

    @Test
    @DisplayName("Tab hides and restores the one-line Add status")
    void tab_togglesAddStatusLine() {
        when(view.showConstructionPlansDialog(anyList(), anyInt(), anyString())).thenReturn(
                new ConstructionPlansDialog.Result(ConstructionPlansDialog.Action.ADD, -1));
        model.setMode(Model.GameMode.RAILS);
        model.getCursor().setPosition(new Point(0, 0));
        presenter.onChar(charKey('b'));

        org.mockito.Mockito.clearInvocations(view);
        presenter.drawRoutePreviewOverlay();
        verify(view, atLeastOnce()).drawRoutePreviewStatus(anyString());

        org.mockito.Mockito.clearInvocations(view);
        presenter.onChar(new InputEvent(KeyType.Tab));
        presenter.drawRoutePreviewOverlay();
        verify(view, never()).drawRoutePreviewStatus(anyString());

        presenter.onChar(new InputEvent(KeyType.Tab));
        presenter.drawRoutePreviewOverlay();
        verify(view, atLeastOnce()).drawRoutePreviewStatus(anyString());
    }

    @Test
    @DisplayName("Edit opens the saved plan, allows changing only its policy and updates the row")
    void edit_opensExistingPlan_andUpdatesPolicy() {
        addStraightRouteThroughList();
        when(view.showConstructionPlansDialog(anyList(), anyInt(), anyString())).thenReturn(
                new ConstructionPlansDialog.Result(ConstructionPlansDialog.Action.EDIT, 0));

        presenter.onChar(charKey('b'));

        assertEquals(Model.GameMode.CONNECT, model.getMode());
        RoutePreviewSession session = presenter.getRoutePreviewSession();
        assertNotNull(session);
        assertEquals(RoutePreviewSession.State.PREVIEW, session.state(),
                "Edit must show the saved plan immediately, without picking A/B again");
        assertEquals(new Point(0, 0), session.start().orElseThrow());
        assertEquals(new Point(4, 0), session.end().orElseThrow());
        assertEquals(RoutePolicy.CHEAPEST, session.policy());

        presenter.onChar(charKey(']'));
        awaitPreview();
        assertEquals(RoutePolicy.DIRECT, session.policy());
        presenter.onChar(enter());

        assertEquals(Model.GameMode.RAILS, model.getMode());
        assertFalse(presenter.isConstructionActive(), "Edit must never build the route");
        assertEquals(1, presenter.getPlannedWorks().size());
        assertEquals(RoutePolicy.DIRECT, presenter.getPlannedWorks().works().get(0).policy());
        assertEquals(new Point(0, 0), presenter.getPlannedWorks().works().get(0).start());
        assertEquals(new Point(4, 0), presenter.getPlannedWorks().works().get(0).end());
        assertEquals(new Point(0, 0), model.getMark("Way-1-start"));
        assertEquals(new Point(4, 0), model.getMark("Way-1-end"));
        assertEquals(2, model.getMarks().size(),
                "Edit must not create or duplicate endpoint marks");
        verify(view, times(2)).showConstructionPlansDialog(anyList(), anyInt(), anyString());
    }

    @Test
    @DisplayName("cancelling Edit leaves the saved policy and plan unchanged")
    void cancellingEdit_leavesTheWorkUnchanged() {
        addStraightRouteThroughList();
        when(view.showConstructionPlansDialog(anyList(), anyInt(), anyString())).thenReturn(
                new ConstructionPlansDialog.Result(ConstructionPlansDialog.Action.EDIT, 0));

        presenter.onChar(charKey('b'));
        presenter.onChar(escape());

        assertEquals(Model.GameMode.RAILS, model.getMode());
        assertEquals(RoutePolicy.CHEAPEST, presenter.getPlannedWorks().works().get(0).policy());
        assertEquals(1, presenter.getPlannedWorks().size());
        assertFalse(presenter.isConstructionActive());
    }

    @Test
    @DisplayName("the works key also opens the list from the main menu")
    void worksKey_opensFromMenu() {
        model.setMode(Model.GameMode.MENU);
        when(view.showConstructionPlansDialog(anyList(), anyInt(), anyString())).thenReturn(
                new ConstructionPlansDialog.Result(ConstructionPlansDialog.Action.CLOSE, -1));

        presenter.onChar(charKey('b'));

        verify(view).showConstructionPlansDialog(anyList(), anyInt(), anyString());
    }

    @Test
    @DisplayName("b during an active construction shows a notice and does not open the list")
    void worksKey_duringConstruction_isBlockedWithNotice() {
        model.setMode(Model.GameMode.RAILS);
        model.getCursor().setPosition(new Point(0, 0));
        presenter.onChar(shiftC());
        presenter.onChar(enter());
        model.getCursor().setPosition(new Point(4, 0));
        presenter.onChar(enter());
        awaitPreview();
        presenter.onChar(enter());
        assertTrue(presenter.isConstructionActive());

        presenter.onChar(charKey('b'));

        verify(view).showMessage("Works", "Finish or cancel the active construction first");
        verify(view, never()).showConstructionPlansDialog(anyList(), anyInt(), anyString());
    }

    @Test
    @DisplayName("the normal Shift+C flow still builds immediately and keeps the list empty")
    void normalConnect_stillBuilds_andDoesNotAddToList() {
        model.setMode(Model.GameMode.RAILS);
        model.getCursor().setPosition(new Point(0, 0));
        presenter.onChar(new InputEvent(KeyType.Character, 'C', false, false, true));
        presenter.onChar(enter());
        model.getCursor().setPosition(new Point(4, 0));
        presenter.onChar(enter());
        awaitPreview();
        presenter.onChar(enter());

        assertTrue(presenter.getPlannedWorks().isEmpty());
        assertTrue(presenter.isConstructionActive());
        assertTrue(model.getMarks().keySet().stream().noneMatch(name -> name.startsWith("Way-")),
                "the immediate Shift+C flow must not create planned-work marks");
        verify(view, never()).showConstructionPlansDialog(anyList(), anyInt(), anyString());
    }
}
