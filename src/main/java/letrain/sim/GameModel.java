package letrain.sim;

import letrain.map.RailMap;
import letrain.trackmaker.RailTrackMaker;
import letrain.vehicle.impl.rail.Train;

import java.util.List;

public interface GameModel {
    RailMap getMap();

    void setMap(RailMap map);

    List<Train> getTrains();

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
