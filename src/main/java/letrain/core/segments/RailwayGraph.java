package letrain.core.segments;

import java.util.List;

/**
 * Gestiona la conectividad lógica de la red ferroviaria.
 */
public interface RailwayGraph {
    /**
     * Dado un PathStep, devuelve el segmento al que pertenece.
     */
    Segment getSegment(PathStep step);

    /**
     * Dado un paso actual, devuelve los posibles pasos siguientes 
     * al final del segmento. Devuelve null si es fin de vía.
     */
    List<PathStep> getNextSteps(PathStep current);

    /**
     * Encuentra la secuencia de segmentos que conectan dos segmentos dados.
     */
    List<Segment> findPath(Segment start, Segment end);
}
