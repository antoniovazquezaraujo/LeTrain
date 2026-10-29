package letrain.construction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import letrain.economy.EconomyManager;
import letrain.map.Point;
import letrain.mvp.Presenter.TrackType;
import letrain.planner.RoutePlan;
import letrain.planner.RouteStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RouteConstructionQueueTest {

    private static EconomyManager economy() {
        EconomyManager economy = mock(EconomyManager.class);
        when(economy.getConstructionDelay(TrackType.NORMAL_TRACK)).thenReturn(0);
        when(economy.getConstructionDelay(TrackType.BRIDGE_TRACK)).thenReturn(3);
        when(economy.getConstructionDelay(TrackType.TUNNEL_TRACK)).thenReturn(2);
        return economy;
    }

    private static RoutePlan normalPlan(int count) {
        RouteStep[] steps = new RouteStep[count];
        for (int i = 0; i < count; i++) {
            steps[i] = new RouteStep(new Point(i, 0), TrackType.NORMAL_TRACK);
        }
        return RoutePlan.feasible(List.of(steps), 0, 0);
    }

    @Test
    @DisplayName("an empty queue is idle, unlocked and reports idle progress")
    void should_BeIdle_When_NothingWasStarted() {
        RouteConstructionQueue queue = new RouteConstructionQueue(economy());

        assertFalse(queue.isActive());
        assertFalse(queue.isEditingLocked());
        assertTrue(queue.job().isEmpty());
        assertEquals(ConstructionState.IDLE, queue.progress().state());
        assertEquals(ConstructionProgress.idle(), queue.tick());
    }

    @Test
    @DisplayName("a started job locks editing and refuses a second job")
    void should_RefuseSecondJob_When_OneIsActive() {
        RouteConstructionQueue queue = new RouteConstructionQueue(economy());
        RecordingTrackMaterializer first = new RecordingTrackMaterializer();
        RecordingTrackMaterializer second = new RecordingTrackMaterializer();

        assertTrue(queue.start(normalPlan(2), first));
        assertTrue(queue.isActive());
        assertTrue(queue.isEditingLocked());
        RouteConstructionJob firstJob = queue.job().orElseThrow();

        assertFalse(queue.start(normalPlan(5), second));
        assertSame(firstJob, queue.job().orElseThrow());
        assertEquals(1, first.prepareCalls);
        assertEquals(0, second.prepareCalls);
    }

    @Test
    @DisplayName("tick advances the active job and exposes its progress")
    void should_AdvanceAndExposeProgress_When_Ticked() {
        RouteConstructionQueue queue = new RouteConstructionQueue(economy());
        RecordingTrackMaterializer materializer = new RecordingTrackMaterializer();
        queue.start(normalPlan(3), materializer);

        ConstructionProgress first = queue.tick();
        assertEquals(1, first.builtSteps());
        assertEquals(stepOf(1), first.currentStep());

        queue.tick();
        ConstructionProgress third = queue.tick();
        assertEquals(ConstructionState.DONE, third.state());
        assertEquals(3, third.builtSteps());
        assertFalse(queue.isActive());
        assertFalse(queue.isEditingLocked());
    }

    @Test
    @DisplayName("cancel stops the active job without losing the built count")
    void should_CancelAndUnlock_When_Cancelled() {
        RouteConstructionQueue queue = new RouteConstructionQueue(economy());
        RecordingTrackMaterializer materializer = new RecordingTrackMaterializer();
        queue.start(normalPlan(4), materializer);
        queue.tick();
        queue.tick();

        ConstructionProgress cancelled = queue.cancel();

        assertEquals(ConstructionState.CANCELLED, cancelled.state());
        assertEquals(2, cancelled.builtSteps());
        assertFalse(queue.isActive());
        assertFalse(queue.isEditingLocked());
        assertEquals(1, materializer.finishCalls);
        assertEquals(2, queue.tick().builtSteps());
    }

    @Test
    @DisplayName("a rejected job is kept for progress but does not lock or block a new start")
    void should_ExposeRejectionAndAllowRestart_When_PreflightRejects() {
        RouteConstructionQueue queue = new RouteConstructionQueue(economy());
        RecordingTrackMaterializer rejected = new RecordingTrackMaterializer();
        rejected.rejection = Optional.of("occupied cell");

        assertTrue(queue.start(normalPlan(2), rejected));
        assertEquals(ConstructionState.REJECTED, queue.progress().state());
        assertEquals("occupied cell", queue.progress().reasonOptional().orElseThrow());
        assertFalse(queue.isActive());
        assertFalse(queue.isEditingLocked());
        assertEquals(2, queue.progress().totalSteps());

        RecordingTrackMaterializer accepted = new RecordingTrackMaterializer();
        assertTrue(queue.start(normalPlan(1), accepted));
        assertEquals(ConstructionState.BUILDING, queue.progress().state());
    }

    @Test
    @DisplayName("a finished job can be replaced by a new one")
    void should_AllowNewJob_When_PreviousIsDone() {
        RouteConstructionQueue queue = new RouteConstructionQueue(economy());
        RecordingTrackMaterializer first = new RecordingTrackMaterializer();
        queue.start(normalPlan(1), first);
        queue.tick();
        assertEquals(ConstructionState.DONE, queue.progress().state());

        RecordingTrackMaterializer second = new RecordingTrackMaterializer();
        assertTrue(queue.start(normalPlan(2), second));
        assertTrue(queue.isActive());
        assertEquals(2, queue.progress().totalSteps());
    }

    @Test
    @DisplayName("the queue passes its base delay to every job")
    void should_UseConfiguredBaseDelay_When_Building() {
        RouteConstructionQueue queue = new RouteConstructionQueue(economy(), 2);
        RecordingTrackMaterializer materializer = new RecordingTrackMaterializer();
        queue.start(normalPlan(1), materializer);

        queue.tick();
        assertEquals(1, queue.progress().remainingTicks());
        assertTrue(materializer.builtIndexes.isEmpty());

        queue.tick();
        assertEquals(List.of(0), materializer.builtIndexes);
    }

    @Test
    @DisplayName("queue input validation rejects nulls and a negative base delay")
    void should_Throw_When_InputIsInvalid() {
        assertThrows(NullPointerException.class, () -> new RouteConstructionQueue(null));
        assertThrows(IllegalArgumentException.class,
                () -> new RouteConstructionQueue(economy(), -1));

        RouteConstructionQueue queue = new RouteConstructionQueue(economy());
        assertThrows(NullPointerException.class,
                () -> queue.start(null, new RecordingTrackMaterializer()));
        assertThrows(NullPointerException.class, () -> queue.start(normalPlan(1), null));
    }

    private static RouteStep stepOf(int x) {
        return new RouteStep(new Point(x, 0), TrackType.NORMAL_TRACK);
    }
}
