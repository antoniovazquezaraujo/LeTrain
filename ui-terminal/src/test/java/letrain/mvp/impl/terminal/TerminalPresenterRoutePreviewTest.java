package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import letrain.audio.AudioController;
import letrain.ground.GroundMap;
import letrain.map.Point;
import letrain.mvp.impl.Model;
import letrain.mvp.input.InputEvent;
import letrain.mvp.input.KeyType;
import letrain.planner.RoutePolicy;
import letrain.planner.RoutePreviewSession;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("2D route preview: CONNECT presenter state")
class TerminalPresenterRoutePreviewTest {

    private static InputEvent shiftC() {
        return new InputEvent(KeyType.Character, 'C', false, false, true);
    }

    private static InputEvent enter() {
        return new InputEvent(KeyType.Enter);
    }

    private static InputEvent escape() {
        return new InputEvent(KeyType.Escape);
    }

    private static TerminalPresenter silentPresenter(Model model) {
        TerminalView view = mock(TerminalView.class);
        when(view.getScrollOffset()).thenReturn(new Point(0, 0));
        when(view.getCols()).thenReturn(80);
        when(view.getRows()).thenReturn(25);
        when(view.isCameraPagination()).thenReturn(false);
        TerminalPresenter presenter = new TerminalPresenter(model, view);
        AudioController realAudio = presenter.audioController;
        presenter.audioController = mock(AudioController.class);
        if (realAudio != null) {
            realAudio.stop();
        }
        return presenter;
    }

    private static Model seededModel() {
        Model model = new Model(1);
        model.updateGroundMap(new Point(-30, -30), 60, 60);
        return model;
    }

    /** Two cells where a route may start/end (ground or an industry tile). */
    private static List<Point> buildablePoints(Model model) {
        List<Point> points = new ArrayList<>();
        GroundMap ground = model.getGroundMap();
        for (int y = -25; y <= 25 && points.size() < 2; y++) {
            for (int x = -25; x <= 25 && points.size() < 2; x++) {
                Integer value = ground.getValueAt(x, y);
                if (value != null && (value == GroundMap.GROUND || (value >= 10 && value <= 29))) {
                    points.add(new Point(x, y));
                }
            }
        }
        return points;
    }

    private static int trackCount(Model model) {
        int[] count = {0};
        model.getRailMap().forEach(track -> count[0]++);
        return count[0];
    }

