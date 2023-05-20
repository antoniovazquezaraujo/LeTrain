package letrain.mvp;

import java.util.List;

import letrain.map.Point;
import letrain.map.RailMap;
import letrain.track.RailSemaphore;
import letrain.track.Sensor;
import letrain.track.rail.ForkRailTrack;
import letrain.vehicle.impl.Cursor;
import letrain.vehicle.impl.rail.Locomotive;
import letrain.vehicle.impl.rail.Train;
import letrain.vehicle.impl.rail.Wagon;

public interface Model {

    public void setShowId(boolean b);

    public boolean isShowId();

    public int nextSemaphoreId();

    public int nextForkId();

    public int nextLocomotiveId();

    public int nextSensorId();

    public int nextTrainId();

    RailMap getRailMap();

    List<Locomotive> getLocomotives();

    List<Wagon> getWagons();

    Cursor getCursor();

    Train getTrainFromLocomotiveId(int locomotiveId);

    List<ForkRailTrack> getForks();

    void addFork(ForkRailTrack fork);

    void removeFork(ForkRailTrack fork);

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

    void selectNextSemaphore();

    void selectPrevSemaphore();

    void selectSemaphore(int id);

    RailSemaphore getSelectedSemaphore();

    void moveLocomotives();

    GameMode getMode();

    void setMode(GameMode mode);

    Locomotive getSelectedLocomotive();

    void setSelectedLocomotive(Locomotive selectedLocomotive);

    public void selectLocomotive(int id);

    void selectFork(int id);

    ForkRailTrack getFork(int id);

    ForkRailTrack getSelectedFork();

    void setSelectedFork(ForkRailTrack selectedFork);

    void selectNextFork();

    void selectPrevFork();

    void selectNextLocomotive();

    void selectPrevLocomotive();

    enum GameMode {
        MENU("Menu mode"),
        RAILS("Navigate map, create and delete tracks"),
        DRIVE("Manage locomotives"),
        FORKS("Manage forks"),
        SEMAPHORES("Manage semaphores"),
        TRAINS("Use factory platforms"),
        LINK("Link trains"),
        UNLINK("Divide trains"),
        CREATE_LOAD_PLATFORM("Create load platform"),
        LOAD_TRAINS("Use load platforms");

        private String name;

        GameMode(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

}
