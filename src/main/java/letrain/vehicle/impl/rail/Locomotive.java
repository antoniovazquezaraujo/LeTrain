package letrain.vehicle.impl.rail;

import letrain.visitor.Visitor;
import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.Tractor;

public class Locomotive extends Linker implements Tractor{

    private float force;
    private String aspect;
    float brakes;
    boolean motorReversed = false;
    public Locomotive(String aspect){
        this.aspect = aspect;
        force = 0;
    }
    public Locomotive(char c) {
        this(""+c);
    }

    @Override
    public float getMass() {
        return super.getMass()+500;
    }

    @Override
    public float getFrictionCoefficient() {
        return 0.0001F ;
    }


    @Override
    public void setAcceleration(float speed) {

    }

    @Override
    public float getAcceleration() {
        return 0;
    }

    @Override
    public float getDistanceTraveled() {
        return 0;
    }

    @Override
    public void resetDistanceTraveled() {

    }

    @Override
    public void incBrakes(int i) {
        brakes+=i;
//        if(brakes>10)brakes=10;
    }

    @Override
    public void decBrakes(int i) {
        brakes-=i;
        if(brakes<0)brakes=0;
    }

    @Override
    public float getBrakes() {
        return brakes;
    }

    @Override
    public void setBrakes(float i) {
        this.brakes=i;
//        if(brakes>10)brakes=10;
        if(brakes<0)brakes=0;
    }

    @Override
    public float getForce() {
        if(isMotorReversed()) {
            return this.force * -1;
        }
        else {
            return this.force;
        }
    }

    @Override
    public void setForce(float force) {
        this.force = force;
    }

    @Override
    public void incForce(float force) {
        this.force+=force;
//        if(this.force>1000)this.force=1000;
    }

    @Override
    public void decForce(float force) {
        this.force-=force;
        if(this.force<0)this.force=0;
    }

    @Override
    public void move() {

    }

    @Override
    public void reverseMotor(boolean motorReversed) {
        reverse(motorReversed);
    }

    @Override
    public boolean isMotorReversed() {
        return motorReversed;
    }

    @Override
    public void reverse(boolean reversed){
        this.motorReversed =reversed;
       super.reverse(reversed);
    }

    @Override
    public boolean isReversed() {
        return super.isReversed();
    }
    /***********************************************************
     * Renderable implementation
     **********************************************************/

    @Override
    public void accept(Visitor visitor) {
        visitor.visitLocomotive(this);
    }

    public String getAspect() {
        return aspect;
    }
}
