package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import letrain.map.Point;
import letrain.mvp.Presenter.TrackType;
import letrain.mvp.impl.Model;
import letrain.planner.RoutePlan;
import letrain.planner.RouteStep;
import letrain.track.rail.RailTrack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Rail buildability: shared preview/queue preflight")
class RailBuildabilityTest {

    private Model model;

    @BeforeEach
    void setUp() {
        model = new Model(1);
        model.updateGroundMap(new Point(-10, -10), 30, 30);
    }

    @Test
    @DisplayName("a clear plan is buildable")
    void clearPlan_isBuildable() {
        Optional<RailBuildability.Issue> issue = RailBuildability.check(model, plan(3));

        assertTrue(issue.isEmpty());
    }

    @Test
    @DisplayName("an existing rail on the start cell reports the cell and that it is track")
    void existingTrackAtStart_isReported() {
        model.addTrack(new Point(0, 0), new RailTrack());

        Optional<RailBuildability.Issue> issue = RailBuildability.check(model, plan(3));

        assertTrue(issue.isPresent());
        assertEquals(new Point(0, 0), issue.orElseThrow().cell());
        assertEquals(RailBuildability.Occupant.TRACK, issue.orElseThrow().occupant());
        assertEquals("cell (0,0) already has track", issue.orElseThrow().description());
    }

    @Test
    @DisplayName("an existing rail on an intermediate cell is reported before the queue starts")
    void existingTrackInTheMiddle_isReported() {
        model.addTrack(new Point(1, 0), new RailTrack());

        Optional<RailBuildability.Issue> issue = RailBuildability.check(model, plan(3));

        assertTrue(issue.isPresent());
        assertEquals(new Point(1, 0), issue.orElseThrow().cell());
        assertEquals("cell (1,0) already has track", issue.orElseThrow().description());
    }

    @Test
    @DisplayName("a rail element is reported as a rail element, not as a track")
    void railElement_isReportedSeparately() {
        letrain.track.Sensor sensor = mock(letrain.track.Sensor.class);
        when(sensor.getPosition()).thenReturn(new Point(2, 0));
        model.addSensor(sensor);

        Optional<RailBuildability.Issue> issue = RailBuildability.check(model, plan(3));

        assertTrue(issue.isPresent());
        assertEquals(new Point(2, 0), issue.orElseThrow().cell());
        assertEquals(RailBuildability.Occupant.RAIL_ELEMENT, issue.orElseThrow().occupant());
        assertEquals("cell (2,0) already has a rail element", issue.orElseThrow().description());
    }

    @Test
    @DisplayName("checkCell enables the immediate warning when A is picked")
    void checkCell_checksTheSingleCell() {
        model.addTrack(new Point(0, 0), new RailTrack());

        assertTrue(RailBuildability.checkCell(model, new Point(0, 0)).isPresent());
        assertTrue(RailBuildability.checkCell(model, new Point(1, 0)).isEmpty());
    }

    @Test
    @DisplayName("an infeasible plan has no cells to build, so it is not a buildability problem")
    void infeasiblePlan_hasNoBuildIssue() {
        assertFalse(RailBuildability.check(model, RoutePlan.infeasible("no route")).isPresent());
    }

    private static RoutePlan plan(int cells) {
        RouteStep[] steps = new RouteStep[cells];
        for (int i = 0; i < cells; i++) {
            steps[i] = new RouteStep(new Point(i, 0), TrackType.NORMAL_TRACK);
        }
        return RoutePlan.feasible(List.of(steps), 0, 0);
    }
}
