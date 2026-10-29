package letrain.planner;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import letrain.economy.EconomyManager;
import letrain.ground.GroundMap;
import letrain.map.Point;
import letrain.utils.ValidationUtils;

/**
 * UI-free asynchronous wrapper around {@link RoutePlanner} for long route previews (ADR-036).
 *
 * <p>
 * It starts one {@code RoutePlanner.plan} call on a daemon worker and publishes immutable snapshots
 * that a presenter can read from the UI thread. The job never touches the model, view, ground map
 * or rail map: it only reads the terrain and economy it was given while planning.
 *
 * <p>
 * States:
 *
 * <ul>
 * <li>{@code PLANNING}: the worker is running (or queued).</li>
 * <li>{@code DONE}: a {@link RoutePlan} is available through {@link #result()} and
 * {@link Snapshot#result()}.</li>
 * <li>{@code CANCELLED}: {@link #cancel()} was observed; no partial plan is exposed.</li>
 * <li>{@code FAILED}: the worker threw an unexpected exception; a short message is exposed.</li>
 * </ul>
 *
 * <p>
 * {@link #cancel()} is idempotent. It flips the cancellation flag, marks the snapshot as cancelled
 * so the UI reacts immediately, and interrupts the worker so a long A* search stops promptly.
 */
public final class RoutePlanningJob {

