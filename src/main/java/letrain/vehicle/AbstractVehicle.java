package letrain.vehicle;

import letrain.map.Dir;

import letrain.physics.PhysicVisitor;
import letrain.physics.Vector2D;
import letrain.visitor.Visitor;


public abstract class AbstractVehicle implements PhysicVehicle {
    protected boolean reversed;
    private Vector2D velocity2D;
    private Vector2D heading2D;
    private double mass;
    private Vector2D position2D;
    private double distanceTraveledInStep;

    public AbstractVehicle(){
        this.velocity2D = new Vector2D();
        this.heading2D = new Vector2D();
        this.position2D = new Vector2D();
        this.reversed=false;
        this.mass = 1;
    }
    // PhysicVehicle
    @Override
    public void applyForces() {

    }

    @Override
    public double getMass() {
        return this.mass;
    }

    @Override
    public void setMass(double mass) {
        this.mass = mass;
    }

    @Override
    public double getMassInverse() {
        return mass != 0 ? 1 / mass : Double.MAX_VALUE;
    }

    @Override
    public void accept(PhysicVisitor visitor) {

    }

    // Vehicle
    @Override
    public void move() {
        move(position2D, velocity2D);
    }
    public static void move(Vector2D from, Vector2D movement) {
        Dir dir = movement.toDir();
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
        from.setX(from.x + hor);
        from.setY(from.y + ver);
        from.round();
    }

    @Override
    public void beginStep() {

    }

    @Override
    public void endStep() {
        if (getDistanceTraveledInStep()> 0) {
            setDistanceTraveledInStep(0);
            move();
        }
    }

    public void setDistanceTraveledInStep(double distance) {
        this.distanceTraveledInStep = distance;
    }

    @Override
    public double getDistanceTraveledInStep() {
        return this.distanceTraveledInStep;
    }

    //MovableBody

    @Override
    public Vector2D getVelocity2D() {
        return this.velocity2D;
    }

    @Override
    public void setVelocity2D(Vector2D velocity) {
        this.velocity2D = velocity2D;
    }

    @Override
    public double getVelocity() {
        return this.velocity2D != null ? this.velocity2D.magnitude() : 0;
    }

    // TangibleBody

    @Override
    public void onContact(ContactResult contactResult, TangibleBody vehicle) {

    }

    // Orientable
    @Override
    public Vector2D getHeading2D() {
        return this.heading2D;
    }

    @Override
    public void setHeading2D(Vector2D heading) {
        this.heading2D = heading;
    }

    @Override
    public Dir getDir() {
        double dirNumber = Vector2D.radiansToCardinal(this.heading2D.angleInRadians());
        return Dir.fromInt((int) dirNumber);
    }

    @Override
    public void setDir(Dir dir) {
        this.heading2D = Vector2D.fromDir(dir, 1);
    }

    //Positionable
    @Override
    public Vector2D getPosition2D() {
        return this.position2D;
    }

    @Override
    public void setPosition2D(Vector2D position2D) {
        this.position2D = position2D;
    }

    // Reversible
    @Override
    public void reverse(boolean reversed) {
        this.reversed = reversed;
    }

    @Override
    public boolean isReversed() {
        return this.reversed;
    }

    @Override
    public void accept(Visitor visitor) {

    }
    void rotateLeft(){}

    void rotateLeft(int angle){}

    void rotateRight(){}

    void rotateRight(int angle){}

    void rotate(int angle){}
}
