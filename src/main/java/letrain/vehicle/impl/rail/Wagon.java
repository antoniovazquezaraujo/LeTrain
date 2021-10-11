package letrain.vehicle.impl.rail;

import letrain.vehicle.Braker;
import letrain.vehicle.impl.Linker;
import letrain.visitor.Visitor;


public class Wagon extends Linker  {
    String aspect;

    public Wagon(String aspect) {
        this.aspect = aspect;
    }
    public Wagon() {
        this("?");
    }

    public Wagon(char c) {
        this(""+c);
    }
    @Override
    public double getMass() {
        return super.getMass();
    }
//    @Override
//    public double getFrictionCoefficient() {
//        return 0.0001F;
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
//    public void incBrakes(int i) {
//        brakes+=i;
//        if(brakes>10)brakes=10;
//    }
//
//    @Override
//    public void decBrakes(int i) {
//        brakes-=i;
//        if(brakes<0)brakes=0;
//    }
//
//    @Override
//    public double getBrakes() {
//        return brakes;
//    }
//
//    @Override
//    public void setBrakes(double i) {
//
//    }
//
//    @Override
//    public void setBrakes(double i) {
//        this.brakes=i;
//        if(brakes>10)brakes=10;
//        if(brakes<0)brakes=0;
//    }

    /***********************************************************
     * Renderable implementation
     **********************************************************/

    @Override
    public void accept(Visitor visitor) {
        visitor.visitWagon(this);
    }
    public String getAspect() {
        return aspect;
    }

    public void setAspect(String aspect) {
        this.aspect = aspect;
    }


    @Override
    public void activateBrakes(boolean active) {

    }

    @Override
    public boolean isBrakesActivated() {
        return false;
    }
}
