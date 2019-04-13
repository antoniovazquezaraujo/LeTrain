package letrain.vehicle.impl;

import letrain.track.Track;
import letrain.track.Trackeable;
import letrain.vehicle.Vehicle;

public abstract class Tracker<T extends Track>
        extends Vehicle
        implements Trackeable<T> {
    T track;

    @Override
    public void setTrack(T track) {
        this.track = track;
    }

    @Override
    public T getTrack() {
        return this.track;
    }

}
