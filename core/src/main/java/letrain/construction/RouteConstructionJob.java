package letrain.construction;

import java.util.Objects;
import java.util.Optional;
import letrain.economy.EconomyManager;
import letrain.planner.RoutePlan;
import letrain.planner.RouteStep;

/**
 * Materialises one {@link RoutePlan} cell by cell, one game tick at a time (ADR-037).
 *
 * <p>
 * The job is deterministic and independent of the wall clock: the caller advances it with
 * {@link #tick()}. Each cell costs {@code baseDelayTicksPerCell +
 * economy.getConstructionDelay(trackType)} ticks; the default base delay is one tick, so normal
 * track (delay 0) takes one tick per cell while bridges and tunnels use their configured delay on
 * top of the base.
 *
 * <p>
 * The constructor runs the preflight through {@link TrackMaterializer#prepare(RoutePlan)}. A
 * rejected or infeasible plan leaves the job in {@link ConstructionState#REJECTED} and no cell is
 * materialised. A feasible empty plan is immediately {@link ConstructionState#DONE}.
 *
 * <p>
 * The job knows nothing about the model, rail maker, views or terminals; it only talks to the
 * {@link TrackMaterializer} port. Trains are not blocked by it and it does not create forks or
 * connect to existing track: that is the adapter's isolated-route contract.
 */
public final class RouteConstructionJob {

    /** Default extra construction ticks charged to every planned cell. */
    public static final long DEFAULT_BASE_DELAY_TICKS_PER_CELL = 1;

    private final RoutePlan plan;
    private final TrackMaterializer materializer;
    private final EconomyManager economy;
    private final long baseDelayTicksPerCell;

    private ConstructionState state;
    private int nextStepIndex;
    private int builtSteps;
    private long remainingTicks;
    private String reason;

    /** Job with the default base construction delay of one tick per cell. */
    public RouteConstructionJob(RoutePlan plan, TrackMaterializer materializer,
            EconomyManager economy) {
        this(plan, materializer, economy, DEFAULT_BASE_DELAY_TICKS_PER_CELL);
    }

    /**
     * @param plan plan to materialise
     * @param materializer port that builds the cells
     * @param economy source of the real per-track construction delays
     * @param baseDelayTicksPerCell extra ticks charged to every cell; must be non-negative
     * @throws NullPointerException if any argument is null
     * @throws IllegalArgumentException if {@code baseDelayTicksPerCell} is negative
     */
    public RouteConstructionJob(RoutePlan plan, TrackMaterializer materializer,
            EconomyManager economy, long baseDelayTicksPerCell) {
        this.plan = Objects.requireNonNull(plan, "plan must not be null");
        this.materializer = Objects.requireNonNull(materializer, "materializer must not be null");
        this.economy = Objects.requireNonNull(economy, "economy must not be null");
        if (baseDelayTicksPerCell < 0) {
            throw new IllegalArgumentException(
                    "baseDelayTicksPerCell must be non-negative, but was: "
                            + baseDelayTicksPerCell);
        }
        this.baseDelayTicksPerCell = baseDelayTicksPerCell;
        preflight();
    }

    private void preflight() {
        state = ConstructionState.IDLE;
        if (!plan.feasible()) {
            reject(plan.reason().orElse("Route plan is not feasible"));
            return;
        }
        Optional<String> rejection;
        try {
            rejection = materializer.prepare(plan);
        } catch (RuntimeException exception) {
            reject("Preflight failed: " + describe(exception));
            return;
        }
        if (rejection == null) {
            reject("Preflight returned no result");
            return;
        }
        if (rejection.isPresent()) {
            String message = rejection.get();
            reject(message == null || message.isBlank() ? "Route was rejected by preflight"
                    : message);
            return;
        }
        if (plan.steps().isEmpty()) {
            state = ConstructionState.DONE;
            nextStepIndex = 0;
            builtSteps = 0;
            remainingTicks = 0;
            materializer.finish();
            return;
        }
        state = ConstructionState.BUILDING;
        nextStepIndex = 0;
        builtSteps = 0;
        remainingTicks = ticksFor(plan.steps().get(0));
    }

    /**
     * Advances construction by exactly one game tick.
     *
     * <p>
     * At most one cell is built per tick. A cell with zero ticks (base 0 and normal track) is built
     * on the first tick; a cell with {@code n} ticks is built on the {@code n}-th tick. Calls in
     * any state other than {@link ConstructionState#BUILDING} are no-ops, so ticking twice after
     * completion cannot build extra cells.
     *
     * @return an immutable snapshot after the tick
     */
    public ConstructionProgress tick() {
        if (state != ConstructionState.BUILDING) {
            return progress();
        }
        if (remainingTicks > 0) {
            remainingTicks--;
        }
        if (remainingTicks > 0) {
            return progress();
        }
        buildCurrentStep();
        return progress();
    }

    private void buildCurrentStep() {
        RouteStep previous = nextStepIndex > 0 ? plan.steps().get(nextStepIndex - 1) : null;
        int nextIndex = nextStepIndex + 1;
        RouteStep next = nextIndex < plan.steps().size() ? plan.steps().get(nextIndex) : null;

        materializer.buildStep(plan, nextStepIndex, previous, next);
        builtSteps++;
        nextStepIndex++;
        if (nextStepIndex >= plan.steps().size()) {
            state = ConstructionState.DONE;
            remainingTicks = 0;
            materializer.finish();
        } else {
            remainingTicks = ticksFor(plan.steps().get(nextStepIndex));
        }
    }

    /**
     * Stops the job immediately. Cells already built remain in the world; the adapter owns them.
     * Idempotent and harmless after completion or rejection.
     */
    public void cancel() {
        if (state != ConstructionState.BUILDING) {
            return;
        }
        state = ConstructionState.CANCELLED;
        reason = "Route construction cancelled";
        remainingTicks = 0;
        materializer.finish();
    }

    private long ticksFor(RouteStep step) {
        int delay = Math.max(0, economy.getConstructionDelay(step.trackType()));
        return Math.addExact(baseDelayTicksPerCell, delay);
    }

    private void reject(String message) {
        state = ConstructionState.REJECTED;
        reason = message;
        remainingTicks = 0;
    }

    /** Immutable snapshot of the job. */
    public ConstructionProgress progress() {
        RouteStep current =
                state == ConstructionState.BUILDING && nextStepIndex < plan.steps().size()
                        ? plan.steps().get(nextStepIndex)
                        : null;
        return new ConstructionProgress(state, builtSteps, plan.steps().size(), current,
                remainingTicks, reason);
    }

    /** Current lifecycle state. */
    public ConstructionState state() {
        return state;
    }

    /** Plan this job materialises; never {@code null}. */
    public RoutePlan plan() {
        return plan;
    }

    /** Number of cells already materialised. */
    public int builtSteps() {
        return builtSteps;
    }

    /** Number of cells in the plan. */
    public int totalSteps() {
        return plan.steps().size();
    }

    /** Cell currently being built, if the job is running. */
    public Optional<RouteStep> currentStep() {
        return progress().currentStepOptional();
    }

    /** True when the job reached a terminal state. */
    public boolean isDone() {
        return progress().isTerminal();
    }

    private static String describe(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName()
                : exception.getClass().getSimpleName() + ": " + message;
    }
}
