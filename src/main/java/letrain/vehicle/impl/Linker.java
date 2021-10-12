package letrain.vehicle.impl;

import letrain.map.Dir;
import letrain.track.Track;
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
    public Linker drag() {
        Track targetTrack = getTrack().getConnectedTrack(this.getDir());
        if (targetTrack.canEnter(this)) {
            targetTrack.enter(this);
            return null;
        }
        return targetTrack.getLinker();
    }

    @Override
    public void move() {
        boolean reversed = isReversed();
        Dir outDir = null;
        if (!reversed) {
            outDir = getDir();
        } else {
            outDir = getDir().inverse();
        }
        Track targetTrack = getTrack().getConnectedTrack(outDir);
        if (targetTrack.canEnter(this)) {
            targetTrack.enter(this);
        }
    }

    @Override
    public void incBrakes(int i) {
        brakes += i;
//        if(brakes>10)brakes=10;
    }

    @Override
    public void decBrakes(int i) {
        brakes -= i;
        if (brakes < 0) brakes = 0;
    }

    @Override
    public double getBrakes() {
        return brakes;
    }

    @Override
    public void setBrakes(double i) {
        this.brakes = i;
//        if(brakes>10)brakes=10;
        if (brakes < 0) brakes = 0;
    }

    @Override
    public void activateBrakes(boolean active) {
        this.brakesActivated = active;
    }

    @Override
    public boolean isBrakesActivated() {
        return this.brakesActivated;
    }

    /**
     * Devuelve la dirección en la que el linker no tiene nada enganchado.
     * Se usa siempre con el dragger, es decir, el linker que está en un extremo
     * y que va a ser el que va a simular que tira de los demás.
     * Se toma la dirección en la que no haya nada o si hay algún linker, que no pertenezca
     * a su mismo tren
     */
    @Override
    public Dir getUnlinkedDir() {
        Dir myDir = this.getDir();
        Track myTrack = this.getTrack();
        Track targetTrack = myTrack.getConnectedTrack(myDir);
        Linker targetLinker = targetTrack.getLinker();
        if (targetLinker == null || !targetLinker.getTrain().equals(this.getTrain())) {
            return myDir;
        } else {
            return this.getTrack().getDirWhenEnteringFrom(myDir.inverse());
        }
    }
}
