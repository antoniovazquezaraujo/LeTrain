package letrain.track;

import letrain.map.Dir;
import letrain.vehicle.impl.Linker;

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

    public void enterLinkerFromDir(T track, Dir d, Linker vehicle) {
        vehicle.setTrack(track);
        vehicle.setPosition(track.getPosition());
        vehicle.setDir(track.getRouter().getDir(d));
        track.setLinker(vehicle);
    }

    public Linker removeLinker(T track) {
        Linker ret = track.getLinker();
        track.setLinker(null);
        return ret;
    }

    public boolean canEnter(T track, Dir d, Trackeable v) {
        return (v == null || track.getLinker() == null);
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