package letrain.track;

import letrain.map.Dir;
import letrain.vehicle.impl.Linker;

import java.io.Serializable;

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

    public void enterLinkerFromDir(T track, Dir d, Linker vehicle) {
        if(vehicle.getTrack() != null){
            vehicle.getTrack().removeLinker();
        }
        vehicle.setTrack(track);
        vehicle.setPosition2D(track.getPosition2D());
        if(vehicle.isReversed()) {
            vehicle.setDir(track.getRouter().getDirWhenEnteringFrom(d));
        }else{
            vehicle.setDir(track.getRouter().getDirWhenEnteringFrom(d.inverse()));
        }
        track.setLinker(vehicle);
    }
    public void enter (T track, Linker vehicle) {
        if(vehicle.getTrack() != null){
            vehicle.getTrack().removeLinker();
        }
        vehicle.setTrack(track);
        vehicle.setPosition2D(track.getPosition2D());
        track.setLinker(vehicle);
        Dir inDir = vehicle.getDir();
        if(!vehicle.isReversed()) {
            inDir = inDir.inverse();
            vehicle.setDir(track.getRouter().getDirWhenEnteringFrom(inDir));
        }else{
            vehicle.setDir(track.getRouter().getDirWhenEnteringFrom(inDir).inverse());
        }
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
             T target = (T) track.getConnectedTrack(d);
            return target != null && target.canEnter( track.getLinker());
        }
        return true; // Qué contestar si estaba vacío??
    }
}