package letrain.itinerary;

import letrain.core.segments.PathStep;
import letrain.core.segments.RailwayGraph;
import letrain.core.segments.Segment;
import letrain.map.Dir;

import java.util.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A* pathfinder over the railway segment graph.
 * Explores neighbors from both ends of each segment (circuits).
 */
public class AStarPathfinder implements SegmentPathfinder {

    private static final Logger log = LoggerFactory.getLogger(AStarPathfinder.class);
    private final RailwayGraph graph;

    public AStarPathfinder(RailwayGraph graph) {
        this.graph = graph;
    }

    @Override
    public List<Segment> find(Segment from, Segment to, Optional<Dir> entryDir) {
        if (from == null || to == null) return List.of();
        if (from.equals(to)) return List.of(from);

        // Fast path: if to is a direct neighbor of from, no need for full A*
        for (Segment n : getNeighbors(from)) {
            if (n.equals(to)) {
                log.info("[A*] direct neighbor {}→{}", from.getId(), to.getId());
                return List.of(from, to);
            }
        }

        Map<Segment, Integer> gScore = new HashMap<>();
        Map<Segment, Segment> cameFrom = new HashMap<>();
        Set<Segment> closed = new HashSet<>();

        Map<Segment, Integer> openMap = new HashMap<>();
        gScore.put(from, 0);
        openMap.put(from, heuristic(from, to));
        int explored = 0;

        while (!openMap.isEmpty()) {
            Segment current = null;
            int bestF = Integer.MAX_VALUE;
            for (var entry : openMap.entrySet()) {
                if (entry.getValue() < bestF) {
                    bestF = entry.getValue();
                    current = entry.getKey();
                }
            }
            openMap.remove(current);
            explored++;

            if (current.equals(to)) {
                if (entryDir.isPresent()) {
                    PathStep entryStep = current.getSteps().getFirst();
                    if (entryStep.getDir() != entryDir.get().inverse()) {
                        continue;
                    }
                }
                log.info("[A*] FOUND {}→{} explored={}", from.getId(), to.getId(), explored);
                return reconstructPath(cameFrom, current);
            }

            if (!closed.add(current)) continue;

            for (Segment neighbor : getNeighbors(current)) {
                if (closed.contains(neighbor)) continue;
                int tentativeG = gScore.get(current) + segmentCost(neighbor);
                if (tentativeG < gScore.getOrDefault(neighbor, Integer.MAX_VALUE)) {
                    cameFrom.put(neighbor, current);
                    gScore.put(neighbor, tentativeG);
                    openMap.put(neighbor, tentativeG + heuristic(neighbor, to));
                }
            }
        }

        log.warn("[A*] NO ROUTE {}→{} explored={}", from.getId(), to.getId(), explored);
        return List.of();
    }

    private List<Segment> reconstructPath(Map<Segment, Segment> cameFrom, Segment current) {
        List<Segment> path = new ArrayList<>();
        path.add(current);
        while (cameFrom.containsKey(current)) {
            current = cameFrom.get(current);
            path.add(current);
        }
        Collections.reverse(path);
        return path;
    }

    private int heuristic(Segment a, Segment b) {
        try {
            var aPair = a.getSteps();
            var bPair = b.getSteps();
            if (aPair == null || bPair == null) return 0;
            var aNode = aPair.getFirst();
            var bNode = bPair.getFirst();
            if (aNode == null || bNode == null) return 0;
            var aTrack = aNode.getRailNode();
            var bTrack = bNode.getRailNode();
            if (aTrack == null || bTrack == null) return 0;
            var aPos = aTrack.getTrack().getPosition();
            var bPos = bTrack.getTrack().getPosition();
            if (aPos == null || bPos == null) return 0;
            return Math.abs(aPos.getX() - bPos.getX()) + Math.abs(aPos.getY() - bPos.getY());
        } catch (NullPointerException e) {
            return 0;
        }
    }

    private int segmentCost(Segment s) {
        return 1;
    }

    private List<Segment> getNeighbors(Segment s) {
        if (graph == null) return List.of();
        List<Segment> neighbors = new ArrayList<>();
        var steps = s.getSteps();
        if (steps == null) return neighbors;
        for (PathStep exitStep : new PathStep[]{steps.getFirst(), steps.getSecond()}) {
            if (exitStep == null) continue;
            for (PathStep next : graph.getNextSteps(exitStep)) {
                Segment neighbor = graph.getSegment(next);
                if (neighbor != null && !neighbor.equals(s)) {
                    neighbors.add(neighbor);
                }
            }
        }
        return neighbors;
    }
}
