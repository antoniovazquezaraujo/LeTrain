package letrain.physics;

import letrain.map.Dir;

public class Body {
    protected VectorXY position;
    protected VectorXY velocity;
    protected VectorXY positionReachedInStep;
    protected double distanceTraveledInStep=0.0;
    protected double mass;
    double motorForce = 0.0;
    double brakesForce = 0.0;
    boolean inverted = false;
    public static final int MAX_BRAKES_FORCE = 10;
    final static double FRICTION_COEFICIENT = 0.9f;

    public Body(double radians, double magnitude) {
        this(
                VectorXY.fromPolar(radians, magnitude),
                VectorXY.fromPolar(0, 0)
        );
    }

    public Body() {
        this(new VectorXY(), new VectorXY());
    }

    public Body(VectorXY position, VectorXY velocity) {
        this.position = position;
        this.velocity = velocity;
        this.mass = 1;
        this.positionReachedInStep = VectorXY.fromPolar(0, 0);
    }

    public void invert(boolean invert) {
        this.inverted = invert;
    }

    public boolean isInverted() {
        return inverted;
    }

    public void setMotorForce(double value) {
        this.motorForce = Math.abs(value);
    }

    public double getMotorForce() {
        return this.motorForce;
    }

    public void setBrakesForce(float value) {
        this.brakesForce = Math.abs(value%MAX_BRAKES_FORCE);
    }

    public double getBrakesForce() {
        return this.brakesForce;
    }

    public void applyForces() {
        applyMotorForce();
        applyBrakesForce();
        applyFrictionForce();
    }
    public void applyMotorForce() {
        VectorXY motorForceAsVectorXY = new VectorXY(velocity);
        motorForceAsVectorXY.normalize();
        motorForceAsVectorXY.mult(inverted ? motorForce * -1 : motorForce);
        addForce(motorForceAsVectorXY);
    }
    public void applyBrakesForce() {
        velocity.mult(1- (brakesForce / MAX_BRAKES_FORCE));
    }
    private void applyFrictionForce() {
        velocity.mult(FRICTION_COEFICIENT);
    }

    public void beginStep() {
        positionReachedInStep.set(position);
    }

    public void addForce(VectorXY force) {
        force.div(this.mass);
        velocity.add(force);
    }

    public void endStep() {
        positionReachedInStep.add(velocity);
        distanceTraveledInStep = VectorXY.distance(position, positionReachedInStep);
        if (distanceTraveledInStep > 0) {
            position.set(positionReachedInStep);
        }
    }
    public void move(){
        position.set(positionReachedInStep);
    }
    public double getDistanceTraveledInStep() {
        return distanceTraveledInStep;
    }
    public double getMass() {
        return mass;
    }
    public double getInverseMass(){
        //Bodies with 0 mass are not real. We make them have INFINITE mass: their inverse will be ZERO.
        return mass>0?1/mass:0;
    }

    public void setMass(double mass) {
        mass = Math.max(1, mass);
        this.mass = mass;
    }

    public Dir getDir() {
        double angle = VectorXY.cardinal(velocity.angle(), 8);
        return Dir.fromInt((int) angle);
    }

    public double getSpeed() {
        return velocity.magnitude();
    }

    public String toString() {
        return "Position: " + position + " TurnPosition:" + positionReachedInStep + " Velocity: " + velocity + ":" + getDir() + " Speed:" + String.format("%.2f", getSpeed());
    }
}
