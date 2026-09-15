package letrain.mvp.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import letrain.map.Dir;
import letrain.map.Point;
import letrain.mvp.Model.GameMode;
import letrain.track.RailSemaphore;
import letrain.track.SpeedSignal;
import letrain.track.Station;
import letrain.track.rail.ForkRailTrack;
import letrain.vehicle.rail.impl.Locomotive;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Model Characterization Tests")
class ModelTest {

    private Model model;

    @BeforeEach
    void setUp() {
        model = new Model(42);
    }

    @Test
    @DisplayName("should initialize with default state and non-null components")
    void should_InitializeWithDefaultState_When_Created() {
        assertNotNull(model.getRailMap());
        assertNotNull(model.getGroundMap());
        assertNotNull(model.getCursor());
        assertEquals(GameMode.RAILS, model.getMode());
        assertTrue(model.getLocomotives().isEmpty());
        assertTrue(model.getStations().isEmpty());
        assertTrue(model.getForks().isEmpty());
        assertTrue(model.getSemaphores().isEmpty());
        assertTrue(model.getSensors().isEmpty());
    }

    @Test
    @DisplayName("should increment entity IDs sequentially")
    void should_IncrementIdsSequentially_When_Requested() {
        assertEquals(1, model.nextLocomotiveId());
        assertEquals(2, model.nextLocomotiveId());
        assertEquals(3, model.peekNextLocomotiveId());

        assertEquals(1, model.nextForkId());
        assertEquals(1, model.nextStationId());
        assertEquals(1, model.nextSensorId());
        assertEquals(1, model.nextSemaphoreId());
        assertEquals(1, model.nextTrainId());
        assertEquals(2, model.peekNextTrainId());
    }

    @Test
    @DisplayName("should add and remove locomotives and update selection")
    void should_ManageLocomotivesAndSelection_When_AddedAndRemoved() {
        letrain.vehicle.rail.impl.Train train1 = new letrain.vehicle.rail.impl.Train(1);
        Locomotive loco1 = new Locomotive(1, 'A');
        train1.pushBack(loco1);
        train1.setDirectorLinker(loco1);
        train1.rebind();

        letrain.vehicle.rail.impl.Train train2 = new letrain.vehicle.rail.impl.Train(2);
        Locomotive loco2 = new Locomotive(2, 'B');
        train2.pushBack(loco2);
        train2.setDirectorLinker(loco2);
        train2.rebind();

        model.addLocomotive(loco1);
        model.addLocomotive(loco2);
        assertEquals(2, model.getLocomotives().size());

        assertTrue(model.selectLocomotive(1));
        assertEquals(loco1, model.getSelectedLocomotive());

        assertTrue(model.selectNextLocomotive());
        assertEquals(loco2, model.getSelectedLocomotive());

        assertTrue(model.selectPrevLocomotive());
        assertEquals(loco1, model.getSelectedLocomotive());

        model.removeLocomotive(loco1);
        assertEquals(1, model.getLocomotives().size());
        assertNull(model.getSelectedLocomotive());
    }

    @Test
    @DisplayName("should add, retrieve and select forks")
    void should_ManageForksAndSelection_When_Added() {
        ForkRailTrack fork1 = new ForkRailTrack(10);
        ForkRailTrack fork2 = new ForkRailTrack(20);

        model.addFork(fork1);
        model.addFork(fork2);

        assertEquals(fork1, model.getFork(10));
        assertEquals(fork2, model.getFork(20));

        assertTrue(model.selectFork(10));
        assertEquals(fork1, model.getSelectedFork());

        assertTrue(model.selectNextFork());
        assertEquals(fork2, model.getSelectedFork());

        assertTrue(model.selectPrevFork());
        assertEquals(fork1, model.getSelectedFork());

        model.removeFork(fork1);
        assertEquals(1, model.getForks().size());
    }

    @Test
    @DisplayName("should add, retrieve and find stations by name and id")
    void should_ManageStationsAndSelection_When_Added() {
        Station station1 = new Station(1);
        station1.setName("Central");
        Station station2 = new Station(2);
        station2.setName("North");

        model.addStation(station1);
        model.addStation(station2);

        assertEquals(station1, model.getStation(1));
        assertEquals(station1, model.findStationByName("Central"));
        assertEquals(station2, model.findStationByName("North"));
        assertNull(model.findStationByName("NonExistent"));

        assertTrue(model.selectStation(1));
        assertEquals(station1, model.getSelectedStation());

        assertTrue(model.selectNextStation());
        assertEquals(station2, model.getSelectedStation());

        assertTrue(model.selectPrevStation());
        assertEquals(station1, model.getSelectedStation());

        model.removeStation(station1);
        assertEquals(1, model.getStations().size());
    }

    @Test
    @DisplayName("should add and select semaphores and speed signals")
    void should_ManageSemaphoresAndSignals_When_Added() {
        RailSemaphore sem1 = new RailSemaphore(1);
        RailSemaphore sem2 = new RailSemaphore(2);

        model.addSemaphore(sem1);
        model.addSemaphore(sem2);

        assertEquals(sem1, model.getSemaphore(1));
        assertTrue(model.selectSemaphore(1));
        assertEquals(sem1, model.getSelectedSemaphore());

        assertTrue(model.selectNextSemaphore());
        assertEquals(sem2, model.getSelectedSemaphore());

        SpeedSignal sig1 = new SpeedSignal(101, Dir.E, 30, false);
        SpeedSignal sig2 = new SpeedSignal(102, Dir.E, 60, true);

        model.addSensor(sig1);
        model.addSensor(sig2);

        assertEquals(sig1, model.getSpeedSignal(101));
        assertTrue(model.selectSpeedSignal(101));
        assertEquals(sig1, model.getSelectedSpeedSignal());

        assertTrue(model.selectNextSpeedSignal());
        assertEquals(sig2, model.getSelectedSpeedSignal());
    }

    @Test
    @DisplayName("should handle marks and mode transitions")
    void should_HandleMarksAndModes_When_Updated() {
        Point markPoint = new Point(15, 25);
        model.setMark("Depot", markPoint);
        assertEquals(markPoint, model.getMark("Depot"));
        assertTrue(model.getMarks().containsKey("Depot"));

        model.setMode(GameMode.DRIVE);
        assertEquals(GameMode.DRIVE, model.getMode());
        assertEquals(GameMode.RAILS, model.getPreviousMode());
    }

    @Test
    @DisplayName("should produce non-empty game objects and graph reports")
    void should_GenerateReports_When_Requested() {
        String objReport = model.getGameObjectsReport();
        assertNotNull(objReport);
        assertTrue(objReport.contains("--- TRAINS ---"));
        assertTrue(objReport.contains("--- STATIONS ---"));

        String graphReport = model.getRailwayGraphReport();
        assertNotNull(graphReport);
        assertTrue(graphReport.contains("--- SEGMENT OWNERSHIP ---"));
    }

    @Test
    @DisplayName("should manage menu model options")
    void should_ReturnMenuModel_When_Queried() {
        var menu = model.getMenuModel();
        assertNotNull(menu);
        assertFalse(menu.isEmpty());
        assertTrue(menu.stream().anyMatch(opt -> opt.gameModeName().contains("Rails")));
    }
}
