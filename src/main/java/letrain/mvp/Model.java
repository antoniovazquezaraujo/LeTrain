package letrain.mvp;

import letrain.map.RailMap;
import letrain.mvp.impl.delegates.TrainFactory;
import letrain.track.rail.ForkRailTrack;
import letrain.vehicle.impl.Cursor;
import letrain.vehicle.impl.rail.Locomotive;
import letrain.vehicle.impl.rail.Wagon;

import java.util.List;

public interface Model {
    RailMap getRailMap();

    List<Locomotive> getLocomotives();

    List<Wagon> getWagons();

    Cursor getCursor();

    List<ForkRailTrack> getForks();

    void addFork(ForkRailTrack fork);

    void removeFork(ForkRailTrack fork);

    void addLocomotive(Locomotive locomotive);

    void removeLocomotive(Locomotive locomotive);

    void addWagon(Wagon wagon);

    void removeWagon(Wagon wagon);

    void moveLocomotives();

    GameMode getMode();

    void setMode(GameMode mode);

    Locomotive getSelectedLocomotive();

    void setSelectedLocomotive(Locomotive selectedLocomotive);

    ForkRailTrack getSelectedFork();

    void setSelectedFork(ForkRailTrack selectedFork);

    void selectNextFork();

    void selectPrevFork();

    void selectNextLocomotive();

    void selectPrevLocomotive();

    enum GameMode {
        TRACKS("Navigate map, create and delete tracks"),
        LOCOMOTIVES("Manage locomotives"),
        FORKS("Manage forks"),
        CREATE_LOAD_PLATFORM("Create load platform"),
        LOAD_TRAINS("Use load platforms"),
        LINK_TRAINS("Link trains"),
        DIVIDE_TRAINS("Divide trains"),
        MAKE_TRAINS("Use factory platforms");

        private String name;

        GameMode(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
