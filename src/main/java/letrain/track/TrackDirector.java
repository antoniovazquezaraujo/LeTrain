package letrain.track;

import letrain.map.Dir;
import letrain.vehicle.impl.Linker;

public class TrackDirector<T extends Track> {
    public static TrackDirector instance;

    private TrackDirector() {

    }

    public static <X extends Track> TrackDirector getInstance() {
        if (instance == null) {
            instance = new TrackDirector<X>();
        }
        return instance;
    }

    public void enterLinker(Track<T> track, Dir d, Linker<Track<T>> vehicle) {
        vehicle.setTrack(track);
        vehicle.setPosition(track.getPosition());
        vehicle.setDir(track.getRouter().getDir(d));
        track.setLinker(vehicle);

    }

    public Linker<Track<T>> exitLinker(Track<T> track, Dir d) {
        Linker<Track<T>> ret = track.getLinker();
        track.setLinker(null);
        return ret;
    }

    public boolean canEnter(Track<T> track, Dir d, Trackeable<T> v) {
        return (v == null || track.getLinker() == null);
    }

    public boolean canExit(Track<T> track, Dir d) {
        if (track.getLinker() != null) {
//            Dir inverseDir = track.getLinker().getDir().inverse();
            Dir exitDir = track.getRouter().getDir(track.getLinker().getDir());
            T target = (T) track.getConnected(d);
            return target != null && target.canEnter(d, track.getLinker());
        }
        return true; // Qué contestar si estaba vacío??
    }
}