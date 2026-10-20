package letrain.segments.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
