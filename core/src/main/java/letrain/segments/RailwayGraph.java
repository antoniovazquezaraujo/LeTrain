package letrain.segments;

import java.util.List;

/** Gestiona la conectividad lógica de la red ferroviaria. */
public interface RailwayGraph {
    /** Dado un Port, devuelve el segmento al que pertenece. */
    Segment getSegment(Port port);

    /**
     * Dado un puerto actual, devuelve los posibles puertos siguientes al final del segmento. En un
     * fin de vía devuelve una <b>lista vacía</b>; {@code null} solo cuando el puerto no pertenece
     * al grafo (no se conoce su segmento).
     */
    List<Port> getNextPorts(Port current);

    /** Devuelve las estaciones presentes en un segmento. */
    List<letrain.track.Station> getStations(Segment segment);

    /** Devuelve los sensores presentes en un segmento. */
    List<letrain.track.Sensor> getSensors(Segment segment);

    /** Devuelve el segmento al que pertenece un raíl físico (si está mapeado). */
    Segment getSegment(letrain.track.rail.RailTrack track);

    /** Comprueba si un segmento contiene un raíl físico. */
    boolean containsTrack(Segment segment, letrain.track.rail.RailTrack track);

    /** Devuelve el número de vías físicas en un segmento. */
    default int getTrackCount(Segment segment) {
        return 0;
    }
}
