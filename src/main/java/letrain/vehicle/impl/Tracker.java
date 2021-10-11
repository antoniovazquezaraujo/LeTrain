package letrain.vehicle.impl;

import letrain.track.Track;
import letrain.track.Trackeable;
import letrain.vehicle.AbstractVehicle;

public abstract class Tracker extends AbstractVehicle implements Trackeable {
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
