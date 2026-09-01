package letrain.itinerary;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import letrain.itinerary.impl.TrainActionManager;
import letrain.itinerary.impl.WaypointImpl;
import letrain.track.Station;
import letrain.vehicle.rail.TrainLogisticsManager;
import letrain.vehicle.rail.TrainMovementManager;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("TrainActionManager")
class TrainActionManagerTest {

    private Train train;
    private TrainActionManager actionManager;
    private Locomotive loco;
    private TrainMovementManager movementManager;
    private TrainLogisticsManager logisticsManager;
    private AutoPilot autopilot;

    @BeforeEach
    void setUp() {
        train = mock(Train.class);
        loco = mock(Locomotive.class);
        movementManager = mock(TrainMovementManager.class);
        logisticsManager = mock(TrainLogisticsManager.class);
        autopilot = mock(AutoPilot.class);

        when(train.getDirectorLinker()).thenReturn(loco);
        when(train.getMovementManager()).thenReturn(movementManager);
        when(train.getLogisticsManager()).thenReturn(logisticsManager);
        when(train.getAutopilot()).thenReturn(autopilot);
        when(train.getId()).thenReturn(1);

        actionManager = new TrainActionManager(train);
    }

    @Test
    @DisplayName(
            "should pass through without braking when train has no capable wagons or is already full")
    void shouldPassThroughWithoutBrakingWhenTrainIsFull() {
        when(loco.getSpeed()).thenReturn(3);
        when(loco.getTargetSpeed()).thenReturn(3);

        Station station = mock(Station.class);
        when(station.getName()).thenReturn("Station A");
        when(logisticsManager.getStationAtTrain()).thenReturn(station);
        when(logisticsManager.getCapableWagons(station, false))
                .thenReturn(List.of()); // No capable wagons / full

        Waypoint waypoint =
                new WaypointImpl(Waypoint.Type.STATION, 1, List.of(WaypointCommand.LOAD));

        actionManager.onWaypointReached(train, waypoint);

        // Train should NOT brake or load; it passes right through
        verify(movementManager, never()).initiateBraking();
        verify(train, never()).load();
    }

    @Test
    @DisplayName(
            "should initiate natural braking when waypoint has LOAD command and train has space in capable wagons")
    void shouldInitiateBrakingWhenMovingWithSpaceInCapableWagons() {
        when(loco.getSpeed()).thenReturn(3);
        when(loco.getTargetSpeed()).thenReturn(3);

        Station station = mock(Station.class);
        letrain.vehicle.rail.impl.Wagon wagon = mock(letrain.vehicle.rail.impl.Wagon.class);
        when(logisticsManager.getStationAtTrain()).thenReturn(station);
        when(logisticsManager.getCapableWagons(station, false)).thenReturn(List.of(wagon));

        Waypoint waypoint =
                new WaypointImpl(Waypoint.Type.STATION, 1, List.of(WaypointCommand.LOAD));

        actionManager.onWaypointReached(train, waypoint);

        verify(movementManager).initiateBraking();
        verify(train, never()).load();
    }

    @Test
    @DisplayName("should load cargo when train stops inside station and resume speed after loading")
    void shouldLoadCargoAndResumeSpeedWhenStoppedInsideStation() {
        when(loco.getSpeed()).thenReturn(3);
        when(loco.getTargetSpeed()).thenReturn(3);

        Station station = mock(Station.class);
        letrain.vehicle.rail.impl.Wagon wagon = mock(letrain.vehicle.rail.impl.Wagon.class);
        when(logisticsManager.getStationAtTrain()).thenReturn(station);
        when(logisticsManager.getCapableWagons(station, false)).thenReturn(List.of(wagon));

        Waypoint waypoint =
                new WaypointImpl(Waypoint.Type.STATION, 1, List.of(WaypointCommand.LOAD));

        actionManager.onWaypointReached(train, waypoint);
        verify(movementManager).initiateBraking();

        when(logisticsManager.isLoading()).thenReturn(true);

        // Train comes to a stop
        when(loco.getSpeed()).thenReturn(0);
        actionManager.onSpeedChanged(0);

        verify(train).load();

        // Loading finishes
        when(logisticsManager.isLoading()).thenReturn(false);
        when(autopilot.itinerary()).thenReturn(java.util.Optional.empty());
        actionManager.onLoadingFinished(train);

        verify(train).setSpeed(3);
    }

    @Test
    @DisplayName("should skip loading and resume speed if train stops outside station (overshoot)")
    void shouldSkipLoadingIfStoppedOutsideStation() {
        when(loco.getSpeed()).thenReturn(4);
        when(loco.getTargetSpeed()).thenReturn(4);

        Waypoint waypoint =
                new WaypointImpl(Waypoint.Type.STATION, 1, List.of(WaypointCommand.LOAD));

        actionManager.onWaypointReached(train, waypoint);
        verify(movementManager).initiateBraking();

        // Train overshoots station platform: station is null at stopped location
        when(loco.getSpeed()).thenReturn(0);
        when(logisticsManager.getStationAtTrain()).thenReturn(null);
        when(autopilot.itinerary()).thenReturn(java.util.Optional.empty());

        actionManager.onSpeedChanged(0);

        verify(train, never()).load();
        verify(train).setSpeed(4);
    }

    @Test
    @DisplayName("should execute STOP command with natural braking and deactivate autopilot")
    void shouldExecuteStopCommandWithNaturalBraking() {
        Waypoint waypoint =
                new WaypointImpl(Waypoint.Type.STATION, 1, List.of(WaypointCommand.STOP));

        actionManager.onWaypointReached(train, waypoint);

        verify(movementManager).initiateBraking();
        verify(train).setPendingManualMode(true);
        verify(autopilot).deactivate();
    }
}
