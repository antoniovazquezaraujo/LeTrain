package letrain.vehicle.impl;

import letrain.track.Track;

import java.util.Deque;
import java.util.List;

public interface Trailer<T extends Track> {
    Deque<Linker<T>> getLinkers();
    Tractor<T> getMainTractor();
    public void setMainTractor(Tractor<T> tractor);

}
