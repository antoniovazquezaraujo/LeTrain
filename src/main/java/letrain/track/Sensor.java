package letrain.track;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.utils.SerializationHelper;
import letrain.vehicle.rail.impl.Train;
import letrain.visitor.Renderable;
import letrain.visitor.Visitor;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@com.fasterxml.jackson.annotation.JsonTypeInfo(
        use = com.fasterxml.jackson.annotation.JsonTypeInfo.Id.NAME,
        include = com.fasterxml.jackson.annotation.JsonTypeInfo.As.PROPERTY, property = "@type")
@com.fasterxml.jackson.annotation.JsonSubTypes({
        @com.fasterxml.jackson.annotation.JsonSubTypes.Type(value = letrain.track.Sensor.class,
                name = "Sensor"),
        @com.fasterxml.jackson.annotation.JsonSubTypes.Type(value = letrain.track.Station.class,
                name = "Station"),
        @com.fasterxml.jackson.annotation.JsonSubTypes.Type(value = letrain.track.SpeedSignal.class,
                name = "SpeedSignal")})
@JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@id")
public class Sensor implements Renderable {
    private int id;
    private String name;
    Track track;

    @JsonIgnore
    transient List<SensorEventListener> listeners = new ArrayList<>();

    @JsonIgnore
    transient List<SensorEventListener> systemListeners = new ArrayList<>();

    private Dir sideDir;
    private Dir creationDir = Dir.E;

    public Track getTrack() {
        return track;
    }

    public Sensor() {}

    public void setTrack(Track track) {
        this.track = track;
    }

    public Sensor(int id) {
        setId(id);
    }

    /**
     * Reinitializes transient fields after deserialization. Ensures listener collections are not
     * null to prevent NPE.
     */
    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        this.listeners = SerializationHelper.ensureListInitialized(listeners);
        this.systemListeners = SerializationHelper.ensureListInitialized(systemListeners);
    }

    public void setId(int i) {
        this.id = i;
    }

    @JsonIgnore
    public Point getPosition() {
        if (track == null) {
            return null;
        }
        return track.getPosition();
    }

    public int getId() {
        return this.id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
        onSensorEnter(train, calculateIsForward(train));
    }

    public void onSensorEnter(Train train, boolean isForward) {
        train.notifyEnterSensor(this, isForward);
        if (listeners != null) {
            for (SensorEventListener listener : listeners) {
                listener.onEnterTrain(train, isForward);
            }
        }
        if (systemListeners != null) {
            for (SensorEventListener listener : systemListeners) {
                listener.onEnterTrain(train, isForward);
            }
        }
    }

    public void onExitTrain(Train train) {
        onSensorExit(train, calculateIsForward(train));
    }

    public void onSensorExit(Train train, boolean isForward) {
        train.notifyExitSensor(this, isForward);
        if (listeners != null) {
            for (SensorEventListener listener : listeners) {
                listener.onExitTrain(train, isForward);
            }
        }
        if (systemListeners != null) {
            for (SensorEventListener listener : systemListeners) {
                listener.onExitTrain(train, isForward);
            }
        }
    }

    private boolean calculateIsForward(Train train) {
        boolean isForward = true;
        if (creationDir != null && train.getDirectorLinker() != null) {
            isForward = (train.getDirectorLinker().getRealDir() == creationDir);
        }
        return isForward;
    }

    public Dir getCreationDir() {
        return creationDir;
    }

    public void setCreationDir(Dir creationDir) {
        this.creationDir = creationDir;
    }

    public void addSensorEventListener(SensorEventListener listener) {
        if (listeners == null)
            listeners = new ArrayList<>();
        this.listeners.add(listener);
    }

    public void addSystemSensorEventListener(SensorEventListener listener) {
        if (systemListeners == null)
            systemListeners = new ArrayList<>();
        this.systemListeners.add(listener);
    }

    public void removeSensorEventListener(SensorEventListener listener) {
        if (listeners != null)
            this.listeners.remove(listener);
    }

    public void removeAllSensorEventListeners() {
        if (listeners != null)
            this.listeners.clear();
    }

    // toString
    @Override
    public String toString() {
        return "Sensor [id=" + id + "]";
    }
}
