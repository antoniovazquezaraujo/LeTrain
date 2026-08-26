package letrain.economy.impl;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

import letrain.mvp.Presenter;
import letrain.track.CargoTypes;
import letrain.track.RailSemaphore;
import letrain.track.Sensor;
import letrain.track.rail.ForkRailTrack;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import letrain.vehicle.rail.impl.Wagon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@id")
public class EconomyManager implements letrain.economy.EconomyManager {
    @com.fasterxml.jackson.annotation.JsonProperty("totalIncome")
    float totalIncome = 0;
    @com.fasterxml.jackson.annotation.JsonProperty("totalExpenses")
    float totalExpenses = 0;
    @com.fasterxml.jackson.annotation.JsonProperty("balance")
    float balance = 1000000f; // Initial balance as requested

    @com.fasterxml.jackson.annotation.JsonProperty("prices")
    Map<ExpenseType, Float> prices = new HashMap<>();
    @com.fasterxml.jackson.annotation.JsonProperty("cargoBaseValues")
    Map<CargoTypes, Float> cargoBaseValues = new HashMap<>();
    @com.fasterxml.jackson.annotation.JsonProperty("fuelCostPerMeter")
    private float fuelCostPerMeter = 0.5f;
    @com.fasterxml.jackson.annotation.JsonProperty("cargoLoadingFee")
    private float cargoLoadingFee = 100f;
    @com.fasterxml.jackson.annotation.JsonProperty("startingBalance")
    private float startingBalance = 0f;
    @com.fasterxml.jackson.annotation.JsonProperty("goldThreshold")
    private float goldThreshold = 0.30f;
    @com.fasterxml.jackson.annotation.JsonProperty("coalThreshold")
    private float coalThreshold = 0.25f;
    @com.fasterxml.jackson.annotation.JsonProperty("rubyThreshold")
    private float rubyThreshold = 0.35f;
    @com.fasterxml.jackson.annotation.JsonProperty("waterThreshold")
    private float waterThreshold = 110f;
    @com.fasterxml.jackson.annotation.JsonProperty("rockThreshold")
    private float rockThreshold = 130f;
    @com.fasterxml.jackson.annotation.JsonProperty("viewRadius")
    private int viewRadius = 15;
    private static final Logger log = LoggerFactory.getLogger(EconomyManager.class);
    @com.fasterxml.jackson.annotation.JsonProperty("eventLogManager")
    private letrain.mvp.impl.EventLogManager eventLogManager;

    @com.fasterxml.jackson.annotation.JsonProperty("constructedNormalRailTracks")
    int constructedNormalRailTracks = 0;
    @com.fasterxml.jackson.annotation.JsonProperty("constructedBridgeRailTracks")
    int constructedBridgeRailTracks = 0;
    @com.fasterxml.jackson.annotation.JsonProperty("constructedTunnelRailTracks")
    int constructedTunnelRailTracks = 0;
    @com.fasterxml.jackson.annotation.JsonProperty("constructedForks")
    int constructedForks = 0;
    @com.fasterxml.jackson.annotation.JsonProperty("constructedStations")
    int constructedStations = 0;
    @com.fasterxml.jackson.annotation.JsonProperty("constructedSensors")
    int constructedSensors = 0;
    @com.fasterxml.jackson.annotation.JsonProperty("constructedSemaphores")
    int constructedSemaphores = 0;
    @com.fasterxml.jackson.annotation.JsonProperty("constructedLocomotives")
    int constructedLocomotives = 0;
    @com.fasterxml.jackson.annotation.JsonProperty("constructedWagons")
    int constructedWagons = 0;
    @com.fasterxml.jackson.annotation.JsonProperty("destroyedNormalRailTracks")
    int destroyedNormalRailTracks = 0;
    @com.fasterxml.jackson.annotation.JsonProperty("destroyedBridgeRailTracks")
    int destroyedBridgeRailTracks = 0;
    @com.fasterxml.jackson.annotation.JsonProperty("destroyedTunnelRailTracks")
    int destroyedTunnelRailTracks = 0;
    @com.fasterxml.jackson.annotation.JsonProperty("destroyedForks")
    int destroyedForks = 0;
    @com.fasterxml.jackson.annotation.JsonProperty("destroyedStations")
    int destroyedStations = 0;
    @com.fasterxml.jackson.annotation.JsonProperty("destroyedSensors")
    int destroyedSensors = 0;
    @com.fasterxml.jackson.annotation.JsonProperty("destroyedSemaphores")
    int destroyedSemaphores = 0;
    @com.fasterxml.jackson.annotation.JsonProperty("destroyedLocomotives")
    int destroyedLocomotives = 0;
    @com.fasterxml.jackson.annotation.JsonProperty("destroyedWagons")
    int destroyedWagons = 0;

