package letrain.vehicle.impl;

import letrain.map.Point;
import letrain.visitor.Visitor;
import letrain.vehicle.Vehicle;

public class Cursor extends Vehicle {
    public enum CursorMode {
        DRAWING,
        ERASING,
        MOVING
    }

    private CursorMode mode;

    public Cursor() {
        this.mode = CursorMode.DRAWING;
    }

    @Override
    public void setPosition(Point pos) {
        super.setPosition(pos);
    }

    public void setMode(CursorMode mode) {
        this.mode = mode;
    }

    public CursorMode getMode() {
        return mode;
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visitCursor(this);
    }

    @Override
    public void toggleReversed() {
        setReversed(!isReversed());
    }

    @Override
    public void destroy() {
    }

    @Override
    public boolean isDestroying() {
        return false;
    }

    @Override
    public boolean isDestroyed() {
        return false;
    }

}
