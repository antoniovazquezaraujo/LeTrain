package letrain.core.segments.impl;

import letrain.core.segments.*;
import letrain.map.Dir;
import letrain.map.RailMap;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;

import java.util.*;

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
                    }
                }
            }
        }

        return graph;
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
        RailTrack currentTrack = (RailTrack) startTrack.getConnected(startDir);
        Dir incomingDir = startDir.inverse();

        while (currentTrack != null && !trackToNode.containsKey(currentTrack)) {
            Dir nextDir = currentTrack.getDir(incomingDir);
            if (nextDir == null) return null;
            
            RailTrack nextTrack = (RailTrack) currentTrack.getConnected(nextDir);
            incomingDir = nextDir.inverse();
            currentTrack = nextTrack;
        }

        if (currentTrack == null) return null;

        return new CrawlResult(trackToNode.get(currentTrack), incomingDir);
    }

    private static class CrawlResult {
        final RailNodeImpl endNode;
        final Dir incomingDir;

        CrawlResult(RailNodeImpl endNode, Dir incomingDir) {
            this.endNode = endNode;
            this.incomingDir = incomingDir;
        }
    }
}
