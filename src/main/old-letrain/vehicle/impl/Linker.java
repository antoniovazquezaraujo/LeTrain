package letrain.vehicle.impl;

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
    public Track move(){
        Track targetTrack =getTrack().getConnectedTrack();
        if(targetTrack.canEnter(this)){
            targetTrack.enter(this);
            return null;
        }
        return targetTrack;
    }
}
