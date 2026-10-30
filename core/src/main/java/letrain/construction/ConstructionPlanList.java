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
 * It stores planned works and keeps the total budget. A work may carry one selected company quote;
 * completed and cancelled works remain visible until deleted. The order is the insertion order and
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

    /** Changes the lifecycle state of the work with {@code id}. */
    public Optional<PlannedWork> replaceState(int id, PlannedWork.State state) {
        for (int index = 0; index < works.size(); index++) {
            PlannedWork current = works.get(index);
            if (current.id() == id) {
                PlannedWork updated = current.withState(state);
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

    /** Number of works that already have a company quote. */
    public long quotedCount() {
        return works.stream().filter(work -> work.quote().isPresent()).count();
    }

    /**
     * Sum of selected quote costs only; unquoted works contribute nothing until a company is
     * chosen.
     */
    public double totalQuotedCost() {
        return works.stream().flatMap(work -> work.quote().stream())
                .mapToDouble(WorkQuote::totalCost).sum();
    }

    /**
     * Sum of selected quote times only; unquoted works contribute nothing until a company is
     * chosen.
     */
    public long totalQuotedTicks() {
        long total = 0;
        for (PlannedWork work : works) {
            if (work.quote().isPresent()) {
                total = Math.addExact(total, work.quote().orElseThrow().totalTicks());
            }
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
