package letrain.core.segments.impl;

import letrain.core.segments.PathStep;
import letrain.core.segments.RailwayGraph;
import letrain.core.segments.Segment;
import letrain.core.segments.RailNode;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.Queue;
import java.util.LinkedList;
import java.util.stream.Collectors;

public class RailwayGraphImpl implements RailwayGraph {
    private final Map<PathStep, Segment> stepToSegment = new HashMap<>();
    private final Map<RailNode, List<Segment>> nodeToSegments = new HashMap<>();

    @Override
    public Segment getSegment(PathStep step) {
        return stepToSegment.get(step);
    }

    @Override
    public List<PathStep> getNextSteps(PathStep current) {
        Segment s = getSegment(current);
        if (s == null) return null;
        
        PathStep targetStep = current.equals(s.getSteps().getFirst()) 
                ? s.getSteps().getSecond() 
                : s.getSteps().getFirst();
        
        RailNode destinationNode = targetStep.getRailNode();
        
        return destinationNode.getOutSteps().stream()
                .filter(step -> getSegment(step) != s)
                .collect(Collectors.toList());
    }

    @Override
    public List<Segment> findPath(Segment start, Segment end) {
        if (start == null || end == null) return new ArrayList<>();
        if (start.equals(end)) return List.of(start);

        Queue<Segment> queue = new java.util.LinkedList<>();
        Map<Segment, Segment> parentMap = new HashMap<>();
        java.util.Set<Segment> visited = new java.util.HashSet<>();

        queue.add(start);
        visited.add(start);

        while (!queue.isEmpty()) {
            Segment current = queue.poll();

            if (current.equals(end)) {
                return reconstructPath(parentMap, end);
            }

            for (Segment neighbor : getConnectedSegments(current)) {
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    parentMap.put(neighbor, current);
                    queue.add(neighbor);
                }
            }
        }

        return new ArrayList<>();
    }

    private List<Segment> getConnectedSegments(Segment s) {
        List<Segment> neighbors = new ArrayList<>();
        // Un segmento está conectado a otros a través de sus dos nodos extremos
        RailNode node1 = s.getSteps().getFirst().getRailNode();
        RailNode node2 = s.getSteps().getSecond().getRailNode();

        if (nodeToSegments.containsKey(node1)) neighbors.addAll(nodeToSegments.get(node1));
        if (nodeToSegments.containsKey(node2)) neighbors.addAll(nodeToSegments.get(node2));

        return neighbors.stream()
                .filter(neighbor -> !neighbor.equals(s))
                .distinct()
                .collect(Collectors.toList());
    }

    private List<Segment> reconstructPath(Map<Segment, Segment> parentMap, Segment end) {
        List<Segment> path = new java.util.LinkedList<>();
        Segment curr = end;
        while (curr != null) {
            path.add(0, curr);
            curr = parentMap.get(curr);
        }
        return path;
    }

    public void registerSegment(PathStep step, Segment segment) {
        stepToSegment.put(step, segment);
        RailNode node = step.getRailNode();
        nodeToSegments.computeIfAbsent(node, k -> new ArrayList<>()).add(segment);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("--- RAILWAY GRAPH TOPOLOGY ---\n");
        
        List<Segment> segments = stepToSegment.values().stream().distinct().collect(Collectors.toList());
        sb.append("SEGMENTS (").append(segments.size()).append("):\n");
        for (Segment s : segments) {
            sb.append("  ").append(s.getId()).append(": ")
              .append(s.getSteps().getFirst().getRailNode()).append(" -> ")
              .append(s.getSteps().getSecond().getRailNode()).append("\n");
        }
        
        sb.append("NODES (").append(nodeToSegments.size()).append("):\n");
        for (Map.Entry<RailNode, List<Segment>> entry : nodeToSegments.entrySet()) {
            sb.append("  ").append(entry.getKey()).append(" connects to segments: ");
            String segIds = entry.getValue().stream()
                    .map(Segment::getId)
                    .collect(Collectors.joining(", "));
            sb.append(segIds).append("\n");
            
            sb.append("    OutSteps: ");
            String outSteps = entry.getKey().getOutSteps().stream()
                    .map(ps -> ps.getDir().toString())
                    .collect(Collectors.joining(", "));
            sb.append(outSteps).append("\n");
        }
        sb.append("------------------------------");
        return sb.toString();
    }
}
