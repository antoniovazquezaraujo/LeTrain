package letrain.model;

import letrain.map.RailMap;
import letrain.track.TrainFactoryTrack;
import letrain.trackmaker.RailTrackMaker;
import letrain.vehicle.impl.rail.Train;

import java.util.List;

public interface GameModel {
    RailMap getMap();

    void setMap(RailMap map);

    List<Train> getTrains();

    List<TrainFactoryTrack> getFactoryGateTracks();

    void addFactoryGateTrack(TrainFactoryTrack track);

    void removeFactoryGateTrack(TrainFactoryTrack track);

    void addTrain(Train train);

    void removeTrain(Train train);

    void moveTrains();

    RailTrackMaker getMaker();

    enum Mode {
        MAP_WALK,
        TRACK_WALK,
        MAKE_TRACK,
        REMOVE_TRACK
    }
}
