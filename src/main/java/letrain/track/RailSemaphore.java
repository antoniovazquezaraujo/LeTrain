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

    public boolean isOpen() {
        return open;
    }

    private java.util.List<SemaphoreEventListener> listeners = new java.util.ArrayList<>();

    public void addSemaphoreEventListener(SemaphoreEventListener listener) {
        listeners.add(listener);
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

    @Override
    public void accept(Visitor visitor) {
        visitor.visitSemaphore(this);
    }

    @Override
    public String toString() {
        return "Semaphore [id=" + id + "]";
    }

}
