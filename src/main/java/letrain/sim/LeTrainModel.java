package letrain.sim;

import letrain.map.Dir;
import letrain.map.RailMap;
import letrain.trackmaker.RailTrackMaker;
import letrain.vehicle.impl.rail.Train;

import java.util.ArrayList;
import java.util.List;

public class LeTrainModel implements GameModel {
    private RailMap map;
    private List<Train> trains;
    RailTrackMaker maker;

    public LeTrainModel() {
        this.trains = new ArrayList<>();
        this.map = new RailMap();
        this.maker = new RailTrackMaker();
        this.maker.setMap(map);
        this.maker.setDirection(Dir.N);
        this.maker.setPosition(10,10);
        this.maker.setMode(GameModel.Mode.MAP_WALK);
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

    @Override
    public RailTrackMaker getMaker() {
        return maker;
    }
}
