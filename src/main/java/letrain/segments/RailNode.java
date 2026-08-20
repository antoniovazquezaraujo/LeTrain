package letrain.segments;

import java.util.List;

import letrain.track.Track;

/**
 * Representa un punto de decisión o frontera en la red (Forks, Topes de vía).
 */
public interface RailNode {
    /**
     * Devuelve el objeto de vía físico que actúa como este nodo.
     */
    Track getTrack();

    List<Port> getPorts();
    TransitionType getTransitionType(Port entry, Port exit);
    boolean setRoute(Port entry, Port exit);
    boolean isRouteActive(Port entry, Port exit);
    Port getActiveExit(Port entry);
}
