package letrain.vehicle.impl.rail;

import letrain.vehicle.Tractor;
import letrain.vehicle.impl.Linker;
import letrain.visitor.Visitor;


public class Locomotive extends Linker implements Tractor {

    private double motorForce;
    private String aspect;

    boolean motorReversed = false;
    public Locomotive(String aspect){
        this.aspect = aspect;
        motorForce = 0.0;
    }
    public Locomotive(char c) {
        this(""+c);
    }

    @Override
    public double getMass() {
        return super.getMass()+500;
    }

//    @Override
//    public double getFrictionCoefficient() {
//        return 0.0001F ;
//    }

//
//    @Override
//    public void setAcceleration(double speed) {
//
//    }
//
//    @Override
//    public double getAcceleration() {
//        return 0;
//    }
//
//    @Override
//    public double getDistanceTraveled() {
//        return 0;
//    }
//
//    @Override
//    public void resetDistanceTraveled() {
//
//    }


//    @Override
//    public double getForce() {
//
//    }
//
//    @Override
//    public void setForce(double force) {
//
//    }
//
//    @Override
//    public void incForce(double force) {
//        this.motorForce+=force;
////        if(this.motorForce>1000)this.motorForce=1000;
//    }
//
//    @Override
//    public void decForce(double force) {
//        this.motorForce-=force;
//        if(this.motorForce<0)this.motorForce=0;
//    }

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

    @Override
    public void incMotorForce(double value) {
        this.motorForce+=value;
    }

    @Override
    public void decMotorForce(double value) {
        this.motorForce-=value;
    }

    @Override
    public void setMotorForce(double value) {
        this.motorForce= value;
    }

    @Override
    public double getMotorForce() {
        // TODO: review this logic!
        if(isMotorReversed()) {
            return this.motorForce * -1;
        }
        else {
            return this.motorForce;
        }
    }

}
