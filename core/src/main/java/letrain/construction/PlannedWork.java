package letrain.construction;

import java.util.Objects;
import java.util.Optional;
import letrain.map.Point;
import letrain.planner.RoutePlan;
import letrain.planner.RoutePolicy;

/**
 * One route kept in the player's planned-works list (ADR-038, phase 1).
 *
 * <p>
 * A planned work is a <b>budget</b>, not a construction order: it stores the endpoints, the policy
 * and the last feasible plan, but it never touches the world. A selected {@link WorkQuote} adds the
 * company, quality and quoted cost/time without changing the route; changing the plan drops that
 * quote. Phase 1 only needs {@link State#DRAFT}.
 *
 * <p>
 * The value is immutable. Recalculating a work produces a new instance with the same id and
 * endpoints through {@link #withPlan(RoutePolicy, RoutePlan, long)}.
 */
public final class PlannedWork {

    /** Lifecycle of a planned work. Phase 1 only creates drafts. */
    public enum State {
        /** A route plan with its budget; it has not been ordered or built. */
        DRAFT
    }

    private final int id;
    private final Point start;
    private final Point end;
    private final RoutePolicy policy;
    private final RoutePlan plan;
    private final long estimatedTicks;
    private final State state;
    private final WorkQuote quote;

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
        this.estimatedTicks = estimatedTicks;
        this.state = Objects.requireNonNull(state, "state must not be null");
        this.quote = quote;
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

    /** Cost to show in the list: the quote total when selected, otherwise the base plan cost. */
    public double displayCost() {
        return quote == null ? cost() : quote.totalCost();
    }

    /** Time to show in the list: the quote time when selected, otherwise the base estimate. */
    public long displayTicks() {
        return quote == null ? estimatedTicks : quote.totalTicks();
    }

    public long estimatedTicks() {
        return estimatedTicks;
    }

    public State state() {
        return state;
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
        return new PlannedWork(id, start, end, policy, plan, estimatedTicks, state, newQuote);
    }

    @Override
    public String toString() {
        return "PlannedWork[id=" + id + ", start=" + start + ", end=" + end + ", policy=" + policy
                + ", cells=" + cells() + ", cost=" + cost() + ", ticks=" + estimatedTicks
                + ", quote=" + (quote == null ? "-" : quote.company().id()) + ", state=" + state
                + "]";
    }
}
