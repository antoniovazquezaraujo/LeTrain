package letrain.vehicle.impl;

import java.util.Iterator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import letrain.map.Dir;
import letrain.map.Mapeable;
import letrain.map.Point;
import letrain.map.Rotable;
import letrain.track.Track;
import letrain.track.Trackeable;
import letrain.vehicle.Transportable;

public class RailIterator implements Transportable, Trackeable, Rotable, Mapeable {
    Logger log = LoggerFactory.getLogger(RailIterator.class);
    Point position;
    Dir dir;
    Track track;

    public RailIterator(Track track, Dir dir) {
        this.track = track;
        this.dir = dir;
        this.position = track.getPosition();
    }

    @Override
    public boolean advance() {
        Track track = getTrack();
        Dir nextDir = getDir();
        Track nextTrack = track.getConnected(nextDir);
        if (nextTrack == null) {
            log.error("No track connected to " + track + " in direction " + nextDir);
            return false;
        }
        setTrack(nextTrack);
        setPosition(nextTrack.getPosition());
        setDir(nextTrack.getDir(nextDir.inverse()));
        return true;
    }

    @Override
    public Point getPosition() {
        return this.position;
    }

    @Override
    public void setPosition(Point position) {
        this.position = position;
    }

    @Override
    public void rotateLeft() {
        rotateLeft(1);
    }

    @Override
    public void rotateLeft(int angle) {
        dir = Dir.add(dir, angle);
    }

    @Override
    public void rotateRight() {
        rotateRight(1);
    }

    @Override
    public void rotateRight(int angle) {
        dir = Dir.add(dir, angle * -1);
    }

    @Override
    public void rotate(int angle) {
        dir = Dir.add(dir, angle);
    }

    @Override
    public Dir getDir() {
        return dir;
    }

    @Override
    public void setDir(Dir dir) {
        this.dir = dir;
    }

    @Override
    public void setTrack(Track track) {
        this.track = track;
    }

    @Override
    public Track getTrack() {
        return this.track;
    }

}
