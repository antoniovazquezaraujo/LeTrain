package letrain.vehicle.impl;

import letrain.track.Track;

import java.util.Deque;
import java.util.List;

public interface Trailer<T extends Track> {
    Deque<Linker> getLinkers();

    void pushFront(Linker linker);

    Linker popFront();

    Linker getFront();

    void joinTrailerBack(Trailer t);

    void joinTrailerFront(Trailer t);

    void pushBack(Linker linker);

    Linker popBack();

    Linker getBack();

    void setDirectorLinker(Linker linker);

    Linker getDirectorLinker();

    List<Tractor> getTractors();

    boolean isEmpty();

    int size();

//    float getTractorsForce();

    float getFrictionForce();

//    float getTotalMass();

//    void applyForces();

//    void move();

//    float getAcceleration();

//    float getSpeed();

//    float getDistanceTraveled();

//    void resetDistanceTraveled();

    Trailer divide(Linker p);
}
