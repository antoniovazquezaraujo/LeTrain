package letrain.track;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;
import letrain.utils.SerializationHelper;
import letrain.vehicle.rail.impl.Train;
import letrain.visitor.Visitor;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@com.fasterxml.jackson.annotation.JsonTypeName("Station")
public class Station extends Sensor {

    private static final int BASE_MAX_STORAGE = 500;
    private static final int STORAGE_PER_INDUSTRY = 100;
    private static final int BASE_REGEN_RATE = 1;
    private static final int REGEN_RATE_PER_INDUSTRY_DIVISOR = 2;
    private static final double CONSUMER_CONSUMPTION_CHANCE = 0.1;
    private static final int BASE_TRANSFER_RATE = 1;
    private static final int TRANSFER_RATE_PER_INDUSTRY_DIVISOR = 3;

    @JsonIgnore private transient List<StationEventListener> stationListeners = new ArrayList<>();

    @JsonIgnore
    private transient List<StationEventListener> systemStationListeners = new ArrayList<>();

    public void addStationEventListener(StationEventListener listener) {
        if (stationListeners == null) stationListeners = new ArrayList<>();
        stationListeners.add(listener);
    }

    public void addSystemStationEventListener(StationEventListener listener) {
        if (systemStationListeners == null) systemStationListeners = new ArrayList<>();
        systemStationListeners.add(listener);
    }

    public void removeStationEventListener(StationEventListener listener) {
        if (stationListeners != null) stationListeners.remove(listener);
    }

    public void removeAllStationEventListeners() {
        if (stationListeners != null) stationListeners.clear();
    }

    public void notifyLoad(Train train) {
        if (stationListeners != null) {
            for (StationEventListener l : stationListeners) {
                l.onLoad(train);
            }
        }
        if (systemStationListeners != null) {
            for (StationEventListener l : systemStationListeners) {
                l.onLoad(train);
            }
        }
    }

    public void notifyUnload(Train train) {
        if (stationListeners != null) {
            for (StationEventListener l : stationListeners) {
                l.onUnload(train);
            }
        }
        if (systemStationListeners != null) {
            for (StationEventListener l : systemStationListeners) {
                l.onUnload(train);
            }
        }
    }

    public void notifyStartLoad(Train train) {
        if (stationListeners != null) {
            for (StationEventListener l : stationListeners) {
                l.onStartLoad(train);
            }
        }
        if (systemStationListeners != null) {
            for (StationEventListener l : systemStationListeners) {
                l.onStartLoad(train);
            }
        }
    }

    public void notifyEndLoad(Train train) {
        if (stationListeners != null) {
            for (StationEventListener l : stationListeners) {
                l.onEndLoad(train);
            }
        }
        if (systemStationListeners != null) {
            for (StationEventListener l : systemStationListeners) {
                l.onEndLoad(train);
            }
        }
    }

    public void notifyStartUnload(Train train) {
        if (stationListeners != null) {
            for (StationEventListener l : stationListeners) {
                l.onStartUnload(train);
            }
        }
        if (systemStationListeners != null) {
            for (StationEventListener l : systemStationListeners) {
                l.onStartUnload(train);
            }
        }
    }

    public void notifyEndUnload(Train train) {
        if (stationListeners != null) {
            for (StationEventListener l : stationListeners) {
                l.onEndUnload(train);
            }
        }
        if (systemStationListeners != null) {
            for (StationEventListener l : systemStationListeners) {
                l.onEndUnload(train);
            }
        }
    }

    public Station() {}

    public Station(int id) {
        super(id);
    }

    /**
     * Reinitializes transient fields after deserialization. Ensures listener collections are not
     * null to prevent NPE.
     */
    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        this.stationListeners = SerializationHelper.ensureListInitialized(stationListeners);
        this.systemStationListeners =
                SerializationHelper.ensureListInitialized(systemStationListeners);
    }

    @Override
    public void onSensorEnter(Train train, boolean isForward) {
        train.setStationId(getId());
        super.onSensorEnter(train, isForward);
        notifyStationEvent(train, true, isForward);
    }

    @Override
    public void onSensorExit(Train train, boolean isForward) {
        super.onSensorExit(train, isForward);
        train.setStationId(0);
        notifyStationEvent(train, false, isForward);
    }

    public void notifyStationEvent(Train train, boolean isEnter, boolean isForward) {
        if (stationListeners != null) {
            for (StationEventListener l : stationListeners) {
                if (isEnter) l.onEnterTrain(train, isForward);
                else l.onExitTrain(train, isForward);
            }
        }
        if (systemStationListeners != null) {
            for (StationEventListener l : systemStationListeners) {
                if (isEnter) l.onEnterTrain(train, isForward);
                else l.onExitTrain(train, isForward);
            }
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
        this.maxStorage = BASE_MAX_STORAGE + (industryCount * STORAGE_PER_INDUSTRY);
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
    @JsonIgnore
    public int getExportCargoAmount() {
        return (role == CargoTypes.StationRole.PRODUCER) ? storage : 0;
    }

    @JsonIgnore
    public int getImportCargoAmount() {
        return (role == CargoTypes.StationRole.CONSUMER) ? storage : 0;
    }

    @JsonIgnore
    public int getCargoAmount() {
        return storage;
    }

    public void regenerateCargo() {
        if (role == CargoTypes.StationRole.PRODUCER && storage < maxStorage) {
            // Regeneration scales with density: Base + bonus per block
            int increment = BASE_REGEN_RATE + (industryCount / REGEN_RATE_PER_INDUSTRY_DIVISOR);
            storage += increment;
            if (storage > maxStorage) storage = maxStorage;
        }
        // Consumers might "consume" their delivery over time to create more demand
        // space,
        // but for now let's keep it simple: they just receive.
        // If we want they to "need" more, we could decrease storage (demand) over time?
        // Wait, storage for consumer = current STOCK received.
        // If storage is high, they are "full".
        if (role == CargoTypes.StationRole.CONSUMER && storage > 0) {
            if (Math.random() < CONSUMER_CONSUMPTION_CHANCE) {
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
     * Returns the number of cargo units that can be transferred per tick. Scales with surrounding
     * industry density.
     */
    @JsonIgnore
    public int getTransferRate() {
        // Base transfer rate of 1, plus 1 for every 3 industry blocks
        return BASE_TRANSFER_RATE + (industryCount / TRANSFER_RATE_PER_INDUSTRY_DIVISOR);
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visitStation(this);
    }
}
