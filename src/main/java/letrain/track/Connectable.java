package letrain.track;

import letrain.map.Dir;


public interface Connectable {
    Connectable getConnected(Dir dir);

    boolean connect(Dir dir, Connectable t);

    Connectable disconnect(Dir dir);
}
