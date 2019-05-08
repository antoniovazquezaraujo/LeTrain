package letrain.mvp.impl;

import letrain.map.Dir;
import letrain.map.Point;
import letrain.map.RailMap;
import letrain.mvp.impl.delegates.TrainFactory;
import letrain.track.rail.ForkRailTrack;
import letrain.vehicle.impl.Cursor;
import letrain.vehicle.impl.rail.Train;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Model implements Serializable , letrain.mvp.Model {


    Train selectedTrain;
    ForkRailTrack selectedFork;
    private int selectedTrainIndex;
    private int selectedForkIndex;

    private GameMode mode = letrain.mvp.Model.GameMode.TRACKS;
    private RailMap map;
    private final List<Train> trains;
    private final List<TrainFactory> trainFactories;
    private Cursor cursor;
    private List<ForkRailTrack> forks;

    public Model() {
        this.cursor = new Cursor();
        this.cursor.setDir(Dir.E);
        this.cursor.setPosition(new Point(10,10));
        this.trainFactories = new ArrayList<>();
        this.trains = new ArrayList<>();
        this.forks = new ArrayList<>();
        this.map = new RailMap();
        selectedTrainIndex = 0;
        if (!getTrains().isEmpty()) {
            selectedTrain = getTrains().get(selectedTrainIndex);
        }
        selectedForkIndex = 0;
        if (!getForks().isEmpty()) {
            selectedFork = getForks().get(selectedForkIndex);
        }

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

    @Override
    public GameMode getMode() {
        return mode;
    }

    @Override
    public void setMode(GameMode mode) {
        this.mode = mode;
    }
    @Override
    public Train getSelectedTrain() {
        return selectedTrain;
    }

    @Override
    public void setSelectedTrain(Train selectedTrain) {
        this.selectedTrain = selectedTrain;
    }

    @Override
    public ForkRailTrack getSelectedFork() {
        return selectedFork;
    }

    @Override
    public void setSelectedFork(ForkRailTrack selectedFork) {
        this.selectedFork = selectedFork;
    }

    @Override
    public void selectNextFork() {
        selectedForkIndex++;
        if (selectedForkIndex >= getForks().size()) {
            selectedForkIndex = 0;
        }
        selectedFork = getForks().get(selectedForkIndex);


    }

    @Override
    public void selectPrevFork() {
        selectedForkIndex--;
        if (selectedForkIndex < 0) {
            selectedForkIndex = getForks().size() - 1;
        }
        selectedFork = getForks().get(selectedForkIndex);
    }

    @Override
    public void selectNextTrain() {
        selectedTrainIndex++;
        if (selectedTrainIndex >= getTrains().size()) {
            selectedTrainIndex = 0;
        }
        selectedTrain = getTrains().get(selectedTrainIndex);
    }

    @Override
    public void selectPrevTrain() {
        selectedTrainIndex--;
        if (selectedTrainIndex < 0) {
            selectedTrainIndex = getTrains().size() - 1;
        }
        selectedTrain = getTrains().get(selectedTrainIndex);
    }
}
