package letrain.vehicle;

import letrain.physics.PhysicVisitor;

public interface PhysicVehicle extends Vehicle {

    void applyForces();
    double getMass();
    void setMass(double mass);
    double getMassInverse();


    void accept(PhysicVisitor visitor);

}