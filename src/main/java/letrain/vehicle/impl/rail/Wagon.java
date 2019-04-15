package letrain.vehicle.impl.rail;

import letrain.vehicle.impl.Linker;

public class Wagon extends Linker {

    @Override
    public float getFrictionCoefficient() {
        return 0.3F;
    }
}
