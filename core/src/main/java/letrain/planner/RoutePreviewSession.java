package letrain.planner;

import java.util.List;
import java.util.Optional;
import letrain.economy.EconomyManager;
import letrain.ground.GroundMap;
import letrain.map.Point;
import letrain.utils.ValidationUtils;

/**
 * UI-free state machine for the 2D route preview (ADR-035).
 *
 * <p>
 * It owns the authoring flow around {@link RoutePlanner}: pick the two endpoints, show one plan at
 * a time, cycle through the {@link RoutePolicy policies} and finally select or cancel. It is a
 * <b>pure read-only</b> layer: it never mutates the terrain, the economy, the model or the rail
 * map, and it does not enqueue construction. Selecting a plan only stores it; materialising it is
 * out of scope (a future construction queue will consume {@link #selectedPlan()}).
 *
 * <p>
 * The cursor is <em>not</em> owned by this class. The presenter moves it and calls
 * {@link #begin(Point)} / {@link #enter(Point)}; the last position is kept only for convenience and
 * status rendering, never mutated internally.
 *
 * <p>
 * States:
 *
 * <ul>
 * <li>{@code PICK_START}: waiting for the first endpoint.</li>
 * <li>{@code PICK_END}: first endpoint fixed, waiting for the second one.</li>
 * <li>{@code PREVIEW}: a feasible plan is displayed and the policy can be changed.</li>
 * <li>{@code SELECTED}: the plan was accepted; the session is closed and {@link #selectedPlan()} is
 * available.</li>
 * <li>{@code CANCELLED}: the session was discarded or has not been started yet.</li>
 * </ul>
 *
 * <p>
 * Construction time shown to the author is the planner time plus a base delay applied to every
 * planned cell (endpoints included). The base delay models the owner decision that automatic route
 * construction is slower than manual construction; it is a session input in this slice and does not
 * change the planner API.
 */
public final class RoutePreviewSession {

    /** Default base construction delay charged per planned cell. */
    public static final long DEFAULT_BASE_DELAY_TICKS_PER_CELL = 1;

    /** Policy cycle order requested by the owner; {@link RoutePolicy#CHEAPEST} comes first. */
    private static final List<RoutePolicy> POLICY_CYCLE =
            List.of(RoutePolicy.CHEAPEST, RoutePolicy.DIRECT, RoutePolicy.AVOID_MOUNTAINS,
                    RoutePolicy.AVOID_WATER, RoutePolicy.AVOID_BOTH);

    private final GroundMap terrain;
    private final EconomyManager economy;
    private final long baseDelayTicksPerCell;

    private State state = State.CANCELLED;
    private RoutePolicy policy = RoutePolicy.CHEAPEST;
    private Point cursor;
    private Point start;
    private Point end;
    private RoutePlan lastPlan;
    private String lastMessage;
    private RoutePlan selectedPlan;

    /** Session with the default base construction delay of one tick per cell. */
    public RoutePreviewSession(GroundMap terrain, EconomyManager economy) {
        this(terrain, economy, DEFAULT_BASE_DELAY_TICKS_PER_CELL);
    }

    /**
     * @param terrain terrain to plan over (never modified)
     * @param economy source of real prices and delays (never modified)
     * @param baseDelayTicksPerCell extra construction ticks charged to every planned cell; must be
     *        non-negative
     * @throws NullPointerException if {@code terrain} or {@code economy} is null
     * @throws IllegalArgumentException if {@code baseDelayTicksPerCell} is negative
     */
    public RoutePreviewSession(GroundMap terrain, EconomyManager economy,
            long baseDelayTicksPerCell) {
        this.terrain = ValidationUtils.requireNonNull(terrain, "terrain");
        this.economy = ValidationUtils.requireNonNull(economy, "economy");
        if (baseDelayTicksPerCell < 0) {
            throw new IllegalArgumentException(
                    "baseDelayTicksPerCell must be non-negative, but was: "
                            + baseDelayTicksPerCell);
        }
        this.baseDelayTicksPerCell = baseDelayTicksPerCell;
    }

    /** Preview states. {@code CANCELLED} is also the initial, not-yet-started state. */
    public enum State {
        PICK_START, PICK_END, PREVIEW, SELECTED, CANCELLED
    }

    /**
     * Starts a fresh preview on {@code cursor} and enters {@link State#PICK_START}.
     *
     * @param cursor current cursor position (defensively copied)
     * @return the new state, always {@link State#PICK_START}
     */
    public State begin(Point cursor) {
        Point position = copy(cursor, "cursor");
        reset();
        this.cursor = position;
        this.state = State.PICK_START;
        return state;
    }

