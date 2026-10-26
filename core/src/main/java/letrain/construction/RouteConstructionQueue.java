package letrain.construction;

import java.util.Objects;
import java.util.Optional;
import letrain.economy.EconomyManager;
import letrain.planner.RoutePlan;

/**
 * Owns at most one active {@link RouteConstructionJob} (ADR-037).
 *
 * <p>
 * The queue is the small, UI-free entry point for the future presenter: it starts a job from a
 * {@link RoutePlan}, advances it once per game tick, can cancel it, and exposes progress plus an
 * editing-lock query. It does not touch the model, the rail maker or any view, and it never
 * connects the route to existing rail.
 *
 * <p>
 * A terminal job ({@code DONE}, {@code REJECTED} or {@code CANCELLED}) no longer blocks the queue,
 * so the next {@link #start(RoutePlan, TrackMaterializer)} replaces it. While a job is
 * {@code BUILDING}, {@code start} is refused and the current job is left untouched.
 */
public final class RouteConstructionQueue implements ConstructionGuard {

    private final EconomyManager economy;
    private final long baseDelayTicksPerCell;

    private RouteConstructionJob job;

    /** Queue with the default base construction delay of one tick per cell. */
    public RouteConstructionQueue(EconomyManager economy) {
        this(economy, RouteConstructionJob.DEFAULT_BASE_DELAY_TICKS_PER_CELL);
    }

    /**
     * @param economy source of the real per-track construction delays
     * @param baseDelayTicksPerCell extra ticks charged to every cell; must be non-negative
     * @throws NullPointerException if {@code economy} is null
     * @throws IllegalArgumentException if {@code baseDelayTicksPerCell} is negative
     */
    public RouteConstructionQueue(EconomyManager economy, long baseDelayTicksPerCell) {
        this.economy = Objects.requireNonNull(economy, "economy must not be null");
        if (baseDelayTicksPerCell < 0) {
            throw new IllegalArgumentException(
                    "baseDelayTicksPerCell must be non-negative, but was: "
                            + baseDelayTicksPerCell);
        }
        this.baseDelayTicksPerCell = baseDelayTicksPerCell;
    }

    /**
     * Starts construction of {@code plan} unless another job is already building.
     *
     * <p>
     * Preflight runs immediately. A rejected plan is reflected in {@link #progress()} as
     * {@link ConstructionState#REJECTED} with its reason; the method still returns {@code true}
     * because the start itself was accepted.
     *
     * @return {@code true} when a new job was created, {@code false} when one was already active
     */
    public boolean start(RoutePlan plan, TrackMaterializer materializer) {
        Objects.requireNonNull(plan, "plan must not be null");
        Objects.requireNonNull(materializer, "materializer must not be null");
        if (isActive()) {
            return false;
        }
        job = new RouteConstructionJob(plan, materializer, economy, baseDelayTicksPerCell);
        return true;
    }

    /**
     * Advances the active job by one game tick. A queue without an active job is a no-op and
     * returns {@link ConstructionProgress#idle()}.
     */
    public ConstructionProgress tick() {
        return job == null ? ConstructionProgress.idle() : job.tick();
    }

    /** Cancels the current job, if any, and returns the resulting progress. */
    public ConstructionProgress cancel() {
        if (job != null) {
            job.cancel();
        }
        return progress();
    }

    /** True while a job is actively materialising cells. */
    public boolean isActive() {
        return job != null && job.state() == ConstructionState.BUILDING;
    }

    /** Alias used by the future UI to lock manual track editing while a route is being built. */
    @Override
    public boolean isEditingLocked() {
        return isActive();
    }

    /** Latest progress; {@link ConstructionProgress#idle()} when no job has ever been started. */
    public ConstructionProgress progress() {
        return job == null ? ConstructionProgress.idle() : job.progress();
    }

    /** Current job, if any (kept after it finishes so the UI can read the final progress). */
    public Optional<RouteConstructionJob> job() {
        return Optional.ofNullable(job);
    }
}
