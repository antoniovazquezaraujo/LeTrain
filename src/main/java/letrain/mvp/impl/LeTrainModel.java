package letrain.mvp.impl;

import letrain.map.Dir;
import letrain.map.Point;
import letrain.map.RailMap;
import letrain.mvp.GameModel;
import letrain.mvp.impl.delegates.TrainFactory;
import letrain.track.rail.ForkRailTrack;
import letrain.vehicle.impl.Cursor;
import letrain.vehicle.impl.rail.Train;

import java.util.ArrayList;
import java.util.List;

public class LeTrainModel implements GameModel {
    private RailMap map;
    private final List<Train> trains;
    private final List<TrainFactory> trainFactories;
    private Cursor cursor;
    private List<ForkRailTrack> forks;

    public LeTrainModel() {
        this.cursor = new Cursor();
        this.cursor.setDir(Dir.N);
        this.cursor.setPosition(new Point(10,10));
        this.trainFactories = new ArrayList<>();
        this.trains = new ArrayList<>();
        this.forks = new ArrayList<>();
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
    public List<ForkRailTrack> getForks() {
        return this.forks;
    }

    @Override
    public void addFork(ForkRailTrack fork) {
        this.forks.add(fork);
    }

    @Override
    public void removeFork(ForkRailTrack fork) {
        this.forks.remove(fork);
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
        trains.forEach(Train::applyForce);
    }

    @Override
    public List<TrainFactory> getTrainFactories() {
        return this.trainFactories;
    }

    @Override
    public void addTrainFactory(TrainFactory factory) {
        this.trainFactories.add(factory);
    }

    @Override
    public void removeTrainFactory(TrainFactory factory) {
        this.trainFactories.remove(factory);
    }

}
