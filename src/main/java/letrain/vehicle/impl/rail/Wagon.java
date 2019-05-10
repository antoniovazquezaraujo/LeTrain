package letrain.vehicle.impl.rail;

import letrain.visitor.Visitor;
import letrain.vehicle.impl.Linker;

public class Wagon extends Linker {
    String aspect;
    int brakes;
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
    public float getMass() {
        return super.getMass();
    }
    @Override
    public float getFrictionCoefficient() {
        return 0.001F * (1+ brakes);
    }

    @Override
    public float getAcceleration() {
        return 0;
    }

    @Override
    public float getSpeed() {
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
    public int getBrakes() {
        return brakes;
    }

    @Override
    public void setBrakes(int i) {
        this.brakes=i;
    }

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
}
