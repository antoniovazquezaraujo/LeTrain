package letrain.itinerary;

import letrain.core.segments.PathStep;
import letrain.core.segments.RailwayGraph;
import letrain.core.segments.Segment;
import letrain.map.Dir;

import java.util.*;

/**
 * A* pathfinder over the railway segment graph.
 * Cost = number of RailTracks in each segment (real length).
 * Heuristic = Manhattan distance between segment entry nodes.
 */
public class AStarPathfinder implements SegmentPathfinder {

    private final RailwayGraph graph;

    public AStarPathfinder(RailwayGraph graph) {
        this.graph = graph;
    }

    @Override
    public List<Segment> find(Segment from, Segment to, Optional<Dir> entryDir) {
        if (from == null || to == null) return List.of();
        if (from.equals(to)) return List.of(from);

        Map<Segment, Integer> gScore = new HashMap<>();
        Map<Segment, Segment> cameFrom = new HashMap<>();
        PriorityQueue<Segment> openSet = new PriorityQueue<>(
            Comparator.comparingInt(s -> gScore.getOrDefault(s, Integer.MAX_VALUE) + heuristic(s, to)));

        gScore.put(from, 0);
        openSet.add(from);
        int explored = 0;

        while (!openSet.isEmpty()) {
            Segment current = openSet.poll();
            explored++;

            if (current.equals(to)) {
                if (entryDir.isPresent()) {
                    PathStep entryStep = current.getSteps().getFirst();
                    if (entryStep.getDir() != entryDir.get().inverse()) {
                        continue;
                    }
                }
                System.out.println("[A*] FOUND route " + from.getId() + "→" + to.getId()
                    + " explored=" + explored + " path=" + reconstructPath(cameFrom, current).size());
                return reconstructPath(cameFrom, current);
            }

            for (Segment neighbor : getNeighbors(current)) {
                int tentativeG = gScore.get(current) + segmentCost(neighbor);
                if (tentativeG < gScore.getOrDefault(neighbor, Integer.MAX_VALUE)) {
                    cameFrom.put(neighbor, current);
                    gScore.put(neighbor, tentativeG);
                    if (!openSet.contains(neighbor)) {
                        openSet.add(neighbor);
                    }
                }
            }
        }

        System.out.println("[A*] NO ROUTE " + from.getId() + "→" + to.getId()
            + " explored=" + explored + " segments");
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
        // Manhattan distance between the segment entry nodes
        var aPos = a.getSteps().getFirst().getRailNode().getTrack().getPosition();
        var bPos = b.getSteps().getFirst().getRailNode().getTrack().getPosition();
        return Math.abs(aPos.getX() - bPos.getX()) + Math.abs(aPos.getY() - bPos.getY());
    }

    private int segmentCost(Segment s) {
        // Cost = 1 for now (we don't have track count easily available)
        // TODO: use actual track count when available
        return 1;
    }

    private List<Segment> getNeighbors(Segment s) {
        if (graph == null) return List.of();
        List<Segment> neighbors = new ArrayList<>();
        PathStep exitStep = s.getSteps().getSecond();
        if (exitStep == null) return neighbors;
        for (PathStep next : graph.getNextSteps(exitStep)) {
            Segment neighbor = graph.getSegment(next);
            if (neighbor != null && !neighbor.equals(s)) {
                neighbors.add(neighbor);
            }
        }
        return neighbors;
    }
}