    /**
     * Handles the modal {@code Enter} action for the current state.
     *
     * <ul>
     * <li>{@code PICK_START}: fixes the start point and enters {@code PICK_END}.</li>
     * <li>{@code PICK_END}: fixes the end point and asks the planner. Feasible plans enter
     * {@code PREVIEW}; infeasible ones stay in {@code PICK_END} with the planner reason.</li>
     * <li>{@code PREVIEW}: selects the current feasible plan and enters {@code SELECTED}. An
     * infeasible plan cannot be accepted.</li>
     * <li>Any other state: no-op.</li>
     * </ul>
     *
     * @param cursor current cursor position (defensively copied)
     * @return the state after the action
     */
    public State enter(Point cursor) {
        Point position = copy(cursor, "cursor");
        this.cursor = position;
        switch (state) {
            case PICK_START:
                start = position;
                state = State.PICK_END;
                lastPlan = null;
                lastMessage = null;
                break;
            case PICK_END:
                end = position;
                planCurrentPolicy();
                if (lastPlan.feasible()) {
                    state = State.PREVIEW;
                }
                break;
            case PREVIEW:
                if (lastPlan != null && lastPlan.feasible()) {
                    selectedPlan = lastPlan;
                    state = State.SELECTED;
                }
                break;
            default:
                // SELECTED and CANCELLED are closed; entering does nothing.
                break;
        }
        return state;
    }

    /**
     * Cancels the preview. {@code PICK_START}, {@code PICK_END} and {@code PREVIEW} become
     * {@code CANCELLED}; closed states are left untouched.
     *
     * @return the state after the action
     */
    public State escape() {
        switch (state) {
            case PICK_START:
            case PICK_END:
            case PREVIEW:
                reset();
                state = State.CANCELLED;
                break;
            default:
                // SELECTED and CANCELLED are already closed.
                break;
        }
        return state;
    }

    /**
     * Moves to the next policy and recomputes the plan. Only valid in {@link State#PREVIEW}; the
     * cycle wraps back to {@link RoutePolicy#CHEAPEST}.
     *
     * @return the active policy
     */
    public RoutePolicy nextPolicy() {
        return shiftPolicy(1);
    }

    /**
     * Moves to the previous policy and recomputes the plan. Only valid in {@link State#PREVIEW};
     * the cycle wraps back to {@link RoutePolicy#AVOID_BOTH}.
     *
     * @return the active policy
     */
    public RoutePolicy previousPolicy() {
        return shiftPolicy(-1);
    }

    private RoutePolicy shiftPolicy(int offset) {
        if (state != State.PREVIEW) {
            return policy;
        }
        int index = POLICY_CYCLE.indexOf(policy);
        int size = POLICY_CYCLE.size();
        policy = POLICY_CYCLE.get(Math.floorMod(index + offset, size));
        planCurrentPolicy();
        return policy;
    }

    private void planCurrentPolicy() {
        lastPlan = RoutePlanner.plan(terrain, economy, start, end, policy);
        lastMessage = lastPlan.reason().orElse(null);
    }

    private void reset() {
        start = null;
        end = null;
        lastPlan = null;
        lastMessage = null;
        selectedPlan = null;
        policy = RoutePolicy.CHEAPEST;
    }

    private static Point copy(Point point, String fieldName) {
        Point value = ValidationUtils.requireNonNull(point, fieldName);
        return new Point(value.getX(), value.getY());
    }

    /** Current state; {@link State#CANCELLED} before {@link #begin(Point)}. */
    public State state() {
        return state;
    }

    /** Active policy; {@link RoutePolicy#CHEAPEST} by default. */
    public RoutePolicy policy() {
        return policy;
    }

    /** Last cursor position passed to the session, if any. */
    public Optional<Point> cursor() {
        return copyOf(cursor);
    }

    /** First endpoint, if already picked. */
    public Optional<Point> start() {
        return copyOf(start);
    }

    /** Second endpoint, if already picked. */
    public Optional<Point> end() {
        return copyOf(end);
    }

    /** Last plan computed for the active policy; empty before the first plan. */
    public Optional<RoutePlan> lastPlan() {
        return Optional.ofNullable(lastPlan);
    }

    /**
     * Why the last plan is infeasible, or why an action was refused. Empty when there is nothing to
     * report (for example, a feasible plan).
     */
    public Optional<String> lastMessage() {
        return Optional.ofNullable(lastMessage);
    }

    /** Plan accepted with {@code Enter} in {@code PREVIEW}; empty until selection. */
    public Optional<RoutePlan> selectedPlan() {
        return Optional.ofNullable(selectedPlan);
    }

    /**
     * Construction time the author should see: planner ticks plus
     * {@code baseDelayTicksPerCell * steps().size()}. The base delay applies to every planned cell,
     * endpoints included. Returns {@code 0} when there is no feasible plan.
     */
    public long effectiveConstructionTicks() {
        if (lastPlan == null || !lastPlan.feasible()) {
            return 0;
        }
        long baseTicks = Math.multiplyExact(baseDelayTicksPerCell, lastPlan.steps().size());
        return Math.addExact(lastPlan.totalConstructionTicks(), baseTicks);
    }

    private static Optional<Point> copyOf(Point point) {
        return point == null ? Optional.empty()
                : Optional.of(new Point(point.getX(), point.getY()));
    }
}
