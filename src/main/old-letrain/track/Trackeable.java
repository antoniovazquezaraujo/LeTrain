package letrain.track;

import letrain.map.Mapeable;
import letrain.map.Rotable;

public interface Trackeable extends Rotable, Mapeable {
    void setTrack(Track track);

    Track getTrack();

}
