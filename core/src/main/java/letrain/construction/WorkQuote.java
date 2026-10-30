package letrain.construction;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import letrain.construction.ConstructionCompany.Capability;
import letrain.construction.ConstructionCompany.Quality;
import letrain.economy.EconomyManager;
import letrain.economy.EconomyManager.ExpenseType;
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
 * Quote feasibility is about capabilities; cash reservation and cancellation are handled by the
 * presenter/queue execution layer, not by the quote itself.
 */
public final class WorkQuote {

    /** Extra ticks charged per planned cell, matching the current preview estimate. */
    public static final long DEFAULT_BASE_DELAY_TICKS_PER_CELL = 1;

    private final ConstructionCompany company;
    private final RoutePlan plan;
    private final Set<Capability> requiredCapabilities;
    private final List<Double> stepCosts;
    private final double totalCost;
    private final long totalTicks;
    private final boolean feasible;
    private final String reason;

    private WorkQuote(ConstructionCompany company, RoutePlan plan,
            Set<Capability> requiredCapabilities, List<Double> stepCosts, double totalCost,
            long totalTicks, boolean feasible, String reason) {
        this.company = company;
        this.plan = plan;
        this.requiredCapabilities = Set.copyOf(requiredCapabilities);
        this.stepCosts = List.copyOf(stepCosts);
        this.totalCost = totalCost;
        this.totalTicks = totalTicks;
        this.feasible = feasible;
        this.reason = reason;
    }

    /** Builds a quote with the default one tick per cell base delay. */
    public static WorkQuote quote(ConstructionCompany company, RoutePlan plan) {
        return quote(company, plan, DEFAULT_BASE_DELAY_TICKS_PER_CELL, null);
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
        return quote(company, plan, baseDelayTicksPerCell, null);
    }

    /**
     * Builds a company quote with per-step construction prices. The per-step costs are used to
     * refund exactly the unbuilt part when an automatic work is cancelled.
     */
    public static WorkQuote quote(ConstructionCompany company, RoutePlan plan,
            long baseDelayTicksPerCell, EconomyManager economy) {
        Objects.requireNonNull(company, "company must not be null");
        Objects.requireNonNull(plan, "plan must not be null");
        if (baseDelayTicksPerCell < 0) {
            throw new IllegalArgumentException(
                    "baseDelayTicksPerCell must be non-negative, but was: "
                            + baseDelayTicksPerCell);
        }
        Set<Capability> required = requiredCapabilities(plan);
        if (!plan.feasible()) {
            return new WorkQuote(company, plan, required, List.of(), 0, 0, false,
                    "route plan is not feasible");
        }
        if (!company.supportsAll(required)) {
            return new WorkQuote(company, plan, required, List.of(), 0, 0, false,
                    "company cannot build " + describeCapabilities(required));
        }
        List<Double> costs = stepCosts(company, plan, economy);
        long baseTicks = Math.addExact(plan.totalConstructionTicks(),
                Math.multiplyExact(baseDelayTicksPerCell, plan.steps().size()));
        long ticks = Math.max(0, Math.round(baseTicks * company.timeFactor()));
        double cost = costs.isEmpty() ? plan.totalCost() * company.costFactor()
                : costs.stream().mapToDouble(Double::doubleValue).sum();
        return new WorkQuote(company, plan, required, costs, cost, ticks, true, null);
    }

    private static List<Double> stepCosts(ConstructionCompany company, RoutePlan plan,
            EconomyManager economy) {
        if (economy == null) {
            return List.of();
        }
        List<Double> costs = new ArrayList<>();
        for (RouteStep step : plan.steps()) {
            costs.add(economy.getCost(expenseFor(step.trackType())) * company.costFactor());
        }
        return costs;
    }

    private static ExpenseType expenseFor(TrackType type) {
        return switch (type) {
            case BRIDGE_TRACK, BRIDGE_GATE_TRACK -> ExpenseType.CONSTRUCTED_BRIDGE_RAIL_TRACK;
            case TUNNEL_TRACK, TUNNEL_GATE_TRACK -> ExpenseType.CONSTRUCTED_TUNNEL_RAIL_TRACK;
            default -> ExpenseType.CONSTRUCTED_NORMAL_RAIL_TRACK;
        };
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

    /**
     * Cost of one planned cell in this quote. Falls back to an even split when the quote was built
     * without an economy source (tests or historical callers).
     */
    public double costForStep(int index) {
        if (index < 0 || index >= plan.steps().size()) {
            throw new IndexOutOfBoundsException("step index out of range: " + index);
        }
        if (!stepCosts.isEmpty()) {
            return stepCosts.get(index);
        }
        return plan.steps().isEmpty() ? 0 : totalCost / plan.steps().size();
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
