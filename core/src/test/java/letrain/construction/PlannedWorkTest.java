package letrain.construction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import letrain.map.Point;
import letrain.mvp.Presenter.TrackType;
import letrain.planner.RoutePlan;
import letrain.planner.RoutePolicy;
import letrain.planner.RouteStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Planned works: immutable budget rows and list totals")
class PlannedWorkTest {

    private static RoutePlan plan(int cells, double cost, long ticks) {
        List<RouteStep> steps = java.util.stream.IntStream.range(0, cells)
                .mapToObj(x -> new RouteStep(new Point(x, 0), TrackType.NORMAL_TRACK)).toList();
        return RoutePlan.feasible(steps, cost, ticks);
    }

    @Test
    @DisplayName("a draft keeps ids, endpoints, policy, plan and estimate")
    void draft_exposesItsBudget() {
        PlannedWork work = PlannedWork.draft(7, new Point(1, 2), new Point(5, 6),
                RoutePolicy.AVOID_ALL, plan(5, 1_250.5, 40), 45);

        assertEquals(7, work.id());
        assertEquals(new Point(1, 2), work.start());
        assertEquals(new Point(5, 6), work.end());
        assertEquals(RoutePolicy.AVOID_ALL, work.policy());
        assertEquals(5, work.cells());
        assertEquals(1_250.5, work.cost());
        assertEquals(45, work.estimatedTicks());
        assertEquals(PlannedWork.State.DRAFT, work.state());
    }

    @Test
    @DisplayName("endpoints are copied, so mutating the source does not alter the work")
    void endpoints_areDefensivelyCopied() {
        Point start = new Point(1, 2);
        Point end = new Point(3, 4);
        PlannedWork work = PlannedWork.draft(1, start, end, RoutePolicy.DIRECT, plan(3, 10, 3), 6);

        start.setX(99);
        end.setY(99);

        assertEquals(new Point(1, 2), work.start());
        assertEquals(new Point(3, 4), work.end());
        work.start().setX(50);
        assertEquals(new Point(1, 2), work.start());
    }

    @Test
    @DisplayName("withPlan keeps identity and endpoints while replacing policy and budget")
    void withPlan_replacesOnlyThePlanSide() {
        PlannedWork original = PlannedWork.draft(3, new Point(0, 0), new Point(9, 9),
                RoutePolicy.DIRECT, plan(4, 100, 4), 8);

        PlannedWork updated = original.withPlan(RoutePolicy.CHEAPEST, plan(6, 80, 6), 12);

        assertEquals(3, updated.id());
        assertEquals(new Point(0, 0), updated.start());
        assertEquals(new Point(9, 9), updated.end());
        assertEquals(RoutePolicy.CHEAPEST, updated.policy());
        assertEquals(6, updated.cells());
        assertEquals(80, updated.cost());
        assertEquals(12, updated.estimatedTicks());
        assertEquals(PlannedWork.State.DRAFT, updated.state());
        assertEquals(RoutePolicy.DIRECT, original.policy(), "the original value must not mutate");
    }

    @Test
    @DisplayName("invalid ids, infeasible plans and negative estimates are rejected")
    void invalidValues_areRejected() {
        assertThrows(IllegalArgumentException.class, () -> PlannedWork.draft(0, new Point(0, 0),
                new Point(1, 0), RoutePolicy.DIRECT, plan(2, 1, 1), 2));
        assertThrows(IllegalArgumentException.class, () -> PlannedWork.draft(1, new Point(0, 0),
                new Point(1, 0), RoutePolicy.DIRECT, RoutePlan.infeasible("blocked"), 2));
        assertThrows(IllegalArgumentException.class, () -> PlannedWork.draft(1, new Point(0, 0),
                new Point(1, 0), RoutePolicy.DIRECT, plan(2, 1, 1), -1));
    }

    @Test
    @DisplayName("the list assigns stable ids, keeps insertion order and sums cost and time")
    void list_addsInOrder_andTotals() {
        ConstructionPlanList list = new ConstructionPlanList();

        PlannedWork first = list.add(new Point(0, 0), new Point(4, 0), RoutePolicy.DIRECT,
                plan(5, 100.25, 10), 15);
        PlannedWork second = list.add(new Point(10, 10), new Point(10, 14), RoutePolicy.AVOID_WATER,
                plan(5, 50.75, 20), 25);

        assertEquals(1, first.id());
        assertEquals(2, second.id());
        assertEquals(List.of(first, second), list.works());
        assertEquals(151.0, list.totalCost());
        assertEquals(40, list.totalTicks());
        assertEquals(2, list.size());
        assertFalse(list.isEmpty());
        assertEquals(3, list.nextId());
    }

