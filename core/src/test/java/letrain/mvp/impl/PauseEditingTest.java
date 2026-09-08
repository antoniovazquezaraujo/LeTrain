package letrain.mvp.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import letrain.map.Dir;
import letrain.map.Point;
import letrain.mvp.Model.GameMode;
import letrain.mvp.Presenter;
import letrain.track.rail.RailTrack;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests for paused editing (ADR-020 roadmap item 1): while pause-editing is on and the game is in
 * the RAILS mode, the world simulation (trains) is frozen and track construction is
 * instantaneous; toggling it off resumes movement.
 */
@DisplayName("Paused editing in RAILS (ADR-020)")
class PauseEditingTest {

    private letrain.mvp.impl.Model model;

    @BeforeEach
    void setUp() {
        model = new Model();
        model.postLoadInit();
    }

    // ------------------------------------------------------------------
    // Helpers (straight E-W line + train at constant speed)
    // ------------------------------------------------------------------

    private RailTrack track(int x, int y) {
        RailTrack t = new RailTrack();
        t.setPosition(new Point(x, y));
        t.addRoute(Dir.E, Dir.W);
        t.addRoute(Dir.W, Dir.E);
        model.getRailMap().addTrack(new Point(x, y), t);
        return t;
    }

    private void connect(RailTrack a, RailTrack b) {
        a.connect(Dir.E, b);
        b.connect(Dir.W, a);
    }

    private void buildEastWestLine(int n) {
        RailTrack prev = null;
        for (int x = 0; x < n; x++) {
            RailTrack t = track(x, 0);
            if (prev != null) {
                connect(prev, t);
            }
            prev = t;
        }
    }

    private Locomotive placeTrainAt(int x, int speed) {
        RailTrack start = model.getRailMap().getTrackAt(x, 0);
        assertNotNull(start, "start track must exist");
        Locomotive loco = new Locomotive(model.nextLocomotiveId(), "A");
        loco.setEngineOn(true);
        loco.setCurrentSpeed(speed);
        loco.setTargetSpeed(speed);
        Train train = new Train(model.nextTrainId());
        train.setModel(model);
        train.pushBack(loco);
        train.setDirectorLinker(loco);
        model.addLocomotive(loco);
        start.enterLinkerFromDir(Dir.W, loco); // enters from W -> moves E
        return loco;
    }

    private SimulationController newController() {
        return new SimulationController(model, null, null);
    }

    private int headX(Locomotive loco) {
        return loco.getPosition().getX();
    }

    // ------------------------------------------------------------------
    // Pause flag semantics
    // ------------------------------------------------------------------

    @Test
    @DisplayName("pauseEditing defaults to off")
    void pauseEditing_defaultsToOff() {
        assertFalse(model.isPauseEditing());
        assertFalse(model.isSimulationPaused(), "simulation must run by default");
    }

    @Test
    @DisplayName("isSimulationPaused is true only when toggled on AND in RAILS")
    void simulationPaused_dependsOnMode() {
        model.setPauseEditing(true);
        assertTrue(model.isSimulationPaused(), "RAILS (default mode) must be paused when enabled");

        model.setMode(GameMode.DRIVE);
        assertFalse(model.isSimulationPaused(), "DRIVE must keep simulating even when enabled");

        model.setMode(GameMode.RAILS);
        assertTrue(model.isSimulationPaused(), "back to RAILS must pause again");

        model.setPauseEditing(false);
        assertFalse(model.isSimulationPaused(), "disabling must resume");
    }

    // ------------------------------------------------------------------
    // Behaviour: trains freeze in paused editing
    // ------------------------------------------------------------------

    @Test
    @DisplayName("train keeps moving when simulation is running")
    void train_moves_whenNotPaused() {
        // Arrange
        buildEastWestLine(12);
        Locomotive loco = placeTrainAt(0, 5);
        int startX = headX(loco);
        SimulationController controller = newController();

        // Act
        for (int i = 0; i < 30; i++) {
            controller.tick();
        }

        // Assert: train has advanced east.
        assertTrue(headX(loco) > startX, "train must move while simulation runs (start=" + startX
                + ", now=" + headX(loco) + ")");
    }

    @Test
    @DisplayName("train is frozen while pause-editing is on in RAILS")
    void train_frozen_whilePauseEditingOnInRails() {
        // Arrange
        buildEastWestLine(12);
        Locomotive loco = placeTrainAt(0, 5);
        model.setMode(GameMode.RAILS);
        model.setPauseEditing(true);
        int startX = headX(loco);
        SimulationController controller = newController();

        // Act
        for (int i = 0; i < 30; i++) {
            controller.tick();
        }

        // Assert
        assertEquals(startX, headX(loco), "train must not move while paused editing in RAILS");
    }

    @Test
    @DisplayName("train resumes moving as soon as pause-editing is turned off")
    void train_resumes_whenPauseDisabled() {
        // Arrange
        buildEastWestLine(12);
        Locomotive loco = placeTrainAt(0, 5);
        model.setMode(GameMode.RAILS);
        model.setPauseEditing(true);
        SimulationController controller = newController();

        // Freeze a while.
        for (int i = 0; i < 15; i++) {
            controller.tick();
        }
        int frozenX = headX(loco);

        // Act: disable pause.
        model.setPauseEditing(false);
        for (int i = 0; i < 30; i++) {
            controller.tick();
        }

        // Assert
        assertNotEquals(frozenX, headX(loco), "train must move again once pause-editing is off");
    }

    // ------------------------------------------------------------------
    // Construction is instantaneous while paused editing
    // ------------------------------------------------------------------

    @Test
    @DisplayName("bridge/tunnel construction delay is skipped while pause-editing is on in RAILS")
    void trackConstruction_isInstantaneous_whenPausedEditing() {
        // Arrange
        model.setMode(GameMode.RAILS);
        Presenter presenter = mock(Presenter.class);
        when(presenter.getModel()).thenReturn(model);
        RailTrackMaker maker = new RailTrackMaker(presenter);

        // A bridge has a delay by default...
        model.setPauseEditing(false);
        maker.startTrackConstruction(Presenter.TrackType.BRIDGE_TRACK);
        assertFalse(maker.isTrackConstructionFinished(),
                "bridge must have a pending delay when not paused");

        // ...and is instantaneous when pause-editing is enabled in RAILS.
        model.setPauseEditing(true);
        maker.startTrackConstruction(Presenter.TrackType.BRIDGE_TRACK);
        assertTrue(maker.isTrackConstructionFinished(),
                "bridge construction must be instantaneous while paused editing");

        model.setPauseEditing(false);
        maker.startTrackConstruction(Presenter.TrackType.TUNNEL_TRACK);
        assertFalse(maker.isTrackConstructionFinished(),
                "tunnel must have a pending delay when not paused");

        model.setPauseEditing(true);
        maker.startTrackConstruction(Presenter.TrackType.TUNNEL_TRACK);
        assertTrue(maker.isTrackConstructionFinished(),
                "tunnel construction must be instantaneous while paused editing");
    }
}
