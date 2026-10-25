package letrain.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import letrain.economy.EconomyManager;
import letrain.economy.EconomyManager.ExpenseType;
import letrain.ground.GroundMap;
import letrain.map.Point;
import letrain.mvp.Presenter.TrackType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RoutePlanningJobTest {

    private static final int NORMAL = GroundMap.GROUND;

    private static EconomyManager economy() {
        EconomyManager economy = mock(EconomyManager.class);
        when(economy.getCost(ExpenseType.CONSTRUCTED_NORMAL_RAIL_TRACK)).thenReturn(100f);
        when(economy.getCost(ExpenseType.CONSTRUCTED_BRIDGE_RAIL_TRACK)).thenReturn(20_000f);
        when(economy.getCost(ExpenseType.CONSTRUCTED_TUNNEL_RAIL_TRACK)).thenReturn(70_000f);
        when(economy.getConstructionDelay(TrackType.NORMAL_TRACK)).thenReturn(0);
        when(economy.getConstructionDelay(TrackType.BRIDGE_TRACK)).thenReturn(20);
        when(economy.getConstructionDelay(TrackType.TUNNEL_TRACK)).thenReturn(30);
        return economy;
    }

    private static Point p(int x, int y) {
        return new Point(x, y);
    }

    private static void awaitDone(RoutePlanningJob job) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!job.isDone() && System.nanoTime() < deadline) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
        assertTrue(job.isDone(), "job did not finish before the timeout");
    }

    @Test
    @DisplayName("a default job starts on a worker and finishes with a result")
    void should_FinishWithResult_When_PlanningSucceeds() {
        FakeGroundMap terrain = new FakeGroundMap().rect(-1, -1, 4, 1, NORMAL);
        RoutePlanningJob job =
                new RoutePlanningJob(terrain, economy(), p(0, 0), p(3, 0), RoutePolicy.DIRECT);

        awaitDone(job);

        assertEquals(RoutePlanningJob.State.DONE, job.state());
        assertTrue(job.result().orElseThrow().feasible());
        assertEquals(4, job.result().orElseThrow().steps().size());
        RoutePlanningJob.Snapshot snapshot = job.snapshot();
        assertEquals(RoutePlanningJob.State.DONE, snapshot.state());
        assertTrue(snapshot.result().isPresent());
        assertTrue(snapshot.progress().isPresent());
        assertEquals(RoutePlanningProgress.Phase.FALLBACK, snapshot.phase().orElseThrow());
        assertTrue(snapshot.expandedNodes() >= 0);
    }

    @Test
    @DisplayName("the initial snapshot is PLANNING while the task is queued")
    void should_BePlanning_When_WorkerHasNotRunYet() {
        FakeGroundMap terrain = new FakeGroundMap().rect(0, 0, 4, 0, NORMAL);
        ManualExecutor executor = new ManualExecutor();
        RoutePlanningJob job = new RoutePlanningJob(terrain, economy(), p(0, 0), p(4, 0),
                RoutePolicy.DIRECT, executor);

        assertEquals(RoutePlanningJob.State.PLANNING, job.state());
        assertFalse(job.isDone());
        assertTrue(job.result().isEmpty());
        assertNotNull(job.snapshot());

        executor.run();

        assertEquals(RoutePlanningJob.State.DONE, job.state());
        assertTrue(job.isDone());
    }

    @Test
    @DisplayName("cancel is idempotent, marks CANCELLED immediately and drops any result")
    void should_CancelWithoutLeakingPartialPlan_When_Cancelled() {
        FakeGroundMap terrain = new FakeGroundMap().rect(0, 0, 4, 0, NORMAL);
        ManualExecutor executor = new ManualExecutor();
        RoutePlanningJob job = new RoutePlanningJob(terrain, economy(), p(0, 0), p(4, 0),
                RoutePolicy.DIRECT, executor);

        job.cancel();
        job.cancel();

        assertEquals(RoutePlanningJob.State.CANCELLED, job.state());
        assertTrue(job.isDone());
        assertTrue(job.result().isEmpty());

        executor.run();

        assertEquals(RoutePlanningJob.State.CANCELLED, job.state(),
                "a cancelled job must not overwrite its terminal state when the task eventually runs");
        assertTrue(job.result().isEmpty());
    }

    @Test
    @DisplayName("cancelling after completion preserves the successful result")
    void should_KeepResult_When_CancelledAfterDone() {
        FakeGroundMap terrain = new FakeGroundMap().rect(0, 0, 4, 0, NORMAL);
        RoutePlanningJob job = new RoutePlanningJob(terrain, economy(), p(0, 0), p(4, 0),
                RoutePolicy.DIRECT, Runnable::run);
        assertEquals(RoutePlanningJob.State.DONE, job.state());

        job.cancel();

        assertEquals(RoutePlanningJob.State.DONE, job.state());
        assertTrue(job.result().isPresent());
    }

    @Test
    @DisplayName("an unexpected exception becomes FAILED with a short message, not a stack trace")
    void should_FailWithMessage_When_PlannerThrows() {
        GroundMap terrain = mock(GroundMap.class);
        when(terrain.getValueAt(any(Point.class)))
                .thenThrow(new IllegalStateException("terrain exploded"));

        RoutePlanningJob job = new RoutePlanningJob(terrain, economy(), p(0, 0), p(3, 0),
                RoutePolicy.DIRECT, Runnable::run);

        assertEquals(RoutePlanningJob.State.FAILED, job.state());
        assertTrue(job.isDone());
        assertTrue(job.result().isEmpty());
        String message = job.snapshot().failureMessage().orElseThrow();
        assertTrue(message.contains("terrain exploded"));
        assertFalse(message.contains("\n"), "failure message must not contain a stack trace");
    }

    @Test
    @DisplayName("policy is exposed for cache bookkeeping")
    void should_ExposePolicy() {
        FakeGroundMap terrain = new FakeGroundMap().rect(0, 0, 4, 0, NORMAL);
        RoutePlanningJob job = new RoutePlanningJob(terrain, economy(), p(0, 0), p(4, 0),
                RoutePolicy.CHEAPEST, Runnable::run);

        assertEquals(RoutePolicy.CHEAPEST, job.policy());
    }

    /** Executor that stores one task so tests can observe PLANNING deterministically. */
    private static final class ManualExecutor implements Executor {

        private Runnable task;

        @Override
        public void execute(Runnable command) {
            this.task = command;
        }

        private void run() {
            assertNotNull(task, "no task was submitted");
            task.run();
        }
    }
}