    /**
     * Drives the presenter poll hook (the same one the frame loop calls) until the session leaves
     * PLANNING. Using the presenter instead of the session also covers the polling wiring.
     */
    private static void awaitPreview(TerminalPresenter presenter) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            presenter.pollRoutePreview();
            RoutePreviewSession session = presenter.getRoutePreviewSession();
            assertNotNull(session, "preview session disappeared while planning");
            if (session.state() != RoutePreviewSession.State.PLANNING) {
                assertEquals(RoutePreviewSession.State.PREVIEW, session.state());
                return;
            }
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
        }
        fail("route preview did not leave PLANNING before the timeout");
    }

    @Test
    @DisplayName("Shift+C enters CONNECT and Esc returns to the previous mode")
    void shiftC_entersConnect_andEscapeReturnsToPreviousMode() {
        Model model = seededModel();
        model.setMode(Model.GameMode.SENSORS);
        TerminalPresenter presenter = silentPresenter(model);

        presenter.onChar(shiftC());

        assertEquals(Model.GameMode.CONNECT, model.getMode());
        assertNotNull(presenter.getRoutePreviewSession());
        assertEquals(RoutePreviewSession.State.PICK_START,
                presenter.getRoutePreviewSession().state());

        presenter.onChar(escape());

        assertEquals(Model.GameMode.SENSORS, model.getMode());
        assertNull(presenter.getRoutePreviewSession());
    }

    @Test
    @DisplayName("Shift+C is ignored from ADD, TRAINS, PROGRAM and COMMAND")
    void shiftC_isIgnoredFromModalModes() {
        Model model = seededModel();
        TerminalPresenter presenter = silentPresenter(model);

        for (Model.GameMode mode : List.of(Model.GameMode.ADD, Model.GameMode.TRAINS,
                Model.GameMode.PROGRAM)) {
            model.setMode(mode);
            presenter.onChar(shiftC());
            assertEquals(mode, model.getMode(), "CONNECT must not open from " + mode);
        }
    }

    @Test
    @DisplayName("CONNECT movement never builds or deletes track")
    void connectMovement_doesNotTouchTheRailMap() {
        Model model = seededModel();
        List<Point> points = buildablePoints(model);
        model.getCursor().setPosition(points.get(0));
        model.setMode(Model.GameMode.RAILS);
        TerminalPresenter presenter = silentPresenter(model);

        presenter.onChar(shiftC());
        Point before = new Point(model.getCursor().getPosition());

        // Shift/Ctrl arrows would draw/erase in RAILS; the sanitized CONNECT event must only move.
        presenter.onChar(new InputEvent(KeyType.ArrowUp, null, false, false, true));
        presenter.onChar(new InputEvent(KeyType.ArrowUp, null, true, false, false));

        assertNotEquals(before, model.getCursor().getPosition());
        assertEquals(0, trackCount(model), "CONNECT must not create track");
    }

    @Test
    @DisplayName("Enter picks A and B, then a second Enter selects without building")
    void enterPicksEndpoints_andSelectStoresPendingPlan() {
        Model model = seededModel();
        List<Point> points = buildablePoints(model);
        model.getCursor().setPosition(points.get(0));
        model.setMode(Model.GameMode.RAILS);
        TerminalPresenter presenter = silentPresenter(model);

        presenter.onChar(shiftC());
        presenter.onChar(enter());
        assertEquals(RoutePreviewSession.State.PICK_END,
                presenter.getRoutePreviewSession().state());

        model.getCursor().setPosition(points.get(1));
        presenter.onChar(enter());
        assertEquals(RoutePreviewSession.State.PLANNING,
                presenter.getRoutePreviewSession().state());
        awaitPreview(presenter);
        assertTrue(presenter.getRoutePreviewSession().lastPlan().orElseThrow().feasible());

        presenter.onChar(enter());

        assertEquals(Model.GameMode.RAILS, model.getMode());
        assertNotNull(presenter.getPendingRoutePlan());
        assertTrue(presenter.getPendingRoutePlan().feasible());
        assertEquals(0, trackCount(model), "selecting a preview must not build anything");
    }

    @Test
    @DisplayName("brackets cycle the policy, using the cache when going back")
    void bracketsCyclePolicy_onlyInPreview() {
        Model model = seededModel();
        List<Point> points = buildablePoints(model);
        model.getCursor().setPosition(points.get(0));
        model.setMode(Model.GameMode.RAILS);
        TerminalPresenter presenter = silentPresenter(model);

        presenter.onChar(shiftC());
        presenter.onChar(enter());
        model.getCursor().setPosition(points.get(1));
        presenter.onChar(enter());
        awaitPreview(presenter);

        RoutePreviewSession session = presenter.getRoutePreviewSession();
        assertEquals(RoutePolicy.CHEAPEST, session.policy());
        Point before = new Point(model.getCursor().getPosition());

        presenter.onChar(new InputEvent(KeyType.Character, ']', false, false, false));
        assertEquals(RoutePolicy.DIRECT, session.policy());
        assertEquals(RoutePreviewSession.State.PLANNING, session.state());
        assertEquals(before, model.getCursor().getPosition(),
                "the policy key must not move the cursor in PREVIEW");
        awaitPreview(presenter);

        presenter.onChar(new InputEvent(KeyType.Character, '[', false, false, false));
        assertEquals(RoutePolicy.CHEAPEST, session.policy());
        assertEquals(RoutePreviewSession.State.PREVIEW, session.state(),
                "the cheapest plan is cached, so going back is immediate");
    }

    @Test
    @DisplayName("h/l and arrows navigate in PREVIEW without changing policy or plan")
    void navigationKeys_keepPolicyAndPlanInPreview() {
        Model model = seededModel();
        List<Point> points = buildablePoints(model);
        model.getCursor().setPosition(points.get(0));
        model.setMode(Model.GameMode.RAILS);
        TerminalPresenter presenter = silentPresenter(model);

        presenter.onChar(shiftC());
        presenter.onChar(enter());
        model.getCursor().setPosition(points.get(1));
        presenter.onChar(enter());
        awaitPreview(presenter);

        RoutePreviewSession session = presenter.getRoutePreviewSession();
        RoutePolicy policyBefore = session.policy();
        Point startBefore = session.start().orElseThrow();
        Point endBefore = session.end().orElseThrow();
        RoutePolicy policyAfterPicks = session.policy();
        letrain.map.Dir dirBefore = model.getCursor().getDir();

        presenter.onChar(new InputEvent(KeyType.Character, 'h', false, false, false));
        assertEquals(dirBefore.turnLeft(), model.getCursor().getDir(),
                "h must turn the cursor left in PREVIEW");

        presenter.onChar(new InputEvent(KeyType.Character, 'l', false, false, false));
        assertEquals(dirBefore, model.getCursor().getDir(),
                "l must turn the cursor right in PREVIEW");

        Point positionBefore = new Point(model.getCursor().getPosition());
        presenter.onChar(new InputEvent(KeyType.ArrowUp));
        assertNotEquals(positionBefore, model.getCursor().getPosition(),
                "arrows must move the cursor in PREVIEW");

        presenter.onChar(new InputEvent(KeyType.PageDown));
        presenter.onChar(new InputEvent(KeyType.Character, 'z', false, false, false));
        presenter.onChar(new InputEvent(KeyType.Character, 'Z', false, false, false));

        assertEquals(policyBefore, session.policy(), "navigation must not change the policy");
        assertEquals(policyBefore, policyAfterPicks);
        assertEquals(startBefore, session.start().orElseThrow());
        assertEquals(endBefore, session.end().orElseThrow());
        assertTrue(session.lastPlan().orElseThrow().feasible(),
                "navigation must not discard the plan");
        assertEquals(RoutePreviewSession.State.PREVIEW, session.state());
    }

    @Test
    @DisplayName("PLANNING ignores navigation, route keys and the console; Esc cancels")
    void planning_blocksEverythingButCancel() {
        Model model = seededModel();
        List<Point> points = buildablePoints(model);
        model.getCursor().setPosition(points.get(0));
        model.setMode(Model.GameMode.RAILS);
        TerminalPresenter presenter = silentPresenter(model);

        presenter.onChar(shiftC());
        presenter.onChar(enter());
        model.getCursor().setPosition(points.get(1));
        presenter.onChar(enter());

        RoutePreviewSession session = presenter.getRoutePreviewSession();
        assertEquals(RoutePreviewSession.State.PLANNING, session.state());
        Point positionBefore = new Point(model.getCursor().getPosition());
        letrain.map.Dir dirBefore = model.getCursor().getDir();

        presenter.onChar(new InputEvent(KeyType.ArrowUp));
        presenter.onChar(new InputEvent(KeyType.Character, 'h', false, false, false));
        presenter.onChar(new InputEvent(KeyType.Character, ']', false, false, false));
        presenter.onChar(new InputEvent(KeyType.Character, ':', false, false, false));
        presenter.onChar(new InputEvent(KeyType.Character, 'z', false, false, false));
        presenter.onChar(enter());

        assertEquals(RoutePreviewSession.State.PLANNING, session.state());
        assertEquals(Model.GameMode.CONNECT, model.getMode());
        assertEquals(positionBefore, model.getCursor().getPosition());
        assertEquals(dirBefore, model.getCursor().getDir());
        assertTrue(model.getCommandText().isEmpty(), "PLANNING must not open the console");

        presenter.onChar(escape());

        assertEquals(Model.GameMode.RAILS, model.getMode());
        assertNull(presenter.getRoutePreviewSession());
    }

    @Test
    @DisplayName(": opens the console from PREVIEW and Esc returns with the session intact")
    void colonOpensConsole_andReturnsToPreview() {
        Model model = seededModel();
        List<Point> points = buildablePoints(model);
        model.getCursor().setPosition(points.get(0));
        model.setMode(Model.GameMode.RAILS);
        TerminalPresenter presenter = silentPresenter(model);

        presenter.onChar(shiftC());
        presenter.onChar(enter());
        model.getCursor().setPosition(points.get(1));
        presenter.onChar(enter());
        awaitPreview(presenter);

        RoutePreviewSession session = presenter.getRoutePreviewSession();
        Point startBefore = session.start().orElseThrow();
        Point endBefore = session.end().orElseThrow();

        presenter.onChar(new InputEvent(KeyType.Character, ':', false, false, false));
        assertEquals(Model.GameMode.COMMAND, model.getMode());

        presenter.onChar(escape());

        assertEquals(Model.GameMode.CONNECT, model.getMode());
        assertEquals(session, presenter.getRoutePreviewSession());
        assertEquals(startBefore, session.start().orElseThrow());
        assertEquals(endBefore, session.end().orElseThrow());
        assertEquals(RoutePreviewSession.State.PREVIEW, session.state());
    }

    @Test
    @DisplayName("console blocks editing commands while previewing and keeps the console open")
    void consoleWhitelist_blocksEditingCommands() {
        Model model = seededModel();
        List<Point> points = buildablePoints(model);
        model.getCursor().setPosition(points.get(0));
        model.setMode(Model.GameMode.RAILS);
        TerminalPresenter presenter = silentPresenter(model);

        presenter.onChar(shiftC());
        presenter.onChar(enter());
        model.getCursor().setPosition(points.get(1));
        presenter.onChar(enter());
        awaitPreview(presenter);

        RoutePreviewSession session = presenter.getRoutePreviewSession();
        Point startBefore = session.start().orElseThrow();
        Point endBefore = session.end().orElseThrow();

        presenter.onChar(new InputEvent(KeyType.Character, ':', false, false, false));
        model.setCommandText("del 0,0");
        presenter.onChar(enter());

        assertEquals(Model.GameMode.COMMAND, model.getMode(),
                "a blocked command must keep the console open");
        assertEquals(RoutePreviewConsolePolicy.BLOCKED_MESSAGE, model.getCommandError());
        assertEquals(startBefore, session.start().orElseThrow());
        assertEquals(endBefore, session.end().orElseThrow());
        assertEquals(RoutePreviewSession.State.PREVIEW, session.state());

        model.setCommandText("go 0,0");
        presenter.onChar(enter());

        assertEquals(Model.GameMode.CONNECT, model.getMode(),
                "an allowed navigation command returns to the preview");
        assertEquals(session, presenter.getRoutePreviewSession());
        assertEquals(startBefore, session.start().orElseThrow());
        assertEquals(endBefore, session.end().orElseThrow());
    }

    @Test
    @DisplayName("h/l still move the cursor and do not touch the policy in PICK_END")
    void hAndLMoveCursor_inPickEnd() {
        Model model = seededModel();
        List<Point> points = buildablePoints(model);
        model.getCursor().setPosition(points.get(0));
        model.setMode(Model.GameMode.RAILS);
        TerminalPresenter presenter = silentPresenter(model);

        presenter.onChar(shiftC());
        presenter.onChar(enter());
        RoutePreviewSession session = presenter.getRoutePreviewSession();
        assertEquals(RoutePreviewSession.State.PICK_END, session.state());
        Point before = new Point(model.getCursor().getPosition());
        letrain.map.Dir dirBefore = model.getCursor().getDir();
        RoutePolicy policyBefore = session.policy();

        presenter.onChar(new InputEvent(KeyType.Character, 'h', false, false, false));
        assertEquals(dirBefore.turnLeft(), model.getCursor().getDir(),
                "h must keep turning the cursor left in PICK_END");
        assertEquals(before, model.getCursor().getPosition(),
                "h must not move the cursor in PICK_END");
        assertEquals(policyBefore, session.policy(), "h must not cycle the policy in PICK_END");

        presenter.onChar(new InputEvent(KeyType.Character, 'l', false, false, false));
        assertEquals(dirBefore, model.getCursor().getDir(),
                "l must keep turning the cursor right in PICK_END");
        assertEquals(before, model.getCursor().getPosition(),
                "l must not move the cursor in PICK_END");
        assertEquals(policyBefore, session.policy(), "l must not cycle the policy in PICK_END");
    }

    @Test
    @DisplayName("Shift+C again cancels CONNECT")
    void shiftCAgain_cancels() {
        Model model = seededModel();
        model.setMode(Model.GameMode.RAILS);
        TerminalPresenter presenter = silentPresenter(model);

        presenter.onChar(shiftC());
        presenter.onChar(shiftC());

        assertEquals(Model.GameMode.RAILS, model.getMode());
        assertNull(presenter.getRoutePreviewSession());
    }
}