    @Test
    @DisplayName("find and replacePlan address one row and keep the existing order")
    void replacePlan_updatesOnlyTheSelectedRow() {
        ConstructionPlanList list = new ConstructionPlanList();
        PlannedWork first = list.add(new Point(0, 0), new Point(4, 0), RoutePolicy.DIRECT,
                plan(5, 100, 10), 15);
        PlannedWork second = list.add(new Point(8, 8), new Point(12, 8), RoutePolicy.DIRECT,
                plan(5, 200, 20), 25);

        assertTrue(list.find(first.id()).isPresent());
        assertTrue(list.find(999).isEmpty());

        PlannedWork updated = list
                .replacePlan(first.id(), RoutePolicy.AVOID_ALL, plan(7, 90, 14), 21).orElseThrow();

        assertEquals(first.id(), updated.id());
        assertEquals(RoutePolicy.AVOID_ALL, updated.policy());
        assertEquals(7, updated.cells());
        assertEquals(List.of(updated, second), list.works());
        assertEquals(290, list.totalCost());
        assertEquals(46, list.totalTicks());
        assertTrue(list.replacePlan(999, RoutePolicy.DIRECT, plan(1, 0, 0), 1).isEmpty());
    }

    @Test
    @DisplayName("remove deletes by id without reusing it")
    void remove_deletesAndNeverReusesIds() {
        ConstructionPlanList list = new ConstructionPlanList();
        PlannedWork first =
                list.add(new Point(0, 0), new Point(1, 0), RoutePolicy.DIRECT, plan(2, 10, 2), 3);
        PlannedWork second =
                list.add(new Point(2, 0), new Point(3, 0), RoutePolicy.DIRECT, plan(2, 20, 2), 3);

        assertFalse(list.remove(999));
        assertTrue(list.remove(first.id()));
        assertFalse(list.remove(first.id()));
        assertEquals(List.of(second), list.works());
        assertEquals(20, list.totalCost());
        assertEquals(3, list.totalTicks());

        PlannedWork third =
                list.add(new Point(4, 0), new Point(5, 0), RoutePolicy.DIRECT, plan(2, 30, 2), 3);
        assertEquals(3, third.id());
    }

    @Test
    @DisplayName("a rejected add does not consume an id")
    void rejectedAdd_doesNotConsumeId() {
        ConstructionPlanList list = new ConstructionPlanList();

        assertThrows(IllegalArgumentException.class, () -> list.add(new Point(0, 0),
                new Point(1, 0), RoutePolicy.DIRECT, RoutePlan.infeasible("blocked"), 2));
        assertEquals(1, list
                .add(new Point(0, 0), new Point(1, 0), RoutePolicy.DIRECT, plan(2, 10, 2), 3).id());
    }

    @Test
    @DisplayName("withQuote stores the selected company quote and changes only displayed cost/time")
    void withQuote_storesTheCompanyOffer() {
        PlannedWork original = PlannedWork.draft(3, new Point(0, 0), new Point(4, 0),
                RoutePolicy.DIRECT, plan(5, 100, 10), 15);
        WorkQuote quote = WorkQuote.quote(ConstructionCompanyCatalog.ARTESANA, original.plan());

        PlannedWork quoted = original.withQuote(quote);

        assertTrue(quoted.quote().isPresent());
        assertEquals("artesana", quoted.quote().orElseThrow().company().id());
        assertEquals(160, quoted.quote().orElseThrow().totalCost());
        assertEquals(24, quoted.quote().orElseThrow().totalTicks());
        assertEquals(100, quoted.cost(), "the base plan cost stays available");
        assertEquals(15, quoted.estimatedTicks(), "the base estimate stays available");
        assertTrue(original.quote().isEmpty(), "the original value must not mutate");
    }

