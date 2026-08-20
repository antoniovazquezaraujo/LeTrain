package letrain.vehicle.rail;

import letrain.track.CargoTypes;
import letrain.track.Station;
import letrain.vehicle.rail.impl.Wagon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public interface TrainLogisticsManager {
    Logger log = LoggerFactory.getLogger(letrain.vehicle.rail.impl.TrainLogisticsManager.class);
    int MAX_LOADING_COUNT = 80;

    boolean isLoading();

    void setLoading(boolean loading);

    int getLoadingCount();

    void setLoadingCount(int loadingCount);

    boolean isUnloadingDirection();

    void setUnloadingDirection(boolean unloadingDirection);

    void startLoadProcess(Station station);

    void startUnloadProcess(Station station);

    void endLoadUnloadProcess();

    List<Wagon> getCapableWagons(Station station, boolean isUnload);

    boolean performIndustrialAction(Station station);

    Station getStationAtTrain();


    CargoTypes getTrainCargoType();
}
