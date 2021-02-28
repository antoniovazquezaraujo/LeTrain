package letrain.vehicle.impl;

import letrain.map.Dir;
import letrain.track.Track;
import letrain.track.Trackeable;
import letrain.vehicle.Vehicle;

public abstract class Tracker extends Vehicle implements Trackeable {
    Track track;

    @Override
    public void setTrack(Track track) {
        this.track = track;
    }

    @Override
    public Track getTrack() {
        return this.track;
    }

 }
