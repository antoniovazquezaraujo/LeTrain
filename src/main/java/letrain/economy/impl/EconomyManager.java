package letrain.economy.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Semaphore;

import letrain.mvp.Presenter;
import letrain.track.Sensor;
import letrain.track.rail.ForkRailTrack;
import letrain.vehicle.impl.rail.Locomotive;
import letrain.vehicle.impl.rail.Train;
import letrain.vehicle.impl.rail.Wagon;

public class EconomyManager implements letrain.economy.EconomyManager {
    float totalIncome;
    float totalExpenses;
    float balance;

    Map<ExpenseType, Float> prices = new HashMap<>();
    
    int constructedNormalRailTracks = 0;
    int constructedBridgeRailTracks = 0;
    int constructedTunnelRailTracks = 0;
    int constructedForks = 0;
    int constructedStations = 0;
    int constructedSensors = 0;
    int constructedSemaphores = 0;
    int constructedLocomotives = 0;
    int constructedWagons = 0;
    int destroyedNormalRailTracks = 0;
    int destroyedBridgeRailTracks = 0;
    int destroyedTunnelRailTracks = 0;
    int destroyedForks = 0;
    int destroyedStations = 0;
    int destroyedSensors = 0;
    int destroyedSemaphores = 0;
    int destroyedLocomotives = 0;
    int destroyedWagons = 0;
    
    public EconomyManager() {
        prices.put(ExpenseType.CONSTRUCTED_NORMAL_RAIL_TRACK, 100f);
        prices.put(ExpenseType.CONSTRUCTED_BRIDGE_RAIL_TRACK, 20000f);
        prices.put(ExpenseType.CONSTRUCTED_TUNNEL_RAIL_TRACK, 70000f);
        prices.put(ExpenseType.CONSTRUCTED_FORK, 1000f);
        prices.put(ExpenseType.CONSTRUCTED_STATION, 1000f);
        prices.put(ExpenseType.CONSTRUCTED_SENSOR, 100f);
        prices.put(ExpenseType.CONSTRUCTED_SEMAPHORE, 100f);
        prices.put(ExpenseType.CONSTRUCTED_LOCOMOTIVE, 10000f);
        prices.put(ExpenseType.CONSTRUCTED_WAGON, 1000f);
        prices.put(ExpenseType.DESTROYED_NORMAL_RAIL_TRACK, 500f);
        prices.put(ExpenseType.DESTROYED_BRIDGE_RAIL_TRACK, 1000f);
        prices.put(ExpenseType.DESTROYED_TUNNEL_RAIL_TRACK, 1500f);
        prices.put(ExpenseType.DESTROYED_FORK, 500f);
        prices.put(ExpenseType.DESTROYED_STATION, 50000f);
        prices.put(ExpenseType.DESTROYED_SENSOR, 500f);
        prices.put(ExpenseType.DESTROYED_SEMAPHORE, 500f);
        prices.put(ExpenseType.DESTROYED_LOCOMOTIVE, 50000f);
        prices.put(ExpenseType.DESTROYED_WAGON, 5000f);
        prices.put(ExpenseType.LOAD_PASSENGERS, 1000f);
        prices.put(ExpenseType.UNLOAD_PASSENGERS, 1000f);
        prices.put(ExpenseType.TRAIN_MOVED, 1f);
        prices.put(ExpenseType.TRAIN_CRASHED, 100000f);
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
        float total = price*amount;
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
        float total = price*amount;
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
            case PLATFORM_TRACK:
                // TODO pending
                break;
        }
    }

    @Override
    public void onForkConstructed(ForkRailTrack fork) {
        this.constructedForks++;
    }

    @Override
    public void onStationConstructed() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'onStationConstructed'");
    }

    @Override
    public void onSensorConstructed(Sensor sensor) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'onSensorConstructed'");
    }

    @Override
    public void onSemaphoreConstructed(Semaphore semaphore) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'onSemaphoreConstructed'");
    }

    @Override
    public void onLocomotiveConstructed(Locomotive locomotive) {
        this.constructedLocomotives++;
    }

    @Override
    public void onWagonConstructed(Wagon wagon) {
        this.constructedWagons++;
    }

    @Override
    public void onRailTrackDestroyed(Presenter.TrackType rail) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'onRailDestroyed'");
    }

    @Override
    public void onForkDestroyed(ForkRailTrack fork) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'onForkDestroyed'");
    }

    @Override
    public void onStationDestroyed() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'onStationDestroyed'");
    }

    @Override
    public void onSensorDestroyed(Sensor sensor) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'onSensorDestroyed'");
    }

    @Override
    public void onSemaphoreDestroyed(Semaphore semaphore) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'onSemaphoreDestroyed'");
    }

    @Override
    public void onLocomotiveDestroyed(Locomotive locomotive) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'onLocomotiveDestroyed'");
    }

    @Override
    public void onWagonDestroyed(Wagon wagon) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'onWagonDestroyed'");
    }

    @Override
    public void onLoadPassengers(Train train) {
        earn(ExpenseType.LOAD_PASSENGERS, train.getLinkers().size());
    }

    @Override
    public void onUnloadPassengers(Train train) {

    }

    @Override
    public void onTrainMoved(Train train) {
        spend(ExpenseType.TRAIN_MOVED, train.getLinkers().size());
    }

    @Override
    public void onTrainCrashed(Train train) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'onTrainCrashed'");
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

}
