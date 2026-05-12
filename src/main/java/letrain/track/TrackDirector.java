package letrain.track;


import letrain.map.Dir;
import letrain.vehicle.impl.Linker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TrackDirector<T extends Track> {
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

    public boolean enterLinkerFromDir(T track, Dir dir, Linker vehicle) {
        if (!track.canEnter(dir, vehicle)) {
            log.debug("Cannot enter linker {} from {} into track {}: occupied or reserved.", vehicle, dir,
                    track.getPosition());
            return false;
        }

        vehicle.setTrack(track);
        vehicle.setPosition(track.getPosition());
        vehicle.setEntryDir(dir);
        Dir exitDir = track.getRouter().getDir(dir);
        if (exitDir != null && track.getConnected(exitDir) != null) {
            vehicle.setDir(exitDir);
        } else {
            // Router gave invalid or unconnected direction — find actual
            // physical exit (skip the entry port we came from).
            Dir entryPort = dir.inverse();
            for (Dir conn : track.getConnections()) {
                if (conn != entryPort && track.getConnected(conn) != null) {
                    vehicle.setDir(conn);
                    break;
                }
            }
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

    public boolean canEnter(T track, Dir dir, Linker v) {
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

    public boolean canExit(T track, Dir dir) {
        if (track.getLinker() != null) {
            Dir exitDir = track.getRouter().getDir(track.getLinker().getDir());
            T target = (T) track.getConnected(dir);
            return target != null && target.canEnter(dir, track.getLinker());
        }
        return true; // Qué contestar si estaba vacío??
    }
}