package letrain.mvp.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.File;
import java.util.List;
import letrain.construction.ConstructionCompanyCatalog;
import letrain.construction.ConstructionPlanList;
import letrain.construction.PlannedWork;
import letrain.construction.WorkQuote;
import letrain.map.Point;
import letrain.mvp.Presenter.TrackType;
import letrain.planner.RoutePlan;
import letrain.planner.RoutePolicy;
import letrain.planner.RouteStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Savegame: the planning list travels with the model")
class PlannedWorksPersistenceTest {

    private static RoutePlan plan(int x, int y, double cost, long ticks) {
        return RoutePlan.feasible(List.of(new RouteStep(new Point(x, y), TrackType.NORMAL_TRACK),
                new RouteStep(new Point(x + 1, y), TrackType.NORMAL_TRACK)), cost, ticks);
    }

    @Test
    @DisplayName("drafts, quotes and cancellation spends survive a save/load round trip")
    void plannedWorks_roundTrip() {
        Model model = new Model(7);
        ConstructionPlanList list = model.getPlannedWorks();
        RoutePlan firstPlan = plan(0, 0, 100, 12);
        WorkQuote quote = WorkQuote.quote(ConstructionCompanyCatalog.IMPERIAL, firstPlan);
        PlannedWork first =
                list.add(new Point(0, 0), new Point(1, 0), RoutePolicy.DIRECT, firstPlan, 12);
        list.replaceQuote(first.id(), quote);
        PlannedWork second = list.add(new Point(2, 0), new Point(3, 0), RoutePolicy.CHEAPEST,
                plan(2, 0, 50, 8), 8);
        list.markCancelled(second.id(), 42.5);
        int nextId = list.nextId();

        GameSaveService saves = new GameSaveService();
        Model loaded = saves.fromBytes(saves.toBytes(model));

        assertNotNull(loaded);
        ConstructionPlanList restored = loaded.getPlannedWorks();
        assertEquals(2, restored.size());
        assertEquals(nextId, restored.nextId());
        PlannedWork loadedFirst = restored.find(first.id()).orElseThrow();
        assertEquals(PlannedWork.State.DRAFT, loadedFirst.state());
        assertEquals(RoutePolicy.DIRECT, loadedFirst.policy());
        assertEquals(quote.company().id(), loadedFirst.quote().orElseThrow().company().id());
        assertEquals(quote.totalCost(), loadedFirst.quote().orElseThrow().totalCost(), 0.001);
        assertEquals(quote.totalTicks(), loadedFirst.quote().orElseThrow().totalTicks());
        PlannedWork loadedSecond = restored.find(second.id()).orElseThrow();
        assertEquals(PlannedWork.State.CANCELLED, loadedSecond.state());
        assertEquals(42.5, loadedSecond.spentCost().orElseThrow(), 0.001);
    }

    @Test
    @DisplayName("a savegame file carries the planning list through GameSaveService.save/load")
    void plannedWorks_surviveFileRoundTrip() throws Exception {
        Model model = new Model(9);
        model.getPlannedWorks().add(new Point(0, 0), new Point(1, 0), RoutePolicy.DIRECT,
                plan(0, 0, 10, 1), 1);

        File file = File.createTempFile("letrain-planned-works", ".json");
        try {
            GameSaveService saves = new GameSaveService();
            assertTrue(saves.save(model, file));
            Model loaded = saves.load(file).orElseThrow();
            assertEquals(1, loaded.getPlannedWorks().size());
            assertEquals(RoutePolicy.DIRECT, loaded.getPlannedWorks().works().get(0).policy());
        } finally {
            file.delete();
        }
    }

    @Test
    @DisplayName("the JSON carries the planning section and never exposes it twice")
    void plannedWorks_areWrittenAsOneSection() throws Exception {
        Model model = new Model(3);
        model.getPlannedWorks().add(new Point(0, 0), new Point(1, 0), RoutePolicy.DIRECT,
                plan(0, 0, 10, 1), 1);

        ObjectNode tree = (ObjectNode) new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(new GameSaveService().toBytes(model));

        assertTrue(tree.has("plannedWorks"), tree.toPrettyString());
        assertEquals(1, tree.path("plannedWorks").path("works").size());
        assertFalse(tree.path("plannedWorks").path("works").get(0).has("plannedWorks"));
    }

    @Test
    @DisplayName("a save without the planning section loads with an empty list (legacy save)")
    void legacySave_hasEmptyPlanningList() throws Exception {
        Model model = new Model(4);
        ObjectNode tree = (ObjectNode) new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(new GameSaveService().toBytes(model));
        tree.remove("plannedWorks");

        GameSaveService saves = new GameSaveService();
        Model loaded = saves
                .load(new java.io.ByteArrayInputStream(
                        new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(tree)))
                .orElseThrow();

        assertTrue(loaded.getPlannedWorks().isEmpty());
        assertEquals(1, loaded.getPlannedWorks().nextId());
    }
}
