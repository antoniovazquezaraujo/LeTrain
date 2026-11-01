package letrain.construction;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import letrain.map.Point;
import letrain.planner.RoutePlan;
import letrain.planner.RoutePolicy;

/**
 * One route kept in the player's planned-works list (ADR-038, phase 1).
 *
 * <p>
 * A planned work is a <b>budget</b>, not a construction order: it stores the endpoints, the policy
 * and the last feasible plan, but it never touches the world until Run. A selected
 * {@link WorkQuote} adds the company, quality and quoted cost/time without changing the route;
 * changing the plan drops that quote. A work can be a draft, working, paused, done or cancelled.
 *
 * <p>
 * The value is immutable. Recalculating a work produces a new instance with the same id and
 * endpoints through {@link #withPlan(RoutePolicy, RoutePlan, long)}.
 */
public final class PlannedWork {

    /** Lifecycle of a planned work. */
    public enum State {
        /** A route plan with its budget; it has not been ordered or built. */
        DRAFT,
        /** An automatic company work is currently being materialised. */
        WORKING,
        /** An automatic work was stopped without cancelling it; it can be resumed. */
        PAUSED,
        /** The automatic work finished and its real track replaced the painted plan. */
        DONE,
        /** The automatic work was stopped; built cells remain, the rest was refunded. */
        CANCELLED
    }

    private final int id;
    private final Point start;
    private final Point end;
    private final RoutePolicy policy;
    private final RoutePlan plan;
    private final long estimatedTicks;
    private final State state;
    private final WorkQuote quote;
    private final Double spentCost;
    private final int builtSteps;
    private final long remainingTicks;
    private final double prepaidCost;

    /**
     * @param id stable, positive identifier inside its list
     * @param start first planned cell
     * @param end last planned cell
     * @param policy policy used to calculate {@code plan}
     * @param plan feasible route plan
     * @param estimatedTicks construction time shown to the author, including the base delay
     * @param state lifecycle state
     */
    public PlannedWork(int id, Point start, Point end, RoutePolicy policy, RoutePlan plan,
            long estimatedTicks, State state) {
        this(id, start, end, policy, plan, estimatedTicks, state, null);
    }

    private PlannedWork(int id, Point start, Point end, RoutePolicy policy, RoutePlan plan,
            long estimatedTicks, State state, WorkQuote quote) {
        this(id, start, end, policy, plan, estimatedTicks, state, quote, null, 0, 0, 0);
    }

    private PlannedWork(int id, Point start, Point end, RoutePolicy policy, RoutePlan plan,
            long estimatedTicks, State state, WorkQuote quote, Double spentCost) {
        this(id, start, end, policy, plan, estimatedTicks, state, quote, spentCost, 0, 0, 0);
    }

    private PlannedWork(int id, Point start, Point end, RoutePolicy policy, RoutePlan plan,
            long estimatedTicks, State state, WorkQuote quote, Double spentCost, int builtSteps,
            long remainingTicks, double prepaidCost) {
        if (id < 1) {
            throw new IllegalArgumentException("id must be positive, but was: " + id);
        }
        this.id = id;
        this.start = new Point(Objects.requireNonNull(start, "start must not be null"));
        this.end = new Point(Objects.requireNonNull(end, "end must not be null"));
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        this.plan = Objects.requireNonNull(plan, "plan must not be null");
        if (!plan.feasible()) {
            throw new IllegalArgumentException("planned work must contain a feasible plan");
        }
        if (estimatedTicks < 0) {
            throw new IllegalArgumentException(
                    "estimatedTicks must be non-negative, but was: " + estimatedTicks);
        }
        if (spentCost != null && (spentCost < 0 || !Double.isFinite(spentCost))) {
            throw new IllegalArgumentException(
                    "spentCost must be finite and non-negative, but was: " + spentCost);
        }
        if (builtSteps < 0 || builtSteps > plan.steps().size()) {
            throw new IllegalArgumentException(
                    "builtSteps must be between 0 and the plan size, but was: " + builtSteps);
        }
        if (remainingTicks < 0) {
            throw new IllegalArgumentException(
                    "remainingTicks must be non-negative, but was: " + remainingTicks);
        }
        if (!Double.isFinite(prepaidCost) || prepaidCost < 0) {
            throw new IllegalArgumentException(
                    "prepaidCost must be finite and non-negative, but was: " + prepaidCost);
        }
        this.estimatedTicks = estimatedTicks;
        this.state = Objects.requireNonNull(state, "state must not be null");
        this.quote = quote;
        this.spentCost = spentCost;
        this.builtSteps = builtSteps;
        this.remainingTicks = remainingTicks;
        this.prepaidCost = prepaidCost;
    }

