package letrain.vehicle.rail;

import letrain.map.Dir;
import letrain.map.Mappable;
import letrain.map.Point;
import letrain.map.Rotatable;
import letrain.track.Track;
import letrain.track.Trackable;
import letrain.vehicle.Transportable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RailIterator implements Transportable, Trackable, Rotatable, Mappable {
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
        Dir movementDir = getDir();

        // 1. Move to the next track in the current direction
        Track nextTrack = currentTrack.getConnected(movementDir);
        if (nextTrack == null) {
            // End of the line
            return false;
        }

        // 2. Find which physical connection of nextTrack points back to currentTrack
        Dir entryPort = null;
        for (Dir conn : nextTrack.getConnections()) {
            if (nextTrack.getConnected(conn) == currentTrack) {
                entryPort = conn;
                break;
            }
        }

        // 3. Determine the exit direction of the next track
        Dir nextExitDir = (entryPort != null) ? nextTrack.getDir(entryPort) : null;

        if (nextExitDir == null) {
            // Fallback: If the track router doesn't have a route for this entry port
            // (kink),
            // keep moving in the same direction. This matches TrackDirector's behavior.
            log.warn(
                    "Kink or missing route detected at {}. Entry port {} not found in router. Falling back to movement dir {}.",
                    nextTrack.getPosition(), entryPort, movementDir);
            nextExitDir = movementDir;
        }

        // 4. Update iterator state
        setTrack(nextTrack);
        setPosition(nextTrack.getPosition());
        setDir(nextExitDir);
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
