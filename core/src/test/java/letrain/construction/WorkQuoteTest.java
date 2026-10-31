package letrain.construction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import letrain.construction.ConstructionCompany.Capability;
import letrain.construction.ConstructionCompany.Quality;
import letrain.economy.EconomyManager;
import letrain.economy.EconomyManager.ExpenseType;
import letrain.map.Point;
import letrain.mvp.Presenter.TrackType;
import letrain.planner.RoutePlan;
import letrain.planner.RouteStep;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Construction companies: profiles and quotes")
class WorkQuoteTest {

    private static RoutePlan plan(TrackType... types) {
        List<RouteStep> steps = new java.util.ArrayList<>();
        for (int i = 0; i < types.length; i++) {
            steps.add(new RouteStep(new Point(i, 0), types[i]));
        }
        return RoutePlan.feasible(steps, 100, 2);
    }

    @Test
    @DisplayName("the catalogue has the four agreed profiles and the two cheap companies lack structures")
    void catalogue_hasTheAgreedProfiles() {
        assertEquals(4, ConstructionCompanyCatalog.all().size());
        assertEquals(2, ConstructionCompanyCatalog.all().stream()
                .filter(company -> company.quality() == Quality.LOW).count());
        assertFalse(ConstructionCompanyCatalog.CHAPUCERT.supports(Capability.BRIDGE));
        assertFalse(ConstructionCompanyCatalog.CHAPUCERT.supports(Capability.TUNNEL));
        assertFalse(ConstructionCompanyCatalog.RAPIDEX.supports(Capability.BRIDGE));
        assertTrue(ConstructionCompanyCatalog.ARTESANA.supports(Capability.BRIDGE));
        assertTrue(ConstructionCompanyCatalog.ARTESANA.supports(Capability.TUNNEL));
        assertTrue(ConstructionCompanyCatalog.IMPERIAL.supports(Capability.BRIDGE));
        assertTrue(ConstructionCompanyCatalog.IMPERIAL.supports(Capability.TUNNEL));
    }

    @Test
    @DisplayName("a normal route can be quoted by every company and applies cost/time factors")
    void normalRoute_isQuotedByEveryone() {
        RoutePlan plan = plan(TrackType.NORMAL_TRACK, TrackType.NORMAL_TRACK);

        WorkQuote cheap = WorkQuote.quote(ConstructionCompanyCatalog.CHAPUCERT, plan);
        WorkQuote fast = WorkQuote.quote(ConstructionCompanyCatalog.RAPIDEX, plan);

        assertTrue(cheap.feasible());
        assertEquals(70, cheap.totalCost());
        assertEquals(7, cheap.totalTicks());
        assertEquals(95, fast.totalCost());
        assertEquals(3, fast.totalTicks());
        assertTrue(cheap.requiredCapabilities().isEmpty());
    }

    @Test
    @DisplayName("per-step quote costs support exact cancellation refunds")
    void stepCosts_areAvailableWhenAnEconomyIsSupplied() {
        EconomyManager economy = mock(EconomyManager.class);
        when(economy.getCost(ExpenseType.CONSTRUCTED_NORMAL_RAIL_TRACK)).thenReturn(100f);
        RoutePlan plan = plan(TrackType.NORMAL_TRACK, TrackType.NORMAL_TRACK);

        WorkQuote quote = WorkQuote.quote(ConstructionCompanyCatalog.ARTESANA, plan, 1, economy);

        assertEquals(160, quote.costForStep(0));
        assertEquals(160, quote.costForStep(1));
        assertEquals(320, quote.totalCost());
        assertEquals(160,
                WorkQuote.quote(ConstructionCompanyCatalog.ARTESANA, plan(TrackType.NORMAL_TRACK))
                        .costForStep(0));
    }

    @Test
    @DisplayName("a route with a bridge needs a company with bridge capability")
    void bridgeRoute_requiresBridgeCapability() {
        RoutePlan plan = plan(TrackType.NORMAL_TRACK, TrackType.BRIDGE_TRACK);

        WorkQuote cheap = WorkQuote.quote(ConstructionCompanyCatalog.CHAPUCERT, plan);
        WorkQuote capable = WorkQuote.quote(ConstructionCompanyCatalog.ARTESANA, plan);

        assertFalse(cheap.feasible());
        assertTrue(cheap.reason().orElseThrow().contains("bridges"));
        assertEquals(Set.of(Capability.BRIDGE), cheap.requiredCapabilities());
        assertTrue(capable.feasible());
    }

    @Test
    @DisplayName("a route with a tunnel needs tunnel capability")
    void tunnelRoute_requiresTunnelCapability() {
        RoutePlan plan = plan(TrackType.TUNNEL_TRACK);

        WorkQuote cheap = WorkQuote.quote(ConstructionCompanyCatalog.RAPIDEX, plan);
        WorkQuote capable = WorkQuote.quote(ConstructionCompanyCatalog.IMPERIAL, plan);

        assertFalse(cheap.feasible());
        assertTrue(cheap.reason().orElseThrow().contains("tunnels"));
        assertEquals(Set.of(Capability.TUNNEL), cheap.requiredCapabilities());
        assertTrue(capable.feasible());
    }

    @Test
    @DisplayName("infeasible routes cannot produce a feasible quote")
    void infeasiblePlan_isRejected() {
        WorkQuote quote = WorkQuote.quote(ConstructionCompanyCatalog.IMPERIAL,
                RoutePlan.infeasible("blocked"));

        assertFalse(quote.feasible());
        assertTrue(quote.reason().orElseThrow().contains("not feasible"));
        assertEquals(0, quote.totalCost());
        assertEquals(0, quote.totalTicks());
    }

    @Test
    @DisplayName("invalid companies and negative base delays are rejected")
    void invalidInputs_areRejected() {
        assertThrows(NullPointerException.class,
                () -> WorkQuote.quote(null, plan(TrackType.NORMAL_TRACK)));
        assertThrows(NullPointerException.class,
                () -> WorkQuote.quote(ConstructionCompanyCatalog.IMPERIAL, null));
        assertThrows(IllegalArgumentException.class, () -> WorkQuote
                .quote(ConstructionCompanyCatalog.IMPERIAL, plan(TrackType.NORMAL_TRACK), -1));
        assertThrows(IllegalArgumentException.class,
                () -> new ConstructionCompany(" ", "X", 1, 1, Quality.LOW, Set.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new ConstructionCompany("x", "X", 0, 1, Quality.LOW, Set.of()));
    }
}
