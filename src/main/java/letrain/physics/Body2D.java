package letrain.physics;

import letrain.map.Dir;

public class Body2D implements PhysicRenderable {
    protected Vector2D position;
    protected Vector2D velocity;
    protected Vector2D externalForce;
    protected double distanceTraveledInStep = 0.0;
    protected double mass;
    double motorForce = 0.0;
    double brakesForce = 0.0;

    boolean brakesActivated = false;
    boolean inverted = false;
    public static final int MAX_BRAKES_FORCE = 10;
    final static double FRICTION_COEFICIENT = 0.9f;
    private String name;

    public Body2D(double radians, double magnitude) {
        this(
                Vector2D.fromPolar(radians, magnitude),
                Vector2D.fromPolar(0, 0)
        );
    }

    public Body2D() {
        this(new Vector2D(), new Vector2D());
    }

    public Body2D(Vector2D position, Vector2D velocity) {
        this.position = position;
        this.velocity = velocity;
        this.mass = 1;
        this.externalForce = Vector2D.fromPolar(0, 0);
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

    public void setBrakesForce(double value) {
        this.brakesForce = Math.min(Math.abs(value), MAX_BRAKES_FORCE);
    }

    public double getBrakesForce() {
        return this.brakesForce;
    }

    public void addExternalForce(Vector2D force) {
        externalForce = externalForce.add(force);
    }

    public void applyForces() {
        velocity = velocity.add(computeExternalForce());
        velocity = velocity.add(computeMotorForce());
        velocity = computeBrakesForce();
        velocity = computeFrictionForce();
        Vector2D positionReachedInStep = new Vector2D(position.x, position.y);
        positionReachedInStep = positionReachedInStep.add(velocity);
        distanceTraveledInStep = Vector2D.distance(position, positionReachedInStep);
    }

    public Vector2D computeExternalForce() {
        return new Vector2D(externalForce.div(this.mass));
    }

    public Vector2D computeMotorForce() {
        Vector2D motorForceAsVector2D = new Vector2D(velocity).normalize();
        Vector2D forceVector = Vector2D.fromDir(getDir(), inverted ? motorForce * -1 : motorForce);
        motorForceAsVector2D = motorForceAsVector2D.add(forceVector);
        return motorForceAsVector2D;
    }

    public Vector2D computeBrakesForce() {
        double n = (MAX_BRAKES_FORCE - brakesForce) / MAX_BRAKES_FORCE;
        return velocity.mult(n);
    }

    private Vector2D computeFrictionForce() {
        return velocity.mult(FRICTION_COEFICIENT);
    }

    public void beginStep() {

    }

    public void endStep() {
        if (distanceTraveledInStep > 0) {
            move();
        }
    }

    public void move() {
        Dir dir = velocity.toDir();
        int hor = 0;
        int ver = 0;
        switch (dir) {
            case E:
                hor++;
                break;
            case NE:
                hor++;
                ver--;
                break;
            case N:
                ver--;
                break;
            case NW:
                ver--;
                hor--;
                break;
            case W:
                hor--;
                break;
            case SW:
                hor--;
                ver++;
                break;
            case S:
                ver++;
                break;
            case SE:
                ver++;
                hor++;
        }
        position = new Vector2D(position.x + hor, position.y + ver);
    }

    public double getDistanceTraveledInStep() {
        return distanceTraveledInStep;
    }

    public double getMass() {
        return mass;
    }

    public double getInverseMass() {
        //Bodies with 0 mass are not real. We make them have INFINITE mass: their inverse will be ZERO.
        return mass > 0 ? 1 / mass : 0;
    }

    public void setMass(double mass) {
        mass = Math.max(1, mass);
        this.mass = mass;
    }

    public Dir getDir() {
        double angle = Vector2D.cardinal(velocity.angle(), 8);
        return Dir.fromInt((int) angle);
    }

    public double getSpeed() {
        return velocity.magnitude();
    }

    public String toString() {
        return "Position: " + position + " Velocity: " + velocity + ":" + getDir();
    }

    @Override
    public void accept(PhysicVisitor visitor) {
        visitor.visitBody(this);
    }

    public Vector2D getPosition() {
        return position;
    }

    public Vector2D getVelocity() {
        return velocity;
    }

    public Vector2D getExternalForce() {
        return externalForce;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setPosition(Vector2D position) {
        this.position = position;
    }

    public void setVelocity(Vector2D velocity) {
        this.velocity = velocity;
    }

    public void setExternalForce(Vector2D externalForce) {
        this.externalForce = externalForce;
    }

    public boolean isBrakesActivated() {
        return brakesActivated;
    }

    public void setBrakesActivated(boolean brakesActivated) {
        this.brakesActivated = brakesActivated;
    }


}
