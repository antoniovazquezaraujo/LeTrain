package letrain.vehicle.impl.rail;

import letrain.visitor.Visitor;
import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.Tractor;

public class Locomotive extends Linker implements Tractor{

    private float force;
    private String aspect;
    int brakes;
    public Locomotive(String aspect){
        this.aspect = aspect;
        force = 0;
    }
    public Locomotive(char c) {
        this(""+c);
    }

    @Override
    public float getMass() {
        return super.getMass()+5000;
    }

    @Override
    public float getFrictionCoefficient() {
        return 0.001F * (1+brakes);
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

    @Override
    public float getForce() {
        return this.force;
    }

    @Override
    public void setForce(float force) {
        this.force = force;
    }

    @Override
    public void incForce(float force) {
        this.force+=force;
    }

    @Override
    public void decForce(float force) {
        this.force-=force;
        if(this.force<0.0F){
            this.force =0.0F;
        }
    }

    @Override
    public void applyForce() {

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
