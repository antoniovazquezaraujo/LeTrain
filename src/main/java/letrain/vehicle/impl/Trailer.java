package letrain.vehicle.impl;

import letrain.track.Track;

import java.util.Deque;
import java.util.List;

public interface Trailer<T extends Track> {
    Deque<Linker<T>> getLinkers();

    void pushFront(Linker<T> linker);

    Linker<T> popFront();

    Linker<T> getFront();

    void pushBack(Linker<T> linker);

    Linker<T> popBack();

    Linker<T> getBack();

    Tractor getMainTractor();

    List<Tractor> getTractors();

    boolean isEmpty();

    int size();

    void setMainTractor(Tractor tractor);

    float getTractorsForce();

    float getFrictionForce();

    float getTotalForce();

    float getTotalMass();

    void applyForces();

    void move();

    float getAcceleration();

    float getSpeed();

    float getDistanceTraveled();

    void resetDistanceTraveled();

    Trailer divide(Linker<T> p);

    void pushBack(Trailer t);

    void pushFront(Trailer t);
}
