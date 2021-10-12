package letrain.vehicle.impl;

import letrain.track.Track;
import letrain.track.rail.RailTrack;
import letrain.vehicle.Tractor;
import letrain.vehicle.impl.rail.Train;

import java.util.Deque;
import java.util.List;
import java.util.stream.Collectors;

public interface Trailer<T extends Track> {
    /***********************************************************
     * Trailer implementation
     **********************************************************/

    public Deque<Linker> getLinkers();

    default void pushFront(Linker linker) {
        this.getLinkers().addFirst(linker);
    }

    default Linker popFront() {
        Linker linker = getLinkers().removeFirst();
        return linker;
    }

    default Linker getFront() {
        return getLinkers().getFirst();
    }
    default Linker getBack() {
        return getLinkers().getLast();
    }
    void joinTrailerFront(Trailer t);
    void joinTrailerBack(Trailer t);



    default void pushBack(Linker linker) {
        this.getLinkers().addLast(linker);
    }

    default Linker popBack() {
        Linker linker = getLinkers().removeLast();
        return linker;
    }

   void setMainTractor(Tractor linker);

    Tractor getMainTractor();

    default List<Tractor> getTractors(){
        return getLinkers().stream()
                .filter(t -> Tractor.class.isAssignableFrom(t.getClass()))
                .map(t -> (Tractor) t)
                .collect(Collectors.toList());
    }

    default boolean isEmpty() {
        return getLinkers().isEmpty();
    }

    default int size() {
        return getLinkers().size();
    }

    default Trailer divide(Linker p) {
        Trailer<RailTrack> ret = new Train();
        Linker first = getFront();
        while (first != p) {
            ret.pushFront(getLinkers().removeFirst());
            first = getFront();
        }
        return ret;
    }
}
