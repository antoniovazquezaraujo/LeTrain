package letrain.construction;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import letrain.construction.ConstructionCompany.Capability;
import letrain.construction.ConstructionCompany.Quality;
import letrain.mvp.Presenter.TrackType;
import letrain.planner.RoutePlan;
import letrain.planner.RouteStep;

/**
 * One company's offer for one planned route.
 *
 * <p>
 * The quote derives price and time from the real route plan plus the company factors, and checks
 * the route's structural needs against the company's capabilities. Quality is carried through for
 * display; it has no mechanical effect until maintenance exists.
 *
 * <p>
 * Quote feasibility is about capabilities, not about cash. Cash reservation and cancellation belong
 * to the execution slice.
 */
public final class WorkQuote {

    /** Extra ticks charged per planned cell, matching the current preview estimate. */
    public static final long DEFAULT_BASE_DELAY_TICKS_PER_CELL = 1;

    private final ConstructionCompany company;
    private final RoutePlan plan;
    private final Set<Capability> requiredCapabilities;
    private final double totalCost;
    private final long totalTicks;
    private final boolean feasible;
    private final String reason;

    private WorkQuote(ConstructionCompany company, RoutePlan plan,
            Set<Capability> requiredCapabilities, double totalCost, long totalTicks,
            boolean feasible, String reason) {
        this.company = company;
        this.plan = plan;
        this.requiredCapabilities = Set.copyOf(requiredCapabilities);
        this.totalCost = totalCost;
        this.totalTicks = totalTicks;
        this.feasible = feasible;
        this.reason = reason;
    }

    /** Builds a quote with the default one tick per cell base delay. */
    public static WorkQuote quote(ConstructionCompany company, RoutePlan plan) {
        return quote(company, plan, DEFAULT_BASE_DELAY_TICKS_PER_CELL);
    }

    /**
     * Builds a company quote for {@code plan}.
     *
     * @param company company offering the work
     * @param plan route plan to quote
     * @param baseDelayTicksPerCell extra ticks per planned cell; must be non-negative
     * @throws NullPointerException if {@code company} or {@code plan} is null
     * @throws IllegalArgumentException if the base delay is negative
     */
    public static WorkQuote quote(ConstructionCompany company, RoutePlan plan,
            long baseDelayTicksPerCell) {
        Objects.requireNonNull(company, "company must not be null");
        Objects.requireNonNull(plan, "plan must not be null");
        if (baseDelayTicksPerCell < 0) {
            throw new IllegalArgumentException(
                    "baseDelayTicksPerCell must be non-negative, but was: "
                            + baseDelayTicksPerCell);
        }
        Set<Capability> required = requiredCapabilities(plan);
        if (!plan.feasible()) {
            return new WorkQuote(company, plan, required, 0, 0, false,
                    "route plan is not feasible");
        }
        if (!company.supportsAll(required)) {
            return new WorkQuote(company, plan, required, 0, 0, false,
                    "company cannot build " + describeCapabilities(required));
        }
        long baseTicks = Math.addExact(plan.totalConstructionTicks(),
                Math.multiplyExact(baseDelayTicksPerCell, plan.steps().size()));
        long ticks = Math.max(0, Math.round(baseTicks * company.timeFactor()));
        double cost = plan.totalCost() * company.costFactor();
        return new WorkQuote(company, plan, required, cost, ticks, true, null);
    }

    /** Structures the route needs, derived from its planned track types. */
    public static Set<Capability> requiredCapabilities(RoutePlan plan) {
        Objects.requireNonNull(plan, "plan must not be null");
        Set<Capability> required = EnumSet.noneOf(Capability.class);
        for (RouteStep step : plan.steps()) {
            TrackType type = step.trackType();
            if (type == TrackType.BRIDGE_TRACK || type == TrackType.BRIDGE_GATE_TRACK) {
                required.add(Capability.BRIDGE);
            } else if (type == TrackType.TUNNEL_TRACK || type == TrackType.TUNNEL_GATE_TRACK) {
                required.add(Capability.TUNNEL);
            }
        }
        return required;
    }

    private static String describeCapabilities(Set<Capability> required) {
        if (required.contains(Capability.BRIDGE) && required.contains(Capability.TUNNEL)) {
            return "bridges or tunnels";
        }
        if (required.contains(Capability.BRIDGE)) {
            return "bridges";
        }
        return "tunnels";
    }

    public ConstructionCompany company() {
        return company;
    }

    public RoutePlan plan() {
        return plan;
    }

    public Set<Capability> requiredCapabilities() {
        return Set.copyOf(requiredCapabilities);
    }

    public Quality quality() {
        return company.quality();
    }

    public double totalCost() {
        return totalCost;
    }

    public long totalTicks() {
        return totalTicks;
    }

    public boolean feasible() {
        return feasible;
    }

    /** Why the quote is unavailable; empty when {@link #feasible()} is true. */
    public Optional<String> reason() {
        return Optional.ofNullable(reason);
    }

    @Override
    public String toString() {
        return "WorkQuote[company=" + company.id() + ", feasible=" + feasible + ", cost="
                + totalCost + ", ticks=" + totalTicks + ", quality=" + quality() + ", required="
                + requiredCapabilities + "]";
    }
}
