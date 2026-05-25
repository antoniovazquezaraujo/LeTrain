package letrain.vehicle.impl.rail;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import letrain.map.Dir;
import letrain.mvp.Model;
import letrain.segments.BlockManager;
import letrain.segments.PathStep;
import letrain.segments.RailNode;
import letrain.segments.RailwayGraph;
import letrain.segments.Segment;
import letrain.segments.impl.PathStepImpl;
import letrain.segments.impl.RailNodeImpl;
import letrain.track.Track;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;
import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.RailIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encapsulates the safety and block management logic for a Train.
 * Implementation of ADR-005 security protocols.
 */
public class TrainSafetyManager {
    private static final Logger log = LoggerFactory.getLogger(TrainSafetyManager.class);

    private final Train train;
    private Segment currentSegment;
    private Segment nextSegment;
    private boolean permissionToMove = true;
    private boolean overshot = false;

    public TrainSafetyManager(Train train) {
        this.train = train;
    }

    public boolean hasPermissionToMove() {
        return permissionToMove;
    }

    public Segment getCurrentSegment() {
        return currentSegment;
    }

    public Segment getNextSegment() {
        return nextSegment;
    }

    public void resetSafetyTimer() {
        this.overshot = false;
    }

    private void setNextSegment(BlockManager bm, Segment next) {
        if (nextSegment != null && !nextSegment.equals(next)) {
            bm.unregisterWaiting(train, nextSegment);
        }
        this.nextSegment = next;
    }

    public boolean checkSafety(Model model) {
        BlockManager bm = model.getBlockManager();
        RailwayGraph graph = model.getRailwayGraph();

        Linker head = (Linker) train.getDirectorLinker();
        if (head == null || !(head.getTrack() instanceof RailTrack)) return true;

        RailTrack headTrack = (RailTrack) head.getTrack();
        Segment headS = resolveSegment(graph, headTrack, head.getDir());

        // 1. Detect segment change to reset permission
        if (headS != null && (currentSegment == null || !headS.equals(currentSegment))) {
            if (currentSegment != null && !permissionToMove) {
                // Overshot! We left currentSegment without permission
                overshot = true;
                currentSegment = headS;
                permissionToMove = false;
                log.warn("Train {} overshot into segment {} without permission! Braking and stopping retries.",
                    train.getId(), headS.getId());
                // Even on overshoot, we must try to lock the segment we are physically occupying to notify other trains
                bm.tryLock(train, currentSegment);
                train.deactivateAutoModeAndStop();
            } else {
                currentSegment = headS;
                overshot = false;
                // Ensure ownership of current segment (Mandatory 1: To step is to own)
                if (!bm.getOwnedSegments(train).contains(currentSegment)) {
                    boolean curLocked = bm.tryLock(train, currentSegment);
                    log.info("[LOCK] {} tryLock(current={}) = {} (owned={})",
                        train.getId(), currentSegment.getId(), curLocked,
                        bm.getOwners(currentSegment).stream().map(t -> String.valueOf(t.getId())).toList());
                }
                permissionToMove = false;
                Segment next = findNextSegment(head, graph);
                setNextSegment(bm, next);

                letrain.itinerary.AutoPilot ap = train.getAutopilot();
                if (ap != null && nextSegment != null && ap.mode() == letrain.itinerary.AutoPilot.Mode.FOLLOWING) {
                    ap.ensureForkRoute(currentSegment, nextSegment);
                }

                if (nextSegment == null || nextSegment.equals(currentSegment)) {
                    permissionToMove = true; // No next segment, move freely
                } else {
                    boolean locked = bm.tryLock(train, nextSegment);
                    log.info("[LOCK] {} tryLock({}) = {} (owners={})",
                        train.getId(), nextSegment.getId(), locked,
                        bm.getOwners(nextSegment).stream().map(t -> String.valueOf(t.getId())).toList());

                    if (!locked) {
                        Segment alt = findAlternativeSegment(head, graph, nextSegment);
                        if (alt != null && !train.containsWaypointElement(nextSegment)) {
                            log.info("[LOCK] {} alternative seg {} found, checking if we can detour",
                                train.getId(), alt.getId());
                            if (ap != null && ap.mode() == letrain.itinerary.AutoPilot.Mode.FOLLOWING) {
                                ap.ensureForkRoute(currentSegment, alt);
                            }
                            boolean altLocked = bm.tryLock(train, alt);
                            if (altLocked) {
                                log.info("[LOCK] {} rerouted to alternative seg {}", train.getId(), alt.getId());
                                setNextSegment(bm, alt);
                                locked = true;
                            }
                        }
                    }

                    if (locked) {
                        log.info("Train {} granted permission to move into {}.",
                            train.getId(), nextSegment.getId());
                        permissionToMove = true;
                    } else {
                        log.info("Train {} denied permission to move into {}. Registering as waiting.",
                            train.getId(), nextSegment.getId());
                        bm.registerWaiting(train, nextSegment);
                        train.deactivateAutoModeAndStop();
                    }
                }
            }
        }

        return true;
    }

