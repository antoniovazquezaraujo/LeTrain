package letrain.vehicle.impl.road;

import letrain.visitor.Visitor;
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
    public void incBrakes(int i) {

    }

    @Override
    public void decBrakes(int i) {

    }

    @Override
    public int getBrakes() {
        return 0;
    }

    @Override
    public void setBrakes(int i) {

    }

    @Override
    public void accept(Visitor visitor) {

    }
}
