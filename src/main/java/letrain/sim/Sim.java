package letrain.sim;

import letrain.map.RailMap;
import letrain.vehicle.impl.rail.Train;

import java.util.List;

public interface Sim {
    RailMap getMap();

    void setMap(RailMap map);

    List<Train> getTrains();

    void addTrain(Train train);

    void removeTrain(Train train);

    void moveTrains();
}
