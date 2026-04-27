package letrain.core.segments;

import letrain.utils.Pair;

/**
 * Conexión física única entre dos RailNode.
 * Contiene los dos PathStep que lo forman (sus entradas desde cada extremo).
 */
public interface Segment {
    /**
     * Devuelve el identificador único del segmento.
     */
    String getId();

    /**
     * Devuelve el par de PathSteps que definen los extremos de este segmento.
     */
    Pair<PathStep, PathStep> getSteps();
}
