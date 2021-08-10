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
    public void move() {

    }

    @Override
    public void reverseMotor(boolean inverted) {

    }

    @Override
    public boolean isMotorReversed() {
        return false;
    }


    @Override
    public void reverse(boolean reversed) {

    }

    @Override
    public boolean isReversed() {
        return false;
    }
}
