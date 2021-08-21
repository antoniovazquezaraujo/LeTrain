package letrain.physics;

import letrain.map.Dir;

public class Body2D implements PhysicRenderable {
    protected Vector2D position;
    protected Vector2D velocity;
    protected Vector2D heading;

    protected double distanceTraveledInStep = 0.0;
    protected double mass;
    double motorForce = 0.0;
    double brakesForce = 0.0;
    boolean brakesActivated = false;
    boolean inverted = false;
    public static final int MAX_BRAKES_FORCE = 10;
    final static double FRICTION_COEFICIENT = 0.99f;

    public Body2D() {
        this(Dir.W);
    }

    public Body2D(Dir dir) {
        this(Vector2D.fromDir(dir, 1));
    }

    public Body2D(Vector2D heading) {
        this.position = new Vector2D();
        this.velocity = new Vector2D();
        this.heading = heading;
        this.mass = 1;
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

    public void applyForces() {
        velocity.add(computeMotorForce());
        velocity.mult(computeBrakesForce());
        velocity.div(computeFrictionForce());

        if (velocity.magnitude() > 0) {
            heading = new Vector2D(velocity);
            heading.normalize();
        }
        Vector2D positionReachedInStep = new Vector2D(position.x, position.y);
        positionReachedInStep.add(velocity);
        distanceTraveledInStep += Vector2D.distance(position, positionReachedInStep);
    }

    public Vector2D computeMotorForce() {
        Vector2D ret = new Vector2D(this.heading);
        ret.mult((inverted ? motorForce * -1 : motorForce));
        return ret;
    }

    public double computeBrakesForce() {
        return (MAX_BRAKES_FORCE - brakesForce) / MAX_BRAKES_FORCE;
    }

    private double computeFrictionForce() {
        return FRICTION_COEFICIENT;
    }

    public void beginStep() {

    }

    public void endStep() {
        if (distanceTraveledInStep > 0) {
            distanceTraveledInStep = 0;
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
        return mass > 0 ? 1 / mass : Double.MAX_VALUE;
    }

    public void setMass(double mass) {
        mass = Math.max(1, mass);
        this.mass = mass;
    }

    public Dir getDir() {
        double dirNumber = Vector2D.radiansToCardinal(heading.angleInRadians());
        return Dir.fromInt((int) dirNumber);
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

    public void setPosition(Vector2D position) {
        this.position = position;
    }

    public void setVelocity(Vector2D velocity) {
        this.velocity = velocity;
        heading = new Vector2D(velocity);
        heading.normalize();
    }

    public boolean isBrakesActivated() {
        return brakesActivated;
    }

    public void setBrakesActivated(boolean brakesActivated) {
        this.brakesActivated = brakesActivated;
    }

    public Vector2D getHeading() {
        return heading;
    }

    public void setHeading(Vector2D heading) {
        this.heading = heading;
    }
}
