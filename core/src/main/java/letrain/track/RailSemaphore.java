package letrain.track;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonTypeName;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;
import letrain.utils.SerializationHelper;
import letrain.vehicle.rail.impl.Train;
import letrain.visitor.Visitor;

/**
 * A railway signal. A {@code RailSemaphore} is a {@link Sensor} (a track device that reacts to a
 * train passing over it) that additionally exposes a signal state ({@code open} / closed).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeName("Semaphore")
public class RailSemaphore extends Sensor {

    @JsonIgnore
    private transient List<SemaphoreEventListener> semaphoreListeners = new ArrayList<>();

    @JsonIgnore
    private transient List<SemaphoreEventListener> systemSemaphoreListeners = new ArrayList<>();

    private boolean open;

    public RailSemaphore() {}

    public RailSemaphore(int id) {
        super(id);
    }

    public boolean isOpen() {
        return open;
    }

    public void setOpen(boolean open) {
        if (this.open != open) {
            this.open = open;
            notifyOpenState(open);
        }
    }

    public void addSemaphoreEventListener(SemaphoreEventListener listener) {
        if (semaphoreListeners == null) {
            semaphoreListeners = new ArrayList<>();
        }
        semaphoreListeners.add(listener);
    }

    public void addSystemSemaphoreEventListener(SemaphoreEventListener listener) {
        if (systemSemaphoreListeners == null) {
            systemSemaphoreListeners = new ArrayList<>();
        }
        systemSemaphoreListeners.add(listener);
    }

    public void removeSemaphoreEventListener(SemaphoreEventListener listener) {
        if (semaphoreListeners != null) {
            semaphoreListeners.remove(listener);
        }
    }

    public void removeAllSemaphoreEventListeners() {
        if (semaphoreListeners != null) {
            semaphoreListeners.clear();
        }
    }

    @Override
    public void onEnterTrain(Train train) {
        notifySemaphoreEvent(train, true, calculateIsForward(train));
    }

    @Override
    public void onExitTrain(Train train) {
        notifySemaphoreEvent(train, false, calculateIsForward(train));
    }

    private void notifyOpenState(boolean open) {
        notifyOpenState(semaphoreListeners, open);
        notifyOpenState(systemSemaphoreListeners, open);
    }

    private void notifyOpenState(List<SemaphoreEventListener> listeners, boolean open) {
        if (listeners == null) {
            return;
        }
        for (SemaphoreEventListener listener : listeners) {
            if (open) {
                listener.onOpen();
            } else {
                listener.onClosed();
            }
        }
    }

    private void notifySemaphoreEvent(Train train, boolean isEnter, boolean isForward) {
        notifySemaphoreEvent(semaphoreListeners, train, isEnter, isForward);
        notifySemaphoreEvent(systemSemaphoreListeners, train, isEnter, isForward);
    }

    private void notifySemaphoreEvent(List<SemaphoreEventListener> listeners, Train train,
            boolean isEnter, boolean isForward) {
        if (listeners == null) {
            return;
        }
        for (SemaphoreEventListener listener : listeners) {
            if (isEnter) {
                listener.onEnterTrain(train, isForward);
            } else {
                listener.onExitTrain(train, isForward);
            }
        }
    }

    /**
     * Reinitializes transient fields after deserialization. Ensures listener collections are not
     * null to prevent NPE.
     */
    private void readObject(ObjectInputStream ois) throws IOException, ClassNotFoundException {
        ois.defaultReadObject();
        this.semaphoreListeners = SerializationHelper.ensureListInitialized(semaphoreListeners);
        this.systemSemaphoreListeners =
                SerializationHelper.ensureListInitialized(systemSemaphoreListeners);
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visitSemaphore(this);
    }

    @Override
    public String toString() {
        return "Semaphore [id=" + getId() + "]";
    }
}
