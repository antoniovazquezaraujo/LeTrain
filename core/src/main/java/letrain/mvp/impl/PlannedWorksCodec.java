package letrain.mvp.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.List;
import letrain.construction.ConstructionCompany;
import letrain.construction.ConstructionCompanyCatalog;
import letrain.construction.ConstructionPlanList;
import letrain.construction.PlannedWork;
import letrain.construction.WorkQuote;
import letrain.economy.EconomyManager;
import letrain.map.Point;
import letrain.planner.RoutePlan;
import letrain.planner.RoutePolicy;
import letrain.planner.RouteStep;

/**
 * JSON codec for the player's planned-works list. It is deliberately separate from the domain
 * objects: {@link PlannedWork}, {@link RoutePlan} and {@link WorkQuote} stay immutable and do not
 * learn Jackson annotations. {@link GameSaveService} embeds this object as the {@code plannedWorks}
 * section of a savegame.
 */
public final class PlannedWorksCodec {

    private PlannedWorksCodec() {}

    /**
     * Serializes a list. Active automatic works cannot be saved because their queue is UI-owned.
     */
    public static ObjectNode encode(ConstructionPlanList list, ObjectMapper mapper) {
        ObjectNode root = mapper.createObjectNode();
        root.put("nextId", list.nextId());
        ArrayNode works = root.putArray("works");
        for (PlannedWork work : list.works()) {
            if (work.state() == PlannedWork.State.WORKING) {
                throw new IllegalArgumentException(
                        "cannot save an active automatic work; finish or cancel it first");
            }
            ObjectNode json = works.addObject();
            json.put("id", work.id());
            putPoint(json.putObject("start"), work.start());
            putPoint(json.putObject("end"), work.end());
            json.put("policy", work.policy().name());
            json.put("estimatedTicks", work.estimatedTicks());
            json.put("state", work.state().name());
            ArrayNode steps = json.putArray("steps");
            for (RouteStep step : work.plan().steps()) {
                ObjectNode stepJson = steps.addObject();
                putPoint(stepJson, step.point());
                stepJson.put("trackType", step.trackType().name());
            }
            json.put("totalCost", work.plan().totalCost());
            json.put("totalTicks", work.plan().totalConstructionTicks());
            work.spentCost().ifPresent(spent -> json.put("spentCost", spent));
            work.quote().ifPresent(quote -> {
                json.put("quoteCompanyId", quote.company().id());
                json.put("quoteCost", quote.totalCost());
                json.put("quoteTicks", quote.totalTicks());
                ArrayNode costs = json.putArray("quoteStepCosts");
                for (int index = 0; index < work.plan().steps().size(); index++) {
                    costs.add(quote.costForStep(index));
                }
            });
        }
        return root;
    }

    /** Restores a decoded section into a fresh (or reused) list. */
    public static void decode(JsonNode node, ConstructionPlanList target, EconomyManager economy) {
        if (node == null || node.isNull()) {
            return;
        }
        List<PlannedWork> works = new ArrayList<>();
        JsonNode array = node.get("works");
        if (array != null && array.isArray()) {
            for (JsonNode work : array) {
                works.add(decodeWork(work, economy));
            }
        }
        target.restore(works, node.path("nextId").asInt(1));
    }

    private static void putPoint(ObjectNode node, Point point) {
        node.put("x", point.getX());
        node.put("y", point.getY());
    }

    private static Point readPoint(JsonNode node, String field) {
        JsonNode point = node.get(field);
        if (point == null || !point.has("x") || !point.has("y")) {
            throw new IllegalArgumentException("planned work has no valid " + field);
        }
        return new Point(point.get("x").asInt(), point.get("y").asInt());
    }

    private static PlannedWork decodeWork(JsonNode json, EconomyManager economy) {
        if (json == null || !json.isObject()) {
            throw new IllegalArgumentException("planned work must be an object");
        }
        if (!json.hasNonNull("id") || !json.hasNonNull("policy") || !json.hasNonNull("steps")
                || !json.hasNonNull("totalCost") || !json.hasNonNull("totalTicks")) {
            throw new IllegalArgumentException("planned work is missing required fields");
        }
        int id = json.get("id").asInt();
        Point start = readPoint(json, "start");
        Point end = readPoint(json, "end");
        RoutePolicy policy = RoutePolicy.valueOf(json.get("policy").asText());
        List<RouteStep> steps = new ArrayList<>();
        for (JsonNode step : json.get("steps")) {
            steps.add(new RouteStep(new Point(step.path("x").asInt(), step.path("y").asInt()),
                    letrain.mvp.Presenter.TrackType.valueOf(step.get("trackType").asText())));
        }
        RoutePlan plan = RoutePlan.feasible(steps, json.get("totalCost").asDouble(),
                json.get("totalTicks").asLong());
        long estimatedTicks = json.path("estimatedTicks").asLong(plan.totalConstructionTicks());
        PlannedWork.State state = PlannedWork.State
                .valueOf(json.path("state").asText(PlannedWork.State.DRAFT.name()));
        PlannedWork work = new PlannedWork(id, start, end, policy, plan, estimatedTicks, state);
        work = restoreQuote(json, work, economy);
        if (json.hasNonNull("spentCost")) {
            work = work.withSpentCost(json.get("spentCost").asDouble());
        }
        return work;
    }

    private static PlannedWork restoreQuote(JsonNode json, PlannedWork work,
            EconomyManager economy) {
        String companyId = json.path("quoteCompanyId").asText("");
        if (companyId.isBlank()) {
            return work;
        }
        ConstructionCompany company = ConstructionCompanyCatalog.byId(companyId).orElse(null);
        if (company == null) {
            return work;
        }
        if (json.hasNonNull("quoteCost") && json.hasNonNull("quoteTicks")) {
            List<Double> costs = new ArrayList<>();
            JsonNode stepCosts = json.get("quoteStepCosts");
            if (stepCosts != null && stepCosts.isArray()) {
                for (JsonNode cost : stepCosts) {
                    costs.add(cost.asDouble());
                }
            }
            WorkQuote quote = WorkQuote.restore(company, work.plan(), costs,
                    json.get("quoteCost").asDouble(), json.get("quoteTicks").asLong());
            return work.withQuote(quote);
        }
        WorkQuote quote = WorkQuote.quote(company, work.plan(),
                WorkQuote.DEFAULT_BASE_DELAY_TICKS_PER_CELL, economy);
        return quote.feasible() ? work.withQuote(quote) : work;
    }
}
