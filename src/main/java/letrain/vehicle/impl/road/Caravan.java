package letrain.vehicle.impl.road;

import letrain.vehicle.impl.Linker;
import letrain.visitor.Visitor;

public class Caravan extends Linker {
    @Override
    public float getFrictionCoefficient() {
        return 0;
    }

    @Override
    public void setAcceleration(float speed) {

    }

    @Override
    public float getAcceleration() {
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
    public float getBrakes() {
        return 0;
    }

    @Override
    public void setBrakes(float i) {

    }

    @Override
    public void accept(Visitor visitor) {

    }
}
