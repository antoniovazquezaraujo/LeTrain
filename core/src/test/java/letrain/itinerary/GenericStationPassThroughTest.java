package letrain.itinerary;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import letrain.itinerary.impl.TrainActionManager;
import letrain.itinerary.impl.WaypointImpl;
import letrain.map.Point;
import letrain.track.CargoTypes;
import letrain.track.Station;
import letrain.track.rail.RailTrack;
import letrain.vehicle.rail.Linker;
import letrain.vehicle.rail.TrainLogisticsManager;
import letrain.vehicle.rail.TrainMovementManager;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import letrain.vehicle.rail.impl.Wagon;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Regression guard for issue #468: a {@link Station} with role {@code GENERIC} and cargo
 * {@code NONE} (for example after being moved out of an industry zone) must be a pure
 * pass-through for itineraries that carry LOAD/UNLOAD commands: the train neither brakes nor
 * loads/unloads when it reaches the waypoint.
 *
 * <p>
 * Unlike the mock-based {@code TrainActionManagerTest}, this test wires the real {@code
 * TrainLogisticsManager} and a real {@code Station} so the GENERIC/NONE short-circuit of {@code
 * getCapableWagons} is exercised end-to-end.
 */
@DisplayName("Generic station waypoints with LOAD/UNLOAD are pass-through (issue #468)")
class GenericStationPassThroughTest {

    private Train newTrainOnStation(Station station) {
        Train train = mock(Train.class);
        Locomotive loco = mock(Locomotive.class);
        Wagon wagon = mock(Wagon.class);
        RailTrack track = new RailTrack();
        track.setPosition(new Point(0, 0));
        track.setComponent(station);
        station.setTrack(track);

        when(wagon.getTrack()).thenReturn(track);
        when(wagon.getCargoType()).thenReturn(CargoTypes.GOLD);
        when(wagon.getCargoAmount()).thenReturn(0);
        when(wagon.isFull()).thenReturn(false);
        when(wagon.getExclusiveCargoType()).thenReturn(CargoTypes.NONE);

        Deque<Linker> linkers = new ArrayDeque<>();
        linkers.add(wagon);
        when(train.getLinkers()).thenReturn(linkers);

        TrainLogisticsManager realLogistics = new letrain.vehicle.rail.impl.TrainLogisticsManager(
                train);
        when(train.getLogisticsManager()).thenReturn(realLogistics);
        when(train.getDirectorLinker()).thenReturn(loco);
        when(loco.getSpeed()).thenReturn(3);
        when(loco.getTargetSpeed()).thenReturn(3);
        when(train.getMovementManager()).thenReturn(mock(TrainMovementManager.class));
        AutoPilot autopilot = mock(AutoPilot.class);
        when(train.getAutopilot()).thenReturn(autopilot);
        when(autopilot.itinerary()).thenReturn(Optional.empty());
        return train;
    }

    @Test
    @DisplayName("train does NOT brake or load at a GENERIC station waypoint with LOAD command")
    void genericStation_withLoadWaypoint_trainPassesThrough() {
        // Arrange
        Station generic = new Station(1);
        generic.setName("Generic");
        generic.setRole(CargoTypes.StationRole.GENERIC);
        generic.setCargoType(CargoTypes.NONE);
        Train train = newTrainOnStation(generic);
        TrainMovementManager movementManager = train.getMovementManager();
        TrainActionManager actionManager = new TrainActionManager(train);
        Waypoint waypoint =
                new WaypointImpl(Waypoint.Type.STATION, 1, List.of(WaypointCommand.LOAD));

        // Act
        actionManager.onWaypointReached(train, waypoint);

        // Assert
        verify(movementManager, never()).initiateBraking();
        verify(train, never()).load();
        verify(train, never()).unload();
    }

    @Test
    @DisplayName("train does NOT brake or unload at a GENERIC station waypoint with UNLOAD command")
    void genericStation_withUnloadWaypoint_trainPassesThrough() {
        // Arrange
        Station generic = new Station(1);
        generic.setName("Generic");
        generic.setRole(CargoTypes.StationRole.GENERIC);
        generic.setCargoType(CargoTypes.NONE);
        Train train = newTrainOnStation(generic);
        TrainMovementManager movementManager = train.getMovementManager();
        TrainActionManager actionManager = new TrainActionManager(train);
        Waypoint waypoint =
                new WaypointImpl(Waypoint.Type.STATION, 1, List.of(WaypointCommand.UNLOAD));

        // Act
        actionManager.onWaypointReached(train, waypoint);

        // Assert
        verify(movementManager, never()).initiateBraking();
        verify(train, never()).load();
        verify(train, never()).unload();
    }

    @Test
    @DisplayName("control: train DOES brake at a PRODUCER station waypoint with LOAD command")
    void producerStation_withLoadWaypoint_trainBrakes() {
        // Arrange: positive control proving the pass-through is not vacuous.
        Station producer = new Station(2);
        producer.setName("Gold Mine");
        producer.setRole(CargoTypes.StationRole.PRODUCER);
        producer.setCargoType(CargoTypes.GOLD);
        producer.setStorage(50);
        Train train = newTrainOnStation(producer);
        TrainMovementManager movementManager = train.getMovementManager();
        TrainActionManager actionManager = new TrainActionManager(train);
        Waypoint waypoint =
                new WaypointImpl(Waypoint.Type.STATION, 2, List.of(WaypointCommand.LOAD));

        // Act
        actionManager.onWaypointReached(train, waypoint);

        // Assert
        verify(movementManager).initiateBraking();
    }
}