    public void onNextSegmentReleased(Model model, Segment segment) {
        if (segment.equals(nextSegment) && !permissionToMove && !overshot) {
            BlockManager bm = model.getBlockManager();
            letrain.itinerary.AutoPilot ap = train.getAutopilot();
            if (ap != null && ap.mode() == letrain.itinerary.AutoPilot.Mode.FOLLOWING) {
                ap.ensureForkRoute(currentSegment, nextSegment);
            }
            boolean locked = bm.tryLock(train, nextSegment);
            if (!locked) {
                Linker head = (Linker) train.getDirectorLinker();
                if (head != null) {
                    Segment alt = findAlternativeSegment(head, model.getRailwayGraph(), nextSegment);
                    if (alt != null && !train.containsWaypointElement(nextSegment)) {
                        if (ap != null && ap.mode() == letrain.itinerary.AutoPilot.Mode.FOLLOWING) {
                            ap.ensureForkRoute(currentSegment, alt);
                        }
                        boolean altLocked = bm.tryLock(train, alt);
                        if (altLocked) {
                            bm.unregisterWaiting(train, nextSegment);
                            setNextSegment(bm, alt);
                            locked = true;
                        }
                    }
                }
            }
            if (locked) {
                log.info("Train {} granted permission into {} via release event.", train.getId(), nextSegment.getId());
                permissionToMove = true;
            } else {
                bm.registerWaiting(train, nextSegment);
            }
        }
    }

    public void releaseOldSegmentsOnForkExit(Model model) {
        BlockManager bm = model.getBlockManager();
        RailwayGraph graph = model.getRailwayGraph();
        Set<Segment> physicallyOccupied = new HashSet<>();
        for (Linker l : train.getLinkers()) {
            if (l.getTrack() instanceof RailTrack) {
                RailTrack track = (RailTrack) l.getTrack();
                Segment s = resolveSegment(graph, track, l.getDir());
                if (s != null) physicallyOccupied.add(s);
            }
        }

        List<Segment> owned = new java.util.ArrayList<>(bm.getOwnedSegments(train));
        for (Segment s : owned) {
            if (physicallyOccupied.contains(s) || s.equals(currentSegment) || s.equals(nextSegment)) {
                continue;
            }
            bm.release(train, s);
            log.info("Train {} released segment {} on fork exit (physically empty).", train.getId(), s.getId());
        }
    }

    private Segment findNextSegment(Linker head, RailwayGraph graph) {
        letrain.itinerary.AutoPilot ap = train.getAutopilot();
        if (ap != null && ap.mode() == letrain.itinerary.AutoPilot.Mode.FOLLOWING) {
            List<Segment> route = ap.currentRoute();
            int index = route.indexOf(currentSegment);
            if (index >= 0 && index + 1 < route.size()) {
                return route.get(index + 1);
            }
        }
        return findNextSegmentTopological(head, graph);
    }

