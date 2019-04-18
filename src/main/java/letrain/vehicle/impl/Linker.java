package letrain.vehicle.impl;

import letrain.map.Dir;
import letrain.track.Track;
import letrain.vehicle.Linkable;
import letrain.vehicle.impl.rail.Train;

public abstract class Linker extends Tracker implements Linkable {
    private Train train;

    @Override
    public String toString() {
        return "Linker{" +
                ", pos=" + pos +
                ", dir=" + dir +
                '}';
    }

    @Override
    public Train getTrain() {
        return this.train;
    }
    @Override
    public void setTrain(Train train) {
        this.train = train;
    }

    @Override
    public boolean advance() {
        if (this.track == null) {
            return true; // de momento
        }
        Dir dir = this.getDir();
        Dir inverseDir = dir.inverse();
        if (this.track.canExit(dir)) {
            Track  target = track.getConnected(dir);
            if (target.canEnter(inverseDir, this)) {
                Linker t = this.track.removeLinker();
                target.enterLinkerFromDir(inverseDir, t);
                return true;
            }
        }
        return false;
    }
}
