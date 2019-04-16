package letrain.track;

import letrain.map.Dir;


public interface Connectable {
    Track getConnected(Dir dir);

    boolean connect(Dir dir, Track t);

    Track disconnect(Dir dir);
}
