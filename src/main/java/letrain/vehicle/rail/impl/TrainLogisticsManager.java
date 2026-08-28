package letrain.vehicle.rail.impl;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.ArrayList;
import java.util.List;
import letrain.track.CargoTypes;
import letrain.track.Station;
import letrain.vehicle.rail.Linker;

@JsonIgnoreProperties(ignoreUnknown = true)
public class TrainLogisticsManager implements letrain.vehicle.rail.TrainLogisticsManager {

    private boolean isLoading = false;
    private int loadingCount = 0;
    private boolean isUnloadingDirection = false;
    private Train train;

    public TrainLogisticsManager(Train train) {
        this.train = train;
    }

    @JsonIgnore private transient List<Wagon> currentCapableWagons = null;

    @Override
    public boolean isLoading() {
        return isLoading;
    }

    @Override
    public void setLoading(boolean loading) {

        isLoading = loading;
    }

    @Override
    public int getLoadingCount() {

        return loadingCount;
    }

    @Override
    public void setLoadingCount(int loadingCount) {

        this.loadingCount = loadingCount;
    }

    @Override
    public boolean isUnloadingDirection() {
        return isUnloadingDirection;
    }

    @Override
    public void setUnloadingDirection(boolean unloadingDirection) {

        isUnloadingDirection = unloadingDirection;
    }

    @Override
    public void startLoadProcess(Station station) {
        this.isLoading = true;
        this.isUnloadingDirection = false;
        this.currentCapableWagons = getCapableWagons(station, false);
        this.loadingCount = MAX_LOADING_COUNT * currentCapableWagons.size();

        if (loadingCount == 0) {
            this.isLoading = false;
            this.currentCapableWagons = null;
        } else {
            station.notifyStartLoad(this.train);
        }
    }

    @Override
    public void startUnloadProcess(Station station) {
        this.isLoading = true;
        this.isUnloadingDirection = true;
        this.currentCapableWagons = getCapableWagons(station, true);
        this.loadingCount = MAX_LOADING_COUNT * currentCapableWagons.size();

        if (loadingCount == 0) {
            this.isLoading = false;
            this.currentCapableWagons = null;
        } else {
            station.notifyStartUnload(this.train);
        }
    }

    @Override
    public void endLoadUnloadProcess() {
        this.isLoading = false;
        this.loadingCount = 0;
        this.currentCapableWagons = null;
        if (this.train != null) {
            this.train.notifyLoadingFinished();
        }
    }

    @Override
    public List<Wagon> getCapableWagons(Station station, boolean isUnload) {
        List<Wagon> result = new ArrayList<>();
        if (station == null) {
            return result;
        }
        CargoTypes stationCargo = station.getCargoType();
        if (stationCargo == null
                || stationCargo == CargoTypes.NONE
                || station.getRole() == CargoTypes.StationRole.GENERIC) {
            return result;
        }
        for (Linker linker : this.train.getLinkers()) {
            if (linker instanceof Wagon) {
                Wagon wagon = (Wagon) linker;
                if (isUnload) {
                    if (wagon.getCargoAmount() > 0 && wagon.getCargoType() == stationCargo) {
                        result.add(wagon);
                    }
                } else {
                    boolean canLoadMore =
                            !wagon.isFull()
                                    && (wagon.getCargoAmount() == 0
                                            || wagon.getCargoType() == stationCargo);
                    if (canLoadMore
                            && (wagon.getExclusiveCargoType() == CargoTypes.NONE
                                    || wagon.getExclusiveCargoType() == stationCargo)) {
                        result.add(wagon);
                    }
                }
            }
        }
        return result;
    }

    @Override
    public boolean performIndustrialAction(Station station) {
        if (this.train.getDirectorLinker() != null
                && this.train.getDirectorLinker().getSpeed() != 0) return false;

        boolean anyActionTaken = false;
        double totalDistance = 0;
        int deliveryCount = 0;

        if (this.train.getLinkers().isEmpty()) {
            return false;
        }

        if (currentCapableWagons == null || currentCapableWagons.isEmpty()) {
            currentCapableWagons = getCapableWagons(station, isUnloadingDirection);
        }

        if (currentCapableWagons.isEmpty()) {
            return false;
        }

        int numCapableWagons = currentCapableWagons.size();
        int totalTicks = MAX_LOADING_COUNT * numCapableWagons;
        int currentTickInTotal = totalTicks - loadingCount;
        int wagonIndex = (currentTickInTotal - 1) / MAX_LOADING_COUNT;

        if (wagonIndex >= numCapableWagons) {
            return false;
        }

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
            int targetRemaining =
                    Wagon.MAX_CARGO_CAPACITY
                            - ((wagonTick + 1) * Wagon.MAX_CARGO_CAPACITY) / MAX_LOADING_COUNT;
            if (wagon.getCargoAmount() > targetRemaining
                    && wagon.getCargoType() == station.getCargoType()) {
                int toUnload = wagon.getCargoAmount() - targetRemaining;
                wagon.unload(toUnload);
                station.receiveImportCargo(toUnload);

                if (wagon.getLoadingPoint() != null) {
                    totalDistance +=
                            letrain.map.Point.distance(
                                    wagon.getLoadingPoint(), station.getTrack().getPosition());
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
    @Override
    public Station getStationAtTrain() {
        for (Linker linker : this.train.getLinkers()) {
            letrain.track.Track track = linker.getTrack();
            if (track != null && track.getSensor() instanceof Station) {
                return (Station) track.getSensor();
            }
        }
        return null;
    }

    @JsonIgnore
    @Override
    public CargoTypes getTrainCargoType() {
        CargoTypes firstCargoType = CargoTypes.NONE;
        for (Linker linker : this.train.getLinkers()) {
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