    private static final ExecutorService SHARED_EXECUTOR =
            Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "route-planning-worker");
                thread.setDaemon(true);
                return thread;
            });

    private final GroundMap terrain;
    private final EconomyManager economy;
    private final Point start;
    private final Point end;
    private final RoutePolicy policy;

    private final AtomicBoolean cancelled = new AtomicBoolean(false);
    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(Snapshot.planning());
    private volatile Thread worker;

    /** Starts planning immediately on the shared daemon worker. */
    public RoutePlanningJob(GroundMap terrain, EconomyManager economy, Point start, Point end,
            RoutePolicy policy) {
        this(terrain, economy, start, end, policy, SHARED_EXECUTOR);
    }

    /** Package-private executor injection for deterministic tests. */
    RoutePlanningJob(GroundMap terrain, EconomyManager economy, Point start, Point end,
            RoutePolicy policy, Executor executor) {
        this.terrain = ValidationUtils.requireNonNull(terrain, "terrain");
        this.economy = ValidationUtils.requireNonNull(economy, "economy");
        Point startCopy = ValidationUtils.requireNonNull(start, "start");
        Point endCopy = ValidationUtils.requireNonNull(end, "end");
        this.start = new Point(startCopy.getX(), startCopy.getY());
        this.end = new Point(endCopy.getX(), endCopy.getY());
        this.policy = ValidationUtils.requireNonNull(policy, "policy");
        ValidationUtils.requireNonNull(executor, "executor");
        try {
            executor.execute(this::run);
        } catch (RuntimeException exception) {
            markFailed(exception);
        }
    }

    private void run() {
        worker = Thread.currentThread();
        try {
            if (cancelled.get()) {
                markCancelled();
                return;
            }
            RoutePlan plan = RoutePlanner.plan(terrain, economy, start, end, policy,
                    this::publishProgress, cancelled::get);
            if (cancelled.get()) {
                markCancelled();
                return;
            }
            markDone(plan);
        } catch (CancellationException exception) {
            markCancelled();
        } catch (RuntimeException exception) {
            markFailed(exception);
        } finally {
            worker = null;
        }
    }

    /**
     * Requests cancellation. Idempotent and safe to call after completion (in which case the
     * terminal state is preserved).
     */
    public void cancel() {
        boolean wasPlanning = state() == State.PLANNING;
        cancelled.set(true);
        markCancelled();
        Thread running = worker;
        if (wasPlanning && running != null) {
            running.interrupt();
        }
    }

    /** Latest immutable snapshot; never {@code null}. */
    public Snapshot snapshot() {
        return snapshot.get();
    }

    /** Current job state. */
    public State state() {
        return snapshot.get().state();
    }

    /** Latest progress report; empty before the first report. */
    public Optional<RoutePlanningProgress> progress() {
        return snapshot.get().progress();
    }

    /** Planned route when the job finished successfully; empty otherwise. */
    public Optional<RoutePlan> result() {
        return snapshot.get().result();
    }

    /** Short failure reason when the job failed; empty otherwise. */
    public Optional<String> failureMessage() {
        return snapshot.get().failureMessage();
    }

    /**
     * True when the job reached a terminal state ({@code DONE}, {@code CANCELLED} or
     * {@code FAILED}).
     */
    public boolean isDone() {
        return state() != State.PLANNING;
    }

    /** Policy this job was created for. */
    public RoutePolicy policy() {
        return policy;
    }

    private void publishProgress(RoutePlanningProgress progress) {
        while (true) {
            Snapshot current = snapshot.get();
            if (current.state() != State.PLANNING) {
                return; // Cancelled or already finished: do not resurrect a terminal snapshot.
            }
            if (snapshot.compareAndSet(current, current.withProgress(progress))) {
                return;
            }
        }
    }

    private void markDone(RoutePlan plan) {
        while (true) {
            Snapshot current = snapshot.get();
            if (current.state() != State.PLANNING) {
                return;
            }
            if (snapshot.compareAndSet(current, current.done(plan))) {
                return;
            }
        }
    }

    private void markCancelled() {
        while (true) {
            Snapshot current = snapshot.get();
            if (current.state() != State.PLANNING) {
                return;
            }
            if (snapshot.compareAndSet(current, current.cancelled())) {
                return;
            }
        }
    }

    private void markFailed(RuntimeException exception) {
        String message = failureMessage(exception);
        while (true) {
            Snapshot current = snapshot.get();
            if (current.state() != State.PLANNING) {
                return;
            }
            if (snapshot.compareAndSet(current, current.failed(message))) {
                return;
            }
        }
    }

    private static String failureMessage(RuntimeException exception) {
        String type = exception.getClass().getSimpleName();
        String detail = exception.getMessage();
        if (detail == null || detail.isBlank()) {
            return "Route planning failed: " + type;
        }
        return "Route planning failed: " + type + ": " + detail;
    }

    /** Job lifecycle states. */
    public enum State {
        PLANNING, DONE, CANCELLED, FAILED
    }

    /**
     * Immutable view of a job at one point in time. It carries the state, the latest progress, the
     * result when done and a short failure reason when planning failed.
     */
    public static final class Snapshot {

        private final State state;
        private final RoutePlanningProgress progress;
        private final RoutePlan result;
        private final String failureMessage;

        private Snapshot(State state, RoutePlanningProgress progress, RoutePlan result,
                String failureMessage) {
            this.state = Objects.requireNonNull(state, "state must not be null");
            this.progress = progress;
            this.result = result;
            this.failureMessage = failureMessage;
        }

        private static Snapshot planning() {
            return new Snapshot(State.PLANNING, null, null, null);
        }

        private Snapshot withProgress(RoutePlanningProgress newProgress) {
            return new Snapshot(state, newProgress, result, failureMessage);
        }

        private Snapshot done(RoutePlan newResult) {
            return new Snapshot(State.DONE, progress, newResult, null);
        }

        private Snapshot cancelled() {
            return new Snapshot(State.CANCELLED, progress, null, null);
        }

        private Snapshot failed(String message) {
            return new Snapshot(State.FAILED, progress, null, message);
        }

        public State state() {
            return state;
        }

        /** Latest progress report, if the planner emitted one. */
        public Optional<RoutePlanningProgress> progress() {
            return Optional.ofNullable(progress);
        }

        /** Route when {@link #state()} is {@code DONE}; empty otherwise. */
        public Optional<RoutePlan> result() {
            return Optional.ofNullable(result);
        }

        /** Short failure reason when {@link #state()} is {@code FAILED}; empty otherwise. */
        public Optional<String> failureMessage() {
            return Optional.ofNullable(failureMessage);
        }

        /** Convenience view of {@link #progress()} for status rendering. */
        public Optional<RoutePlanningProgress.Phase> phase() {
            return progress().map(RoutePlanningProgress::phase);
        }

        /** Expanded nodes in the latest report, or {@code 0} before the first one. */
        public int expandedNodes() {
            return progress == null ? 0 : progress.expandedNodes();
        }

        /** Elapsed milliseconds in the latest report, or {@code 0} before the first one. */
        public long elapsedMillis() {
            return progress == null ? 0 : progress.elapsedMillis();
        }

        /** Current cell in the latest report, if any. */
        public Optional<Point> current() {
            return progress == null ? Optional.empty() : progress.currentPosition();
        }

        @Override
        public String toString() {
            return "RoutePlanningJob.Snapshot[state=" + state + ", progress=" + progress
                    + ", result=" + result + ", failureMessage=" + failureMessage + "]";
        }
    }
}
