package letrain.track;

import java.io.Serializable;

import letrain.map.Point;
import letrain.visitor.Renderable;
import letrain.visitor.Visitor;

public class RailSemaphore implements Renderable, Serializable {
    private static final long serialVersionUID = 1L;
    private int id;
    private Point position;
    private boolean open;
    private letrain.map.Dir creationDir = letrain.map.Dir.E;

    public boolean isOpen() {
        return open;
    }

    private java.util.List<SemaphoreEventListener> listeners = new java.util.ArrayList<>();
    private java.util.List<SemaphoreEventListener> systemListeners = new java.util.ArrayList<>();

    public void addSemaphoreEventListener(SemaphoreEventListener listener) {
        listeners.add(listener);
    }

    public void addSystemSemaphoreEventListener(SemaphoreEventListener listener) {
        systemListeners.add(listener);
    }

    public void removeSemaphoreEventListener(SemaphoreEventListener listener) {
        listeners.remove(listener);
    }

    public void removeAllSemaphoreEventListeners() {
        listeners.clear();
    }

    public void setOpen(boolean open) {
        if (this.open != open) {
            this.open = open;
            for (SemaphoreEventListener listener : listeners) {
                if (open) {
                    listener.onOpen();
                } else {
                    listener.onClosed();
                }
            }
            for (SemaphoreEventListener listener : systemListeners) {
                if (open) {
                    listener.onOpen();
                } else {
                    listener.onClosed();
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

    public RailSemaphore(int id, Point position) {
        setId(id);
        setPosition(new Point(position));
    }

    public void setId(int i) {
        this.id = i;
    }

    public int getId() {
        return this.id;
    }

    public void onEnterTrain(letrain.vehicle.impl.rail.Train train) {
        onEnterTrain(train, calculateIsForward(train));
    }

    public void onEnterTrain(letrain.vehicle.impl.rail.Train train, boolean isForward) {
        for (SemaphoreEventListener listener : listeners) {
            listener.onEnterTrain(train, isForward);
        }
        for (SemaphoreEventListener listener : systemListeners) {
            listener.onEnterTrain(train, isForward);
        }
    }

    public void onExitTrain(letrain.vehicle.impl.rail.Train train) {
        onExitTrain(train, calculateIsForward(train));
    }

    public void onExitTrain(letrain.vehicle.impl.rail.Train train, boolean isForward) {
        for (SemaphoreEventListener listener : listeners) {
            listener.onExitTrain(train, isForward);
        }
        for (SemaphoreEventListener listener : systemListeners) {
            listener.onExitTrain(train, isForward);
        }
    }

    private boolean calculateIsForward(letrain.vehicle.impl.rail.Train train) {
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
