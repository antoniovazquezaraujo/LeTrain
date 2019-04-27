package letrain.vehicle.impl;

import letrain.render.Visitor;
import letrain.vehicle.Vehicle;

public class Cursor extends Vehicle {
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
