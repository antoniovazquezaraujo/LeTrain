package letrain.core.segments;

import java.util.List;
import letrain.track.Track;

/**
 * Representa un punto de decisión o frontera en la red (Forks, Topes de vía).
 */
public interface RailNode {
    /**
     * Devuelve los pasos de salida disponibles desde este nodo.
     */
    List<PathStep> getOutSteps();

    /**
     * Devuelve el objeto de vía físico que actúa como este nodo.
     */
    Track getTrack();
}
