package letrain.mvp.impl;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import letrain.map.Dir;
import letrain.map.Point;
import letrain.map.RailMap;
import letrain.track.RailSemaphore;
import letrain.track.Sensor;
import letrain.track.rail.ForkRailTrack;
import letrain.vehicle.impl.Cursor;
import letrain.vehicle.impl.rail.Locomotive;
import letrain.vehicle.impl.rail.Train;
import letrain.vehicle.impl.rail.Wagon;

public class Model implements Serializable, letrain.mvp.Model {
    static Logger log = LoggerFactory.getLogger(Model.class);
    Locomotive selectedLocomotive;
    ForkRailTrack selectedFork;
    RailSemaphore selectedSemaphore;

    int selectedLocomotiveIndex;
    int selectedForkIndex;
    int selectedSemaphoreIndex;
    boolean showId = false;

    GameMode mode = letrain.mvp.Model.GameMode.RAILS;
    RailMap map;
    List<Locomotive> locomotives;
    List<Wagon> wagons;
    Cursor cursor;
    List<ForkRailTrack> forks;
    List<Sensor> sensors;
    List<RailSemaphore> semaphores;
    int nextLocomotiveId;
    int nextForkId;
    int nextSensorId;
    int nextSemaphoreId;
    int nextTrainId;

    public int nextSemaphoreId() {
        return ++nextSemaphoreId;
    }

    public int nextForkId() {
        return ++nextForkId;
    }

    public int nextLocomotiveId() {
        return ++nextLocomotiveId;
    }

    public int nextSensorId() {
        return ++nextSensorId;
    }

    public int nextTrainId() {
        return ++nextTrainId;
    }

    public Model() {
        this.cursor = new Cursor();
        this.cursor.setDir(Dir.E);
        this.cursor.setPosition(new Point(10, 10));
        this.locomotives = new ArrayList<>();
        this.wagons = new ArrayList<>();
        this.forks = new ArrayList<>();
        this.sensors = new ArrayList<>();
        this.semaphores = new ArrayList<>();
        this.map = new RailMap();
        selectedLocomotiveIndex = 0;
        if (!getLocomotives().isEmpty()) {
            selectedLocomotive = getLocomotives().get(selectedLocomotiveIndex);
        }
        selectedForkIndex = 0;
        if (!getForks().isEmpty()) {
            selectedFork = getForks().get(selectedForkIndex);
        }
        selectedSemaphoreIndex = 0;
        if (!getSemaphores().isEmpty()) {
            selectedSemaphore = getSemaphores().get(selectedSemaphoreIndex);
        }
    }

    @Override
    public RailMap getRailMap() {
        return map;
    }

    @Override
    public List<Sensor> getSensors() {
        return sensors;
    }

    @Override
    public Train getTrainFromLocomotiveId(int locomotiveId) {
        for (Locomotive locomotive : getLocomotives()) {
            if (locomotive.getId() == locomotiveId) {
                return locomotive.getTrain();
            }
        }
        return null;
    }

    @Override
    public void addSensor(Sensor sensor) {
        sensors.add(sensor);
    }

    @Override
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

    @Override
    public List<Locomotive> getLocomotives() {
        return locomotives;
    }

    @Override
    public List<Wagon> getWagons() {
        return wagons;
    }

    @Override
    public void removeWagon(Wagon wagon) {
        this.wagons.remove(wagon);
    }

    @Override
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

    @Override
    public void addLocomotive(Locomotive locomotive) {
        this.locomotives.add(locomotive);
    }

    @Override
    public void removeLocomotive(Locomotive locomotive) {
        this.locomotives.remove(locomotive);
    }

    @Override
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

    public void selectLocomotive(int id) {
        for (Locomotive Locomotive : getLocomotives()) {
            if (Locomotive.getId() == id) {
                selectedLocomotive = Locomotive;
                break;
            }
        }
    }

    @Override
    public List<RailSemaphore> getSemaphores() {
        return this.semaphores;
    }

    @Override
    public RailSemaphore getSemaphore(int id) {
        for (RailSemaphore semaphore : getSemaphores()) {
            if (semaphore.getId() == id) {
                return semaphore;
            }
        }
        return null;
    }

    @Override
    public void addSemaphore(RailSemaphore semaphore) {
        this.semaphores.add(semaphore);
    }

    @Override
    public void removeSemaphore(RailSemaphore semaphore) {
        this.semaphores.remove(semaphore);
    }

    @Override
    public RailSemaphore getSemaphoreAt(Point pos) {
        for (RailSemaphore semaphore : getSemaphores()) {
            if (semaphore.getPosition().equals(pos)) {
                return semaphore;
            }
        }
        return null;
    }

    @Override
    public void selectNextSemaphore() {
        if (getSemaphores().isEmpty()) {
            return;
        }

        selectedSemaphoreIndex++;
        if (selectedSemaphoreIndex >= getSemaphores().size()) {
            selectedSemaphoreIndex = 0;
        }
        selectedSemaphore = getSemaphores().get(selectedSemaphoreIndex);

    }

    @Override
    public void selectPrevSemaphore() {
        if (getSemaphores().isEmpty()) {
            return;
        }
        selectedSemaphoreIndex--;
        if (selectedSemaphoreIndex < 0) {
            selectedSemaphoreIndex = getSemaphores().size() - 1;
        }
        selectedSemaphore = getSemaphores().get(selectedSemaphoreIndex);
    }

    @Override
    public RailSemaphore getSelectedSemaphore() {
        return selectedSemaphore;
    }

    @Override
    public void selectSemaphore(int id) {
        for (RailSemaphore semaphore : getSemaphores()) {
            if (semaphore.getId() == id) {
                selectedSemaphore = semaphore;
                break;
            }
        }
    }

    @Override
    public void setShowId(boolean b) {
        this.showId = b;
    }

    @Override
    public boolean isShowId() {
        return this.showId;
    }

}
