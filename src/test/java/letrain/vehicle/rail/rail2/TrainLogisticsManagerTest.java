package letrain.vehicle.rail.rail2;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import letrain.map.Point;
import letrain.track.CargoTypes;
import letrain.track.Station;
import letrain.track.rail.RailTrack;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import letrain.vehicle.rail.impl.TrainLogisticsManager;
import letrain.vehicle.rail.impl.Wagon;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TrainLogisticsManagerTest {

    private Train train;
    private Locomotive loco;
    private Wagon wagon;
    private TrainLogisticsManager logistics;

    @BeforeEach
    void setUp() {
        train = new Train(1);
        loco = new Locomotive(10, 'L');
        wagon = new Wagon('W');

        train.pushBack(loco);
        train.pushBack(wagon);
        train.rebind();

        logistics = (TrainLogisticsManager) train.getLogisticsManager();
    }

    @Test
    @DisplayName("should load cargo into wagon when locomotive is stopped at station")
    void shouldLoadCargoWhenLocomotiveIsStopped() {
        loco.setCurrentSpeed(0);
        assertEquals(0, loco.getSpeed());

        Station producerStation = new Station(1);
        producerStation.setRole(CargoTypes.StationRole.PRODUCER);
        producerStation.setCargoType(CargoTypes.COAL);
        producerStation.setStorage(50);

        RailTrack track = mock(RailTrack.class);
        when(track.getPosition()).thenReturn(new Point(5, 5));
        producerStation.setTrack(track);

        logistics.startLoadProcess(producerStation);
        assertTrue(logistics.isLoading());
        assertEquals(TrainLogisticsManager.MAX_LOADING_COUNT, logistics.getLoadingCount());

        // Perform industrial action across ticks
        for (int i = 0; i < TrainLogisticsManager.MAX_LOADING_COUNT; i++) {
            logistics.setLoadingCount(logistics.getLoadingCount() - 1);
            logistics.performIndustrialAction(producerStation);
        }

        assertEquals(50, wagon.getCargoAmount());
        assertEquals(CargoTypes.COAL, wagon.getCargoType());
        assertEquals(0, producerStation.getStorage());
    }

    @Test
    @DisplayName("should unload cargo from wagon when locomotive is stopped at station")
    void shouldUnloadCargoWhenLocomotiveIsStopped() {
        loco.setCurrentSpeed(0);
        wagon.load(50);
        wagon.setCargoType(CargoTypes.COAL);

        Station consumerStation = new Station(2);
        consumerStation.setRole(CargoTypes.StationRole.CONSUMER);
        consumerStation.setCargoType(CargoTypes.COAL);
        consumerStation.setStorage(0);

        RailTrack track = mock(RailTrack.class);
        when(track.getPosition()).thenReturn(new Point(10, 10));
        consumerStation.setTrack(track);

        logistics.startUnloadProcess(consumerStation);
        assertTrue(logistics.isLoading());

        for (int i = 0; i < TrainLogisticsManager.MAX_LOADING_COUNT; i++) {
            logistics.setLoadingCount(logistics.getLoadingCount() - 1);
            logistics.performIndustrialAction(consumerStation);
        }

        assertEquals(0, wagon.getCargoAmount());
        assertEquals(50, consumerStation.getStorage());
    }
}
