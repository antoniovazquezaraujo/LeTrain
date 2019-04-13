package letrain.track;

import letrain.map.Mapeable;
import letrain.map.Rotable;

public interface Trackeable<T extends Track> extends Rotable, Mapeable {
    void setTrack(T track);

    T getTrack();
}
