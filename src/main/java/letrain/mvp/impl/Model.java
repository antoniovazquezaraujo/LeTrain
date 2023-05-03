package letrain.mvp.impl;

import letrain.map.Dir;
import letrain.map.Point;
import letrain.map.RailMap;
import letrain.track.rail.ForkRailTrack;
import letrain.vehicle.impl.Cursor;
import letrain.vehicle.impl.rail.Locomotive;
import letrain.vehicle.impl.rail.Wagon;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Model implements Serializable, letrain.mvp.Model {

    Locomotive selectedLocomotive;
    ForkRailTrack selectedFork;
    private int selectedLocomotiveIndex;
    private int selectedForkIndex;

    private GameMode mode = letrain.mvp.Model.GameMode.RAILS;
    private RailMap map;
    private final List<Locomotive> locomotives;
    List<Wagon> wagons;
    private Cursor cursor;
    private List<ForkRailTrack> forks;

    public Model() {
        this.cursor = new Cursor();
        this.cursor.setDir(Dir.E);
        this.cursor.setPosition(new Point(10, 10));
        this.locomotives = new ArrayList<>();
        this.wagons = new ArrayList<>();
        this.forks = new ArrayList<>();
        this.map = new RailMap();
        selectedLocomotiveIndex = 0;
        if (!getLocomotives().isEmpty()) {
            selectedLocomotive = getLocomotives().get(selectedLocomotiveIndex);
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

    public List<Locomotive> getLocomotives() {
        return locomotives;
    }

    public List<Wagon> getWagons() {
        return wagons;
    }

    public void removeWagon(Wagon wagon) {
        this.wagons.remove(wagon);
    }

    public void addWagon(Wagon wagon) {
        this.wagons.add(wagon);
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

    public void addLocomotive(Locomotive locomotive) {
        this.locomotives.add(locomotive);
    }

    public void removeLocomotive(Locomotive locomotive) {
        this.locomotives.remove(locomotive);
    }

    public void moveLocomotives() {
        locomotives.forEach(Locomotive::update);
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
    public ForkRailTrack getSelectedFork() {
        return selectedFork;
    }

    @Override
    public void setSelectedFork(ForkRailTrack selectedFork) {
        this.selectedFork = selectedFork;
    }

    @Override
    public void selectNextFork() {
        if (getForks().isEmpty()) {
            return;
        }

        selectedForkIndex++;
        if (selectedForkIndex >= getForks().size()) {
            selectedForkIndex = 0;
        }
        selectedFork = getForks().get(selectedForkIndex);

    }

    @Override
    public void selectPrevFork() {
        if (getForks().isEmpty()) {
            return;
        }
        selectedForkIndex--;
        if (selectedForkIndex < 0) {
            selectedForkIndex = getForks().size() - 1;
        }
        selectedFork = getForks().get(selectedForkIndex);
    }

    @Override
    public void selectNextLocomotive() {
        if (getLocomotives().isEmpty()) {
            return;
        }
        do {
            selectedLocomotiveIndex++;
            if (selectedLocomotiveIndex >= getLocomotives().size()) {
                selectedLocomotiveIndex = 0;
            }
            selectedLocomotive = getLocomotives().get(selectedLocomotiveIndex);
        } while (!selectedLocomotive.isDirectorLinker() && selectedLocomotiveIndex < getLocomotives().size());
    }

    @Override
    public void selectPrevLocomotive() {
        if (getLocomotives().isEmpty()) {
            return;
        }
        do {
            selectedLocomotiveIndex--;
            if (selectedLocomotiveIndex < 0) {
                selectedLocomotiveIndex = getLocomotives().size() - 1;
            }
            selectedLocomotive = getLocomotives().get(selectedLocomotiveIndex);
        } while (!selectedLocomotive.isDirectorLinker() && selectedLocomotiveIndex >= 0);
    }

    @Override
    public Locomotive getSelectedLocomotive() {
        return selectedLocomotive;
    }

    @Override
    public void setSelectedLocomotive(Locomotive selectedLocomotive) {
        this.selectedLocomotive = selectedLocomotive;
    }
}
