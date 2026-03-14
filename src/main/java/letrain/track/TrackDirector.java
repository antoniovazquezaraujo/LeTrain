package letrain.track;

import java.io.Serializable;

import letrain.map.Dir;
import letrain.vehicle.impl.Linker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TrackDirector<T extends Track> implements Serializable {
    private static TrackDirector instance;

    public TrackDirector() {

    }

    public static <X extends Track> TrackDirector getInstance() {
        if (instance == null) {
            instance = new TrackDirector<X>();
        }
        return instance;
    }

    private static final Logger log = LoggerFactory.getLogger(TrackDirector.class);

    public boolean enterLinkerFromDir(T track, Dir d, Linker vehicle) {
        if (!canEnter(track, d, vehicle)) {
            log.warn("Cannot enter linker {} from {} into track {}: occupied or reserved.", vehicle, d, track.getPosition());
            return false;
        }

        vehicle.setTrack(track);
        vehicle.setPosition(track.getPosition());
        Dir exitDir = track.getRouter().getDir(d);
        if (exitDir != null) {
            vehicle.setDir(exitDir);
        } else {
            log.error("No exit direction found for track at {} entering from {}. Keeping current vehicle dir: {}.",
                    track.getPosition(), d, vehicle.getDir());
        }
        track.setLinker(vehicle);
        return true;
    }

    public Linker removeLinker(T track) {
        Linker ret = track.getLinker();
        if (ret != null) {
            ret.setTrack(null);
        }
        track.setLinker(null);
        return ret;
    }

    public boolean canEnter(T track, Dir d, Linker v) {
        // Points 5 & 10: One vehicle per track
        if (track.getLinker() != null) {
            return false;
        }

        // Reservation check to prevent race conditions in multi-train ticks
        if (track.getReservation() != null && track.getReservation() != v) {
            return false;
        }

        // If the track is empty and not reserved by another vehicle, it can be entered.
        // Further checks might be needed based on router logic or specific track types.
        return true;
    }

    public boolean canExit(T track, Dir d) {
        if (track.getLinker() != null) {
            Dir exitDir = track.getRouter().getDir(track.getLinker().getDir());
            T target = (T) track.getConnected(d);
            return target != null && target.canEnter(d, track.getLinker());
        }
        return true; // Qué contestar si estaba vacío??
    }
}