package letrain.track;

import letrain.map.Dir;


public interface Connectable<T extends Track> {
    Connectable<T> getConnected(Dir dir);

    boolean connect(Dir dir, Connectable<T> t);

    Connectable<T> disconnect(Dir dir);
}
