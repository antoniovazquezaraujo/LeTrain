package letrain.itinerary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import letrain.map.Dir;
import letrain.segments.Port;
import letrain.segments.RailNode;
import letrain.segments.RailwayGraph;
import letrain.segments.Segment;
import letrain.segments.impl.RailNodeImpl;
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
        if (entryDir.isEmpty()) {
            for (Segment n : getNeighbors(from)) {
                if (n.equals(to)) {
                    log.info("[A*] direct neighbor {}→{}", from.getId(), to.getId());
                    return List.of(from, to);
                }
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
                log.info("[A*] FOUND {}→{} explored={}", from.getId(), to.getId(), explored);
                return reconstructPath(cameFrom, current);
            }

            if (!closed.add(current)) continue;

            for (Segment neighbor : getNeighbors(current)) {
                if (closed.contains(neighbor)) continue;

                // Entry direction constraint check
                if (neighbor.equals(to) && entryDir.isPresent()) {
                    boolean validEntry = false;
                    var ports = current.getPorts();
                    if (ports != null) {
                        for (Port exitPort : new Port[]{ports.getFirst(), ports.getSecond()}) {
                            if (exitPort == null) continue;
                            List<Port> nextPorts = graph.getNextPorts(exitPort);
                            if (nextPorts != null) {
                                for (Port next : nextPorts) {
                                    if (neighbor.equals(graph.getSegment(next))) {
                                        if (next.getNode() instanceof RailNodeImpl nodeImpl) {
                                            Dir portDir = nodeImpl.getDirForPort(next.getType());
                                            if (portDir == entryDir.get()) {
                                                validEntry = true;
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                            if (validEntry) break;
                        }
                    }
                    if (!validEntry) {
                        continue;
                    }
                }

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
        if (a == null || b == null) return 0;
        RailNode aNode = null;
        RailNode bNode = null;
        
        var aPorts = a.getPorts();
        if (aPorts != null && aPorts.getFirst() != null) {
            aNode = aPorts.getFirst().getNode();
        }
        
        var bPorts = b.getPorts();
        if (bPorts != null && bPorts.getFirst() != null) {
            bNode = bPorts.getFirst().getNode();
        }

        if (aNode == null || bNode == null) return 0;
        var aTrack = aNode.getTrack();
        var bTrack = bNode.getTrack();
        if (aTrack == null || bTrack == null) return 0;
        var aPos = aTrack.getPosition();
        var bPos = bTrack.getPosition();
        if (aPos == null || bPos == null) return 0;
        return Math.abs(aPos.getX() - bPos.getX()) + Math.abs(aPos.getY() - bPos.getY());
    }

    private int segmentCost(Segment s) {
        int count = graph.getTrackCount(s);
        return count > 0 ? count : 1;
    }

    private List<Segment> getNeighbors(Segment s) {
        if (graph == null) return List.of();
        List<Segment> neighbors = new ArrayList<>();
        var ports = s.getPorts();
        if (ports != null) {
            for (Port exitPort : new Port[]{ports.getFirst(), ports.getSecond()}) {
                if (exitPort == null) continue;
                List<Port> nextPorts = graph.getNextPorts(exitPort);
                if (nextPorts != null) {
                    for (Port next : nextPorts) {
                        Segment neighbor = graph.getSegment(next);
                        if (neighbor != null && !neighbor.equals(s)) {
                            neighbors.add(neighbor);
                        }
                    }
                }
            }
        }
        return neighbors;
    }
}
