package letrain.economy;

import java.util.concurrent.Semaphore;

import letrain.mvp.Presenter;
import letrain.track.Sensor;
import letrain.track.rail.ForkRailTrack;
import letrain.vehicle.impl.rail.Locomotive;
import letrain.vehicle.impl.rail.Train;
import letrain.vehicle.impl.rail.Wagon;

public interface EconomyManager {
    enum ExpenseType {
        CONSTRUCTED_NORMAL_RAIL_TRACK,
        CONSTRUCTED_BRIDGE_RAIL_TRACK,
        CONSTRUCTED_TUNNEL_RAIL_TRACK,
        CONSTRUCTED_FORK,
        CONSTRUCTED_STATION,
        CONSTRUCTED_SENSOR,
        CONSTRUCTED_SEMAPHORE,
        CONSTRUCTED_LOCOMOTIVE,
        CONSTRUCTED_WAGON,
        DESTROYED_NORMAL_RAIL_TRACK,
        DESTROYED_BRIDGE_RAIL_TRACK,
        DESTROYED_TUNNEL_RAIL_TRACK,
        DESTROYED_FORK,
        DESTROYED_STATION,
        DESTROYED_SENSOR,
        DESTROYED_SEMAPHORE,
        DESTROYED_LOCOMOTIVE,
        DESTROYED_WAGON,
        LOAD_PASSENGERS,
        UNLOAD_PASSENGERS,
        TRAIN_MOVED,
        TRAIN_CRASHED
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

    void onSemaphoreConstructed(Semaphore semaphore);

    void onLocomotiveConstructed(Locomotive locomotive);

    void onWagonConstructed(Wagon wagon);

    void onRailTrackDestroyed(Presenter.TrackType type);

    void onForkDestroyed(ForkRailTrack fork);

    void onStationDestroyed();

    void onSensorDestroyed(Sensor sensor);

    void onSemaphoreDestroyed(Semaphore semaphore);

    void onLocomotiveDestroyed(Locomotive locomotive);

    void onWagonDestroyed(Wagon wagon);

    void onLoadPassengers(Train train);

    void onUnloadPassengers(Train train);

    void onTrainMoved(Train train);

    void onTrainCrashed(Train train);

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

}
