package letrain.vehicle.impl.road;

import letrain.vehicle.impl.Linker;
import letrain.render.Renderer;

public class Caravan extends Linker {
    @Override
    public float getFrictionCoefficient() {
        return 0;
    }

    @Override
    public void accept(Renderer renderer) {

    }
}
