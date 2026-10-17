package letrain.mvp.impl.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import letrain.itinerary.AutoPilot;
import letrain.mvp.impl.Model;
import letrain.track.Station;
import letrain.vehicle.rail.TrainLogisticsManager;
import letrain.vehicle.rail.TrainSafetyManager;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the train state shown by {@code info} (issue #701): the report used to hardcode
 * {@code CRUIZING}. Each case pins one step of the priority mapping.
 */
@DisplayName("info: train state derivation (issue #701)")
class ModelReportServiceStateTest {

    private Train train;
    private TrainSafetyManager safety;
    private TrainLogisticsManager logistics;
    private Locomotive director;
    private AutoPilot autopilot;

    @BeforeEach
    void setUp() {
        train = mock(Train.class);
        safety = mock(TrainSafetyManager.class);
        logistics = mock(TrainLogisticsManager.class);
        director = mock(Locomotive.class);
        autopilot = mock(AutoPilot.class);
        when(train.getSafetyManager()).thenReturn(safety);
        when(train.getLogisticsManager()).thenReturn(logistics);
        when(train.getDirectorLinker()).thenReturn(director);
        when(train.getAutopilot()).thenReturn(autopilot);
        when(director.isEngineOn()).thenReturn(true);
    }

    private String state() {
        return ModelReportService.describeTrainState(train);
    }

    @Test
    @DisplayName("loading a station wins over every other flag")
    void loading_isReportedWithStation() {
        when(logistics.isLoading()).thenReturn(true);
        when(logistics.getStationAtTrain()).thenReturn(new Station(7));

        assertEquals("LOADING at Station 7", state());
    }

    @Test
    @DisplayName("stalled wins over the autopilot state")
    void stalled_isReported() {
        when(train.isStalled()).thenReturn(true);
        when(autopilot.mode()).thenReturn(AutoPilot.Mode.FOLLOWING);

        assertEquals("STALLED", state());
    }

    @Test
    @DisplayName("waiting for the next canton is reported")
    void waitingForBlock_isReported() {
        when(safety.isWaitingForBlock()).thenReturn(true);

        assertEquals("WAITING FOR BLOCK", state());
    }

    @Test
    @DisplayName("held by the schedule is reported")
    void heldBySchedule_isReported() {
        when(train.isHeldBySchedule()).thenReturn(true);

        assertEquals("HOLDING FOR DEPARTURE", state());
    }

    @Test
    @DisplayName("a stopped engine-off train with the autopilot armed is PARKED")
    void stoppedEngineOffAuto_isParked() {
        when(train.isStopped()).thenReturn(true);
        when(train.isAutoMode()).thenReturn(true);
        when(director.isEngineOn()).thenReturn(false);

        assertEquals("PARKED", state());
    }

    @Test
    @DisplayName("a stopped engine-off train in manual is ENGINE OFF")
    void stoppedEngineOffManual_isEngineOff() {
        when(train.isStopped()).thenReturn(true);
        when(train.isAutoMode()).thenReturn(false);
        when(director.isEngineOn()).thenReturn(false);

        assertEquals("ENGINE OFF", state());
    }

    @Test
    @DisplayName("rolling with target speed 0 is BRAKING")
    void rollingWithTargetZero_isBraking() {
        when(train.isStopped()).thenReturn(false);
        when(director.isBraking()).thenReturn(true);
        when(director.getTargetSpeed()).thenReturn(0);

        assertEquals("BRAKING", state());
    }

    @Test
    @DisplayName("autopilot following while moving is CRUISING")
    void followingMoving_isCruising() {
        when(autopilot.mode()).thenReturn(AutoPilot.Mode.FOLLOWING);
        when(train.isStopped()).thenReturn(false);

        assertEquals("CRUISING", state());
    }

    @Test
    @DisplayName("autopilot following while stopped is IDLE")
    void followingStopped_isIdle() {
        when(autopilot.mode()).thenReturn(AutoPilot.Mode.FOLLOWING);
        when(train.isStopped()).thenReturn(true);

        assertEquals("IDLE", state());
    }

    @Test
    @DisplayName("autopilot reversing is REVERSING")
    void reversing_isReported() {
        when(autopilot.mode()).thenReturn(AutoPilot.Mode.REVERSING);

        assertEquals("REVERSING", state());
    }

    @Test
    @DisplayName("autopilot in error is ERROR")
    void error_isReported() {
        when(autopilot.mode()).thenReturn(AutoPilot.Mode.ERROR);

        assertEquals("ERROR", state());
    }

    @Test
    @DisplayName("a stopped manual train with the engine on is STOPPED (MANUAL)")
    void stoppedManual_isStoppedManual() {
        when(autopilot.mode()).thenReturn(AutoPilot.Mode.IDLE);
        when(train.isStopped()).thenReturn(true);

        assertEquals("STOPPED (MANUAL)", state());
    }

    @Test
    @DisplayName("a moving manual train is CRUISING")
    void movingManual_isCruising() {
        when(autopilot.mode()).thenReturn(AutoPilot.Mode.IDLE);
        when(train.isStopped()).thenReturn(false);

        assertEquals("CRUISING", state());
    }

    @Test
    @DisplayName("the full report shows the derived state and no CRUIZING typo")
    void report_showsDerivedStateWithoutTypo() {
        Model model = new Model(1);
        Train realTrain = new Train(1);
        realTrain.setModel(model);
        Locomotive loco = new Locomotive(1, "A", "RED");
        realTrain.pushBack(loco);
        realTrain.setDirectorLinker(loco);
        model.addLocomotive(loco);
        loco.setEngineOn(false);

        String report = ModelReportService.generateGameObjectsReport(model);

        assertTrue(report.contains("State: ENGINE OFF"), report);
        assertFalse(report.contains("CRUIZING"), report);
    }
}
