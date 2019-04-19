package letrain.mvp;

import letrain.map.RailMap;
import letrain.track.rail.TrainFactoryRailTrack;
import letrain.trackmaker.TrackMaker;
import letrain.vehicle.impl.rail.Train;

import java.util.List;

public interface GameModel {
    RailMap getRailMap();

    List<Train> getTrains();

    TrackMaker getMaker();

    List<TrainFactoryRailTrack> getTrainFactoryRailTracks();

    void addTrainFactoryRailTrack(TrainFactoryRailTrack track);

    void removeTrainFactoryRailTrack(TrainFactoryRailTrack track);

    void addTrain(Train train);

    void removeTrain(Train train);

    void moveTrains();

    enum Mode {
        MAP_WALK,
        TRACK_WALK,
        MAKE_TRACK,
        REMOVE_TRACK
    }
}
