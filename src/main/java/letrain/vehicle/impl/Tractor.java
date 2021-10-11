package letrain.vehicle.impl;

import letrain.map.Reversible;

public interface Tractor extends Reversible {
    double getForce();

    void setForce(double force);

    default void incForce() {
        incForce(1.0F);
    }

    void incForce(double force);

    default void decForce() {
        decForce(1.0F);
    }

    void decForce(double force);

    void reverseMotor(boolean inverted);

    boolean isMotorReversed();
}
