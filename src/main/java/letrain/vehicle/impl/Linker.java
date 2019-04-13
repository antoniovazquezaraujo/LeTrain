package letrain.vehicle.impl;

import letrain.map.Dir;
import letrain.track.Track;
import letrain.vehicle.Linkable;

public class Linker<T extends Track> extends Tracker<T> implements Linkable {
    private final Linkable[] linkables = new Linkable[LinkSide.values().length];

    //Linkable interface
    @Override
    public void link(LinkSide side, Linkable other) {
        this.linkables[side.getValue()] = other;
    }

    @Override
    public Linkable unlink(LinkSide side) {
        Linkable ret = getLinked(side);
        linkables[side.getValue()] = null;
        return ret;
    }

    @Override
    public Linkable getLinked(LinkSide side) {
        return this.linkables[side.getValue()];
    }

    // Transportable implementation -----------------------------------------
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
