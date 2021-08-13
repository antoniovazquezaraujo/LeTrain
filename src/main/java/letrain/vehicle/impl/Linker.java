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
    public Track move(){
        boolean reversed = isReversed();
        Dir outDir =null;
        if(!isReversed()) {
            outDir=getDir();
        }else{
            outDir=getDir().inverse();
        }

        Track targetTrack =getTrack().getConnectedTrack(outDir);
        if(targetTrack.canEnter(this)){
            targetTrack.enter(this);
            return null;
        }
        return targetTrack;
    }

    public Track getForwardTrack(){
        return getTrack().getConnectedTrack(getDir());
    }
    public Track getBackwardTrack(){
        return getTrack().getConnectedTrack(getTrack().getDirWhenEnteringFrom(getDir().inverse()));
    }
}
