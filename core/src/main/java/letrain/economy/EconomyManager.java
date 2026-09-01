package letrain.economy;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import java.time.LocalDateTime;
import letrain.mvp.Presenter;
import letrain.track.RailSemaphore;
import letrain.track.Sensor;
import letrain.track.rail.ForkRailTrack;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import letrain.vehicle.rail.impl.Wagon;

@JsonDeserialize(as = letrain.economy.impl.EconomyManager.class)
public interface EconomyManager {
    enum ExpenseType {
        CONSTRUCTED_NORMAL_RAIL_TRACK, CONSTRUCTED_BRIDGE_RAIL_TRACK, CONSTRUCTED_TUNNEL_RAIL_TRACK, CONSTRUCTED_FORK, CONSTRUCTED_STATION, CONSTRUCTED_SENSOR, CONSTRUCTED_SEMAPHORE, CONSTRUCTED_LOCOMOTIVE, CONSTRUCTED_WAGON, DESTROYED_NORMAL_RAIL_TRACK, DESTROYED_BRIDGE_RAIL_TRACK, DESTROYED_TUNNEL_RAIL_TRACK, DESTROYED_FORK, DESTROYED_STATION, DESTROYED_SENSOR, DESTROYED_SEMAPHORE, DESTROYED_LOCOMOTIVE, DESTROYED_WAGON, LOAD_PASSENGERS, UNLOAD_PASSENGERS, TRAIN_MOVED, TRAIN_CRASHED
    }

    float getBalance();

    float getTotalIncome();

    float getTotalExpenses();

    float getCost(ExpenseType type);

    void spend(ExpenseType type);

    void spend(ExpenseType type, int amount);

    void earn(ExpenseType type);

    void earn(ExpenseType type, int amount);

    void onRailTrackConstructed(Presenter.TrackType type);

    void onForkConstructed(ForkRailTrack fork);

    void onStationConstructed();

    void onSensorConstructed(Sensor sensor);

    void onSemaphoreConstructed(RailSemaphore semaphore);

    void onLocomotiveConstructed(Locomotive locomotive);

    void onWagonConstructed(Wagon wagon);

    void onRailTrackDestroyed(Presenter.TrackType type);

    void onForkDestroyed(ForkRailTrack fork);

    void onStationDestroyed();

    void onSensorDestroyed(Sensor sensor);

    void onSemaphoreDestroyed(RailSemaphore semaphore);

    void onLocomotiveDestroyed(Locomotive locomotive);

    void onWagonDestroyed(Wagon wagon);

    void onLoadPassengers(Train train, LocalDateTime elapsedTime, int totalDistanceTraveled,
            double linearDistanceToStart);

    void onTrainMoved(Train train);

    void onTrainCrashed(Train train);

    void chargeFuel(Train train);

    void onLoadCargo(Wagon wagon);

    void onUnloadCargo(Wagon wagon, letrain.track.CargoTypes type, int amount, int distance);

    public int getConstructedNormalRailTracks();

    public int getConstructedBridgeRailTracks();

    public int getConstructedTunnelRailTracks();

    public int getConstructedForks();

    public int getConstructedStations();

    public int getConstructedSensors();

    public int getConstructedSemaphores();

    public int getConstructedLocomotives();

    public int getConstructedWagons();

    public int getDestroyedNormalRailTracks();

    public int getDestroyedBridgeRailTracks();

    public int getDestroyedTunnelRailTracks();

    public int getDestroyedForks();

    public int getDestroyedStations();

    public int getDestroyedSensors();

    public int getDestroyedSemaphores();

    public int getDestroyedLocomotives();

    public int getDestroyedWagons();

    public void reloadConfig();

    public float getGoldThreshold();

    public float getCoalThreshold();

    public float getRubyThreshold();

    public float getWaterThreshold();

    public float getRockThreshold();

    int getConstructionDelay(Presenter.TrackType type);

    public int getViewRadius();
}
