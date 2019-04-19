package letrain.vehicle.impl;

import letrain.render.Renderer;
import letrain.vehicle.Vehicle;

public class Cursor extends Vehicle {
    @Override
    public void accept(Renderer renderer) {
        renderer.renderCursor(this);
    }
}
