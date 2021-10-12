package letrain.vehicle;

import letrain.physics.Vector2D;

public interface Tractor {
    void incMotorForce(double value);
    void decMotorForce(double value);
    void setMotorForce(double value);
    double getMotorForce();
    public void reverseMotor(boolean reverse);
    public boolean isMotorReversed();
}