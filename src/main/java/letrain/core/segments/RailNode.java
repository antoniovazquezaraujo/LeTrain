package letrain.core.segments;

import java.util.List;

/**
 * Representa un punto de decisión o frontera en la red (Forks, Topes de vía).
 */
public interface RailNode {
    /**
     * Devuelve los pasos de salida disponibles desde este nodo.
     */
    List<PathStep> getOutSteps();
}
