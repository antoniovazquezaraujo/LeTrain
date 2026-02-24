package letrain.track;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import letrain.map.Dir;
import letrain.map.Point;
import letrain.vehicle.impl.rail.Train;
import letrain.visitor.Renderable;
import letrain.visitor.Visitor;

public class Sensor implements Renderable, Serializable {
    private static final long serialVersionUID = 1L;
    private int id;
    Track track;
    List<SensorEventListener> listeners = new ArrayList<>();
    private Dir sideDir;

    public Track getTrack() {
        return track;
    }

    public void setTrack(Track track) {
        this.track = track;
    }

    public Sensor(int id) {
        setId(id);
    }

    public void setId(int i) {
        this.id = i;
    }

    public Point getPosition() {
        if (track == null) {
            return null;
        }
        return track.getPosition();
    }

    public int getId() {
        return this.id;
    }

    public Dir getSideDir() {
        return sideDir;
    }

    public void setSideDir(Dir sideDir) {
        this.sideDir = sideDir;
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visitSensor(this);
    }

    public void onEnterTrain(Train train) {
        for (SensorEventListener listener : listeners) {
            listener.onEnterTrain(train);
        }
    }

    public void onExitTrain(Train train) {
        for (SensorEventListener listener : listeners) {
            listener.onExitTrain(train);
        }
    }

    public void addSensorEventListener(SensorEventListener listener) {
        this.listeners.add(listener);
    }

    public void removeSensorEventListener(SensorEventListener listener) {
        this.listeners.remove(listener);
    }

    public void removeAllSensorEventListeners() {
        this.listeners.clear();
    }

    // toString
    @Override
    public String toString() {
        return "Sensor [id=" + id + "]";
    }

}
