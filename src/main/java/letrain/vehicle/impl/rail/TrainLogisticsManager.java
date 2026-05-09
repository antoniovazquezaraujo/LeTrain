package letrain.vehicle.impl.rail;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import letrain.track.CargoTypes;
import letrain.track.Station;
import letrain.vehicle.impl.Linker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TrainLogisticsManager {
    private static final Logger log = LoggerFactory.getLogger(TrainLogisticsManager.class);
    private static final int MAX_LOADING_COUNT = 80;

    private boolean isLoading = false;
    private int loadingCount = 0;
    private boolean isUnloadingDirection = false;
    
    @JsonIgnore
    private transient List<Wagon> currentCapableWagons = null;

    public boolean isLoading() {
        return isLoading;
    }

    public void setLoading(boolean loading) {
        isLoading = loading;
    }

    public int getLoadingCount() {
        return loadingCount;
    }

    public void setLoadingCount(int loadingCount) {
        this.loadingCount = loadingCount;
    }

    public boolean isUnloadingDirection() {
        return isUnloadingDirection;
    }

    public void setUnloadingDirection(boolean unloadingDirection) {
        isUnloadingDirection = unloadingDirection;
    }

    public void startLoadProcess(Train train, Station station) {
        this.isLoading = true;
        this.isUnloadingDirection = false;
        this.currentCapableWagons = getCapableWagons(train, station, false);
        this.loadingCount = MAX_LOADING_COUNT * currentCapableWagons.size();

        if (loadingCount == 0) {
            this.isLoading = false;
            this.currentCapableWagons = null;
        } else {
            station.notifyStartLoad(train);
        }
    }

    public void startUnloadProcess(Train train, Station station) {
        this.isLoading = true;
        this.isUnloadingDirection = true;
        this.currentCapableWagons = getCapableWagons(train, station, true);
        this.loadingCount = MAX_LOADING_COUNT * currentCapableWagons.size();

        if (loadingCount == 0) {
            this.isLoading = false;
            this.currentCapableWagons = null;
        } else {
            station.notifyStartUnload(train);
        }
    }

    public void endLoadUnloadProcess() {
        this.isLoading = false;
        this.loadingCount = 0;
        this.currentCapableWagons = null;
    }

    public List<Wagon> getCapableWagons(Train train, Station station, boolean isUnload) {
        List<Wagon> result = new ArrayList<>();
        CargoTypes stationCargo = station.getCargoType();
        for (Linker linker : train.getLinkers()) {
            if (linker instanceof Wagon) {
                Wagon wagon = (Wagon) linker;
                if (isUnload) {
                    if (wagon.getCargoAmount() > 0 && wagon.getCargoType() == stationCargo) {
                        result.add(wagon);
                    }
                } else {
                    boolean canLoadMore = !wagon.isFull() && (wagon.getCargoAmount() == 0 || wagon.getCargoType() == stationCargo);
                    if (canLoadMore && (wagon.getExclusiveCargoType() == CargoTypes.NONE || wagon.getExclusiveCargoType() == stationCargo)) {
                        result.add(wagon);
                    }
                }
            }
        }
        return result;
    }

    public boolean performIndustrialAction(Train train, Station station) {
        if (train.getDirectorLinker().getSpeed() != 0)
            return false;

        boolean anyActionTaken = false;
        double totalDistance = 0;
        int deliveryCount = 0;

        if (train.getLinkers().isEmpty())
            return false;

        if (currentCapableWagons == null || currentCapableWagons.isEmpty()) {
            currentCapableWagons = getCapableWagons(train, station, isUnloadingDirection);
        }

        if (currentCapableWagons.isEmpty())
            return false;

        int numCapableWagons = currentCapableWagons.size();
        int totalTicks = MAX_LOADING_COUNT * numCapableWagons;
        int currentTickInTotal = totalTicks - loadingCount;
        int wagonIndex = (currentTickInTotal - 1) / MAX_LOADING_COUNT;

        if (wagonIndex >= numCapableWagons)
            return false;

        Wagon wagon = currentCapableWagons.get(wagonIndex);
        int wagonTick = (currentTickInTotal - 1) % MAX_LOADING_COUNT;

        if (station.getRole() == CargoTypes.StationRole.PRODUCER) {
            int targetCargo = ((wagonTick + 1) * Wagon.MAX_CARGO_CAPACITY) / MAX_LOADING_COUNT;
            if (wagon.getCargoAmount() < targetCargo && !wagon.isFull()) {
                int toLoad = targetCargo - wagon.getCargoAmount();
                int taken = station.takeExportCargo(toLoad);
                if (taken > 0) {
                    wagon.load(taken);
                    wagon.setCargoType(station.getCargoType());
                    wagon.setLoadingPoint(station.getTrack().getPosition());
                    anyActionTaken = true;
                }
            }
        } else if (station.getRole() == CargoTypes.StationRole.CONSUMER) {
            int targetRemaining = Wagon.MAX_CARGO_CAPACITY - ((wagonTick + 1) * Wagon.MAX_CARGO_CAPACITY) / MAX_LOADING_COUNT;
            if (wagon.getCargoAmount() > targetRemaining && wagon.getCargoType() == station.getCargoType()) {
                int toUnload = wagon.getCargoAmount() - targetRemaining;
                wagon.unload(toUnload);
                station.receiveImportCargo(toUnload);

                if (wagon.getLoadingPoint() != null) {
                    totalDistance += letrain.map.Point.distance(wagon.getLoadingPoint(),
                            station.getTrack().getPosition());
                    deliveryCount++;
                }
                if (wagon.getCargoAmount() == 0) {
                    wagon.setCargoType(CargoTypes.NONE);
                    wagon.setLoadingPoint(null);
                }
                anyActionTaken = true;
            }
        }

        return anyActionTaken;
    }

    @JsonIgnore
    public Station getStationAtTrain(Train train) {
        for (Linker linker : train.getLinkers()) {
            letrain.track.Track track = linker.getTrack();
            if (track != null && track.getSensor() instanceof Station) {
                return (Station) track.getSensor();
            }
        }
        return null;
    }

    @JsonIgnore
    public CargoTypes getTrainCargoType(Train train) {
        CargoTypes firstCargoType = CargoTypes.NONE;
        for (Linker linker : train.getLinkers()) {
            if (linker instanceof Wagon) {
                Wagon wagon = (Wagon) linker;
                if (wagon.getCargoAmount() > 0) {
                    if (firstCargoType == CargoTypes.NONE) {
                        firstCargoType = wagon.getCargoType();
                    } else if (firstCargoType != wagon.getCargoType()) {
                        return null;
                    }
                }
            }
        }
        return firstCargoType;
    }
}
