package letrain.vehicle.rail;

import letrain.map.Dir;
import letrain.track.Track;
import letrain.vehicle.Linkable;
import letrain.vehicle.Cursor;
import letrain.vehicle.Tracker;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Wagon;
import letrain.vehicle.rail.impl.Train;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

/**
 * Abstract base for any vehicle that can be linked into a train
 * (locomotives, wagons, cursors). Tracks its current position,
 * direction, previous cell, and the train it belongs to.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
@JsonSubTypes({
    @JsonSubTypes.Type(value = Locomotive.class, name = "Locomotive"),
    @JsonSubTypes.Type(value = Wagon.class, name = "Wagon"),
    @JsonSubTypes.Type(value = Cursor.class, name = "Cursor")
})
@JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@id")
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
        if (this.getTrack() == null) {
            return true; // de momento
        }
        Dir dir = this.getDir();
        Dir inverseDir = dir.inverse();
        if (this.getTrack().canExit(dir)) {
            Track target = getTrack().getConnected(dir);
            if (target.canEnter(inverseDir, this)) {
                this.previousTrack = this.getTrack();
                this.previousDir = dir;
                Linker t = this.getTrack().removeLinker();
                target.enterLinkerFromDir(inverseDir, t);
                this.railsSinceStop++;
                return true;
            }
        }
        return false;
    }
}
