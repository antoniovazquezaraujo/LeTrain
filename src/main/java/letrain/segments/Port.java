package letrain.segments;

/**
 * Representa un extremo o puerto de conexión lógica en un nodo.
 */
public interface Port {
    RailNode getNode();
    PortType getType();
}
