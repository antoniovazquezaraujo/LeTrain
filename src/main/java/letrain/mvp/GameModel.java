package letrain.mvp;

import letrain.map.Dir;
import letrain.map.Point;
import letrain.map.RailMap;
import letrain.mvp.impl.delegates.TrainFactory;
import letrain.track.rail.TrainFactoryRailTrack;
import letrain.vehicle.impl.Cursor;
import letrain.vehicle.impl.rail.Train;

import java.util.List;

public interface GameModel {
    RailMap getRailMap();

    List<Train> getTrains();

    Cursor getCursor();

    void addTrain(Train train);

    void removeTrain(Train train);

    void moveTrains();

    List<TrainFactory> getTrainFactories();

    void addTrainFactory(TrainFactory factory);
    void removeTrainFactory(TrainFactory factory);


    enum Mode {
        MAP_WALK,
        TRACK_WALK,
        MAKE_TRACKS,
        REMOVE_TRACKS
    }
}
