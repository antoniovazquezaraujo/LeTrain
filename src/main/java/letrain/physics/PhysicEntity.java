package letrain.physics;

import letrain.map.Dir;

public class PhysicEntity {
    protected VectorXY turnPosition;
    protected VectorXY position;
    protected VectorXY velocity;
    protected double mass;
    final static double FRICTION_COEFICIENT = 0.9f;
    public PhysicEntity(double radians, double magnitude) {
        this(
                VectorXY.fromPolar(radians, magnitude),
                VectorXY.fromPolar(0, 0)
        );
    }

    public PhysicEntity() {
        this(new VectorXY(), new VectorXY());
    }

    public PhysicEntity(VectorXY position, VectorXY velocity) {
        this.position = position;
        this.velocity = velocity;
        this.mass = 1;
        this.turnPosition = VectorXY.fromPolar(0, 0);
    }

    public void beginStep() {
        turnPosition.set(position);
    }

    public void addForce(VectorXY force) {
        velocity.add(force);
    }
    public void applyForces(){
        applyFrictionForce();
    }

    public void endStep() {
        turnPosition.add(velocity);
        double distance = VectorXY.distance(position, turnPosition);
        if (distance > 0) {
            position.set(turnPosition);
            move(distance);
        }
        System.out.println(this);
    }
    public double getMass() {
        return mass;
    }

    public void setMass(double mass) {
        this.mass = mass;
    }

    private void applyFrictionForce() {
        velocity.mult(FRICTION_COEFICIENT);
    }

    public void move(double distance) {
        System.out.println("Moving distance: " + distance);
    }
    public Dir getDir () {
        double angle = VectorXY.cardinal(velocity.angle(), 8);
        return Dir.fromInt((int) angle);
    }

    public double getSpeed(){
        return velocity.magnitude();
    }
    public String toString() {
        return "Position: " + position + " TurnPosition:" + turnPosition + " Velocity: " + velocity + ":"+ getDir()+ " Speed:"+ String.format("%.2f",getSpeed());
    }
}
