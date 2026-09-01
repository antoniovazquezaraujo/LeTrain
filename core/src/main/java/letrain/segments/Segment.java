package letrain.segments;

import letrain.utils.Pair;

/** Conexión física única entre dos RailNode. */
public interface Segment {
    /** Devuelve el identificador único del segmento. */
    String getId();

    /** Devuelve el par de puertos lógicos en los extremos de este segmento. */
    Pair<Port, Port> getPorts();
}