    protected EconomyManager() {
    }
 
    public EconomyManager(letrain.mvp.impl.EventLogManager eventLogManager) {
        this.eventLogManager = eventLogManager;
        prices.put(ExpenseType.CONSTRUCTED_NORMAL_RAIL_TRACK, 100f);
        prices.put(ExpenseType.CONSTRUCTED_BRIDGE_RAIL_TRACK, 20000f);
        prices.put(ExpenseType.CONSTRUCTED_TUNNEL_RAIL_TRACK, 70000f);
        prices.put(ExpenseType.CONSTRUCTED_FORK, 1000f);
        prices.put(ExpenseType.CONSTRUCTED_STATION, 100000f);
        prices.put(ExpenseType.CONSTRUCTED_SENSOR, 100f);
        prices.put(ExpenseType.CONSTRUCTED_SEMAPHORE, 100f);
        prices.put(ExpenseType.CONSTRUCTED_LOCOMOTIVE, 50000f);
        prices.put(ExpenseType.CONSTRUCTED_WAGON, 30000f);
        prices.put(ExpenseType.DESTROYED_NORMAL_RAIL_TRACK, 500f);
        prices.put(ExpenseType.DESTROYED_BRIDGE_RAIL_TRACK, 10000f);
        prices.put(ExpenseType.DESTROYED_TUNNEL_RAIL_TRACK, 30000f);
        prices.put(ExpenseType.DESTROYED_FORK, 500f);
        prices.put(ExpenseType.DESTROYED_STATION, 50000f);
        prices.put(ExpenseType.DESTROYED_SENSOR, 500f);
        prices.put(ExpenseType.DESTROYED_SEMAPHORE, 500f);
        prices.put(ExpenseType.DESTROYED_LOCOMOTIVE, 30000f);
        prices.put(ExpenseType.DESTROYED_WAGON, 10000f);
        prices.put(ExpenseType.LOAD_PASSENGERS, 1000f);
        prices.put(ExpenseType.UNLOAD_PASSENGERS, 1000f);
        prices.put(ExpenseType.TRAIN_MOVED, 0f);
        prices.put(ExpenseType.TRAIN_CRASHED, 1000000f);

        // Cargo values
        cargoBaseValues.put(CargoTypes.GOLD, 2000f);
        cargoBaseValues.put(CargoTypes.COAL, 200f);
        cargoBaseValues.put(CargoTypes.RUBY, 20000f);
        cargoBaseValues.put(CargoTypes.NONE, 0f);
    }

    @Override
    public float getCost(ExpenseType type) {
        return prices.get(type);
    }

    @Override
    public void spend(ExpenseType type) {
        Float amount = prices.get(type);
        totalExpenses += amount;
        balance -= amount;
    }

    @Override
    public void spend(ExpenseType type, int amount) {
        Float price = prices.get(type);
        float total = price * amount;
        totalExpenses += total;
        balance -= total;
    }

    @Override
    public void earn(ExpenseType type) {
        Float price = prices.get(type);
        totalIncome += price;
        balance += price;
    }

    @Override
    public void earn(ExpenseType type, int amount) {
        Float price = prices.get(type);
        float total = price * amount;
        totalIncome += (total);
        balance += (total);
    }

    @Override
    public void onRailTrackConstructed(Presenter.TrackType type) {
        switch (type) {
            case NORMAL_TRACK:
                constructedNormalRailTracks++;
                spend(ExpenseType.CONSTRUCTED_NORMAL_RAIL_TRACK);
                break;
            case BRIDGE_GATE_TRACK:
            case BRIDGE_TRACK:
                constructedBridgeRailTracks++;
                spend(ExpenseType.CONSTRUCTED_BRIDGE_RAIL_TRACK);
                break;
            case TUNNEL_GATE_TRACK:
            case TUNNEL_TRACK:
                constructedTunnelRailTracks++;
                spend(ExpenseType.CONSTRUCTED_TUNNEL_RAIL_TRACK);
                break;
            case STATION_TRACK:
                // TODO pending
                break;
        }
    }

    @Override
    public void onForkConstructed(ForkRailTrack fork) {
        this.constructedForks++;
        spend(ExpenseType.CONSTRUCTED_FORK);
    }

    @Override
    public void onStationConstructed() {
        this.constructedStations++;
        spend(ExpenseType.CONSTRUCTED_STATION);
    }

    @Override
    public void onSensorConstructed(Sensor sensor) {
        this.constructedSensors++;
        spend(ExpenseType.CONSTRUCTED_SENSOR);
    }

