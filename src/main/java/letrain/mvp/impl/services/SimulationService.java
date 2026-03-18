package letrain.mvp.impl.services;

import letrain.economy.EconomyManager;
import letrain.mvp.Model;
import letrain.track.CargoTypes;
import letrain.track.Station;
import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.rail.Locomotive;
import letrain.vehicle.impl.rail.Stop;
import letrain.vehicle.impl.rail.Train;
import letrain.vehicle.impl.rail.Wagon;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import com.fasterxml.jackson.annotation.JsonIgnoreType;

/**
 * Handles the core simulation logic: movement, industrial actions, and entity lifecycle.
 */
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
        model.getWagons().forEach(w -> wagonsPrevState.put(w, new CargoState(w.getCargoType(), w.getCargoAmount())));

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
        if (train.isLoading()) {
            int count = train.getLoadingCount();
            if (count > 0) {
                train.setLoadingCount(count - 1);
                Station station = train.getStationAtTrain();
                if (station != null) {
                    train.performIndustrialAction(station);
                }
            } else {
                Station station = train.getStationAtTrain();
                if (station != null) {
                    if (train.isUnloadingDirection()) {
                        station.notifyEndUnload(train);
                    } else {
                        station.notifyEndLoad(train);
                    }
                }
                train.endLoadUnloadProcess();
            }
        }
    }

    private void processCargoEconomyEvents(Train train, Map<Wagon, CargoState> wagonsPrevState, EconomyManager economyManager) {
        for (Linker linker : train.getLinkers()) {
            if (linker instanceof Wagon) {
                Wagon wagon = (Wagon) linker;
                CargoState prevState = wagonsPrevState.get(wagon);
                if (prevState == null) continue;

                int currentAmount = wagon.getCargoAmount();
                if (currentAmount > prevState.amount) {
                    if (prevState.amount == 0) {
                        economyManager.onLoadCargo(wagon);
                    }
                } else if (currentAmount < prevState.amount) {
                    int unitsUnloaded = prevState.amount - currentAmount;
                    int distance = calculateDistanceSinceLastStop(train);
                    economyManager.onUnloadCargo(wagon, wagon.getCargoType(), unitsUnloaded, distance);
                }
            }
        }
    }

    private int calculateDistanceSinceLastStop(Train train) {
        if (train.getItinerary() == null) return 0;
        List<Stop> stops = train.getItinerary().getStopsList();
        if (stops.isEmpty()) return 0;
        
        Stop lastStop = stops.get(stops.size() - 1);
        Locomotive director = (Locomotive) train.getDirectorLinker();
        if (director == null) return 0;
        
        return director.getDistanceTraveled() - lastStop.distanceTraveled();
    }

    public void cleanupEntities() {
        AtomicBoolean removed = new AtomicBoolean(false);

        model.getLocomotives().forEach(locomotive -> {
            locomotive.updateDestroyTimer();
            if (locomotive.isDestroyed()) {
                removed.set(true);
            }
        });

        model.getLocomotives().removeIf(locomotive -> {
            if (locomotive.isDestroyed()) {
                locomotive.getTrack().removeLinker();
                return true;
            }
            return false;
        });

        model.getWagons().removeIf(wagon -> {
            wagon.updateDestroyTimer();
            if (wagon.isDestroyed()) {
                wagon.getTrack().removeLinker();
                return true;
            }
            return false;
        });

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
