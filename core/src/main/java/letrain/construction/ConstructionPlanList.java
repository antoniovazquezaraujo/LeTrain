package letrain.construction;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import letrain.map.Point;
import letrain.planner.RoutePlan;
import letrain.planner.RoutePolicy;

/**
 * Ordered, UI-free list of {@link PlannedWork planned works} (ADR-038, phase 1).
 *
 * <p>
 * It only stores drafts and keeps the total budget. A draft may carry one selected company quote;
 * contractor selection and execution belong to later slices. The order is the insertion order and
 * the ids are never reused, so the selected row stays stable when an entry is deleted.
 */
public final class ConstructionPlanList {

    private final List<PlannedWork> works = new ArrayList<>();
    private int nextId = 1;

    /**
     * Appends a new draft and returns it with the assigned id.
     *
     * @throws NullPointerException if any argument is null
     * @throws IllegalArgumentException if {@code plan} is infeasible or the estimate is negative
     */
    public PlannedWork add(Point start, Point end, RoutePolicy policy, RoutePlan plan,
            long estimatedTicks) {
        PlannedWork work = PlannedWork.draft(nextId, start, end, policy, plan, estimatedTicks);
        nextId++;
        works.add(work);
        return work;
    }

    /** Returns the work with {@code id}, if it is still in the list. */
    public Optional<PlannedWork> find(int id) {
        return works.stream().filter(work -> work.id() == id).findFirst();
    }

    /**
     * Replaces the plan and policy of the work with {@code id}.
     *
     * @return the updated work, or empty when the id is unknown
     */
    public Optional<PlannedWork> replacePlan(int id, RoutePolicy policy, RoutePlan plan,
            long estimatedTicks) {
        for (int index = 0; index < works.size(); index++) {
            PlannedWork current = works.get(index);
            if (current.id() == id) {
                PlannedWork updated = current.withPlan(policy, plan, estimatedTicks);
                works.set(index, updated);
                return Optional.of(updated);
            }
        }
        return Optional.empty();
    }

    /**
     * Replaces the quote of the work with {@code id}, keeping its route and identity.
     *
     * @return the updated work, or empty when the id is unknown
     */
    public Optional<PlannedWork> replaceQuote(int id, WorkQuote quote) {
        for (int index = 0; index < works.size(); index++) {
            PlannedWork current = works.get(index);
            if (current.id() == id) {
                PlannedWork updated = current.withQuote(quote);
                works.set(index, updated);
                return Optional.of(updated);
            }
        }
        return Optional.empty();
    }

    /** Removes the work with {@code id}; returns whether it existed. */
    public boolean remove(int id) {
        return works.removeIf(work -> work.id() == id);
    }

    /** Immutable snapshot in insertion order. */
    public List<PlannedWork> works() {
        return List.copyOf(works);
    }

    public int size() {
        return works.size();
    }

    public boolean isEmpty() {
        return works.isEmpty();
    }

    /** Sum of the real plan costs. */
    public double totalCost() {
        double total = 0;
        for (PlannedWork work : works) {
            total += work.cost();
        }
        return total;
    }

    /** Sum of the estimated construction ticks. */
    public long totalTicks() {
        long total = 0;
        for (PlannedWork work : works) {
            total = Math.addExact(total, work.estimatedTicks());
        }
        return total;
    }

    /** Sum of the costs actually shown: selected quote totals plus unquoted base costs. */
    public double totalDisplayCost() {
        double total = 0;
        for (PlannedWork work : works) {
            total += work.displayCost();
        }
        return total;
    }

    /** Sum of the times actually shown: selected quote times plus unquoted base estimates. */
    public long totalDisplayTicks() {
        long total = 0;
        for (PlannedWork work : works) {
            total = Math.addExact(total, work.displayTicks());
        }
        return total;
    }

    /** Removes every entry and resets the id sequence. Used when the model is swapped. */
    public void clear() {
        works.clear();
        nextId = 1;
    }

    /** Next id that {@link #add} will use; package visible for focused tests. */
    int nextId() {
        return nextId;
    }

    @Override
    public String toString() {
        return "ConstructionPlanList" + works;
    }
}
