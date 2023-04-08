package letrain.vehicle.impl;

import letrain.map.Reversible;

public interface Tractor extends Reversible {
    boolean isTimeToMove();

    void consumeTurn();

    void resetTurns();

    int getSpeed();

    void setSpeed(int speed);

    void incSpeed();

    void decSpeed();

    void toggleMotorInversion();

    boolean isMotorInverted();
}
