package letrain.itinerary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
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
 *
 * <p>
 * <b>Cost metric.</b> Entering a segment costs its {@link RailwayGraph#getTrackCount(Segment) track
 * count} (one unit per physical rail, minimum 1); the start segment is not charged. A path cost is
 * therefore the number of rails the train still has to enter. A blocked canton owned by another
 * train adds a large penalty (see {@link #segmentCost(Segment)}) so free routes win, but the
 * heuristic never includes it.
 *
 * <p>
 * <b>Optimality.</b> The heuristic documented on {@link #heuristic(NodeState, Segment)} is
 * admissible and consistent for that metric, so the first target state popped from the priority
 * queue is optimal. The open set is a {@link PriorityQueue} with stale-entry discard, giving
 * {@code O(E log V)} instead of the previous linear min-scan per expansion ({@code O(V^2)}).
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

    /** Package-private for the contract tests (#816): the state the heuristic is defined on. */
    record NodeState(Segment segment, Port entryPort) {
    }

    /**
     * Open-set entry. {@code g} is the g-score this entry was pushed with, so an entry is stale
     * when a shorter g-score for the same state has been recorded meanwhile. {@code seq} is a
     * monotonic insertion counter used to break f-score ties deterministically (FIFO among
     * equal-cost states), keeping the returned route stable across runs and JVMs.
     */
    private record QueueEntry(int f, int g, long seq, NodeState state) {
    }

    private static final Comparator<QueueEntry> QUEUE_ORDER =
            Comparator.comparingInt(QueueEntry::f).thenComparingLong(QueueEntry::seq);

    @Override
    public List<Segment> find(Segment from, Segment to, Optional<Dir> entryDir) {
        return find(from, Optional.empty(), to, entryDir);
    }

    @Override
    public List<Segment> find(Segment from, Optional<Port> fromExitPort, Segment to,
            Optional<Dir> entryDir) {
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
        PriorityQueue<QueueEntry> open = new PriorityQueue<>(QUEUE_ORDER);
        long nextSequence = 0;

        if (fromExitPort.isPresent() && fromExitPort.get() != null) {
            Port entryPort = getOtherPort(from, fromExitPort.get());
            if (entryPort != null) {
                NodeState startState = new NodeState(from, entryPort);
                gScore.put(startState, 0);
                open.add(new QueueEntry(heuristic(startState, to), 0, nextSequence++, startState));
            }
        }

        if (open.isEmpty()) {
            var ports = from.getPorts();
            if (ports != null) {
                for (Port p : new Port[] {ports.getFirst(), ports.getSecond()}) {
                    if (p != null) {
                        NodeState startState = new NodeState(from, p);
                        gScore.put(startState, 0);
                        open.add(new QueueEntry(heuristic(startState, to), 0, nextSequence++,
                                startState));
                    }
                }
            }
        }

        if (open.isEmpty()) {
            return List.of();
        }

        int explored = 0;
        NodeState bestTargetState = null;

        while (!open.isEmpty()) {
            QueueEntry entry = open.poll();
            NodeState current = entry.state();
            // Stale entry: a shorter path to this state was queued after this one.
            Integer currentG = gScore.get(current);
            if (currentG == null || entry.g() != currentG) {
                continue;
            }
            explored++;

            if (current.segment().equals(to)) {
                log.debug("[A*] found {} -> {} explored={}", from.getId(), to.getId(), explored);
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

                int tentativeG = currentG + segmentCost(neighbor);
                if (tentativeG < gScore.getOrDefault(neighborState, Integer.MAX_VALUE)) {
                    cameFrom.put(neighborState, current);
                    gScore.put(neighborState, tentativeG);
                    int f = tentativeG + heuristic(neighborState, to);
                    open.add(new QueueEntry(f, tentativeG, nextSequence++, neighborState));
                }
            }
        }

        if (bestTargetState != null) {
            return reconstructPath(cameFrom, bestTargetState);
        }

        log.debug("[A*] no route {} -> {} explored={}", from.getId(), to.getId(), explored);
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

    /**
     * Admissible and consistent lower bound of the remaining cost from {@code state} to
     * {@code target}, in the same unit as {@link #segmentCost(Segment)}.
     *
     * <p>
     * <b>Cost metric.</b> Entering a segment costs its track count (one unit per physical rail,
     * minimum 1) and the start segment is not charged, so a path cost counts the rails the train
     * still has to enter — a number of grid steps.
     *
     * <p>
     * <b>Bound.</b> The eight map directions move at most one cell per axis per rail, so the
     * Chebyshev distance {@code max(|dx|, |dy|)} between two positions never exceeds the number of
     * rail steps needed to join them. The train still has to cross its current segment before
     * leaving it, so the estimate is
     * {@code max(0, chebyshev(entryNode, nearest target endpoint) - trackCount(current))}. Using
     * the state's <b>entry port</b> (instead of the segment's first port) keeps the bound valid for
     * both travel senses and for segments entered from either end on a circuit.
     *
     * <p>
     * The blocked-canton {@code +10000} penalty is deliberately not included, so the bound stays
     * admissible. It is also consistent: Chebyshev distance obeys the triangle inequality and a
     * segment is at most {@code trackCount} steps long, hence {@code h(a) <= cost(a -> b) + h(b)}.
     */
    int heuristic(NodeState state, Segment target) {
        if (state == null || target == null || state.segment().equals(target)) {
            return 0;
        }
        letrain.map.Point from = portPosition(state.entryPort());
        int distance = distanceToSegment(target, from);
        if (from == null || distance < 0) {
            return 0;
        }
        return Math.max(0, distance - trackCount(state.segment()));
    }

    /** Map position of a port's node, or null when the topology/position is unavailable. */
    private static letrain.map.Point portPosition(Port port) {
        if (port == null) {
            return null;
        }
        RailNode node = port.getNode();
        if (node == null || node.getTrack() == null) {
            return null;
        }
        return node.getTrack().getPosition();
    }

    /** Chebyshev distance from {@code from} to the nearest endpoint of {@code target}, or -1. */
    private static int distanceToSegment(Segment target, letrain.map.Point from) {
        if (from == null || target.getPorts() == null) {
            return -1;
        }
        int best = -1;
        for (Port port : new Port[] {target.getPorts().getFirst(), target.getPorts().getSecond()}) {
            letrain.map.Point to = portPosition(port);
            if (to == null) {
                continue;
            }
            int distance =
                    Math.max(Math.abs(from.getX() - to.getX()), Math.abs(from.getY() - to.getY()));
            if (best < 0 || distance < best) {
                best = distance;
            }
        }
        return best;
    }

    /** Physical rails in a segment, at least 1 so the heuristic's subtraction stays sane. */
    private int trackCount(Segment segment) {
        int count = graph != null ? graph.getTrackCount(segment) : 0;
        return count > 0 ? count : 1;
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
