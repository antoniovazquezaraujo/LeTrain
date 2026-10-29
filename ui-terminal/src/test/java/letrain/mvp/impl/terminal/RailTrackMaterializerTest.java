package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import letrain.audio.AudioController;
import letrain.ground.GroundMap;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.mvp.Presenter.TrackType;
import letrain.mvp.impl.Model;
import letrain.planner.RoutePlan;
import letrain.planner.RouteStep;
import letrain.track.rail.RailTrack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Construction materializer: isolated route on the real rail machinery")
class RailTrackMaterializerTest {

    private Model model;
    private TerminalPresenter presenter;
    private RailTrackMaterializer materializer;

    @BeforeEach
    void setUp() {
        model = new Model(1);
        model.updateGroundMap(new Point(-10, -10), 30, 30);
        for (int x = 0; x <= 4; x++) {
            model.getGroundMap().setValueAt(x, 0, GroundMap.GROUND);
        }
        model.getGroundMap().setValueAt(1, 1, GroundMap.GROUND);
        TerminalView view = mock(TerminalView.class);
        when(view.getScrollOffset()).thenReturn(new Point(0, 0));
        when(view.getCols()).thenReturn(80);
        when(view.getRows()).thenReturn(25);
        when(view.isCameraPagination()).thenReturn(false);
        presenter = new TerminalPresenter(model, view);
        AudioController real = presenter.audioController;
        presenter.audioController = mock(AudioController.class);
        if (real != null) {
            real.stop();
        }
        materializer = new RailTrackMaterializer(presenter);
    }

    @Test
    @DisplayName("preflight rejects a planned cell occupied by existing rail and does not mutate")
    void preflight_rejectsOccupiedCell() {
        RailTrack existing = new RailTrack();
        model.addTrack(new Point(1, 0), existing);
        int before = trackCount();

        Optional<String> rejection = materializer.prepare(plan(3));

        assertTrue(rejection.isPresent());
        assertEquals("cell (1,0) already has track", rejection.orElseThrow());
        assertEquals(before, trackCount(), "preflight must not add or remove track");
        assertSame(existing, model.getRailMap().getTrackAt(new Point(1, 0)));
    }

    @Test
    @DisplayName("preflight rejects a planned cell occupied by a rail element")
    void preflight_rejectsRailElement() {
        letrain.track.Sensor sensor = mock(letrain.track.Sensor.class);
        when(sensor.getPosition()).thenReturn(new Point(1, 0));
        model.addSensor(sensor);

        Optional<String> rejection = materializer.prepare(plan(3));

        assertTrue(rejection.isPresent());
        assertTrue(rejection.orElseThrow().contains("rail element"));
    }

    @Test
    @DisplayName("build chains only the new cells and never connects to existing adjacent rail")
    void build_createsIsolatedRouteWithoutForks() {
        RailTrack neighbour = new RailTrack();
        model.addTrack(new Point(1, 1), neighbour);
        Point cursorBefore = new Point(model.getCursor().getPosition());
        Dir dirBefore = model.getCursor().getDir();

        RoutePlan plan = plan(3);
        assertTrue(materializer.prepare(plan).isEmpty());
        for (int index = 0; index < plan.steps().size(); index++) {
            materializer.buildStep(plan, index, previous(plan, index), next(plan, index));
        }

        RailTrack first = model.getRailMap().getTrackAt(new Point(0, 0));
        RailTrack second = model.getRailMap().getTrackAt(new Point(1, 0));
        RailTrack third = model.getRailMap().getTrackAt(new Point(2, 0));
        assertNotNull(first);
        assertNotNull(second);
        assertNotNull(third);
        assertEquals(4, trackCount(), "three new cells plus the untouched neighbour");
        assertTrue(model.getForks().isEmpty(), "an isolated route must not create forks");
        assertTrue(model.getStations().isEmpty());

        // Internal chaining exists between the cells built by this route...
        assertSame(second, first.getConnected(Dir.E));
        assertSame(first, second.getConnected(Dir.W));
        assertSame(third, second.getConnected(Dir.E));
        // ...but the pre-existing neighbour is not adopted in either direction.
        assertFalse(neighbour.getConnectedPositions().stream()
                .anyMatch(pair -> pair.getValue().equals(new Point(0, 0))
                        || pair.getValue().equals(new Point(1, 0))
                        || pair.getValue().equals(new Point(2, 0))));

        assertEquals(cursorBefore, model.getCursor().getPosition(),
                "construction must restore the author's cursor");
        assertEquals(dirBefore, model.getCursor().getDir(),
                "construction must restore the author's facing");
    }

    private int trackCount() {
        int[] count = {0};
        model.getRailMap().forEach(track -> count[0]++);
        return count[0];
    }

    private static RoutePlan plan(int cells) {
        RouteStep[] steps = new RouteStep[cells];
        for (int i = 0; i < cells; i++) {
            steps[i] = new RouteStep(new Point(i, 0), TrackType.NORMAL_TRACK);
        }
        return RoutePlan.feasible(List.of(steps), 0, 0);
    }

    private static RouteStep previous(RoutePlan plan, int index) {
        return index > 0 ? plan.steps().get(index - 1) : null;
    }

    private static RouteStep next(RoutePlan plan, int index) {
        return index + 1 < plan.steps().size() ? plan.steps().get(index + 1) : null;
    }
}
