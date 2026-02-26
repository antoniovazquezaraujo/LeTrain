package letrain.vehicle.impl;

import letrain.map.Dir;
import letrain.map.Mapeable;
import letrain.map.Point;
import letrain.map.Rotable;
import letrain.track.Track;
import letrain.track.Trackeable;
import letrain.vehicle.Transportable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        Track currentTrack = getTrack();
        Dir entryDir = getDir();
        Dir exitDir = currentTrack.getDir(entryDir.inverse());

        if (exitDir == null) {
            log.error("No exit direction found for track at {} with entry dir {}", currentTrack.getPosition(),
                    entryDir);
            return false;
        }

        Track nextTrack = currentTrack.getConnected(exitDir);
        if (nextTrack == null) {
            log.error("No track connected to {} in direction {}", currentTrack, exitDir);
            return false;
        }

        setTrack(nextTrack);
        setPosition(nextTrack.getPosition());
        setDir(exitDir); // We exited in exitDir, so we entered the next track from exitDir
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
