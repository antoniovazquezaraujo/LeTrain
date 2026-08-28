package letrain.segments;

import letrain.map.RailMap;

/**
 * Servicio encargado de analizar el RailMap físico para construir
 * la topología lógica del RailwayGraph.
 */
public interface TopologyService {
    /**
     * Escanea el mapa proporcionado y genera un grafo de segmentos y nodos.
     *
     * @param railMap El mapa físico de raíles.
     * @return Un RailwayGraph poblado con la topología actual.
     */
    RailwayGraph discover(RailMap railMap);
}
