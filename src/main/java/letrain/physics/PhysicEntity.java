package letrain.physics;

import letrain.map.Dir;

public class PhysicEntity {
    VectorXY turnPosition;
    VectorXY position;
    VectorXY velocity;
    VectorXY acceleration;
    VectorXY friction;
    float mass;
    final static float FRICTION_COEFICIENT = 0.9f;
    public PhysicEntity(float radians, float magnitude) {
        this(
                VectorXY.fromPolar(radians, magnitude),
                VectorXY.fromPolar(0, 0),
                VectorXY.fromPolar(0, 0),
                1);
    }

    public PhysicEntity(Dir dir, float magnitude) {
        this(VectorXY.fromDir(dir, magnitude), VectorXY.fromPolar(0, 0), VectorXY.fromPolar(0, 0), 1);
    }

    public PhysicEntity() {
        this(new VectorXY(), new VectorXY(), new VectorXY(), 1);
    }

    public PhysicEntity(VectorXY position, VectorXY velocity, VectorXY acceleration, float mass) {
        this.position = position;
        this.velocity = velocity;
        this.acceleration = acceleration;
        this.mass = mass;
        this.turnPosition = VectorXY.fromPolar(0, 0);
        this.friction = new VectorXY(0,0);
    }

    public void startTurn() {
        acceleration.reset();
        turnPosition.set(position);
    }

    public void applyForce(VectorXY force) {
        acceleration.add(force);
    }

    public void endTurn() {
        System.out.println(this);
        applyFrictionForce();
        velocity.add(acceleration);
        turnPosition.add(velocity);
        float distance = VectorXY.distance(position, turnPosition);
        System.out.println(this);
        if (distance > 0) {
            position.set(turnPosition);
            move(distance);
        }
        acceleration.reset();
    }

    private void applyFrictionForce() {
        velocity.mult(FRICTION_COEFICIENT);
    }

    public void move(float distance) {
        System.out.println("Moving distance: "+ distance);
//        position.normalize();
    }

    public String toString() {
        return "Position: " + position + " TurnPosition:" + turnPosition + " Acceleration: " + acceleration + " Velocity: " + velocity;
    }
}
