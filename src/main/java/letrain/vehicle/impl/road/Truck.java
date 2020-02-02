package letrain.vehicle.impl.road;

import letrain.vehicle.impl.Tractor;

public class Truck implements Tractor {
    @Override
    public float getForce() {
        return 0;
    }

    @Override
    public void setForce(float force) {

    }

    @Override
    public void incForce(float force) {

    }

    @Override
    public void decForce(float force) {

    }

    @Override
    public void applyForce() {

    }

    @Override
    public void setMotorInverted(boolean inverted) {

    }

    @Override
    public boolean isMotorInverted() {
        return false;
    }


    @Override
    public void setReversed(boolean reversed) {

    }

    @Override
    public boolean isReversed() {
        return false;
    }
}