    private Segment findNextSegmentTopological(Linker head, RailwayGraph graph) {
        RailTrack headTrack = (RailTrack) head.getTrack();
        Segment s = resolveSegment(graph, headTrack, head.getDir());

        Dir exitDir = head.getDir();
        RailIterator it = new RailIterator(headTrack, exitDir);
        int maxIterations = 10000; // Safety guard against infinite loops on circuits
        while (it.advance() && maxIterations-- > 0) {
            Track t = it.getTrack();
            if (t instanceof RailTrack) {
                Segment nextS = graph.getSegment((RailTrack) t);
                if (nextS != null && !nextS.equals(s)) {
                    return nextS;
                }
            }
        }
        return null;
    }

    private RailNode getApproachingNode(Linker head, RailwayGraph graph) {
        RailTrack headTrack = (RailTrack) head.getTrack();
        Segment s = resolveSegment(graph, headTrack, head.getDir());
        if (s == null) return null;

        Dir exitDir = head.getDir();
        RailIterator it = new RailIterator(headTrack, exitDir);
        int maxIterations = 10000;
        while (it.advance() && maxIterations-- > 0) {
            Track t = it.getTrack();
            if (t instanceof RailTrack) {
                if (s.getSteps() != null) {
                    letrain.segments.PathStep s1 = s.getSteps().getFirst();
                    letrain.segments.PathStep s2 = s.getSteps().getSecond();
                    if (s1 != null && s1.getRailNode() != null && s1.getRailNode().getTrack() == t) {
                        return s1.getRailNode();
                    }
                    if (s2 != null && s2.getRailNode() != null && s2.getRailNode().getTrack() == t) {
                        return s2.getRailNode();
                    }
                }
            }
        }
        return null;
    }

    private Segment findAlternativeSegment(Linker head, RailwayGraph graph, Segment occupiedSeg) {
        RailNode approachingNode = getApproachingNode(head, graph);
        if (approachingNode == null || !(approachingNode.getTrack() instanceof ForkRailTrack)) {
            return null;
        }

        List<PathStep> outSteps = approachingNode.getOutSteps().stream()
            .filter(step -> {
                Segment seg = graph.getSegment(step);
                return seg != null && !seg.equals(currentSegment);
            })
            .collect(java.util.stream.Collectors.toList());

        if (outSteps.size() < 2) {
            return null;
        }

        // Find the far-end node of the occupied segment (the node that is NOT the fork)
        RailNode farNode = findFarNode(occupiedSeg, approachingNode.getTrack());
        if (farNode == null) return null;

        // Look for another outStep whose segment shares that same far node
        for (PathStep step : outSteps) {
            Segment altSeg = graph.getSegment(step);
            if (altSeg == null || altSeg.equals(occupiedSeg)) continue;
            if (farNode.equals(findFarNode(altSeg, approachingNode.getTrack()))) {
                return altSeg; // same destination, different route
            }
        }
        return null;
    }

    /** Returns the RailNode at the FAR end of a segment (not the one containing headTrack). */
    private RailNode findFarNode(Segment seg, Track forkTrack) {
        var steps = seg.getSteps();
        if (steps == null) return null;
        PathStep s1 = steps.getFirst();
        PathStep s2 = steps.getSecond();
        if (s1 != null) {
            Track t = s1.getRailNode() != null ? s1.getRailNode().getTrack() : null;
            if (t != forkTrack) return s1.getRailNode();
        }
        if (s2 != null) {
            Track t = s2.getRailNode() != null ? s2.getRailNode().getTrack() : null;
            if (t != forkTrack) return s2.getRailNode();
        }
        return null;
    }

    public void forceSegmentReset() {
        if (train.getModel() != null && nextSegment != null) {
            train.getModel().getBlockManager().unregisterWaiting(train, nextSegment);
        }
        this.nextSegment = null;
        this.currentSegment = null;
        this.overshot = false;
    }

    private Segment resolveSegment(RailwayGraph graph, RailTrack track, Dir exitDir) {
        if (track instanceof ForkRailTrack) {
            return graph.getSegment(track, exitDir);
        }
        return graph.getSegment(track);
    }
}
