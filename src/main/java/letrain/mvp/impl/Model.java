package letrain.mvp.impl;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import letrain.map.Dir;
import letrain.map.Point;
import letrain.map.RailMap;
import letrain.track.Sensor;
import letrain.track.SensorEventListener;
import letrain.track.rail.ForkRailTrack;
import letrain.vehicle.impl.Cursor;
import letrain.vehicle.impl.rail.Locomotive;
import letrain.vehicle.impl.rail.Train;
import letrain.vehicle.impl.rail.Wagon;

public class Model implements Serializable, letrain.mvp.Model {
    Logger log = LoggerFactory.getLogger(Model.class);
    Locomotive selectedLocomotive;
    ForkRailTrack selectedFork;
    private int selectedLocomotiveIndex;
    private int selectedForkIndex;

    private GameMode mode = letrain.mvp.Model.GameMode.RAILS;
    private RailMap map;
    private List<Locomotive> locomotives;
    List<Wagon> wagons;
    private Cursor cursor;
    private List<ForkRailTrack> forks;
    private List<Sensor> sensors;

    public Model() {
        this.cursor = new Cursor();
        this.cursor.setDir(Dir.E);
        this.cursor.setPosition(new Point(10, 10));
        this.locomotives = new ArrayList<>();
        this.wagons = new ArrayList<>();
        this.forks = new ArrayList<>();
        this.sensors = new ArrayList<>();
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

    public List<Sensor> getSensors() {
        return sensors;
    }

    public Train getTrainFromLocomotiveId(int locomotiveId) {
        for (Locomotive locomotive : getLocomotives()) {
            if (locomotive.getId() == locomotiveId) {
                return locomotive.getTrain();
            }
        }
        return null;
    }

    public void addSensor(Sensor sensor) {
        // sensor.addSensorEventListener(new SensorEventListener() {

        // @Override
        // public void onExitTrain(Train train) {
        // log.debug("Train " + train + " exited sensor " + sensor);
        // }

        // @Override
        // public void onEnterTrain(Train train) {
        // log.debug("Train " + train + " entered sensor " + sensor);
        // }
        // });
        sensors.add(sensor);
    }

    public void removeSensor(Sensor sensor) {
        sensors.remove(sensor);
    }

    @Override
    public Sensor getSensor(int id) {
        for (Sensor sensor : getSensors()) {
            if (sensor.getId() == id) {
                return sensor;
            }
        }
        return null;
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

    public void selectFork(int id) {
        for (ForkRailTrack fork : getForks()) {
            if (fork.getId() == id) {
                selectedFork = fork;
                break;
            }
        }
    }

    @Override
    public ForkRailTrack getFork(int id) {
        for (ForkRailTrack fork : getForks()) {
            if (fork.getId() == id) {
                return fork;
            }
        }
        return null;
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

    @Override
    public void saveModel(String file) {
        // serialize the model
        try (FileOutputStream fos = new FileOutputStream(file);
                ObjectOutputStream oos = new ObjectOutputStream(fos)) {
            oos.writeObject(this);
        } catch (IOException ex) {
            log.error("Error saving model", ex);
        }

    }

    @Override
    public void loadModel(String file) {
        // deserialize the model
        try (FileInputStream fis = new FileInputStream(file);
                ObjectInputStream ois = new ObjectInputStream(fis)) {
            Model model = (Model) ois.readObject();
            this.cursor = model.cursor;
            this.locomotives = model.locomotives;
            this.wagons = model.wagons;
            this.forks = model.forks;
            this.map = model.map;
            this.sensors = model.sensors;
            this.selectedLocomotiveIndex = model.selectedLocomotiveIndex;
            this.selectedLocomotive = model.selectedLocomotive;
            this.selectedForkIndex = model.selectedForkIndex;
            this.selectedFork = model.selectedFork;
            this.mode = model.mode;
        } catch (IOException | ClassNotFoundException ex) {
            log.error("Error loading model", ex);
        }
    }

}