    @Override
    public void onSemaphoreConstructed(RailSemaphore semaphore) {
        this.constructedSemaphores++;
        spend(ExpenseType.CONSTRUCTED_SEMAPHORE);
    }

    @Override
    public void onLocomotiveConstructed(Locomotive locomotive) {
        this.constructedLocomotives++;
        spend(ExpenseType.CONSTRUCTED_LOCOMOTIVE);
    }

    @Override
    public void onWagonConstructed(Wagon wagon) {
        this.constructedWagons++;
        spend(ExpenseType.CONSTRUCTED_WAGON);
    }

    @Override
    public void onRailTrackDestroyed(Presenter.TrackType type) {
        switch (type) {
            case NORMAL_TRACK:
                destroyedNormalRailTracks++;
                spend(ExpenseType.DESTROYED_NORMAL_RAIL_TRACK);
                break;
            case BRIDGE_GATE_TRACK:
            case BRIDGE_TRACK:
                destroyedBridgeRailTracks++;
                spend(ExpenseType.DESTROYED_BRIDGE_RAIL_TRACK);
                break;
            case TUNNEL_GATE_TRACK:
            case TUNNEL_TRACK:
                destroyedTunnelRailTracks++;
                spend(ExpenseType.DESTROYED_TUNNEL_RAIL_TRACK);
                break;
            case STATION_TRACK:
                // Station destruction is handled by onStationDestroyed
                break;
        }
    }

    @Override
    public void onForkDestroyed(ForkRailTrack fork) {
        destroyedForks++;
        spend(ExpenseType.DESTROYED_FORK);
    }

    @Override
    public void onStationDestroyed() {
        destroyedStations++;
        spend(ExpenseType.DESTROYED_STATION);
    }

    @Override
    public void onSensorDestroyed(Sensor sensor) {
        destroyedSensors++;
        spend(ExpenseType.DESTROYED_SENSOR);
    }

    @Override
    public void onSemaphoreDestroyed(RailSemaphore semaphore) {
        destroyedSemaphores++;
        spend(ExpenseType.DESTROYED_SEMAPHORE);
    }

    @Override
    public void onLocomotiveDestroyed(Locomotive locomotive) {
        destroyedLocomotives++;
        spend(ExpenseType.DESTROYED_LOCOMOTIVE);
    }

    @Override
    public void onWagonDestroyed(Wagon wagon) {
        destroyedWagons++;
        spend(ExpenseType.DESTROYED_WAGON);
    }

    @Override
    public void onLoadPassengers(Train train, LocalDateTime elapsedTime, int totalDistanceTraveled,
            double linearDistanceToStart) {
        // We keep this for compatibility if needed, but logic moves to cargo
    }

    @Override
    public void chargeFuel(Train train) {
        float cost = Math.abs(fuelCostPerMeter * train.getLinkers().size());
        totalExpenses += cost;
        balance -= cost;
    }

    @Override
    public void onLoadCargo(Wagon wagon) {
        float fee = cargoLoadingFee;
        totalExpenses += fee;
        balance -= fee;
    }

    @Override
    public void onUnloadCargo(Wagon wagon, CargoTypes type, int amount, int distance) {
        float baseValue = cargoBaseValues.getOrDefault(type, 0f);
        // Payment = (Quantity * BaseValue) * (1 + TravelDistance / 100)
        float payment = ((float) amount * baseValue) * (1f + (float) distance / 100f);
        totalIncome += payment;
        balance += payment;
    }

    @Override
    public void onTrainMoved(Train train) {
        spend(ExpenseType.TRAIN_MOVED, train.getLinkers().size());
    }

    @Override
    public void onTrainCrashed(Train train) {
        spend(ExpenseType.TRAIN_CRASHED);
        eventLogManager.addEntry("CRASH! Train " + train.getId() + " crashed!");
    }

    public int getConstructedNormalRailTracks() {
        return constructedNormalRailTracks;
    }

    public int getConstructedBridgeRailTracks() {
        return constructedBridgeRailTracks;
    }

    public int getConstructedTunnelRailTracks() {
        return constructedTunnelRailTracks;
    }

    public int getConstructedForks() {
        return constructedForks;
    }

    public int getConstructedStations() {
        return constructedStations;
    }

    public int getConstructedSensors() {
        return constructedSensors;
    }

    public int getConstructedSemaphores() {
        return constructedSemaphores;
    }

    public int getConstructedLocomotives() {
        return constructedLocomotives;
    }

    public int getConstructedWagons() {
        return constructedWagons;
    }

    public int getDestroyedNormalRailTracks() {
        return destroyedNormalRailTracks;
    }

    public int getDestroyedBridgeRailTracks() {
        return destroyedBridgeRailTracks;
    }

