package letrain.segments.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import letrain.map.Dir;
import letrain.map.RailMap;
import letrain.segments.Port;
import letrain.segments.RailwayGraph;
import letrain.segments.Segment;
import letrain.segments.TopologyService;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;

public class TopologyServiceImpl implements TopologyService {

    @Override
    public RailwayGraph discover(RailMap railMap) {
        RailwayGraphImpl graph = new RailwayGraphImpl();
        Map<RailTrack, RailNodeImpl> trackToNode = new HashMap<>();

        // 1. Identificar todos los nodos basándose en la estructura física
        railMap.forEach(obj -> {
            if (obj instanceof RailTrack) {
                RailTrack track = (RailTrack) obj;
                if (isNode(track)) {
                    trackToNode.put(track, new RailNodeImpl(track));
                }
            }
        });

        // 2. Rastrear conexiones para crear Segments usando puertos
        Set<Set<Port>> discoveredSegments = new HashSet<>();
        int segmentCounter = 0;

        List<Map.Entry<RailTrack, RailNodeImpl>> sortedNodes = new ArrayList<>(trackToNode.entrySet());
        sortedNodes.sort(Comparator.comparingInt((Map.Entry<RailTrack, RailNodeImpl> e) ->
                        e.getKey().getPosition().getY())
                .thenComparingInt(e -> e.getKey().getPosition().getX()));
        for (Map.Entry<RailTrack, RailNodeImpl> entry : sortedNodes) {
            RailTrack startTrack = entry.getKey();
            RailNodeImpl startNode = entry.getValue();

            for (Dir dir : startTrack.getConnections()) {
                Port startPort = startNode.getPortForDir(dir);
                if (startPort == null) continue;

                CrawlResult result = crawl(startTrack, dir, trackToNode);
                if (result != null) {
                    Port endPort = result.endNode.getPortForDir(result.incomingDir);
                    if (endPort == null) continue;

                    Set<Port> segmentKey = new HashSet<>(Arrays.asList(startPort, endPort));

                    if (!discoveredSegments.contains(segmentKey)) {
                        String segmentId = "S" + (segmentCounter++);
                        Segment segment = new SegmentImpl(segmentId, startPort, endPort);
                        graph.registerSegment(startPort, segment);
                        graph.registerSegment(endPort, segment);
                        discoveredSegments.add(segmentKey);

                        // Registrar elementos en el nodo de inicio
                        registerElements(graph, segment, startTrack);

                        // Registrar raíles intermedios, estaciones y sensores
                        for (RailTrack track : result.visitedTracks) {
                            graph.registerTrack(segment, track);
                            registerElements(graph, segment, track);
                        }

                        // Registrar elementos en el nodo de fin
                        registerElements(graph, segment, (RailTrack) result.endNode.getTrack());
                    }
                }
            }
        }

        return graph;
    }

    private void registerElements(RailwayGraphImpl graph, Segment segment, RailTrack track) {
        graph.registerTrack(segment, track);
        letrain.track.Sensor sensor = track.getSensor();
        if (sensor != null) {
            if (sensor instanceof letrain.track.Station) {
                graph.registerStation(segment, (letrain.track.Station) sensor);
            } else {
                graph.registerSensor(segment, sensor);
            }
        }
    }

    private boolean isNode(RailTrack track) {
        return (track instanceof ForkRailTrack) || (track.getConnections().size() != 2);
    }

    private CrawlResult crawl(RailTrack startTrack, Dir startDir, Map<RailTrack, RailNodeImpl> trackToNode) {
        List<RailTrack> visited = new ArrayList<>();
        java.util.Set<RailTrack> seen = new java.util.HashSet<>();
        RailTrack currentTrack = (RailTrack) startTrack.getConnected(startDir);
        Dir incomingDir = startDir.inverse();

        while (currentTrack != null && !trackToNode.containsKey(currentTrack)) {
            if (!seen.add(currentTrack)) {
                RailNodeImpl startNode = trackToNode.get(startTrack);
                if (startNode != null) {
                    return new CrawlResult(startNode, incomingDir, visited);
                }
                return null;
            }
            visited.add(currentTrack);
            Dir nextDir = currentTrack.getDir(incomingDir);
            if (nextDir == null) return null;

            RailTrack nextTrack = (RailTrack) currentTrack.getConnected(nextDir);
            incomingDir = nextDir.inverse();
            currentTrack = nextTrack;
        }

        if (currentTrack == null) return null;

        return new CrawlResult(trackToNode.get(currentTrack), incomingDir, visited);
    }

    private static class CrawlResult {
        final RailNodeImpl endNode;
        final Dir incomingDir;
        final List<RailTrack> visitedTracks;

        CrawlResult(RailNodeImpl endNode, Dir incomingDir, List<RailTrack> visitedTracks) {
            this.endNode = endNode;
            this.incomingDir = incomingDir;
            this.visitedTracks = visitedTracks;
        }
    }
}
