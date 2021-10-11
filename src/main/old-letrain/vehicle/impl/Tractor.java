package letrain.vehicle.impl;

import letrain.map.Reversible;

public interface Tractor extends Reversible {
    float getForce();

    void setForce(float force);

    default void incForce() {
        incForce(1.0F);
    }

    void incForce(float force);

    default void decForce() {
        decForce(1.0F);
    }

    void decForce(float force);

    void reverseMotor(boolean inverted);

    boolean isMotorReversed();
}
