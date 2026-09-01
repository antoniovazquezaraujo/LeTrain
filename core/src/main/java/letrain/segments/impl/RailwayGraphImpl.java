package letrain.segments.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;
import letrain.segments.Port;
import letrain.segments.RailNode;
import letrain.segments.RailwayGraph;
import letrain.segments.Segment;
import letrain.segments.TransitionType;

public class RailwayGraphImpl implements RailwayGraph {
    private final Map<Port, Segment> portToSegment = new HashMap<>();
    private final Map<RailNode, List<Segment>> nodeToSegments = new HashMap<>();
    private final Map<Segment, List<letrain.track.Station>> segmentToStations = new HashMap<>();
    private final Map<Segment, List<letrain.track.Sensor>> segmentToSensors = new HashMap<>();
    private final Map<letrain.track.rail.RailTrack, Segment> trackToSegment = new HashMap<>();
    private final Map<Segment, Set<letrain.track.rail.RailTrack>> segmentToTracks = new HashMap<>();

    @Override
    public Segment getSegment(Port port) {
        return portToSegment.get(port);
    }

    @Override
    public List<letrain.track.Station> getStations(Segment segment) {
        return segmentToStations.getOrDefault(segment, Collections.emptyList());
    }

    @Override
    public List<letrain.track.Sensor> getSensors(Segment segment) {
        return segmentToSensors.getOrDefault(segment, Collections.emptyList());
    }

    @Override
    public Segment getSegment(letrain.track.rail.RailTrack track) {
        return trackToSegment.get(track);
    }

    @Override
    public boolean containsTrack(Segment segment, letrain.track.rail.RailTrack track) {
        Set<letrain.track.rail.RailTrack> tracks = segmentToTracks.get(segment);
        return tracks != null && tracks.contains(track);
    }

    @Override
    public int getTrackCount(Segment segment) {
        Set<letrain.track.rail.RailTrack> tracks = segmentToTracks.get(segment);
        return tracks != null ? tracks.size() : 0;
    }

    public Set<letrain.track.rail.RailTrack> getTracksForSegment(Segment segment) {
        return segmentToTracks.getOrDefault(segment, Collections.emptySet());
    }

    public List<Segment> getSegmentsForNode(RailNode node) {
        return nodeToSegments.getOrDefault(node, Collections.emptyList());
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
    public List<Port> getNextPorts(Port current) {
        Segment s = getSegment(current);
        if (s == null) {
            return null;
        }

        Port targetPort = current.equals(s.getPorts().getFirst()) ? s.getPorts().getSecond()
                : s.getPorts().getFirst();

        RailNode destinationNode = targetPort.getNode();

        return destinationNode.getPorts().stream()
                .filter(port -> getSegment(port) != null && getSegment(port) != s)
                .filter(port -> destinationNode.getTransitionType(targetPort,
                        port) != TransitionType.BLOCKED)
                .collect(Collectors.toList());
    }

    @Override
    public List<Segment> findPath(Segment start, Segment end) {
        if (start == null || end == null) {
            return new ArrayList<>();
        }
        if (start.equals(end)) {
            return List.of(start);
        }

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
        RailNode node1 = (s.getPorts() != null && s.getPorts().getFirst() != null)
                ? s.getPorts().getFirst().getNode()
                : null;

        RailNode node2 = (s.getPorts() != null && s.getPorts().getSecond() != null)
                ? s.getPorts().getSecond().getNode()
                : null;

        if (node1 != null && nodeToSegments.containsKey(node1))
            neighbors.addAll(nodeToSegments.get(node1));
        if (node2 != null && nodeToSegments.containsKey(node2))
            neighbors.addAll(nodeToSegments.get(node2));

        return neighbors.stream().filter(neighbor -> !neighbor.equals(s)).distinct()
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

    public void registerSegment(Port port, Segment segment) {
        portToSegment.put(port, segment);
        RailNode node = port.getNode();
        nodeToSegments.computeIfAbsent(node, k -> new ArrayList<>()).add(segment);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("--- RAILWAY GRAPH TOPOLOGY ---\n");

        List<Segment> segments =
                portToSegment.values().stream().distinct().collect(Collectors.toList());
        sb.append("SEGMENTS (").append(segments.size()).append("):\n");
        for (Segment s : segments) {
            sb.append("  ").append(s.getId()).append(": ").append(s.getPorts().getFirst().getNode())
                    .append(" -> ").append(s.getPorts().getSecond().getNode()).append("\n");

            List<letrain.track.Station> stations = getStations(s);
            if (!stations.isEmpty()) {
                sb.append("    Stations: ").append(stations.stream().map(st -> "ID=" + st.getId())
                        .collect(Collectors.joining(", "))).append("\n");
            }

            List<letrain.track.Sensor> sensors = getSensors(s);
            if (!sensors.isEmpty()) {
                sb.append("    Sensors: ").append(sensors.stream().map(se -> "ID=" + se.getId())
                        .collect(Collectors.joining(", "))).append("\n");
            }
        }

        sb.append("NODES (").append(nodeToSegments.size()).append("):\n");
        for (Map.Entry<RailNode, List<Segment>> entry : nodeToSegments.entrySet()) {
            sb.append("  ").append(entry.getKey()).append(" connects to segments: ");
            String segIds =
                    entry.getValue().stream().map(Segment::getId).collect(Collectors.joining(", "));
            sb.append(segIds).append("\n");

            sb.append("    Ports: ");
            String portsStr = entry.getKey().getPorts().stream().map(p -> p.getType().toString())
                    .collect(Collectors.joining(", "));
            sb.append(portsStr).append("\n");
        }
        sb.append("------------------------------");
        return sb.toString();
    }
}
