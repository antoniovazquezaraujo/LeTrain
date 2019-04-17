package letrain.vehicle.impl.road;

import letrain.vehicle.impl.Linker;
import letrain.view.Renderer;

public class Caravan extends Linker {
    @Override
    public float getFrictionCoefficient() {
        return 0;
    }

    @Override
    public void accept(Renderer renderer) {

    }
}
