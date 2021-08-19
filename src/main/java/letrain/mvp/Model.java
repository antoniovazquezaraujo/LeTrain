package letrain.mvp;

import letrain.map.RailMap;
import letrain.track.rail.ForkRailTrack;
import letrain.vehicle.impl.Cursor;
import letrain.vehicle.impl.rail.Train;

import java.util.List;

public interface Model {
    RailMap getRailMap();

    List<Train> getTrains();

    Cursor getCursor();

    List<ForkRailTrack> getForks();

    void addFork(ForkRailTrack fork);

    void removeFork(ForkRailTrack fork);

    void addTrain(Train train);

    void removeTrain(Train train);

    void moveTrains();

    GameMode getMode();

    void setMode(GameMode mode);

    Train getSelectedTrain();

    void setSelectedTrain(Train selectedTrain);

    ForkRailTrack getSelectedFork();

    void setSelectedFork(ForkRailTrack selectedFork);

    void selectNextFork();

    void selectPrevFork();

    void selectNextTrain();

    void selectPrevTrain();

    enum GameMode {
        TRACKS("Navigate map, create and delete tracks"),
        TRAINS("Use trains"),
        FORKS("Use forks"),
        CREATE_LOAD_PLATFORM("Create load platform"),
        LOAD_TRAINS("Use load platforms"),
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
