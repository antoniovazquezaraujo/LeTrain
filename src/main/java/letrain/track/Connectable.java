package letrain.track;

import java.util.List;

import letrain.map.Dir;

interface Connectable {
    Track getConnected(Dir dir);

    boolean connect(Dir dir, Track t);

    Track disconnect(Dir dir);

    List<Dir> getConnections();
}
