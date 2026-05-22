package letrain.segments.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import letrain.map.Dir;
import letrain.map.RailMap;
import letrain.segments.PathStep;
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

        // 2. Rastrear conexiones para crear PathSteps y Segments
        Set<Set<PathStep>> discoveredSegments = new HashSet<>();
        int segmentCounter = 0;

        for (Map.Entry<RailTrack, RailNodeImpl> entry : trackToNode.entrySet()) {
            RailTrack startTrack = entry.getKey();
            RailNodeImpl startNode = entry.getValue();

            for (Dir dir : startTrack.getConnections()) {
                PathStep startStep = findOrCreateStep(startNode, dir);
                
                CrawlResult result = crawl(startTrack, dir, trackToNode);
                if (result != null) {
                    PathStep endStep = findOrCreateStep(result.endNode, result.incomingDir);
                    
                    Set<PathStep> segmentKey = new HashSet<>(Arrays.asList(startStep, endStep));
                    
                    if (!discoveredSegments.contains(segmentKey)) {
                        String segmentId = "S" + (segmentCounter++);
                        Segment segment = new SegmentImpl(segmentId, startStep, endStep);
                        graph.registerSegment(startStep, segment);
                        graph.registerSegment(endStep, segment);
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
        graph.registerTrack(segment, track); // register the track itself (needed for getSegment(RailTrack))
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

    private PathStep findOrCreateStep(RailNodeImpl node, Dir dir) {
        return node.getOutSteps().stream()
                .filter(s -> s.getDir() == dir)
                .findFirst()
                .orElseGet(() -> {
                    PathStep newStep = new PathStepImpl(node, dir);
                    node.addOutStep(newStep);
                    return newStep;
                });
    }

    private CrawlResult crawl(RailTrack startTrack, Dir startDir, Map<RailTrack, RailNodeImpl> trackToNode) {
        List<RailTrack> visited = new ArrayList<>();
        java.util.Set<RailTrack> seen = new java.util.HashSet<>();
        RailTrack currentTrack = (RailTrack) startTrack.getConnected(startDir);
        Dir incomingDir = startDir.inverse();

        while (currentTrack != null && !trackToNode.containsKey(currentTrack)) {
            if (!seen.add(currentTrack)) {
                // Cycle detected — circuit with no intermediate fork nodes.
                // The entire loop forms one segment from start node back to itself.
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
