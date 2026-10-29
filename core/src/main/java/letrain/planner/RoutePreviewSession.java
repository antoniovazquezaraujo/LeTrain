package letrain.planner;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
 * <li>{@code PLANNING}: a {@link RoutePlanningJob} is running; only {@link #escape()} cancels.</li>
 * <li>{@code PREVIEW}: a plan (feasible or infeasible) is available and the policy can be
 * changed.</li>
 * <li>{@code SELECTED}: the plan was accepted; the session is closed and {@link #selectedPlan()} is
 * available.</li>
 * <li>{@code CANCELLED}: the session was discarded or has not been started yet.</li>
 * </ul>
 *
 * <p>
 * Planning is asynchronous: {@link #enter(Point)} in {@code PICK_END} and policy changes that miss
 * the cache start a {@link RoutePlanningJob} and return {@code PLANNING}. The UI thread calls
 * {@link #poll()} periodically; when the job finishes the session moves to {@code PREVIEW} with
 * either a feasible plan, an infeasible plan and its reason, or a failure message. Plans are cached
 * per policy for the current session, so returning to an already calculated policy is immediate.
 *
 * <p>
 * Construction time shown to the author is the planner time plus a base delay applied to every
 * planned cell (endpoints included). The base delay models the owner decision that automatic route
 * construction is slower than manual construction; it is a session input in this slice and does not
 * change the planner API. While a job is running {@link #effectiveConstructionTicks()} is zero.
 */
public final class RoutePreviewSession {

    /** Default base construction delay charged per planned cell. */
    public static final long DEFAULT_BASE_DELAY_TICKS_PER_CELL = 1;

    /** Policy cycle order requested by the owner; {@link RoutePolicy#CHEAPEST} comes first. */
    private static final List<RoutePolicy> POLICY_CYCLE =
            List.of(RoutePolicy.CHEAPEST, RoutePolicy.DIRECT, RoutePolicy.AVOID_MOUNTAINS,
                    RoutePolicy.AVOID_WATER, RoutePolicy.AVOID_ALL);

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

    /** One plan per policy for the current session/world; reset by {@link #begin(Point)}. */
    private final Map<RoutePolicy, RoutePlan> planCache = new EnumMap<>(RoutePolicy.class);
    private RoutePlanningJob job;
    private RoutePolicy jobPolicy;

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
        PICK_START, PICK_END, PLANNING, PREVIEW, SELECTED, CANCELLED
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
     * Opens an existing plan for editing without changing its endpoints.
     *
     * <p>
     * The session starts directly in {@link State#PREVIEW} with the saved plan and policy, so the
     * author can inspect it and cycle policies. Endpoint picking is deliberately not available: if
     * the author wants a different origin or destination, they create another route. Accepting with
     * {@code Enter} selects the current plan like a normal preview.
     *
     * @param start saved first endpoint
     * @param end saved last endpoint
     * @param policy policy that produced {@code plan}
     * @param plan feasible saved plan
     * @return the new state, always {@link State#PREVIEW}
     * @throws NullPointerException if any argument is null
     * @throws IllegalArgumentException if {@code plan} is infeasible
     */
    public State beginEdit(Point start, Point end, RoutePolicy policy, RoutePlan plan) {
        Point startCopy = copy(start, "start");
        Point endCopy = copy(end, "end");
        RoutePolicy savedPolicy = ValidationUtils.requireNonNull(policy, "policy");
        RoutePlan savedPlan = ValidationUtils.requireNonNull(plan, "plan");
        if (!savedPlan.feasible()) {
            throw new IllegalArgumentException("edited plan must be feasible");
        }
        reset();
        this.start = startCopy;
        this.end = endCopy;
        this.policy = savedPolicy;
        this.lastPlan = savedPlan;
        this.lastMessage = null;
        this.planCache.put(savedPolicy, savedPlan);
        this.state = State.PREVIEW;
        return state;
    }

    /**
     * Handles the modal {@code Enter} action for the current state.
     *
     * <ul>
     * <li>{@code PICK_START}: fixes the start point and enters {@code PICK_END}.</li>
     * <li>{@code PICK_END}: fixes the end point and starts a planning job; enters {@code PLANNING}.
     * The result (feasible or infeasible) arrives through {@link #poll()}.</li>
     * <li>{@code PREVIEW}: selects the current feasible plan and enters {@code SELECTED}. An
     * infeasible plan cannot be accepted.</li>
     * <li>{@code PLANNING}: no-op; only {@link #escape()} cancels.</li>
     * <li>Any other state: no-op.</li>
     * </ul>
     *
     * @param cursor current cursor position (defensively copied)
     * @return the state after the action
     */
    public State enter(Point cursor) {
        Point position = copy(cursor, "cursor");
        if (state == State.PLANNING) {
            return state; // Only escape() can interrupt a running job.
        }
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
                startPlanning();
                break;
            case PREVIEW:
                if (lastPlan != null && lastPlan.feasible()) {
                    selectedPlan = lastPlan;
                    state = State.SELECTED;
                }
                break;
            default:
                // PLANNING, SELECTED and CANCELLED: entering does nothing.
                break;
        }
        return state;
    }

    /**
     * Cancels the preview and any running planning job. {@code PICK_START}, {@code PICK_END},
     * {@code PLANNING} and {@code PREVIEW} become {@code CANCELLED}; closed states are left
     * untouched.
     *
     * @return the state after the action
     */
    public State escape() {
        switch (state) {
            case PICK_START:
            case PICK_END:
            case PLANNING:
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
     * Advances the asynchronous state machine from the UI thread. While a job is running this is a
     * cheap snapshot read. When the job finishes, the session moves to {@code PREVIEW} with the
     * plan and caches it under its policy:
     *
     * <ul>
     * <li>{@code DONE}: feasible or infeasible plan; {@link #lastPlan()} and {@link #lastMessage()}
     * are set.</li>
     * <li>{@code FAILED}: no plan; {@link #lastMessage()} exposes the failure and the state is
     * {@code PREVIEW} so the author can retry another policy or cancel.</li>
     * <li>{@code CANCELLED}: no plan; the session becomes {@code CANCELLED}.</li>
     * </ul>
     *
     * <p>
     * Safe to call in any state; outside {@code PLANNING} it is a no-op.
     *
     * @return the state after polling
     */
    public State poll() {
        if (state != State.PLANNING || job == null) {
            return state;
        }
        RoutePlanningJob.Snapshot snapshot = job.snapshot();
        switch (snapshot.state()) {
            case PLANNING -> {
                // Keep waiting; the next poll will read a newer snapshot.
            }
            case DONE -> {
                RoutePolicy completedPolicy = jobPolicy;
                RoutePlan plan = snapshot.result().orElse(null);
                job = null;
                jobPolicy = null;
                if (plan == null) {
                    lastPlan = null;
                    lastMessage = "Route planning finished without a plan";
                    state = State.PREVIEW;
                } else {
                    if (completedPolicy != null) {
                        planCache.put(completedPolicy, plan);
                    }
                    showPlan(plan);
                    state = State.PREVIEW;
                }
            }
            case CANCELLED -> {
                job = null;
                jobPolicy = null;
                lastPlan = null;
                lastMessage = "Route planning cancelled";
                state = State.CANCELLED;
            }
            case FAILED -> {
                lastMessage = snapshot.failureMessage().orElse("Route planning failed");
                job = null;
                jobPolicy = null;
                lastPlan = null;
                state = State.PREVIEW;
            }
        }
        return state;
    }

    /**
     * Alias for {@link #poll()} kept for callers that prefer an explicit "advance" name.
     *
     * @return the state after polling
     */
    public State advance() {
        return poll();
    }

    /** Latest progress while {@code PLANNING}; empty in every other state. */
    public Optional<RoutePlanningProgress> planningProgress() {
        if (state != State.PLANNING || job == null) {
            return Optional.empty();
        }
        return job.progress();
    }

    /** Latest job snapshot while {@code PLANNING}; empty in every other state. */
    public Optional<RoutePlanningJob.Snapshot> planningSnapshot() {
        if (state != State.PLANNING || job == null) {
            return Optional.empty();
        }
        return Optional.of(job.snapshot());
    }

    /**
     * Moves to the next policy. Only valid in {@link State#PREVIEW}. If that policy is already
     * cached the plan is shown immediately and the state stays {@code PREVIEW}; otherwise a new job
     * starts and the state becomes {@code PLANNING}. The cycle wraps back to
     * {@link RoutePolicy#CHEAPEST}.
     *
     * @return the active policy
     */
    public RoutePolicy nextPolicy() {
        return shiftPolicy(1);
    }

    /**
     * Moves to the previous policy. Only valid in {@link State#PREVIEW}. Cached policies are shown
     * immediately; uncached ones start a job and enter {@code PLANNING}. The cycle wraps back to
     * {@link RoutePolicy#AVOID_ALL}.
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
        RoutePlan cached = planCache.get(policy);
        if (cached != null) {
            showPlan(cached);
            state = State.PREVIEW;
        } else {
            startPlanning();
        }
        return policy;
    }

    private void startPlanning() {
        cancelJob();
        lastPlan = null;
        lastMessage = null;
        jobPolicy = policy;
        job = new RoutePlanningJob(terrain, economy, start, end, policy);
        state = State.PLANNING;
    }

    private void showPlan(RoutePlan plan) {
        lastPlan = plan;
        lastMessage = plan.reason().orElse(null);
    }

    private void reset() {
        cancelJob();
        start = null;
        end = null;
        lastPlan = null;
        lastMessage = null;
        selectedPlan = null;
        policy = RoutePolicy.CHEAPEST;
        planCache.clear();
    }

    private void cancelJob() {
        if (job != null) {
            job.cancel();
            job = null;
        }
        jobPolicy = null;
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
        if (state == State.PLANNING || lastPlan == null || !lastPlan.feasible()) {
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
