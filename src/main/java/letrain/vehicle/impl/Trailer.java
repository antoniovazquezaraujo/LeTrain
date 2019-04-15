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

    void addTractor(Tractor tractor);

    void removeTractor(Tractor tractor);

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
}
