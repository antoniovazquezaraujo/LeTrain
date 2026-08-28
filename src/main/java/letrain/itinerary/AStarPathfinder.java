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
import letrain.segments.BlockManager;
import letrain.segments.Port;
import letrain.segments.RailNode;
import letrain.segments.RailwayGraph;
import letrain.segments.Segment;
import letrain.segments.impl.RailNodeImpl;
import letrain.vehicle.rail.impl.Train;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A* pathfinder over the railway segment graph. Explores neighbors from both ends of each segment
 * (circuits).
 */
public class AStarPathfinder implements SegmentPathfinder {

    private static final Logger log = LoggerFactory.getLogger(AStarPathfinder.class);
    private final RailwayGraph graph;
    private final BlockManager blockManager;
    private final Train train;

    public AStarPathfinder(RailwayGraph graph) {
        this(graph, null, null);
    }

    public AStarPathfinder(RailwayGraph graph, BlockManager blockManager, Train train) {
        this.graph = graph;
        this.blockManager = blockManager;
        this.train = train;
    }

    private record NodeState(Segment segment, Port entryPort) {}

    @Override
    public List<Segment> find(Segment from, Segment to, Optional<Dir> entryDir) {
        return find(from, Optional.empty(), to, entryDir);
    }

    @Override
    public List<Segment> find(
            Segment from, Optional<Port> fromExitPort, Segment to, Optional<Dir> entryDir) {
        if (from == null || to == null) {
            return List.of();
        }
        if (from.equals(to)) {
            return List.of(from);
        }
        if (graph == null) {
            return List.of();
        }

        Map<NodeState, Integer> gScore = new HashMap<>();
        Map<NodeState, NodeState> cameFrom = new HashMap<>();
        Set<NodeState> closed = new HashSet<>();
        Map<NodeState, Integer> openMap = new HashMap<>();

        if (fromExitPort.isPresent() && fromExitPort.get() != null) {
            Port entryPort = getOtherPort(from, fromExitPort.get());
            if (entryPort != null) {
                NodeState startState = new NodeState(from, entryPort);
                gScore.put(startState, 0);
                openMap.put(startState, heuristic(from, to));
            }
        }

        if (openMap.isEmpty()) {
            var ports = from.getPorts();
            if (ports != null) {
                for (Port p : new Port[] {ports.getFirst(), ports.getSecond()}) {
                    if (p != null) {
                        NodeState startState = new NodeState(from, p);
                        gScore.put(startState, 0);
                        openMap.put(startState, heuristic(from, to));
                    }
                }
            }
        }

        if (openMap.isEmpty()) {
            return List.of();
        }

        int explored = 0;
        NodeState bestTargetState = null;

        while (!openMap.isEmpty()) {
            NodeState current = null;
            int bestF = Integer.MAX_VALUE;
            for (var entry : openMap.entrySet()) {
                if (entry.getValue() < bestF) {
                    bestF = entry.getValue();
                    current = entry.getKey();
                }
            }
            openMap.remove(current);
            explored++;

            if (current.segment().equals(to)) {
                log.info("[A*] FOUND {}→{} explored={}", from.getId(), to.getId(), explored);
                bestTargetState = current;
                break;
            }

            if (!closed.add(current)) {
                continue;
            }

            Port entryPort = current.entryPort();
            if (entryPort == null) {
                continue;
            }
            List<Port> nextPorts = graph.getNextPorts(entryPort);
            if (nextPorts == null) {
                continue;
            }

            for (Port nextEntryPort : nextPorts) {
                if (nextEntryPort == null) {
                    continue;
                }
                Segment neighbor = graph.getSegment(nextEntryPort);
                if (neighbor == null || neighbor.equals(current.segment())) {
                    continue;
                }

                // Entry direction constraint check for target segment
                if (neighbor.equals(to) && entryDir.isPresent()) {
                    if (nextEntryPort.getNode() instanceof RailNodeImpl nodeImpl) {
                        Dir portDir = nodeImpl.getDirForPort(nextEntryPort.getType());
                        if (portDir != entryDir.get()) {
                            continue;
                        }
                    }
                }

                NodeState neighborState = new NodeState(neighbor, nextEntryPort);
                if (closed.contains(neighborState)) {
                    continue;
                }

                int tentativeG = gScore.get(current) + segmentCost(neighbor);
                if (tentativeG < gScore.getOrDefault(neighborState, Integer.MAX_VALUE)) {
                    cameFrom.put(neighborState, current);
                    gScore.put(neighborState, tentativeG);
                    openMap.put(neighborState, tentativeG + heuristic(neighbor, to));
                }
            }
        }

        if (bestTargetState != null) {
            return reconstructPath(cameFrom, bestTargetState);
        }

        log.warn("[A*] NO ROUTE {}→{} explored={}", from.getId(), to.getId(), explored);
        return List.of();
    }

    private Port getOtherPort(Segment segment, Port entryPort) {
        var ports = segment.getPorts();
        if (ports == null) {
            return null;
        }
        if (entryPort.equals(ports.getFirst())) {
            return ports.getSecond();
        }
        if (entryPort.equals(ports.getSecond())) {
            return ports.getFirst();
        }
        return null;
    }

    private List<Segment> reconstructPath(Map<NodeState, NodeState> cameFrom, NodeState current) {
        List<Segment> path = new ArrayList<>();
        NodeState curr = current;
        while (curr != null) {
            path.add(curr.segment());
            curr = cameFrom.get(curr);
        }
        Collections.reverse(path);
        return path;
    }

    private int heuristic(Segment a, Segment b) {
        if (a == null || b == null) {
            return 0;
        }
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

        if (aNode == null || bNode == null) {
            return 0;
        }
        var aTrack = aNode.getTrack();
        var bTrack = bNode.getTrack();
        if (aTrack == null || bTrack == null) {
            return 0;
        }
        var aPos = aTrack.getPosition();
        var bPos = bTrack.getPosition();
        if (aPos == null || bPos == null) {
            return 0;
        }
        return Math.abs(aPos.getX() - bPos.getX()) + Math.abs(aPos.getY() - bPos.getY());
    }

    private int segmentCost(Segment s) {
        int count = graph.getTrackCount(s);
        int base = count > 0 ? count : 1;
        BlockManager bm = this.blockManager;
        if (bm == null && train != null && train.getModel() != null) {
            bm = train.getModel().getBlockManager();
        }
        if (bm != null && train != null) {
            List<Train> owners = bm.getOwners(s);
            if (owners != null && !owners.isEmpty() && !owners.contains(train)) {
                return base + 10000;
            }
        }
        return base;
    }
}