    public int getDestroyedTunnelRailTracks() {
        return destroyedTunnelRailTracks;
    }

    public int getDestroyedForks() {
        return destroyedForks;
    }

    public int getDestroyedStations() {
        return destroyedStations;
    }

    public int getDestroyedSensors() {
        return destroyedSensors;
    }

    public int getDestroyedSemaphores() {
        return destroyedSemaphores;
    }

    public int getDestroyedLocomotives() {
        return destroyedLocomotives;
    }

    public int getDestroyedWagons() {
        return destroyedWagons;
    }

    @Override
    public float getBalance() {
        return balance;
    }

    @Override
    public float getTotalIncome() {
        return totalIncome;
    }

    @Override
    public float getTotalExpenses() {
        return totalExpenses;
    }

    @Override
    public void reloadConfig() {
        File configFile = new File("economy.properties");
        if (!configFile.exists()) {
            return;
        }

        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(configFile)) {
            props.load(fis);
            log.info("Loading economy configuration from {}", configFile.getAbsolutePath());

            // Load general costs
            fuelCostPerMeter = Float
                    .parseFloat(props.getProperty("fuelCostPerMeter", String.valueOf(fuelCostPerMeter)));
            cargoLoadingFee = Float.parseFloat(props.getProperty("cargoLoadingFee", String.valueOf(cargoLoadingFee)));
            float newStartingBalance = Float
                    .parseFloat(props.getProperty("startingBalance", String.valueOf(startingBalance)));

            // Only update current balance if it's the very beginning of the game (total
            // income/expenses are zero)
            if (totalIncome == 0 && totalExpenses == 0) {
                balance = newStartingBalance;
            }
            startingBalance = newStartingBalance;

            // Load Cargo Probabilities (0-100) and convert to thresholds
            if (props.containsKey("prob.GOLD")) {
                float prob = Float.parseFloat(props.getProperty("prob.GOLD"));
                goldThreshold = 1.0f - (prob / 100.0f);
            } else {
                goldThreshold = Float.parseFloat(props.getProperty("threshold.GOLD", "0.28"));
            }

            if (props.containsKey("prob.COAL")) {
                float prob = Float.parseFloat(props.getProperty("prob.COAL"));
                coalThreshold = 1.0f - (prob / 100.0f);
            } else {
                coalThreshold = Float.parseFloat(props.getProperty("threshold.COAL", "0.28"));
            }

            if (props.containsKey("prob.RUBY")) {
                float prob = Float.parseFloat(props.getProperty("prob.RUBY"));
                rubyThreshold = 1.0f - (prob / 100.0f);
            } else {
                rubyThreshold = Float.parseFloat(props.getProperty("threshold.RUBY", "0.28"));
            }

            // Load Terrain Probabilities (0-100) and convert to thresholds
            if (props.containsKey("prob.WATER")) {
                float prob = Float.parseFloat(props.getProperty("prob.WATER"));
                waterThreshold = (prob / 100.0f) * 255.0f;
            } else {
                waterThreshold = Float.parseFloat(props.getProperty("threshold.WATER", "130"));
            }

            if (props.containsKey("prob.ROCK")) {
                float prob = Float.parseFloat(props.getProperty("prob.ROCK"));
                rockThreshold = 255.0f - ((prob / 100.0f) * 255.0f);
            } else {
                rockThreshold = Float.parseFloat(props.getProperty("threshold.ROCK", "180"));
            }
            viewRadius = Integer.parseInt(props.getProperty("map.VIEW_RADIUS", "5"));

            // Load ExpenseType prices
            for (ExpenseType type : ExpenseType.values()) {
                String key = "price." + type.name();
                if (props.containsKey(key)) {
                    prices.put(type, Float.parseFloat(props.getProperty(key)));
                }
            }

            // Load CargoTypes values
            for (CargoTypes type : CargoTypes.values()) {
                String key = "cargo." + type.name();
                if (props.containsKey(key)) {
                    cargoBaseValues.put(type, Float.parseFloat(props.getProperty(key)));
                }
            }
        } catch (IOException | NumberFormatException e) {
            log.error("Error loading economy configuration: {}", e.getMessage());
        }
    }

    @Override
    public float getGoldThreshold() {
        return goldThreshold;
    }

    @Override
    public float getCoalThreshold() {
        return coalThreshold;
    }

    @Override
    public float getRubyThreshold() {
        return rubyThreshold;
    }

    @Override
    public float getWaterThreshold() {
        return waterThreshold;
    }

    @Override
    public float getRockThreshold() {
        return rockThreshold;
    }

    @Override
    public int getViewRadius() {
        return viewRadius;
    }

}
