package letrain.mvp;

import letrain.map.RailMap;
import letrain.mvp.impl.delegates.TrainFactory;
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

    List<TrainFactory> getTrainFactories();

    void addTrainFactory(TrainFactory factory);
    void removeTrainFactory(TrainFactory factory);

    GameMode getMode();

    void setMode(GameMode mode);

    enum GameMode {
        TRACKS("Navigate map, create and delete tracks"),
        TRAINS("Use trains"),
        FORKS("Use forks"),
        CREATE_LOAD_PLATFORM("Create load platform"),
        CREATE_FACTORY_PLATFORM("Create factory platform"),
        USE_LOAD_PLATFORMS("Use load platforms"),
        USE_FACTORY_PLATFORMS("Use factory platforms");

        private String name;

        GameMode(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }
}
