package letrain.mvp;

import java.util.List;

import letrain.map.RailMap;
import letrain.track.RailSemaphore;
import letrain.track.Sensor;
import letrain.track.rail.ForkRailTrack;
import letrain.vehicle.impl.Cursor;
import letrain.vehicle.impl.rail.Locomotive;
import letrain.vehicle.impl.rail.Train;
import letrain.vehicle.impl.rail.Wagon;

public interface Model {
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

    void removeSemaphore(RailSemaphore sensor);

    void moveLocomotives();

    GameMode getMode();

    void setMode(GameMode mode);

    Locomotive getSelectedLocomotive();

    void setSelectedLocomotive(Locomotive selectedLocomotive);

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
