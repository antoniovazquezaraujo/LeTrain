package letrain.track;

import java.util.ArrayList;
import java.util.List;

import letrain.vehicle.impl.rail.Train;
import letrain.visitor.Visitor;

public class Station extends Sensor {

    private List<StationEventListener> stationListeners = new ArrayList<>();
    private List<StationEventListener> systemStationListeners = new ArrayList<>();

    public void addStationEventListener(StationEventListener listener) {
        stationListeners.add(listener);
    }

    public void addSystemStationEventListener(StationEventListener listener) {
        systemStationListeners.add(listener);
    }

    public void removeStationEventListener(StationEventListener listener) {
        stationListeners.remove(listener);
    }

    public void removeAllStationEventListeners() {
        stationListeners.clear();
    }

    public void notifyLoad(Train train) {
        for (StationEventListener l : stationListeners) {
            l.onLoad(train);
        }
        for (StationEventListener l : systemStationListeners) {
            l.onLoad(train);
        }
    }

    public void notifyUnload(Train train) {
        for (StationEventListener l : stationListeners) {
            l.onUnload(train);
        }
        for (StationEventListener l : systemStationListeners) {
            l.onUnload(train);
        }
    }

    public void notifyStartLoad(Train train) {
        for (StationEventListener l : stationListeners) {
            l.onStartLoad(train);
        }
        for (StationEventListener l : systemStationListeners) {
            l.onStartLoad(train);
        }
    }

    public void notifyEndLoad(Train train) {
        for (StationEventListener l : stationListeners) {
            l.onEndLoad(train);
        }
        for (StationEventListener l : systemStationListeners) {
            l.onEndLoad(train);
        }
    }

    public void notifyStartUnload(Train train) {
        for (StationEventListener l : stationListeners) {
            l.onStartUnload(train);
        }
        for (StationEventListener l : systemStationListeners) {
            l.onStartUnload(train);
        }
    }

    public void notifyEndUnload(Train train) {
        for (StationEventListener l : stationListeners) {
            l.onEndUnload(train);
        }
        for (StationEventListener l : systemStationListeners) {
            l.onEndUnload(train);
        }
    }

    public Station(int id) {
        super(id);
    }

    @Override
    public void onEnterTrain(Train train, boolean isForward) {
        super.onEnterTrain(train, isForward);
        train.setStationId(getId());
        for (StationEventListener l : stationListeners) {
            l.onEnterTrain(train, isForward);
        }
        for (StationEventListener l : systemStationListeners) {
            l.onEnterTrain(train, isForward);
        }
    }

    @Override
    public void onExitTrain(Train train, boolean isForward) {
        super.onExitTrain(train, isForward);
        train.setStationId(0);
        for (StationEventListener l : stationListeners) {
            l.onExitTrain(train, isForward);
        }
        for (StationEventListener l : systemStationListeners) {
            l.onExitTrain(train, isForward);
        }
    }

    @Override
    public String toString() {
        return "Station [id=" + getId() + "]";
    }

    private String name;
    private int storage = 0;
    private int maxStorage = 500;
    private int industryCount = 0;
    private CargoTypes cargoType = CargoTypes.NONE;
    private CargoTypes.StationRole role = CargoTypes.StationRole.GENERIC;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public CargoTypes getCargoType() {
        return cargoType;
    }

    public void setCargoType(CargoTypes cargoType) {
        this.cargoType = cargoType;
    }

    public CargoTypes.StationRole getRole() {
        return role;
    }

    public void setRole(CargoTypes.StationRole role) {
        this.role = role;
    }

    public int getIndustryCount() {
        return industryCount;
    }

    public void setIndustryCount(int industryCount) {
        this.industryCount = industryCount;
        // Base max storage 500 + 100 per additional industry block
        this.maxStorage = 500 + (industryCount * 100);
    }

    public int getStorage() {
        return storage;
    }

    public void setStorage(int storage) {
        this.storage = storage;
    }

    public int getMaxStorage() {
        return maxStorage;
    }

    // Compatibility methods redirected to storage
    public int getExportCargoAmount() {
        return (role == CargoTypes.StationRole.PRODUCER) ? storage : 0;
    }

    public int getImportCargoAmount() {
        return (role == CargoTypes.StationRole.CONSUMER) ? storage : 0;
    }

    public int getCargoAmount() {
        return storage;
    }

    public void regenerateCargo() {
        if (role == CargoTypes.StationRole.PRODUCER && storage < maxStorage) {
            // Regeneration scales with density: Base + bonus per block
            int increment = 1 + (industryCount / 2);
            storage += increment;
            if (storage > maxStorage)
                storage = maxStorage;
        }
        // Consumers might "consume" their delivery over time to create more demand
        // space,
        // but for now let's keep it simple: they just receive.
        // If we want they to "need" more, we could decrease storage (demand) over time?
        // Wait, storage for consumer = current STOCK received.
        // If storage is high, they are "full".
        if (role == CargoTypes.StationRole.CONSUMER && storage > 0) {
            if (Math.random() < 0.1) {
                storage--;
            }
        }
    }

    public int takeExportCargo(int amount) {
        int taken = Math.min(amount, storage);
        storage -= taken;
        return taken;
    }

    public void receiveImportCargo(int amount) {
        storage += amount;
        if (storage > maxStorage) {
            storage = maxStorage;
        }
    }

    /**
     * Returns the number of cargo units that can be transferred per tick.
     * Scales with surrounding industry density.
     */
    public int getTransferRate() {
        // Base transfer rate of 1, plus 1 for every 3 industry blocks
        return 1 + (industryCount / 3);
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visitStation(this);
    }
}
