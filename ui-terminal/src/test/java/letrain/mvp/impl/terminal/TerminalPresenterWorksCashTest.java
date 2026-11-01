package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.util.List;
import letrain.audio.AudioController;
import letrain.construction.ConstructionCompanyCatalog;
import letrain.construction.PlannedWork;
import letrain.construction.WorkQuote;
import letrain.ground.GroundMap;
import letrain.map.Point;
import letrain.mvp.impl.Model;
import letrain.mvp.input.InputEvent;
import letrain.mvp.input.KeyType;
import letrain.planner.RoutePlan;
import letrain.planner.RoutePolicy;
import letrain.planner.RouteStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Automatic works: prepaid quotes, Run and cancellation refund")
class TerminalPresenterWorksCashTest {

    private Model model;
    private TerminalView view;
    private TerminalPresenter presenter;

    private record Harness(Model model, TerminalPresenter presenter, TerminalView view) {
    }

    private static InputEvent charKey(char c) {
        return new InputEvent(KeyType.Character, c, false, false, false);
    }

    private static InputEvent escape() {
        return new InputEvent(KeyType.Escape);
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
        for (int x = 0; x <= 5; x++) {
            model.getGroundMap().setValueAt(x, 0, GroundMap.GROUND);
        }
        model.getEconomyManager().refund(1_000_000f);
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

    private RoutePlan plan() {
        List<RouteStep> steps = java.util.stream.IntStream.range(0, 5).mapToObj(
                x -> new RouteStep(new Point(x, 0), letrain.mvp.Presenter.TrackType.NORMAL_TRACK))
                .toList();
        return RoutePlan.feasible(steps, 500, 0);
    }

    private PlannedWork quotedWork() {
        PlannedWork work = presenter.getPlannedWorks().add(new Point(0, 0), new Point(4, 0),
                RoutePolicy.CHEAPEST, plan(), 10);
        WorkQuote quote = WorkQuote.quote(ConstructionCompanyCatalog.IMPERIAL, plan(), 1,
                model.getEconomyManager());
        presenter.getPlannedWorks().replaceQuote(work.id(), quote);
        return presenter.getPlannedWorks().find(work.id()).orElseThrow();
    }

    @Test
    @DisplayName("Run prepays the quote once and builds without charging each cell again")
    void run_prepaysAndBuildsWithoutDoubleCharge() {
        PlannedWork work = quotedWork();
        double quoteCost = work.quote().orElseThrow().totalCost();
        float before = model.getEconomyManager().getBalance();
        when(view.showConstructionPlansDialog(anyList(), anyInt(), anyString())).thenReturn(
                new ConstructionPlansDialog.Result(ConstructionPlansDialog.Action.RUN, 0));

        presenter.onChar(charKey('b'));

        assertTrue(presenter.isConstructionActive());
        assertEquals(PlannedWork.State.WORKING,
                presenter.getPlannedWorks().find(work.id()).orElseThrow().state());
        assertEquals(before - quoteCost, model.getEconomyManager().getBalance(), 0.01f);
        org.mockito.Mockito.clearInvocations(view);
        presenter.drawPlannedWorksOverlay();
        verify(view, org.mockito.Mockito.never()).drawRoutePreviewCell(anyInt(), anyInt(),
                anyString(), anyBoolean(), org.mockito.ArgumentMatchers.any());
        presenter.onChar(escape());
        verify(view).showFileMenu();
        assertTrue(presenter.isConstructionActive(), "Esc must not cancel an automatic work");
        for (int i = 0; i < 10 && presenter.isConstructionActive(); i++) {
            presenter.tickConstruction();
        }
        assertFalse(presenter.isConstructionActive());
        assertEquals(PlannedWork.State.DONE,
                presenter.getPlannedWorks().find(work.id()).orElseThrow().state());
        org.mockito.Mockito.clearInvocations(view);
        presenter.drawPlannedWorksOverlay();
        verify(view, org.mockito.Mockito.never()).drawRoutePreviewCell(anyInt(), anyInt(),
                anyString(), anyBoolean(), org.mockito.ArgumentMatchers.any());
        assertEquals(before - quoteCost, model.getEconomyManager().getBalance(), 0.01f,
                "automatic construction must not charge the base cost per cell again");
    }

    @Test
    @DisplayName("cancelling an automatic work refunds the unbuilt quote minus the penalty")
    void cancel_refundsUnbuiltPart() {
        PlannedWork work = quotedWork();
        double quoteCost = work.quote().orElseThrow().totalCost();
        float before = model.getEconomyManager().getBalance();
        when(view.showConstructionPlansDialog(anyList(), anyInt(), anyString())).thenReturn(
                new ConstructionPlansDialog.Result(ConstructionPlansDialog.Action.RUN, 0));

        presenter.onChar(charKey('b'));
        presenter.tickConstruction();
        presenter.tickConstruction();
        int built = presenter.getConstructionProgress().builtSteps();
        double unbuilt = 0;
        for (int index = built; index < work.quote().orElseThrow().plan().steps().size(); index++) {
            unbuilt += work.quote().orElseThrow().costForStep(index);
        }
        double penalty = Math.max(unbuilt * 0.10, quoteCost * 0.05);
        double expectedRefund = unbuilt - penalty;

        when(view.showConstructionPlansDialog(anyList(), anyInt(), anyString()))
                .thenReturn(new ConstructionPlansDialog.Result(
                        ConstructionPlansDialog.Action.CANCEL, 0))
                .thenReturn(new ConstructionPlansDialog.Result(ConstructionPlansDialog.Action.CLOSE,
                        0));
        presenter.onChar(charKey('b'));

        assertFalse(presenter.isConstructionActive());
        PlannedWork cancelled = presenter.getPlannedWorks().find(work.id()).orElseThrow();
        assertEquals(PlannedWork.State.CANCELLED, cancelled.state());
        assertEquals(quoteCost - expectedRefund, cancelled.spentCost().orElseThrow(), 0.01);
        assertEquals(before - quoteCost + expectedRefund, model.getEconomyManager().getBalance(),
                0.01f);
        presenter.drawConstructionOverlay();
        org.mockito.ArgumentCaptor<String> status =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(view, org.mockito.Mockito.atLeastOnce()).drawRoutePreviewStatus(status.capture());
        assertTrue(status.getAllValues().stream().anyMatch(text -> text.contains("spent $")));
        assertTrue(presenter.getConstructionProgress().builtSteps() > 0);
    }

    @Test
    @DisplayName("a savegame is blocked while an automatic work is active")
    void save_whileAutomaticWorkIsActive_isBlocked() throws Exception {
        quotedWork();
        when(view.showConstructionPlansDialog(anyList(), anyInt(), anyString())).thenReturn(
                new ConstructionPlansDialog.Result(ConstructionPlansDialog.Action.RUN, 0));
        presenter.onChar(charKey('b'));
        assertTrue(presenter.isConstructionActive());
        File file = File.createTempFile("letrain-active-work", ".json");
        file.delete();

        presenter.onSaveGame(file);

        verify(view).showMessage("Save", "Finish or cancel the active work before saving.");
        assertFalse(file.exists());
    }

    @Test
    @DisplayName("Run with insufficient cash leaves the work stopped")
    void run_withoutCash_doesNotStart() {
        PlannedWork work = quotedWork();
        model.getEconomyManager().charge(model.getEconomyManager().getBalance() + 1);
        when(view.showConstructionPlansDialog(anyList(), anyInt(), anyString())).thenReturn(
                new ConstructionPlansDialog.Result(ConstructionPlansDialog.Action.RUN, 0));

        presenter.onChar(charKey('b'));

        assertFalse(presenter.isConstructionActive());
        assertEquals(1, presenter.getPlannedWorks().size());
        assertTrue(model.getEconomyManager().getBalance() < work.quote().orElseThrow().totalCost());
    }
}
