package letrain.vehicle.impl;

import letrain.map.Point;
import letrain.visitor.Visitor;
import letrain.vehicle.Vehicle;

public class Cursor extends Vehicle {
    public enum CursorMode{
        DRAWING,
        ERASING,
        MOVING
    }
    private CursorMode mode;

    public Cursor(){
        this.mode = CursorMode.DRAWING;
    }
    @Override
    public void setPosition(Point pos) {
        super.setPosition(pos);
    }
    public void setMode(CursorMode mode){
        this.mode = mode;
    }
    public CursorMode getMode(){
        return mode;
    }

    @Override
    public void accept(Visitor visitor) {
        visitor.visitCursor(this);
    }

    @Override
    public float getAcceleration() {
        return 0;
    }

    @Override
    public float getSpeed() {
        return 0;
    }

    @Override
    public float getDistanceTraveled() {
        return 0;
    }

    @Override
    public void resetDistanceTraveled() {

    }
}