    /** Creates a draft work. */
    public static PlannedWork draft(int id, Point start, Point end, RoutePolicy policy,
            RoutePlan plan, long estimatedTicks) {
        return new PlannedWork(id, start, end, policy, plan, estimatedTicks, State.DRAFT);
    }

    public int id() {
        return id;
    }

    public Point start() {
        return new Point(start);
    }

    public Point end() {
        return new Point(end);
    }

    public RoutePolicy policy() {
        return policy;
    }

    public RoutePlan plan() {
        return plan;
    }

    /** Selected company quote, if the player has chosen one. */
    public Optional<WorkQuote> quote() {
        return Optional.ofNullable(quote);
    }

    public long estimatedTicks() {
        return estimatedTicks;
    }

    public State state() {
        return state;
    }

    /** Number of cells already built when the work was paused. */
    public int builtSteps() {
        return builtSteps;
    }

    /** Ticks still needed for the current cell when the work was paused. */
    public long remainingTicks() {
        return remainingTicks;
    }

    /** Cash charged when the automatic work started; zero for free construction. */
    public double prepaidCost() {
        return prepaidCost;
    }

    /** Actual cash spent when the work was cancelled; empty for drafts and completed works. */
    public OptionalDouble spentCost() {
        return spentCost == null ? OptionalDouble.empty() : OptionalDouble.of(spentCost);
    }

    /** Number of planned cells, endpoints included. */
    public int cells() {
        return plan.steps().size();
    }

    /** Real construction cost of the plan. */
    public double cost() {
        return plan.totalCost();
    }

    /**
     * Returns a copy with a new policy and plan, keeping the identity and endpoints. Used by the
     * list's recalculation action.
     */
    public PlannedWork withPlan(RoutePolicy newPolicy, RoutePlan newPlan, long newEstimatedTicks) {
        return new PlannedWork(id, start, end, newPolicy, newPlan, newEstimatedTicks, state);
    }

    /** Returns a copy in another lifecycle state, keeping the route, quote and identity. */
    public PlannedWork withState(State newState) {
        return new PlannedWork(id, start, end, policy, plan, estimatedTicks,
                Objects.requireNonNull(newState, "state must not be null"), quote, spentCost,
                builtSteps, remainingTicks, prepaidCost);
    }

    /**
     * Returns a paused copy with the exact queue progress and the cash already paid for it.
     */
    public PlannedWork withPausedProgress(int newBuiltSteps, long newRemainingTicks,
            double newPrepaidCost) {
        return new PlannedWork(id, start, end, policy, plan, estimatedTicks, State.PAUSED, quote,
                spentCost, newBuiltSteps, newRemainingTicks, newPrepaidCost);
    }

    /** Returns a copy with the cash charged when the automatic work started. */
    public PlannedWork withPrepaidCost(double newPrepaidCost) {
        return new PlannedWork(id, start, end, policy, plan, estimatedTicks, state, quote,
                spentCost, builtSteps, remainingTicks, newPrepaidCost);
    }

    /** Returns a copy with the actual cash spent on a cancelled work. */
    public PlannedWork withSpentCost(double newSpentCost) {
        return new PlannedWork(id, start, end, policy, plan, estimatedTicks, state, quote,
                newSpentCost, builtSteps, remainingTicks, prepaidCost);
    }

    /**
     * Returns a copy with the selected feasible company quote. The quote must be for this exact
     * plan; changing the plan drops any previous quote instead of silently keeping an invalid one.
     */
    public PlannedWork withQuote(WorkQuote newQuote) {
        Objects.requireNonNull(newQuote, "quote must not be null");
        if (!newQuote.feasible()) {
            throw new IllegalArgumentException("quote must be feasible");
        }
        if (!newQuote.plan().equals(plan)) {
            throw new IllegalArgumentException("quote must belong to the work plan");
        }
        return new PlannedWork(id, start, end, policy, plan, estimatedTicks, state, newQuote,
                spentCost, builtSteps, remainingTicks, prepaidCost);
    }

    @Override
    public String toString() {
        return "PlannedWork[id=" + id + ", start=" + start + ", end=" + end + ", policy=" + policy
                + ", cells=" + cells() + ", cost=" + cost() + ", ticks=" + estimatedTicks
                + ", quote=" + (quote == null ? "-" : quote.company().id()) + ", state=" + state
                + ", built=" + builtSteps + "/" + cells() + ", remaining=" + remainingTicks + "]";
    }
}
