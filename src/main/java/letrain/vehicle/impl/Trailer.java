package letrain.vehicle.impl;

import letrain.track.Track;

import java.util.Deque;
import java.util.List;

public interface Trailer<T extends Track> {
    Deque<Linker> getLinkers();

    Deque<Linker> getLinkersToJoin();

    void pushFront(Linker linker);

    Linker popFront();

    Linker getFront();

    void joinTrailerBack(Trailer t);

    void joinTrailerFront(Trailer t);

    void pushBack(Linker linker);

    Linker popBack();

    Linker getBack();

    void setDirectorLinker(Tractor linker);

    Tractor getDirectorLinker();

    List<Tractor> getTractors();

    boolean isEmpty();

    int size();

    Trailer divide(Linker p);
}
