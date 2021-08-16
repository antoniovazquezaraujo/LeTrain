package letrain.physics;

public class MotorizedEntity extends PhysicEntity{
    double motorForce = 0.0;
    double brakesForce = 0.0;
    boolean inverted = false;
    public void invert(boolean invert){
        this.inverted = invert;
    }
    public boolean isInverted(){
        return inverted;
    }
    public void setMotorForce(double value){
        this.motorForce = Math.abs(value);
    }
    public double getMotorForce(){
        return this.motorForce;
    }

    public MotorizedEntity(double radians, double magnitude) {
        super(radians, magnitude);
    }

    public MotorizedEntity(VectorXY position, VectorXY velocity ) {
        super(position, velocity);
    }
    public MotorizedEntity(){
        this(0,0);
    }

    public void applyMotorForce(){
        VectorXY motorForceAsVectorXY = new VectorXY(velocity);
        motorForceAsVectorXY.normalize();
        motorForceAsVectorXY.mult(inverted?motorForce*-1:motorForce);
        addForce(motorForceAsVectorXY);
    }
    public void setBrakesForce(float value){
        this.brakesForce = Math.abs(value);
    }
    public double getBrakesForce(){
        return this.brakesForce;
    }
    public void applyForces(){
        super.applyForces();
        applyMotorForce();
        applyBrakesForce();
    }

    public void applyBrakesForce(){
        velocity.mult((10-brakesForce)/10);
    }

}
