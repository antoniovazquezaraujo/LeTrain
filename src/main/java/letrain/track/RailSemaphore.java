package letrain.track;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import letrain.map.Point;
import letrain.utils.SerializationHelper;
import letrain.vehicle.rail.impl.Train;
import letrain.visitor.Renderable;
import letrain.visitor.Visitor;

@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@id")
public class RailSemaphore implements Renderable, Serializable {
    private static final long serialVersionUID = 1L;
    private int id;
    private Point position;
    private boolean open;
    private letrain.map.Dir creationDir = letrain.map.Dir.E;

    public boolean isOpen() {
        return open;
    }

    @JsonIgnore
    private transient java.util.List<SemaphoreEventListener> listeners = new java.util.ArrayList<>();

    @JsonIgnore
    private transient java.util.List<SemaphoreEventListener> systemListeners = new java.util.ArrayList<>();

    public void addSemaphoreEventListener(SemaphoreEventListener listener) {
        if (listeners == null) listeners = new java.util.ArrayList<>();
        listeners.add(listener);
    }

    public void addSystemSemaphoreEventListener(SemaphoreEventListener listener) {
        if (systemListeners == null) systemListeners = new java.util.ArrayList<>();
        systemListeners.add(listener);
    }

    public void removeSemaphoreEventListener(SemaphoreEventListener listener) {
        if (listeners != null) listeners.remove(listener);
    }

    public void removeAllSemaphoreEventListeners() {
        if (listeners != null) listeners.clear();
    }

    public void setOpen(boolean open) {
        if (this.open != open) {
            this.open = open;
            if (listeners != null) {
                for (SemaphoreEventListener listener : listeners) {
                    if (open) {
                        listener.onOpen();
                    } else {
                        listener.onClosed();
                    }
                }
            }
            if (systemListeners != null) {
                for (SemaphoreEventListener listener : systemListeners) {
                    if (open) {
                        listener.onOpen();
                    } else {
                        listener.onClosed();
                    }
                }
            }
        }
    }

    public Point getPosition() {
        return this.position;
    }

    public void setPosition(Point position) {
        this.position = position;
    }

    public RailSemaphore() {}

    public RailSemaphore(int id, Point position) {
        setId(id);
        setPosition(new Point(position));
    }

    /**
     * Reinitializes transient fields after deserialization.
     * Ensures listener collections are not null to prevent NPE.
     */
    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        this.listeners = SerializationHelper.ensureListInitialized(listeners);
        this.systemListeners = SerializationHelper.ensureListInitialized(systemListeners);
    }

    public void setId(int i) {
        this.id = i;
    }

    public int getId() {
        return this.id;
    }

    public void onEnterTrain(Train train) {
        onEnterTrain(train, calculateIsForward(train));
    }

    public void onEnterTrain(Train train, boolean isForward) {
        if (listeners != null) {
            for (SemaphoreEventListener listener : listeners) {
                listener.onEnterTrain(train, isForward);
            }
        }
        if (systemListeners != null) {
            for (SemaphoreEventListener listener : systemListeners) {
                listener.onEnterTrain(train, isForward);
            }
        }
    }

    public void onExitTrain(Train train) {
        onExitTrain(train, calculateIsForward(train));
    }

    public void onExitTrain(Train train, boolean isForward) {
        if (listeners != null) {
            for (SemaphoreEventListener listener : listeners) {
                listener.onExitTrain(train, isForward);
            }
        }
        if (systemListeners != null) {
            for (SemaphoreEventListener listener : systemListeners) {
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

    public letrain.map.Dir getCreationDir() {
        return creationDir;
    }

    public void setCreationDir(letrain.map.Dir creationDir) {
        this.creationDir = creationDir;
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visitSemaphore(this);
    }

    @Override
    public String toString() {
        return "Semaphore [id=" + id + "]";
    }
}
