package letrain.physics;

public class MotorizedEntity extends PhysicEntity{
    float motorForce;
    boolean inverted;
    public void invert(boolean invert){
        this.inverted = invert;
    }
    public boolean isInverted(){
        return inverted;
    }
    public void setMotorForce(float value){
        this.motorForce = Math.abs(value);
    }
    public float getMotorForce(){
        return this.motorForce;
    }

    public void applyMotorForce(){
        VectorXY motorForceAsVectorXY = new VectorXY(velocity);
        motorForceAsVectorXY.normalize();
        motorForceAsVectorXY.mult(inverted?motorForce*-1:motorForce);
        acceleration.add(motorForceAsVectorXY);
    }
}
