package letrain.physics;

import letrain.map.Dir;

public interface PhysicVehicle {
    int MAX_BRAKES_FORCE = 1000;
    void move(Vector2D from, Vector2D movement);
    void move();
    void applyForces();
    double getMass();
    void setMass(double mass);
    double getInverseMass();
    Vector2D getPosition();
    void setPosition(Vector2D position);
    Vector2D getVelocity();
    void setVelocity(Vector2D velocity);
    double getSpeed();
    void setBrakesForce(double value);
    double getBrakesForce();
    double computeBrakesForce();
    boolean isBrakesActivated();
    void setBrakesActivated(boolean brakesActivated);
    Vector2D getHeading();
    void setHeading(Vector2D heading);
    void updateHeading();
    void invert(boolean invert);
    boolean isInverted();
    void onContact(Body2D.ContactResult contactResult, Body2D body2);
    void beginStep();
    void endStep();
    double getDistanceTraveledInStep();
    Dir getDir();
    void setDir(Dir dir);
    void accept(PhysicVisitor visitor);

}