    @Test
    @DisplayName("a built or cancelled work keeps its route and quote in the new state")
    void withState_changesOnlyTheLifecycleState() {
        PlannedWork original = PlannedWork.draft(3, new Point(0, 0), new Point(4, 0),
                RoutePolicy.DIRECT, plan(5, 100, 10), 15);
        WorkQuote quote = WorkQuote.quote(ConstructionCompanyCatalog.ARTESANA, original.plan());
        PlannedWork quoted = original.withQuote(quote);

        PlannedWork done = quoted.withState(PlannedWork.State.DONE);
        PlannedWork cancelled = quoted.withState(PlannedWork.State.CANCELLED);

        assertEquals(PlannedWork.State.DONE, done.state());
        assertEquals(PlannedWork.State.CANCELLED, cancelled.state());
        assertEquals(quoted.plan(), done.plan());
        assertEquals(quoted.quote(), done.quote());
    }

    @Test
    @DisplayName("a cancelled work can record the actual cash spent")
    void withSpentCost_recordsCancellationCost() {
        PlannedWork work =
                PlannedWork
                        .draft(3, new Point(0, 0), new Point(4, 0), RoutePolicy.DIRECT,
                                plan(5, 100, 10), 15)
                        .withState(PlannedWork.State.CANCELLED).withSpentCost(42.5);

        assertEquals(42.5, work.spentCost().orElseThrow());
        assertEquals(PlannedWork.State.CANCELLED, work.state());
        assertThrows(IllegalArgumentException.class, () -> work.withSpentCost(-1));
    }

    @Test
    @DisplayName("changing the plan drops the old quote; a quote from another plan is rejected")
    void quote_isDroppedWhenThePlanChanges() {
        PlannedWork original = PlannedWork.draft(3, new Point(0, 0), new Point(4, 0),
                RoutePolicy.DIRECT, plan(5, 100, 10), 15);
        WorkQuote quote = WorkQuote.quote(ConstructionCompanyCatalog.ARTESANA, original.plan());
        PlannedWork quoted = original.withQuote(quote);

        PlannedWork changed = quoted.withPlan(RoutePolicy.CHEAPEST, plan(6, 80, 6), 12);

        assertTrue(changed.quote().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> quoted
                .withQuote(WorkQuote.quote(ConstructionCompanyCatalog.ARTESANA, plan(99, 1, 1))));
        assertThrows(IllegalArgumentException.class,
                () -> quoted.withQuote(WorkQuote.quote(ConstructionCompanyCatalog.CHAPUCERT,
                        RoutePlan.feasible(
                                List.of(new RouteStep(new Point(0, 0), TrackType.BRIDGE_TRACK)), 1,
                                1))));
    }

    @Test
    @DisplayName("the list replaces a quote and totals the displayed cost/time")
    void list_replacesQuote_andTotalsDisplayedValues() {
        ConstructionPlanList list = new ConstructionPlanList();
        PlannedWork first = list.add(new Point(0, 0), new Point(4, 0), RoutePolicy.DIRECT,
                plan(5, 100, 10), 15);
        WorkQuote quote = WorkQuote.quote(ConstructionCompanyCatalog.CHAPUCERT, first.plan());

        assertTrue(list.replaceQuote(first.id(), quote).isPresent());

        assertEquals(1, list.quotedCount());
        assertEquals(70, list.totalQuotedCost());
        assertEquals(27, list.totalQuotedTicks());
        assertEquals(100, list.totalCost());
        assertEquals(15, list.totalTicks());
        assertTrue(list.replaceQuote(999, quote).isEmpty());

        list.markCancelled(first.id(), 42);
        assertEquals(42, list.totalListedCost());
        assertEquals(PlannedWork.State.CANCELLED, list.find(first.id()).orElseThrow().state());
    }

    @Test
    @DisplayName("clear empties the list and resets the id sequence")
    void clear_resetsTheList() {
        ConstructionPlanList list = new ConstructionPlanList();
        list.add(new Point(0, 0), new Point(1, 0), RoutePolicy.DIRECT, plan(2, 10, 2), 3);

        list.clear();

        assertTrue(list.isEmpty());
        assertEquals(0, list.totalCost());
        assertEquals(0, list.totalTicks());
        assertEquals(1, list
                .add(new Point(0, 0), new Point(1, 0), RoutePolicy.DIRECT, plan(2, 10, 2), 3).id());
    }
}
