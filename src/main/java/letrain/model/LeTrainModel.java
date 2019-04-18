package letrain.model;

import letrain.map.Dir;
import letrain.map.RailMap;
import letrain.track.TrainFactoryTrack;
import letrain.trackmaker.RailTrackMaker;
import letrain.vehicle.impl.rail.Train;

import java.util.ArrayList;
import java.util.List;

public class LeTrainModel implements GameModel {
    private RailMap map;
    private final List<Train> trains;
    private final RailTrackMaker maker;
    private final List<TrainFactoryTrack> factoryGateTracks;
    public LeTrainModel() {
        this.factoryGateTracks = new ArrayList<>();
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
    public List<TrainFactoryTrack> getFactoryGateTracks() {
        return factoryGateTracks;
    }

    @Override
    public void addFactoryGateTrack(TrainFactoryTrack track) {
        factoryGateTracks.add(track);
    }

    @Override
    public void removeFactoryGateTrack(TrainFactoryTrack track) {
        factoryGateTracks.remove(track);
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
        trains.forEach(Train::applyForces);
    }

    @Override
    public RailTrackMaker getMaker() {
        return maker;
    }
}
