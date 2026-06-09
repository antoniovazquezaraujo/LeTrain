package letrain.segments;

import java.util.List;

/**
 * Gestiona la conectividad lógica de la red ferroviaria.
 */
public interface RailwayGraph {
    /**
     * Dado un PathStep, devuelve el segmento al que pertenece.
     */
    @Deprecated
    Segment getSegment(PathStep step);

    /**
     * Dado un paso actual, devuelve los posibles pasos siguientes 
     * al final del segmento. Devuelve null si es fin de vía.
     */
    @Deprecated
    List<PathStep> getNextSteps(PathStep current);

    /**
     * Dado un Port, devuelve el segmento al que pertenece.
     */
    Segment getSegment(Port port);

    /**
     * Dado un puerto actual, devuelve los posibles puertos siguientes 
     * al final del segmento. Devuelve null si es fin de vía.
     */
    List<Port> getNextPorts(Port current);

    /**
     * Encuentra la secuencia de segmentos que conectan dos segmentos dados.
     */
    List<Segment> findPath(Segment start, Segment end);

    /**
     * Devuelve las estaciones presentes en un segmento.
     */
    List<letrain.track.Station> getStations(Segment segment);

    /**
     * Devuelve los sensores presentes en un segmento.
     */
    List<letrain.track.Sensor> getSensors(Segment segment);

    /**
     * Devuelve el segmento al que pertenece un raíl físico (si está mapeado).
     */
    Segment getSegment(letrain.track.rail.RailTrack track);

    /**
     * Devuelve el número de vías físicas en un segmento.
     */
    default int getTrackCount(Segment segment) {
        return 0;
    }
}
