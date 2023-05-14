package letrain.track;

import java.util.ArrayList;
import java.util.List;

import letrain.vehicle.impl.rail.Train;
import letrain.visitor.Renderable;
import letrain.visitor.Visitor;

public class Sensor implements Renderable {
    private static int numSensorsCreated = 0;
    private int id;
    Track track;
    List<SensorEventListener> listeners = new ArrayList<>();

    public Track getTrack() {
        return track;
    }

    public void setTrack(Track track) {
        this.track = track;
    }

    public Sensor() {
        setId(++numSensorsCreated);
    }

    public void setId(int i) {
        this.id = i;
    }

    public int getId() {
        return this.id;
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

    // toString
    @Override
    public String toString() {
        return "Sensor [id=" + id + "]";
    }

}
