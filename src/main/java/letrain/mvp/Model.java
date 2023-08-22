package letrain.mvp;

import java.util.List;

import letrain.economy.EconomyManager;
import letrain.ground.GroundMap;
import letrain.map.Point;
import letrain.map.impl.RailMap;
import letrain.track.RailSemaphore;
import letrain.track.Sensor;
import letrain.track.Station;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;
import letrain.vehicle.impl.Cursor;
import letrain.vehicle.impl.rail.Locomotive;
import letrain.vehicle.impl.rail.Train;
import letrain.vehicle.impl.rail.Wagon;

public interface Model {

    public void setShowId(boolean b);

    public boolean isShowId();

    public int nextSemaphoreId();

    public int nextForkId();

    public int nextStationId();

    public int nextLocomotiveId();

    public int nextSensorId();

    public int nextTrainId();

    RailMap getRailMap();

    GroundMap getGroundMap();

    List<Locomotive> getLocomotives();

    List<Wagon> getWagons();

    Cursor getCursor();

    Train getTrainFromLocomotiveId(int locomotiveId);

    List<ForkRailTrack> getForks();

    void addFork(ForkRailTrack fork);

    void removeFork(ForkRailTrack fork);

    List<Station> getStations();

    void addStation(Station Station);

    void removeStation(Station Station);

    Station getStation(int id);

    Station getSelectedStation();

    void setSelectedStation(Station selectedStation);

    boolean selectNextStation();

    boolean selectPrevStation();

    boolean selectStation(int id);

    void addLocomotive(Locomotive locomotive);

    void removeLocomotive(Locomotive locomotive);

    void addWagon(Wagon wagon);

    void removeWagon(Wagon wagon);

    List<Sensor> getSensors();

    Sensor getSensor(int id);

    void addSensor(Sensor sensor);

    void removeSensor(Sensor sensor);

    List<RailSemaphore> getSemaphores();

    RailSemaphore getSemaphore(int id);

    void addSemaphore(RailSemaphore semaphore);

    void removeSemaphore(RailSemaphore semaphore);

    RailSemaphore getSemaphoreAt(Point point);

    boolean selectNextSemaphore();

    boolean selectPrevSemaphore();

    boolean selectSemaphore(int id);

    RailSemaphore getSelectedSemaphore();

    void moveLocomotives();

    GameMode getMode();

    void setMode(GameMode mode);

    Locomotive getSelectedLocomotive();

    void setSelectedLocomotive(Locomotive selectedLocomotive);

    boolean selectLocomotive(int id);

    boolean selectFork(int id);

    ForkRailTrack getFork(int id);

    ForkRailTrack getSelectedFork();

    void setSelectedFork(ForkRailTrack selectedFork);

    boolean selectNextFork();

    boolean selectPrevFork();

    boolean selectNextLocomotive();

    boolean selectPrevLocomotive();

    enum GameMode {
        MENU("Menu mode"),
        RAILS("Navigate map, create and delete tracks"),
        DRIVE("Manage locomotives"),
        FORKS("Manage forks"),
        SEMAPHORES("Manage semaphores"),
        TRAINS("Create trains"),
        LINK("Link trains"),
        UNLINK("Divide trains"),
        STATIONS("Stations"),
        LOAD_TRAINS("Use load Stations");

        private String name;

        GameMode(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public void loadAndUnloadTrains();

    public void removeDestroyedTrains();

    public void setProgram(String program);

    public String getProgram();

    public EconomyManager getEconomyManager();

    public RailTrack getCursorRailTrack();

}
