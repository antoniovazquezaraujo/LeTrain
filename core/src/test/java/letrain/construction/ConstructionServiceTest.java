package letrain.construction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import letrain.economy.EconomyManager;
import letrain.map.Point;
import letrain.mvp.Presenter.TrackType;
import letrain.planner.RoutePlan;
import letrain.planner.RoutePolicy;
import letrain.planner.RouteStep;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Shared construction service: execution, pause and resume")
class ConstructionServiceTest {

    private EconomyManager economy;
    private ConstructionPlanList works;
    private ConstructionService service;
    private RecordingTrackMaterializer materializer;

    @BeforeEach
    void setUp() {
        economy = mock(EconomyManager.class);
        when(economy.getBalance()).thenReturn(10_000f);
        when(economy.getConstructionDelay(TrackType.NORMAL_TRACK)).thenReturn(0);
        when(economy.getConstructionDelay(TrackType.BRIDGE_TRACK)).thenReturn(0);
        when(economy.getConstructionDelay(TrackType.TUNNEL_TRACK)).thenReturn(0);
        works = new ConstructionPlanList();
        service = new ConstructionService(works, economy);
        materializer = new RecordingTrackMaterializer();
    }

    private static RoutePlan plan(int cells, double cost) {
        List<RouteStep> steps = java.util.stream.IntStream.range(0, cells)
                .mapToObj(x -> new RouteStep(new Point(x, 0), TrackType.NORMAL_TRACK)).toList();
        return RoutePlan.feasible(steps, cost, cells);
    }

    private PlannedWork quotedWork() {
        RoutePlan plan = plan(3, 100);
        PlannedWork work = works.add(new Point(0, 0), new Point(2, 0), RoutePolicy.DIRECT, plan, 3);
        WorkQuote quote = WorkQuote.quote(ConstructionCompanyCatalog.IMPERIAL, plan);
        works.replaceQuote(work.id(), quote);
        return works.find(work.id()).orElseThrow();
    }

    @Test
    @DisplayName("start charges once, pause stores progress, resume does not charge again")
    void startPauseResume_keepsMoneyAndProgress() {
        PlannedWork work = quotedWork();
        WorkQuote quote = work.quote().orElseThrow();

        ConstructionService.Outcome start = service.start(work, quote, materializer);
        assertEquals(ConstructionService.Status.STARTED, start.status());
        assertEquals(PlannedWork.State.WORKING, works.find(work.id()).orElseThrow().state());
        verify(economy).charge((float) quote.totalCost());

        service.tick();
        ConstructionService.Outcome pause = service.pause();
        assertEquals(ConstructionService.Status.PAUSED, pause.status());
        PlannedWork paused = works.find(work.id()).orElseThrow();
        assertEquals(PlannedWork.State.PAUSED, paused.state());
        assertEquals(1, paused.builtSteps());
        assertFalse(service.isActive());

        ConstructionService.Outcome resume = service.resume(paused, materializer);
        assertEquals(ConstructionService.Status.RESUMED, resume.status());
        assertEquals(PlannedWork.State.WORKING, works.find(work.id()).orElseThrow().state());
        verify(economy, times(1)).charge((float) quote.totalCost());
        assertTrue(service.isActive());

        for (int i = 0; i < 10 && service.isActive(); i++) {
            service.tick();
        }
        assertEquals(PlannedWork.State.DONE, works.find(work.id()).orElseThrow().state());
        assertFalse(service.isActive());
    }

    @Test
    @DisplayName("cancelling a paused work refunds the unbuilt quote and records the spend")
    void cancelPaused_refundsUnbuiltPart() {
        PlannedWork work = quotedWork();
        WorkQuote quote = work.quote().orElseThrow();
        service.start(work, quote, materializer);
        service.tick();
        service.pause();
        PlannedWork paused = works.find(work.id()).orElseThrow();

        ConstructionService.Outcome cancel = service.cancelPaused(paused);

        assertEquals(ConstructionService.Status.CANCELLED, cancel.status());
        assertEquals(PlannedWork.State.CANCELLED, works.find(work.id()).orElseThrow().state());
        assertTrue(cancel.spent() > 0);
        verify(economy).refund(org.mockito.ArgumentMatchers.anyFloat());
    }

    @Test
    @DisplayName("start refuses a second work while one is active")
    void start_refusesSecondWork() {
        PlannedWork first = quotedWork();
        ConstructionService.Outcome started =
                service.start(first, first.quote().orElseThrow(), materializer);
        assertEquals(ConstructionService.Status.STARTED, started.status());
        PlannedWork second =
                works.add(new Point(10, 0), new Point(12, 0), RoutePolicy.DIRECT, plan(3, 100), 3);
        works.replaceQuote(second.id(),
                WorkQuote.quote(ConstructionCompanyCatalog.ARTESANA, second.plan()));

        ConstructionService.Outcome outcome = service.start(works.find(second.id()).orElseThrow(),
                works.find(second.id()).orElseThrow().quote().orElseThrow(), materializer);

        assertEquals(ConstructionService.Status.ACTIVE, outcome.status());
        verify(economy, times(1)).charge(org.mockito.ArgumentMatchers.anyFloat());
    }
}
