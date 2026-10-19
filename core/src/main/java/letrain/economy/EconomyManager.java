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

    /** Real seconds a full game day lasts (ADR-022). */
    int getDayDurationSeconds();

    /**
     * World latitude for the solar day/night cycle (ADR-022 phase 1), from {@code world.latitude}.
     */
    double getLatitude();

    /**
     * The {@code letrain.cfg} this manager reads (working directory first, then next to the app),
     * or {@code null} when neither exists. The settings dialog uses it as the target of "save as
     * defaults" (D4, S3a).
     */
    java.io.File getConfigFile();

    /**
     * Creation-time start day of the game clock ({@code time.startDay}, S2b): 1..366, default 1.
     * {@code Model.newGame} applies it before the first tick; loading a savegame restores its own
     * origin and never moves the running clock.
     */
    int getStartDay();

    /** Creation-time start minute of day of the game clock ({@code time.startTime}), 0..1439. */
    int getStartMinuteOfDay();

    float getCost(ExpenseType type);

    void spend(ExpenseType type);

    void spend(ExpenseType type, int amount);

    /**
     * Free-construction mode (ADR-020 scenario "constructor libre"): while true, spending has no
     * effect on the balance or totals (statistics counters still update).
     */
    void setFreeConstruction(boolean free);

    boolean isFreeConstruction();

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

    /**
     * Charges fuel for a distance actually travelled (one successful rail advance = one unit, see
     * {@code Locomotive.getDistanceTraveled()}), applying {@code fuelCostPerMeter} per unit (#812).
     *
     * @param train the moving train (context for logging/future per-train pricing)
     * @param distanceTravelled distance moved since the previous charge; negative values are
     *        clamped to zero
     */
    void chargeFuel(Train train, int distanceTravelled);

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

    /**
     * All effective settings (defaults + file overrides) as key=value entries, for scenario export.
     */
    java.util.Map<String, String> effectiveConfig();

    /**
     * Applies settings (from a scenario's {@code configuration} section); they win over the file.
     */
    void applyConfig(java.util.Map<String, String> config);

    public float getGoldThreshold();

    public float getCoalThreshold();

    public float getRubyThreshold();

    public float getWaterThreshold();

    public float getRockThreshold();

    /**
     * True when the terrain proportions drive world generation: {@code GroundMap} calibrates the
     * water/rock thresholds per seed from {@link #getWaterPercent()} and
     * {@link #getMountainPercent()} (new keys {@code terrain.waterPercent} /
     * {@code terrain.mountainPercent}, D2). When false, the legacy raw thresholds
     * ({@link #getWaterThreshold()} / {@link #getRockThreshold()}) apply, which keeps old
     * scenarios, savegames and cfg files looking the same.
     */
    boolean isTerrainPercentEnabled();

    /** Requested water percentage (0..95). Only meaningful in percent mode. */
    int getWaterPercent();

    /** Requested mountain percentage (0..95). Only meaningful in percent mode. */
    int getMountainPercent();

    /**
     * Switches to the legacy raw thresholds. Savegames written before the settings section only
     * carried {@code threshold.WATER/ROCK}, so keeping them preserves their exact look.
     */
    void fallBackToLegacyTerrain();

    int getConstructionDelay(Presenter.TrackType type);

    public int getViewRadius();

    /**
     * Minimum number of simulation ticks that must pass between two curves for a train to be safe.
     * If the head enters a curve sooner than this after the previous curve (at speed ≥
     * {@link #getDerailMinSpeed()}), the train derails.
     */
    int getDerailMinCurveInterval();

    /**
     * Minimum current speed below which a train never derails from consecutive curves.
     */
    int getDerailMinSpeed();

    /**
     * Whether reserved/blocked tracks are tinted with the livery of the owning train
     * ({@code ui.highlightBlockedTracks}, default true). When false, both clients keep the normal
     * rail colour (issue #694).
     */
    boolean isHighlightBlockedTracks();

    /** Enables/disables the train-livery tint of reserved/blocked tracks (issue #694). */
    void setHighlightBlockedTracks(boolean highlight);

}
