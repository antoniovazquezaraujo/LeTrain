package letrain.construction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import letrain.economy.EconomyManager;
import letrain.map.Point;
import letrain.mvp.Presenter.TrackType;
import letrain.planner.RoutePlan;
import letrain.planner.RouteStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RouteConstructionJobTest {

    private static EconomyManager economy(int normal, int bridge, int tunnel) {
        EconomyManager economy = mock(EconomyManager.class);
        when(economy.getConstructionDelay(TrackType.NORMAL_TRACK)).thenReturn(normal);
        when(economy.getConstructionDelay(TrackType.BRIDGE_TRACK)).thenReturn(bridge);
        when(economy.getConstructionDelay(TrackType.TUNNEL_TRACK)).thenReturn(tunnel);
        return economy;
    }

    private static EconomyManager economy() {
        return economy(0, 3, 2);
    }

    private static RouteStep step(int x, int y, TrackType type) {
        return new RouteStep(new Point(x, y), type);
    }

    private static RoutePlan plan(RouteStep... steps) {
        return RoutePlan.feasible(List.of(steps), 0, 0);
    }

    private static RoutePlan normalPlan(int count) {
        RouteStep[] steps = new RouteStep[count];
        for (int i = 0; i < count; i++) {
            steps[i] = step(i, 0, TrackType.NORMAL_TRACK);
        }
        return plan(steps);
    }

    @Test
    @DisplayName("a rejected preflight stops the job without materialising anything")
    void should_RejectWithoutBuilding_When_PreflightRejects() {
        RecordingTrackMaterializer materializer = new RecordingTrackMaterializer();
        materializer.rejection = java.util.Optional.of("cell is occupied by an existing fork");

        RouteConstructionJob job = new RouteConstructionJob(normalPlan(3), materializer, economy());

        assertEquals(ConstructionState.REJECTED, job.state());
        ConstructionProgress progress = job.progress();
        assertEquals(0, progress.builtSteps());
        assertEquals(3, progress.totalSteps());
        assertTrue(progress.currentStepOptional().isEmpty());
        assertEquals("cell is occupied by an existing fork",
                progress.reasonOptional().orElseThrow());
        assertTrue(progress.isTerminal());
        assertEquals(1, materializer.prepareCalls);
        assertTrue(materializer.builtIndexes.isEmpty());
        assertEquals(0, materializer.finishCalls);

        job.tick();
        job.tick();
        assertEquals(ConstructionState.REJECTED, job.state());
        assertTrue(materializer.builtIndexes.isEmpty());
    }

    @Test
    @DisplayName("an infeasible plan is rejected with the planner reason and no preflight")
    void should_Reject_When_PlanIsInfeasible() {
        RecordingTrackMaterializer materializer = new RecordingTrackMaterializer();
        RouteConstructionJob job =
                new RouteConstructionJob(RoutePlan.infeasible("No route"), materializer, economy());

        assertEquals(ConstructionState.REJECTED, job.state());
        assertEquals("No route", job.progress().reasonOptional().orElseThrow());
        assertEquals(0, materializer.prepareCalls);
        assertTrue(materializer.builtIndexes.isEmpty());
    }

    @Test
    @DisplayName("a preflight exception becomes a rejection instead of escaping")
    void should_Reject_When_PreflightThrows() {
        RecordingTrackMaterializer materializer = new RecordingTrackMaterializer();
        materializer.prepareFailure = new IllegalStateException("rail map not ready");

        RouteConstructionJob job = new RouteConstructionJob(normalPlan(2), materializer, economy());

        assertEquals(ConstructionState.REJECTED, job.state());
        assertTrue(job.progress().reasonOptional().orElseThrow().contains("rail map not ready"));
        assertTrue(materializer.builtIndexes.isEmpty());
    }

    @Test
    @DisplayName("normal track with base delay one builds one cell per tick in plan order")
    void should_BuildOneNormalCellPerTick_When_BaseDelayIsOne() {
        RecordingTrackMaterializer materializer = new RecordingTrackMaterializer();
        RouteConstructionJob job =
                new RouteConstructionJob(normalPlan(3), materializer, economy(0, 3, 2));

        ConstructionProgress initial = job.progress();
        assertEquals(ConstructionState.BUILDING, initial.state());
        assertEquals(0, initial.builtSteps());
        assertEquals(3, initial.totalSteps());
        assertEquals(step(0, 0, TrackType.NORMAL_TRACK), initial.currentStep());
        assertEquals(1, initial.remainingTicks());

        job.tick();
        assertEquals(List.of(0), materializer.builtIndexes);
        assertEquals(1, job.progress().builtSteps());
        assertEquals(step(1, 0, TrackType.NORMAL_TRACK), job.progress().currentStep());

        job.tick();
        assertEquals(List.of(0, 1), materializer.builtIndexes);
        assertEquals(2, job.progress().builtSteps());

        job.tick();
        assertEquals(List.of(0, 1, 2), materializer.builtIndexes);
        ConstructionProgress done = job.progress();
        assertEquals(ConstructionState.DONE, done.state());
        assertEquals(3, done.builtSteps());
        assertTrue(done.currentStepOptional().isEmpty());
        assertEquals(0, done.remainingTicks());
        assertTrue(done.isTerminal());
        assertEquals(1, materializer.finishCalls);

        job.tick();
        assertEquals(List.of(0, 1, 2), materializer.builtIndexes);
        assertEquals(3, job.builtSteps());
        assertEquals(1, materializer.finishCalls);
    }

    @Test
    @DisplayName("the materializer receives the previous and next planned steps")
    void should_PassNeighbours_When_BuildingEachStep() {
        RecordingTrackMaterializer materializer = new RecordingTrackMaterializer();
        RouteConstructionJob job =
                new RouteConstructionJob(normalPlan(3), materializer, economy(0, 3, 2));

        job.tick();
        job.tick();
        job.tick();

        assertNull(materializer.previousSteps.get(0));
        assertEquals(step(1, 0, TrackType.NORMAL_TRACK), materializer.nextSteps.get(0));
        assertEquals(step(0, 0, TrackType.NORMAL_TRACK), materializer.previousSteps.get(1));
        assertEquals(step(2, 0, TrackType.NORMAL_TRACK), materializer.nextSteps.get(1));
        assertEquals(step(1, 0, TrackType.NORMAL_TRACK), materializer.previousSteps.get(2));
        assertNull(materializer.nextSteps.get(2));
    }

    @Test
    @DisplayName("bridge and tunnel cells use their real economy delay plus the base delay")
    void should_WaitForBridgeAndTunnelDelays_When_BuildingThem() {
        RecordingTrackMaterializer materializer = new RecordingTrackMaterializer();
        RoutePlan plan = plan(step(0, 0, TrackType.NORMAL_TRACK),
                step(1, 0, TrackType.BRIDGE_TRACK), step(2, 0, TrackType.TUNNEL_TRACK));
        RouteConstructionJob job = new RouteConstructionJob(plan, materializer, economy(0, 3, 2));

        job.tick();
        assertEquals(List.of(0), materializer.builtIndexes);
        assertEquals(step(1, 0, TrackType.BRIDGE_TRACK), job.progress().currentStep());
        assertEquals(4, job.progress().remainingTicks());

        job.tick();
        job.tick();
        job.tick();
        assertEquals(1, job.progress().remainingTicks());
        assertEquals(List.of(0), materializer.builtIndexes);

        job.tick();
        assertEquals(List.of(0, 1), materializer.builtIndexes);
        assertEquals(step(2, 0, TrackType.TUNNEL_TRACK), job.progress().currentStep());
        assertEquals(3, job.progress().remainingTicks());

        job.tick();
        job.tick();
        assertEquals(1, job.progress().remainingTicks());
        job.tick();
        assertEquals(List.of(0, 1, 2), materializer.builtIndexes);
        assertEquals(ConstructionState.DONE, job.state());
    }

    @Test
    @DisplayName("base delay zero builds a zero-delay cell on the first tick")
    void should_BuildOnFirstTick_When_BaseDelayAndTrackDelayAreZero() {
        RecordingTrackMaterializer materializer = new RecordingTrackMaterializer();
        RouteConstructionJob job =
                new RouteConstructionJob(normalPlan(1), materializer, economy(0, 3, 2), 0);

        assertEquals(0, job.progress().remainingTicks());
        job.tick();

        assertEquals(List.of(0), materializer.builtIndexes);
        assertEquals(ConstructionState.DONE, job.state());
    }

    @Test
    @DisplayName("a larger base delay applies to every cell, including normal track")
    void should_UseBaseDelay_When_ItIsGreaterThanOne() {
        RecordingTrackMaterializer materializer = new RecordingTrackMaterializer();
        RouteConstructionJob job =
                new RouteConstructionJob(normalPlan(2), materializer, economy(0, 3, 2), 2);

        job.tick();
        assertEquals(1, job.progress().remainingTicks());
        assertTrue(materializer.builtIndexes.isEmpty());

        job.tick();
        assertEquals(List.of(0), materializer.builtIndexes);
        assertEquals(2, job.progress().remainingTicks());
    }

    @Test
    @DisplayName("a company time factor scales every per-cell delay")
    void should_ScaleDelaysByTimeFactor() {
        RecordingTrackMaterializer materializer = new RecordingTrackMaterializer();
        RouteConstructionJob job =
                new RouteConstructionJob(normalPlan(1), materializer, economy(0, 3, 2), 1, 1.5);

        job.tick();
        assertEquals(1, job.progress().remainingTicks());
        assertTrue(materializer.builtIndexes.isEmpty());

        job.tick();
        assertEquals(List.of(0), materializer.builtIndexes);
    }

    @Test
    @DisplayName("cancel keeps the built count, stops further builds and calls finish once")
    void should_KeepBuiltCellsAndStop_When_Cancelled() {
        RecordingTrackMaterializer materializer = new RecordingTrackMaterializer();
        RouteConstructionJob job =
                new RouteConstructionJob(normalPlan(4), materializer, economy(0, 3, 2));

        job.tick();
        job.tick();
        assertEquals(2, job.progress().builtSteps());

        job.cancel();
        ConstructionProgress cancelled = job.progress();
        assertEquals(ConstructionState.CANCELLED, cancelled.state());
        assertEquals(2, cancelled.builtSteps());
        assertEquals(4, cancelled.totalSteps());
        assertTrue(cancelled.currentStepOptional().isEmpty());
        assertEquals(0, cancelled.remainingTicks());
        assertTrue(cancelled.reasonOptional().orElseThrow().contains("cancelled"));
        assertTrue(cancelled.isTerminal());
        assertEquals(1, materializer.finishCalls);

        job.tick();
        job.cancel();
        assertEquals(List.of(0, 1), materializer.builtIndexes);
        assertEquals(2, job.builtSteps());
        assertEquals(1, materializer.finishCalls);
    }

    @Test
    @DisplayName("cancel after completion does not change the terminal state")
    void should_StayDone_When_CancelledAfterCompletion() {
        RecordingTrackMaterializer materializer = new RecordingTrackMaterializer();
        RouteConstructionJob job =
                new RouteConstructionJob(normalPlan(1), materializer, economy(0, 3, 2));

        job.tick();
        job.cancel();

        assertEquals(ConstructionState.DONE, job.state());
        assertEquals(1, materializer.finishCalls);
    }

    @Test
    @DisplayName("an empty feasible plan is immediately done and finishes cleanly")
    void should_BeDone_When_PlanHasNoSteps() {
        RecordingTrackMaterializer materializer = new RecordingTrackMaterializer();
        RouteConstructionJob job = new RouteConstructionJob(plan(), materializer, economy(0, 3, 2));

        assertEquals(ConstructionState.DONE, job.state());
        assertEquals(0, job.totalSteps());
        assertEquals(1, materializer.finishCalls);

        job.tick();
        assertEquals(0, job.builtSteps());
        assertEquals(1, materializer.finishCalls);
    }

    @Test
    @DisplayName("progress snapshots do not change after the job advances")
    void should_KeepProgressSnapshotsImmutable_When_JobAdvances() {
        RecordingTrackMaterializer materializer = new RecordingTrackMaterializer();
        RouteConstructionJob job =
                new RouteConstructionJob(normalPlan(2), materializer, economy(0, 3, 2));

        ConstructionProgress first = job.progress();
        job.tick();
        ConstructionProgress second = job.progress();

        assertEquals(0, first.builtSteps());
        assertEquals(ConstructionState.BUILDING, first.state());
        assertEquals(1, second.builtSteps());
        assertFalse(first.isTerminal());
    }

    @Test
    @DisplayName("job input validation rejects nulls and a negative base delay")
    void should_Throw_When_InputIsInvalid() {
        RecordingTrackMaterializer materializer = new RecordingTrackMaterializer();
        RoutePlan plan = normalPlan(1);

        assertThrows(NullPointerException.class,
                () -> new RouteConstructionJob(null, materializer, economy()));
        assertThrows(NullPointerException.class,
                () -> new RouteConstructionJob(plan, null, economy()));
        assertThrows(NullPointerException.class,
                () -> new RouteConstructionJob(plan, materializer, null));
        assertThrows(IllegalArgumentException.class,
                () -> new RouteConstructionJob(plan, materializer, economy(), -1));
    }

    @Test
    @DisplayName("the default base delay is one tick")
    void should_DefaultToOneBaseDelayTick() {
        assertEquals(1, RouteConstructionJob.DEFAULT_BASE_DELAY_TICKS_PER_CELL);
    }
}
