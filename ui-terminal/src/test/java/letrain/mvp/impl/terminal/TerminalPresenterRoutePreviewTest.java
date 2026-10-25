package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
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
        TerminalPresenter presenter = new TerminalPresenter(model, mock(TerminalView.class));
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
        assertEquals(RoutePreviewSession.State.PREVIEW, presenter.getRoutePreviewSession().state());
        assertTrue(presenter.getRoutePreviewSession().lastPlan().orElseThrow().feasible());

        presenter.onChar(enter());

        assertEquals(Model.GameMode.RAILS, model.getMode());
        assertNotNull(presenter.getPendingRoutePlan());
        assertTrue(presenter.getPendingRoutePlan().feasible());
        assertEquals(0, trackCount(model), "selecting a preview must not build anything");
    }

    @Test
    @DisplayName("left/right cycle the policy only in PREVIEW and leave the cursor alone")
    void arrowsCyclePolicy_onlyInPreview() {
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
        assertEquals(letrain.planner.RoutePolicy.CHEAPEST, session.policy());
        Point before = new Point(model.getCursor().getPosition());

        presenter.onChar(new InputEvent(KeyType.ArrowRight));
        assertEquals(letrain.planner.RoutePolicy.DIRECT, session.policy());
        assertEquals(before, model.getCursor().getPosition(),
                "the policy arrow must not move the cursor in PREVIEW");

        presenter.onChar(new InputEvent(KeyType.ArrowLeft));
        assertEquals(letrain.planner.RoutePolicy.CHEAPEST, session.policy());
    }

    @Test
    @DisplayName("h/l cycle the policy only in PREVIEW and leave the cursor alone")
    void hAndLCyclePolicy_onlyInPreview() {
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
        Point before = new Point(model.getCursor().getPosition());
        assertEquals(RoutePolicy.CHEAPEST, session.policy());

        presenter.onChar(new InputEvent(KeyType.Character, 'h', false, false, false));
        assertEquals(RoutePolicy.AVOID_ALL, session.policy(),
                "h must select the previous policy in PREVIEW");
        assertEquals(before, model.getCursor().getPosition(),
                "h must not move the cursor in PREVIEW");

        presenter.onChar(new InputEvent(KeyType.Character, 'l', false, false, false));
        assertEquals(RoutePolicy.CHEAPEST, session.policy(),
                "l must select the next policy in PREVIEW");
        assertEquals(before, model.getCursor().getPosition(),
                "l must not move the cursor in PREVIEW");
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
