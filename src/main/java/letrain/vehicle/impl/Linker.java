package letrain.vehicle.impl;

import letrain.map.Dir;
import letrain.track.Track;
import letrain.vehicle.Linkable;
import letrain.vehicle.impl.rail.Train;

public abstract class Linker<T extends Track> extends Tracker<T> implements Linkable {
    private Train train;
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
            Track<T> target = (Track<T>) track.getConnected(dir);
            if (target.canEnter(inverseDir, this)) {
                Linker<Track<T>> t = this.track.exitLinker(dir);
                target.enterLinker(inverseDir, t);
                return true;
            }
        }
        return false;
    }
}
