package letrain.vehicle.impl;

import letrain.vehicle.Braker;
import letrain.vehicle.Linkable;
import letrain.vehicle.impl.rail.Train;

public abstract class Linker extends Tracker implements Linkable, Braker {
    private Train train;
    double brakes;
    boolean brakesActivated;
    @Override
    public Train getTrain() {
        return this.train;
    }
    @Override
    public void setTrain(Train train) {
        this.train = train;
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
    public double getBrakes() {
        return brakes;
    }

    @Override
    public void setBrakes(double i) {
        this.brakes=i;
//        if(brakes>10)brakes=10;
        if(brakes<0)brakes=0;
    }

    @Override
    public void activateBrakes(boolean active) {
        this.brakesActivated=active;
    }

    @Override
    public boolean isBrakesActivated() {
        return this.brakesActivated;
    }
}
