package letrain.vehicle.impl;

import letrain.render.Renderer;
import letrain.vehicle.Vehicle;

public class Cursor extends Vehicle {
    @Override
    public void accept(Renderer renderer) {
        renderer.renderCursor(this);
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
