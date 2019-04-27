package letrain.vehicle.impl.road;

import letrain.render.Visitor;
import letrain.vehicle.impl.Linker;

public class Caravan extends Linker {
    @Override
    public float getFrictionCoefficient() {
        return 0;
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

    @Override
    public void accept(Visitor visitor) {

    }
}
