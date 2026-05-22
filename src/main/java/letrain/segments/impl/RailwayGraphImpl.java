package letrain.segments.impl;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.Queue;
import java.util.LinkedList;
import java.util.stream.Collectors;

import letrain.segments.PathStep;
import letrain.segments.RailNode;
import letrain.segments.RailwayGraph;
import letrain.segments.Segment;

public class RailwayGraphImpl implements RailwayGraph {
    private final Map<PathStep, Segment> stepToSegment = new HashMap<>();
    private final Map<RailNode, List<Segment>> nodeToSegments = new HashMap<>();
    private final Map<Segment, List<letrain.track.Station>> segmentToStations = new HashMap<>();
    private final Map<Segment, List<letrain.track.Sensor>> segmentToSensors = new HashMap<>();
    private final Map<letrain.track.rail.RailTrack, Segment> trackToSegment = new HashMap<>();
    private final Map<Segment, Set<letrain.track.rail.RailTrack>> segmentToTracks = new HashMap<>();

    @Override
    public Segment getSegment(PathStep step) {
        return stepToSegment.get(step);
    }

    @Override
    public List<letrain.track.Station> getStations(Segment segment) {
        return segmentToStations.getOrDefault(segment, new ArrayList<>());
    }

    @Override
    public List<letrain.track.Sensor> getSensors(Segment segment) {
        return segmentToSensors.getOrDefault(segment, new ArrayList<>());
    }

    @Override
    public Segment getSegment(letrain.track.rail.RailTrack track) {
        return trackToSegment.get(track);
    }

    @Override
    public int getTrackCount(Segment segment) {
        Set<letrain.track.rail.RailTrack> tracks = segmentToTracks.get(segment);
        return tracks != null ? tracks.size() : 0;
    }

    public void registerStation(Segment segment, letrain.track.Station station) {
        segmentToStations.computeIfAbsent(segment, k -> new ArrayList<>()).add(station);
    }

    public void registerSensor(Segment segment, letrain.track.Sensor sensor) {
        segmentToSensors.computeIfAbsent(segment, k -> new ArrayList<>()).add(sensor);
    }

    public void registerTrack(Segment segment, letrain.track.rail.RailTrack track) {
        trackToSegment.putIfAbsent(track, segment);
        segmentToTracks.computeIfAbsent(segment, k -> new HashSet<>()).add(track);
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
            
            List<letrain.track.Station> stations = getStations(s);
            if (!stations.isEmpty()) {
                sb.append("    Stations: ").append(stations.stream().map(st -> "ID=" + st.getId()).collect(Collectors.joining(", "))).append("\n");
            }
            
            List<letrain.track.Sensor> sensors = getSensors(s);
            if (!sensors.isEmpty()) {
                sb.append("    Sensors: ").append(sensors.stream().map(se -> "ID=" + se.getId()).collect(Collectors.joining(", "))).append("\n");
            }
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
