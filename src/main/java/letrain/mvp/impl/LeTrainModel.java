package letrain.mvp.impl;

import letrain.map.Dir;
import letrain.map.Point;
import letrain.map.RailMap;
import letrain.mvp.GameModel;
import letrain.track.rail.TrainFactoryRailTrack;
import letrain.vehicle.impl.Cursor;
import letrain.vehicle.impl.rail.Train;

import java.util.ArrayList;
import java.util.List;

public class LeTrainModel implements GameModel {
    private RailMap map;
    private final List<Train> trains;
    private final List<TrainFactoryRailTrack> factoryRailTracks;
    private Cursor cursor;
    public LeTrainModel() {
        this.cursor = new Cursor();
        this.cursor.setDir(Dir.N);
        this.cursor.setPosition(new Point(10,10));
        this.factoryRailTracks = new ArrayList<>();
        this.trains = new ArrayList<>();
        this.map = new RailMap();
    }

    @Override
    public RailMap getRailMap() {
        return map;
    }

    @Override
    public List<Train> getTrains() {
        return trains;
    }

    @Override
    public Cursor getCursor() {
        return cursor;
    }

    @Override
    public List<TrainFactoryRailTrack> getTrainFactoryRailTracks() {
        return factoryRailTracks;
    }

    @Override
    public void addTrainFactoryRailTrack(TrainFactoryRailTrack track) {
        factoryRailTracks.add(track);
    }

    @Override
    public void removeTrainFactoryRailTrack(TrainFactoryRailTrack track) {
        factoryRailTracks.remove(track);
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

}
