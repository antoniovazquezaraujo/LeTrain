package letrain.sim;

import letrain.map.RailMap;
import letrain.vehicle.impl.rail.Train;

import java.util.ArrayList;
import java.util.List;

public class Game implements Sim {
    private RailMap map;
    private List<Train> trains;


    public Game() {
        this.trains = new ArrayList<>();
        this.map = new RailMap();
    }

    @Override
    public RailMap getMap() {
        return map;
    }

    @Override
    public void setMap(RailMap map) {
        this.map = map;
    }

    @Override
    public List<Train> getTrains() {
        return trains;
    }

    @Override
    public void addTrain(Train train) {
        this.trains.add(train);
    }

    @Override
    public void removeTrain(Train train) {
        this.trains.remove(train);
    }

    @Override
    public void moveTrains() {
        trains.stream().forEach(t-> t.applyForces());
    }
}
