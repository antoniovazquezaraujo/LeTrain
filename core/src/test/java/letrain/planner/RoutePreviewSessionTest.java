package letrain.planner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import letrain.economy.EconomyManager;
import letrain.economy.EconomyManager.ExpenseType;
import letrain.ground.GroundMap;
import letrain.map.Point;
import letrain.mvp.Presenter.TrackType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RoutePreviewSessionTest {

    private static final int NORMAL = GroundMap.GROUND;
    private static final int WATER = GroundMap.WATER;

    private static EconomyManager economy() {
        EconomyManager economy = mock(EconomyManager.class);
        when(economy.getCost(ExpenseType.CONSTRUCTED_NORMAL_RAIL_TRACK)).thenReturn(100f);
        when(economy.getCost(ExpenseType.CONSTRUCTED_BRIDGE_RAIL_TRACK)).thenReturn(20_000f);
        when(economy.getCost(ExpenseType.CONSTRUCTED_TUNNEL_RAIL_TRACK)).thenReturn(70_000f);
        when(economy.getConstructionDelay(TrackType.NORMAL_TRACK)).thenReturn(0);
        when(economy.getConstructionDelay(TrackType.BRIDGE_TRACK)).thenReturn(20);
        when(economy.getConstructionDelay(TrackType.TUNNEL_TRACK)).thenReturn(30);
        return economy;
    }

    private static Point p(int x, int y) {
        return new Point(x, y);
    }

    private static RoutePreviewSession previewOn(FakeGroundMap terrain) {
        RoutePreviewSession session = new RoutePreviewSession(terrain, economy());
        session.begin(p(0, 0));
        session.enter(p(0, 0));
        assertEquals(RoutePreviewSession.State.PLANNING, session.enter(p(3, 0)));
        awaitPreview(session);
        return session;
    }

    /**
     * Polls until the session leaves PLANNING. The timeout keeps the test honest without arbitrary
     * sleeps; route planning on the tiny fake maps normally completes in microseconds.
     */
    private static void awaitPreview(RoutePreviewSession session) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            session.poll();
            if (session.state() != RoutePreviewSession.State.PLANNING) {
                assertEquals(RoutePreviewSession.State.PREVIEW, session.state());
                return;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
        fail("session did not reach PREVIEW before the timeout; state=" + session.state());
    }

    @Test
    @DisplayName("happy path picks both endpoints, previews and selects with one Enter")
    void should_SelectPlan_When_HappyPathIsFollowed() {
        FakeGroundMap terrain = new FakeGroundMap().rect(-1, -1, 4, 1, NORMAL);
        RoutePreviewSession session = new RoutePreviewSession(terrain, economy());

        assertEquals(RoutePreviewSession.State.CANCELLED, session.state());

        assertEquals(RoutePreviewSession.State.PICK_START, session.begin(p(0, 0)));
        assertTrue(session.start().isEmpty());

        assertEquals(RoutePreviewSession.State.PICK_END, session.enter(p(0, 0)));
        assertEquals(p(0, 0), session.start().orElseThrow());
        assertTrue(session.end().isEmpty());
        assertEquals(RoutePolicy.CHEAPEST, session.policy());

        assertEquals(RoutePreviewSession.State.PLANNING, session.enter(p(3, 0)));
        assertEquals(p(3, 0), session.end().orElseThrow());
        assertEquals(0, session.effectiveConstructionTicks());
        awaitPreview(session);
        assertTrue(session.lastPlan().orElseThrow().feasible());
        assertEquals(4, session.lastPlan().orElseThrow().steps().size());
        assertEquals(4, session.effectiveConstructionTicks());
        assertTrue(session.planningProgress().isEmpty());
        assertTrue(session.planningSnapshot().isEmpty());

        assertEquals(RoutePreviewSession.State.SELECTED, session.enter(p(3, 0)));
        assertEquals(session.lastPlan(), session.selectedPlan());

        // A second Enter in a closed state is a no-op.
        assertEquals(RoutePreviewSession.State.SELECTED, session.enter(p(1, 1)));
    }

    @Test
    @DisplayName("beginEdit starts in PREVIEW with the saved plan and endpoints")
    void should_StartInPreview_When_EditingSavedPlan() {
        FakeGroundMap terrain = new FakeGroundMap().rect(-1, -1, 4, 1, NORMAL);
        RoutePreviewSession preview = previewOn(terrain);
        RoutePlan plan = preview.lastPlan().orElseThrow();

        RoutePreviewSession session = new RoutePreviewSession(terrain, economy());
        assertEquals(RoutePreviewSession.State.PREVIEW,
                session.beginEdit(p(0, 0), p(3, 0), RoutePolicy.CHEAPEST, plan));

        assertEquals(p(0, 0), session.start().orElseThrow());
        assertEquals(p(3, 0), session.end().orElseThrow());
        assertEquals(RoutePolicy.CHEAPEST, session.policy());
        assertEquals(plan, session.lastPlan().orElseThrow());
        assertEquals(4, session.effectiveConstructionTicks());
        assertTrue(session.selectedPlan().isEmpty());
        assertTrue(session.planningProgress().isEmpty());
        assertEquals(RoutePreviewSession.State.SELECTED, session.enter(p(1, 1)));
        assertEquals(plan, session.selectedPlan().orElseThrow());
    }

    @Test
    @DisplayName("beginEdit rejects null values and infeasible plans")
    void should_RejectInvalidEditSeed() {
        FakeGroundMap terrain = new FakeGroundMap().rect(-1, -1, 4, 1, NORMAL);
        RoutePreviewSession session = new RoutePreviewSession(terrain, economy());
        RoutePlan plan = RoutePlan.feasible(List.of(), 0, 0);

        assertThrows(NullPointerException.class,
                () -> session.beginEdit(null, p(1, 1), RoutePolicy.DIRECT, plan));
        assertThrows(NullPointerException.class,
                () -> session.beginEdit(p(0, 0), null, RoutePolicy.DIRECT, plan));
        assertThrows(NullPointerException.class,
                () -> session.beginEdit(p(0, 0), p(1, 1), null, plan));
        assertThrows(NullPointerException.class,
                () -> session.beginEdit(p(0, 0), p(1, 1), RoutePolicy.DIRECT, null));
        assertThrows(IllegalArgumentException.class, () -> session.beginEdit(p(0, 0), p(1, 1),
                RoutePolicy.DIRECT, RoutePlan.infeasible("blocked")));
    }

    @Test
    @DisplayName("an invalid end point reaches PREVIEW with the planner reason")
    void should_ShowInfeasiblePlan_When_EndPointIsNotBuildable() {
        FakeGroundMap terrain = new FakeGroundMap().rect(-1, -1, 4, 1, NORMAL).put(3, 0, WATER);
        RoutePreviewSession session = new RoutePreviewSession(terrain, economy());
        session.begin(p(0, 0));
        session.enter(p(0, 0));

        assertEquals(RoutePreviewSession.State.PLANNING, session.enter(p(3, 0)));
        awaitPreview(session);
        assertFalse(session.lastPlan().orElseThrow().feasible());
        assertTrue(session.lastMessage().orElseThrow().contains("End"));
        assertTrue(session.selectedPlan().isEmpty());
        assertEquals(0, session.effectiveConstructionTicks());
    }

    @Test
    @DisplayName("an unreachable destination reaches PREVIEW with the planner reason")
    void should_ShowInfeasiblePlan_When_NoRouteExists() {
        // Two islands separated by cells with no terrain data (blocked for every policy).
        FakeGroundMap terrain =
                new FakeGroundMap().rect(0, 0, 2, 0, NORMAL).rect(4, 0, 6, 0, NORMAL);
        RoutePreviewSession session = new RoutePreviewSession(terrain, economy());
        session.begin(p(1, 0));
        session.enter(p(1, 0));

        assertEquals(RoutePreviewSession.State.PLANNING, session.enter(p(5, 0)));
        awaitPreview(session);
        assertFalse(session.lastPlan().orElseThrow().feasible());
        assertTrue(session.lastMessage().orElseThrow().contains("No route"));
    }

    @Test
    @DisplayName("policy cycle follows the owner order, wraps and recomputes the plan")
    void should_CyclePoliciesAndRecompute_When_InPreview() {
        FakeGroundMap terrain = new FakeGroundMap().rect(-1, -1, 4, 1, NORMAL).put(1, 0, WATER);
        RoutePreviewSession session = previewOn(terrain);
        assertEquals(RoutePreviewSession.State.PREVIEW, session.state());

        RoutePlan cheapest = session.lastPlan().orElseThrow();
        assertTrue(cheapest.steps().stream()
                .noneMatch(step -> step.trackType() == TrackType.BRIDGE_TRACK));

        assertEquals(RoutePolicy.DIRECT, session.nextPolicy());
        assertEquals(RoutePreviewSession.State.PLANNING, session.state());
        awaitPreview(session);
        RoutePlan direct = session.lastPlan().orElseThrow();
        assertNotEquals(cheapest, direct);
        assertTrue(direct.steps().stream()
                .anyMatch(step -> step.trackType() == TrackType.BRIDGE_TRACK));

        assertEquals(RoutePolicy.AVOID_MOUNTAINS, session.nextPolicy());
        awaitPreview(session);
        assertEquals(RoutePolicy.AVOID_WATER, session.nextPolicy());
        awaitPreview(session);
        assertEquals(RoutePolicy.AVOID_ALL, session.nextPolicy());
        awaitPreview(session);
        assertEquals(RoutePolicy.CHEAPEST, session.nextPolicy());
        awaitPreview(session);

        assertEquals(RoutePolicy.AVOID_ALL, session.previousPolicy());
        awaitPreview(session);
        assertEquals(RoutePolicy.AVOID_WATER, session.previousPolicy());
        awaitPreview(session);
        assertEquals(RoutePolicy.AVOID_MOUNTAINS, session.previousPolicy());
        awaitPreview(session);
        assertEquals(RoutePolicy.DIRECT, session.previousPolicy());
        awaitPreview(session);
        assertEquals(RoutePolicy.CHEAPEST, session.previousPolicy());
        assertEquals(RoutePreviewSession.State.PREVIEW, session.state(),
                "a cached policy must be shown immediately");
    }

    @Test
    @DisplayName("returning to a cached policy does not start another job")
    void should_ShowCachedPlanImmediately_When_PolicyWasAlreadyCalculated() {
        FakeGroundMap terrain = new FakeGroundMap().rect(-1, -1, 4, 1, NORMAL).put(1, 0, WATER);
        RoutePreviewSession session = previewOn(terrain);
        RoutePlan cheapest = session.lastPlan().orElseThrow();

        assertEquals(RoutePolicy.DIRECT, session.nextPolicy());
        awaitPreview(session);
        assertEquals(RoutePolicy.CHEAPEST, session.previousPolicy());

        assertEquals(RoutePreviewSession.State.PREVIEW, session.state());
        assertEquals(cheapest, session.lastPlan().orElseThrow());
        assertTrue(session.planningSnapshot().isEmpty());
    }

    @Test
    @DisplayName("policy changes are ignored outside PREVIEW")
    void should_IgnorePolicyChanges_When_NotInPreview() {
        FakeGroundMap terrain = new FakeGroundMap().rect(-1, -1, 4, 1, NORMAL);
        RoutePreviewSession session = new RoutePreviewSession(terrain, economy());

        assertEquals(RoutePolicy.CHEAPEST, session.nextPolicy());
        assertEquals(RoutePolicy.CHEAPEST, session.previousPolicy());

        session.begin(p(0, 0));
        assertEquals(RoutePolicy.CHEAPEST, session.nextPolicy());
        assertEquals(RoutePolicy.CHEAPEST, session.previousPolicy());
    }

    @Test
    @DisplayName("an infeasible policy stays in PREVIEW and cannot be selected")
    void should_NotSelect_When_ActivePolicyHasNoRoute() {
        // A closed wall of water and rock makes AVOID_ALL infeasible while CHEAPEST crosses it.
        FakeGroundMap terrain = new FakeGroundMap().rect(-1, -1, 6, 1, NORMAL);
        for (int y = -1; y <= 1; y++) {
            terrain.put(2, y, WATER);
            terrain.put(3, y, GroundMap.ROCK);
        }
        RoutePreviewSession session = new RoutePreviewSession(terrain, economy());
        session.begin(p(0, 0));
        session.enter(p(0, 0));
        assertEquals(RoutePreviewSession.State.PLANNING, session.enter(p(5, 0)));
        awaitPreview(session);

        assertEquals(RoutePolicy.DIRECT, session.nextPolicy());
        awaitPreview(session);
        assertEquals(RoutePolicy.AVOID_MOUNTAINS, session.nextPolicy());
        awaitPreview(session);
        assertEquals(RoutePolicy.AVOID_WATER, session.nextPolicy());
        awaitPreview(session);
        assertEquals(RoutePolicy.AVOID_ALL, session.nextPolicy());
        awaitPreview(session);
        assertEquals(RoutePreviewSession.State.PREVIEW, session.state());
        assertFalse(session.lastPlan().orElseThrow().feasible());
        assertTrue(session.lastMessage().orElseThrow().contains("AVOID_ALL"));
        assertEquals(0, session.effectiveConstructionTicks());

        assertEquals(RoutePreviewSession.State.PREVIEW, session.enter(p(5, 0)));
        assertTrue(session.selectedPlan().isEmpty());
    }

    @Test
    @DisplayName("Esc cancels from PICK_START")
    void should_Cancel_When_EscapeInPickStart() {
        FakeGroundMap terrain = new FakeGroundMap().rect(-1, -1, 4, 1, NORMAL);
        RoutePreviewSession session = new RoutePreviewSession(terrain, economy());
        session.begin(p(0, 0));

        assertEquals(RoutePreviewSession.State.CANCELLED, session.escape());
        assertTrue(session.start().isEmpty());
        assertTrue(session.lastPlan().isEmpty());
    }

    @Test
    @DisplayName("Esc cancels directly from PICK_END")
    void should_Cancel_When_EscapeInPickEnd() {
        FakeGroundMap terrain = new FakeGroundMap().rect(-1, -1, 4, 1, NORMAL);
        RoutePreviewSession session = new RoutePreviewSession(terrain, economy());
        session.begin(p(0, 0));
        session.enter(p(0, 0));

        assertEquals(RoutePreviewSession.State.CANCELLED, session.escape());
        assertTrue(session.start().isEmpty());
        assertTrue(session.end().isEmpty());
    }

    @Test
    @DisplayName("Esc cancels a running planning job without leaking a plan")
    void should_CancelJob_When_EscapeInPlanning() {
        FakeGroundMap terrain = new FakeGroundMap().rect(-1, -1, 4, 1, NORMAL);
        RoutePreviewSession session = new RoutePreviewSession(terrain, economy());
        session.begin(p(0, 0));
        session.enter(p(0, 0));
        assertEquals(RoutePreviewSession.State.PLANNING, session.enter(p(3, 0)));
        assertTrue(session.planningSnapshot().isPresent());

        // Enter during PLANNING is a no-op; only Esc cancels.
        assertEquals(RoutePreviewSession.State.PLANNING, session.enter(p(2, 0)));

        assertEquals(RoutePreviewSession.State.CANCELLED, session.escape());
        assertTrue(session.lastPlan().isEmpty());
        assertTrue(session.planningProgress().isEmpty());
        assertTrue(session.planningSnapshot().isEmpty());
        assertEquals(0, session.effectiveConstructionTicks());
    }

    @Test
    @DisplayName("begin resets any previous job and cached plans")
    void should_ResetJobAndCache_When_BeginIsCalledAgain() {
        FakeGroundMap terrain = new FakeGroundMap().rect(-1, -1, 4, 1, NORMAL);
        RoutePreviewSession session = previewOn(terrain);
        assertEquals(RoutePreviewSession.State.PREVIEW, session.state());

        assertEquals(RoutePreviewSession.State.PICK_START, session.begin(p(1, 1)));
        assertTrue(session.lastPlan().isEmpty());
        assertTrue(session.planningSnapshot().isEmpty());
        assertTrue(session.selectedPlan().isEmpty());
    }

    @Test
    @DisplayName("Esc discards the preview from PREVIEW")
    void should_Cancel_When_EscapeInPreview() {
        FakeGroundMap terrain = new FakeGroundMap().rect(-1, -1, 4, 1, NORMAL);
        RoutePreviewSession session = previewOn(terrain);
        assertEquals(RoutePreviewSession.State.PREVIEW, session.state());

        assertEquals(RoutePreviewSession.State.CANCELLED, session.escape());
        assertTrue(session.lastPlan().isEmpty());
        assertTrue(session.selectedPlan().isEmpty());
        assertEquals(0, session.effectiveConstructionTicks());
    }

    @Test
    @DisplayName("Esc after selection leaves the selected plan untouched")
    void should_KeepSelection_When_EscapeInSelected() {
        FakeGroundMap terrain = new FakeGroundMap().rect(-1, -1, 4, 1, NORMAL);
        RoutePreviewSession session = previewOn(terrain);
        session.enter(p(3, 0));

        assertEquals(RoutePreviewSession.State.SELECTED, session.escape());
        assertTrue(session.selectedPlan().isPresent());
        assertEquals(RoutePreviewSession.State.PICK_START, session.begin(p(1, 1)));
        assertTrue(session.selectedPlan().isEmpty());
    }

    @Test
    @DisplayName("effective ticks add the base delay to every planned cell")
    void should_AddBaseDelayPerCell_When_ComputingEffectiveTicks() {
        FakeGroundMap flat = new FakeGroundMap().rect(-1, -1, 4, 1, NORMAL);
        RoutePreviewSession flatSession = new RoutePreviewSession(flat, economy(), 5);
        flatSession.begin(p(0, 0));
        flatSession.enter(p(0, 0));
        flatSession.enter(p(3, 0));
        awaitPreview(flatSession);

        RoutePlan flatPlan = flatSession.lastPlan().orElseThrow();
        assertEquals(0, flatPlan.totalConstructionTicks());
        assertEquals(4, flatPlan.steps().size());
        assertEquals(20, flatSession.effectiveConstructionTicks());

        FakeGroundMap water = new FakeGroundMap().rect(-1, -1, 4, 1, NORMAL).put(1, 0, WATER);
        RoutePreviewSession waterSession = new RoutePreviewSession(water, economy(), 2);
        waterSession.begin(p(0, 0));
        waterSession.enter(p(0, 0));
        waterSession.enter(p(2, 0));
        awaitPreview(waterSession);
        waterSession.nextPolicy();
        awaitPreview(waterSession);

        RoutePlan bridgePlan = waterSession.lastPlan().orElseThrow();
        assertEquals(RoutePolicy.DIRECT, waterSession.policy());
        assertEquals(3, bridgePlan.steps().size());
        assertEquals(20, bridgePlan.totalConstructionTicks());
        assertEquals(26, waterSession.effectiveConstructionTicks());
    }

    @Test
    @DisplayName("the session copies endpoints and never exposes mutable cursor state")
    void should_CopyPoints_When_TheyEnterTheSession() {
        FakeGroundMap terrain = new FakeGroundMap().rect(-1, -1, 4, 1, NORMAL);
        RoutePreviewSession session = new RoutePreviewSession(terrain, economy());
        Point cursor = p(0, 0);
        session.begin(cursor);
        session.enter(cursor);
        cursor.setX(99);
        cursor.setY(99);

        assertEquals(p(0, 0), session.start().orElseThrow());
        assertEquals(p(0, 0), session.cursor().orElseThrow());
    }

    @Test
    @DisplayName("null arguments are rejected")
    void should_Throw_When_ArgumentsAreNull() {
        FakeGroundMap terrain = new FakeGroundMap().rect(-1, -1, 4, 1, NORMAL);
        EconomyManager economy = economy();

        assertThrows(NullPointerException.class, () -> new RoutePreviewSession(null, economy));
        assertThrows(NullPointerException.class, () -> new RoutePreviewSession(terrain, null));
        assertThrows(IllegalArgumentException.class,
                () -> new RoutePreviewSession(terrain, economy, -1));

        RoutePreviewSession session = new RoutePreviewSession(terrain, economy);
        assertThrows(NullPointerException.class, () -> session.begin(null));
        assertThrows(NullPointerException.class, () -> session.enter(null));
    }
}
