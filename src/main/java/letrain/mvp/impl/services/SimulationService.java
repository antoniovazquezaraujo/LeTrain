package letrain.mvp.impl.services;

import com.fasterxml.jackson.annotation.JsonIgnoreType;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import letrain.economy.EconomyManager;
import letrain.mvp.Model;
import letrain.track.CargoTypes;
import letrain.track.Station;
import letrain.vehicle.rail.Linker;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Stop;
import letrain.vehicle.rail.impl.Train;
import letrain.vehicle.rail.impl.Wagon;

/** Handles the core simulation logic: movement, industrial actions, and entity lifecycle. */
@JsonIgnoreType
public class SimulationService {
    private final Model model;

    public SimulationService(Model model) {
        this.model = model;
    }

    public void moveVehicles() {
        EconomyManager economyManager = model.getEconomyManager();
        model.getLocomotives().forEach(locomotive -> {
            if (locomotive.update()) {
                if (locomotive.isDirectorLinker()) {
                    economyManager.onTrainMoved(locomotive.getTrain());
                    economyManager.chargeFuel(locomotive.getTrain());
                }
            }
        });
    }

    public void handleIndustrialActions() {
        EconomyManager economyManager = model.getEconomyManager();

        // Regenerate cargo at all stations
        if (Math.random() < 0.05) {
            model.getStations().forEach(Station::regenerateCargo);
        }

        Map<Wagon, CargoState> wagonsPrevState = new HashMap<>();
        model.getWagons().forEach(
                w -> wagonsPrevState.put(w, new CargoState(w.getCargoType(), w.getCargoAmount())));

        Set<Train> processedTrains = new HashSet<>();
        model.getLocomotives().forEach(locomotive -> {
            Train train = locomotive.getTrain();
            if (train != null && !processedTrains.contains(train)) {
                processedTrains.add(train);
                processTrainLoading(train);
                processCargoEconomyEvents(train, wagonsPrevState, economyManager);
            }
        });
    }

    private void processTrainLoading(Train train) {
        if (train.getLogisticsManager().isLoading()) {
            int count = train.getLogisticsManager().getLoadingCount();
            if (count > 0) {
                train.getLogisticsManager().setLoadingCount(count - 1);
                Station station = train.getLogisticsManager().getStationAtTrain();
                if (station != null) {
                    train.getLogisticsManager().performIndustrialAction(station);
                }
            } else {
                Station station = train.getLogisticsManager().getStationAtTrain();
                if (station != null) {
                    if (train.getLogisticsManager().isUnloadingDirection()) {
                        station.notifyEndUnload(train);
                    } else {
                        station.notifyEndLoad(train);
                    }
                }
                train.getLogisticsManager().endLoadUnloadProcess();
            }
        }
    }

    private void processCargoEconomyEvents(Train train, Map<Wagon, CargoState> wagonsPrevState,
            EconomyManager economyManager) {
        for (Linker linker : train.getLinkers()) {
            if (linker instanceof Wagon) {
                Wagon wagon = (Wagon) linker;
                CargoState prevState = wagonsPrevState.get(wagon);
                if (prevState == null) {
                    continue;
                }

                int currentAmount = wagon.getCargoAmount();
                if (currentAmount > prevState.amount) {
                    if (prevState.amount == 0) {
                        economyManager.onLoadCargo(wagon);
                    }
                } else if (currentAmount < prevState.amount) {
                    int unitsUnloaded = prevState.amount - currentAmount;
                    int distance = calculateDistanceSinceLastStop(train);
                    economyManager.onUnloadCargo(wagon, wagon.getCargoType(), unitsUnloaded,
                            distance);
                }
            }
        }
    }

    private int calculateDistanceSinceLastStop(Train train) {
        if (train.getTrip() == null) {
            return 0;
        }
        List<Stop> stops = train.getTrip().getStopsList();
        if (stops.isEmpty()) {
            return 0;
        }

        Stop lastStop = stops.get(stops.size() - 1);
        Locomotive director = (Locomotive) train.getDirectorLinker();
        if (director == null) {
            return 0;
        }

        return director.getDistanceTraveled() - lastStop.distanceTraveled();
    }

    public void cleanupEntities() {
        AtomicBoolean removed = new AtomicBoolean(false);
        Set<Train> affectedTrains = new HashSet<>();

        model.getLocomotives().removeIf(locomotive -> {
            locomotive.updateDestroyTimer();
            if (locomotive.isDestroyed()) {
                Train train = locomotive.getTrain();
                if (train != null) {
                    affectedTrains.add(train);
                    train.getLinkers().remove(locomotive);
                    train.assignDefaultDirectorLinker();
                }
                if (locomotive.getTrack() != null) {
                    locomotive.getTrack().removeLinker();
                }
                removed.set(true);
                return true;
            }
            return false;
        });

        model.getWagons().removeIf(wagon -> {
            wagon.updateDestroyTimer();
            if (wagon.isDestroyed()) {
                Train train = wagon.getTrain();
                if (train != null) {
                    affectedTrains.add(train);
                    train.getLinkers().remove(wagon);
                }
                if (wagon.getTrack() != null) {
                    wagon.getTrack().removeLinker();
                }
                return true;
            }
            return false;
        });

        for (Train train : affectedTrains) {
            if (train.isEmpty()) {
                model.getBlockManager().releaseAll(train);
            }
        }

        if (removed.get()) {
            model.selectNextLocomotive();
        }
    }

    private static class CargoState {
        int amount;

        CargoState(CargoTypes t, int a) {
            amount = a;
        }
    }
}
