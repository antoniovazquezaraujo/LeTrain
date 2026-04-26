package letrain.core.segments.impl;

import letrain.core.segments.PathStep;
import letrain.core.segments.RailwayGraph;
import letrain.core.segments.Segment;
import letrain.core.segments.RailNode;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.stream.Collectors;

public class RailwayGraphImpl implements RailwayGraph {
    private final Map<PathStep, Segment> stepToSegment = new HashMap<>();

    @Override
    public Segment getSegment(PathStep step) {
        return stepToSegment.get(step);
    }

    @Override
    public List<PathStep> getNextSteps(PathStep current) {
        Segment s = getSegment(current);
        if (s == null) return null;
        
        // Identificar el PathStep opuesto en el segmento usando el Pair
        PathStep targetStep = current.equals(s.getSteps().getFirst()) 
                ? s.getSteps().getSecond() 
                : s.getSteps().getFirst();
        
        // El nodo de llegada es el que define los siguientes pasos de salida
        RailNode destinationNode = targetStep.getRailNode();
        
        // Devolvemos todos los pasos de salida de ese nodo, excepto el que 
        // nos haría volver por el mismo segmento
        return destinationNode.getOutSteps().stream()
                .filter(step -> getSegment(step) != s)
                .collect(Collectors.toList());
    }

    @Override
    public List<Segment> findPath(Segment start, Segment end) {
        // Por implementar: BFS topológico
        return new ArrayList<>();
    }

    public void registerSegment(PathStep step, Segment segment) {
        stepToSegment.put(step, segment);
    }
}
