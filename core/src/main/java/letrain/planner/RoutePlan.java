package letrain.planner;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Result of {@link RoutePlanner#plan}. Immutable value object.
 *
 * <p>
 * When feasible, {@link #steps()} holds the ordered cells to build (start included, end included)
 * and the totals are the real construction figures for those cells. When infeasible,
 * {@link #steps()} is empty, totals are zero and {@link #reason()} explains why. That distinction
 * is what a future authoring UI needs to show a price/time option or a clear rejection.
 */
public final class RoutePlan {

    private final List<RouteStep> steps;
    private final double totalCost;
    private final long totalConstructionTicks;
    private final boolean feasible;
    private final String reason;

    private RoutePlan(List<RouteStep> steps, double totalCost, long totalConstructionTicks,
            boolean feasible, String reason) {
        this.steps = List.copyOf(steps);
        this.totalCost = totalCost;
        this.totalConstructionTicks = totalConstructionTicks;
        this.feasible = feasible;
        this.reason = reason;
    }

    /** A route that can be built, with its real cost and construction time. */
    public static RoutePlan feasible(List<RouteStep> steps, double totalCost,
            long totalConstructionTicks) {
        Objects.requireNonNull(steps, "steps must not be null");
        if (totalCost < 0) {
            throw new IllegalArgumentException("totalCost must be non-negative");
        }
        if (totalConstructionTicks < 0) {
            throw new IllegalArgumentException("totalConstructionTicks must be non-negative");
        }
        return new RoutePlan(steps, totalCost, totalConstructionTicks, true, null);
    }

    /** A route that cannot be built. {@code reason} is shown to the scenario author. */
    public static RoutePlan infeasible(String reason) {
        String message = Objects.requireNonNull(reason, "reason must not be null");
        if (message.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        return new RoutePlan(List.of(), 0, 0, false, message);
    }

    public List<RouteStep> steps() {
        return steps;
    }

    public double totalCost() {
        return totalCost;
    }

    public long totalConstructionTicks() {
        return totalConstructionTicks;
    }

    public boolean feasible() {
        return feasible;
    }

    /** Why the route is infeasible; empty when {@link #feasible()} is true. */
    public Optional<String> reason() {
        return Optional.ofNullable(reason);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof RoutePlan plan)) {
            return false;
        }
        return feasible == plan.feasible && Double.compare(totalCost, plan.totalCost) == 0
                && totalConstructionTicks == plan.totalConstructionTicks && steps.equals(plan.steps)
                && Objects.equals(reason, plan.reason);
    }

    @Override
    public int hashCode() {
        return Objects.hash(steps, totalCost, totalConstructionTicks, feasible, reason);
    }

    @Override
    public String toString() {
        if (!feasible) {
            return "RoutePlan[infeasible: " + reason + "]";
        }
        return "RoutePlan[steps=" + steps.size() + ", cost=" + totalCost + ", ticks="
                + totalConstructionTicks + "]";
    }
}
