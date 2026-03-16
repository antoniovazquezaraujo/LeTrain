package letrain.vehicle.impl;

import letrain.map.Dir;
import letrain.track.Track;
import letrain.vehicle.Linkable;
import letrain.vehicle.impl.rail.Train;

public abstract class Linker extends Tracker implements Linkable {
    private Train train;
    private Dir entryDir;
    private Track previousTrack;
    private Dir previousDir;
    private Dir previousEntryDir;
    private int railsSinceStop = 0;

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

    public Dir getEntryDir() {
        return entryDir;
    }

    public void setEntryDir(Dir entryDir) {
        this.previousEntryDir = this.entryDir;
        this.entryDir = entryDir;
    }

    public int getRailsSinceStop() {
        return railsSinceStop;
    }

    public void setRailsSinceStop(int railsSinceStop) {
        this.railsSinceStop = railsSinceStop;
    }

    public Track getPreviousTrack() {
        return previousTrack;
    }

    public Dir getPreviousDir() {
        return previousDir;
    }

    public Dir getPreviousEntryDir() {
        return previousEntryDir;
    }

    public void setPreviousTrack(Track previousTrack) {
        this.previousTrack = previousTrack;
    }

    public void setPreviousDir(Dir previousDir) {
        this.previousDir = previousDir;
    }

    @Override
    public boolean advance() {
        if (this.track == null) {
            return true; // de momento
        }
        Dir dir = this.getDir();
        Dir inverseDir = dir.inverse();
        if (this.track.canExit(dir)) {
            Track target = track.getConnected(dir);
            if (target.canEnter(inverseDir, this)) {
                this.previousTrack = this.track;
                this.previousDir = dir;
                Linker t = this.track.removeLinker();
                target.enterLinkerFromDir(inverseDir, t);
                this.railsSinceStop++;
                return true;
            }
        }
        return false;
    }
}
