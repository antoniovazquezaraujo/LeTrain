package letrain.vehicle.impl.rail;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import letrain.map.Dir;
import letrain.mvp.impl.Model;
import letrain.segments.BlockManager;
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
    private static final int SAFETY_RETRY_TICKS = 300; // 15s at 20fps

    private final Train train;
    private Segment currentSegment;
    private Segment nextSegment;
    private boolean permissionToMove = true;
    private int safetyRetryTimer = 0;

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
        this.safetyRetryTimer = 0;
    }

    public boolean checkSafety(Model model) {
        BlockManager bm = model.getBlockManager();
        RailwayGraph graph = model.getRailwayGraph();

        Linker head = (Linker) train.getDirectorLinker();
        if (head == null || !(head.getTrack() instanceof RailTrack)) return true;

        RailTrack headTrack = (RailTrack) head.getTrack();
        Segment headS = graph.getSegment(headTrack);
        if (headS == null) return true;

        // 1. Detect segment change to reset permission
        if (currentSegment == null || !headS.equals(currentSegment)) {
            currentSegment = headS;
            // Ensure ownership of current segment (Mandatory 1: To step is to own)
            if (!bm.getOwnedSegments(train).contains(currentSegment)) {
                boolean curLocked = bm.tryLock(train, currentSegment);
                log.info("[LOCK] {} tryLock(current={}) = {} (owned={})",
                    train.getId(), currentSegment.getId(), curLocked,
                    bm.getOwners(currentSegment).stream().map(t -> String.valueOf(t.getId())).toList());
            }
            permissionToMove = false;
            nextSegment = findNextSegment(head, graph);
            safetyRetryTimer = 0; // Try immediately

            letrain.itinerary.AutoPilot ap = train.getAutopilot();
            if (ap != null && nextSegment != null && ap.mode() == letrain.itinerary.AutoPilot.Mode.FOLLOWING) {
                ap.ensureForkRoute(currentSegment, nextSegment);
            }
        }

        // 2. If no permission, try to acquire it (lock Sn+1)
        if (!permissionToMove) {
            if (safetyRetryTimer <= 0) {
                // Refresh nextSegment from autopilot route if available
                nextSegment = findNextSegment(head, graph);

                if (nextSegment == null || nextSegment.equals(currentSegment)) {
                    permissionToMove = true; // No next segment, move freely
                } else {
                    boolean locked = bm.tryLock(train, nextSegment);
                    log.info("[LOCK] {} tryLock({}) = {} (owners={})",
                        train.getId(), nextSegment.getId(), locked,
                        bm.getOwners(nextSegment).stream().map(t -> String.valueOf(t.getId())).toList());

                    if (!locked) {
                        Segment alt = findAlternativeSegment(head, graph, nextSegment);
                        if (alt != null) {
                            log.info("[LOCK] {} alternative seg {} found, owners={}",
                                train.getId(), alt.getId(),
                                bm.getOwners(alt).stream().map(t -> String.valueOf(t.getId())).toList());
                            boolean altLocked = bm.tryLock(train, alt);
                            if (altLocked) {
                                log.info("[LOCK] {} rerouted to alternative seg {}", train.getId(), alt.getId());
                                nextSegment = alt;
                                locked = true;
                            }
                        }
                    }

                    if (locked) {
                        log.info("Train {} granted permission to move into {}.",
                            train.getId(), nextSegment.getId());
                        permissionToMove = true;
                    } else {
                        log.debug("Train {} denied permission. Retrying in 15s.",
                            train.getId());
                        safetyRetryTimer = SAFETY_RETRY_TICKS;
                    }
                }
            } else {
                safetyRetryTimer--;
            }
        }

        // 3. Release old segments
        releaseOldSegments(bm, graph);

        // 4. Proactive braking logic (Mandatory 3)
        if (!permissionToMove) {
            if (train.getDirectorLinker() != null) {
                train.getDirectorLinker().setTargetSpeed(0);
            }

            // If physically crossed boundary without permission: ILLEGAL ENTRY
            // No shunting fallback — train will crash via physical collision.
        }

        return true;
    }

    /**
     * Finds the next segment the train should enter.
     * When the autopilot is active, uses its planned route for correct fork routing.
     * Otherwise falls back to topological inference.
     */
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

    private void releaseOldSegments(BlockManager bm, RailwayGraph graph) {
        Set<Segment> physicallyOccupied = new HashSet<>();
        for (Linker l : train.getLinkers()) {
            if (l.getTrack() instanceof RailTrack) {
                Segment s = graph.getSegment((RailTrack) l.getTrack());
                if (s != null) physicallyOccupied.add(s);
            }
        }

        List<Segment> owned = bm.getOwnedSegments(train);
        for (Segment s : owned) {
            if (!physicallyOccupied.contains(s)) {
                Segment sNext = findNextSegmentTopological((Linker)train.getDirectorLinker(), graph);
                if (sNext == null || !s.equals(sNext)) {
                    bm.release(train, s);
                    log.debug("Train {} released segment {}", train.getId(), s.getId());
                }
            }
        }
    }

    private Segment findNextSegmentTopological(Linker head, RailwayGraph graph) {
        RailTrack headTrack = (RailTrack) head.getTrack();
        Segment s = graph.getSegment(headTrack);
        if (s == null) return null;

        Dir exitDir = head.getDir();
        List<letrain.segments.PathStep> nextSteps = graph.getNextSteps(new PathStepImpl(
            new RailNodeImpl(headTrack), exitDir));

        if (nextSteps == null || nextSteps.isEmpty()) {
            RailIterator it = new RailIterator(headTrack, exitDir);
            int maxIterations = 10000; // Safety guard against infinite loops on circuits
            while (it.advance() && maxIterations-- > 0) {
                Track t = it.getTrack();
                Segment nextS = graph.getSegment((RailTrack) t);
                if (nextS != null && !nextS.equals(s)) return nextS;
            }
        } else {
            return graph.getSegment(nextSteps.get(0));
        }

        return null;
    }

    /** Find an alternative branch that leads to the same downstream node (siding). */
    private Segment findAlternativeSegment(Linker head, RailwayGraph graph, Segment occupiedSeg) {
        RailTrack headTrack = (RailTrack) head.getTrack();
        letrain.segments.PathStep currentStep = new letrain.segments.impl.PathStepImpl(
            new letrain.segments.impl.RailNodeImpl(headTrack), head.getDir());
        List<letrain.segments.PathStep> outSteps = graph.getNextSteps(currentStep);
        if (outSteps == null || outSteps.size() < 2) return null; // no fork / no alternative

        // Find the far-end node of the occupied segment (the node that is NOT the fork)
        letrain.segments.RailNode farNode = findFarNode(occupiedSeg, headTrack);
        if (farNode == null) return null;

        // Look for another outStep whose segment shares that same far node
        for (letrain.segments.PathStep step : outSteps) {
            Segment altSeg = graph.getSegment(step);
            if (altSeg == null || altSeg.equals(occupiedSeg)) continue;
            if (farNode.equals(findFarNode(altSeg, headTrack))) {
                return altSeg; // same destination, different route
            }
        }
        return null;
    }

    /** Returns the RailNode at the FAR end of a segment (not the one containing headTrack). */
    private letrain.segments.RailNode findFarNode(Segment seg, RailTrack forkTrack) {
        var steps = seg.getSteps();
        if (steps == null) return null;
        letrain.segments.PathStep s1 = steps.getFirst();
        letrain.segments.PathStep s2 = steps.getSecond();
        if (s1 != null) {
            letrain.track.Track t = s1.getRailNode() != null ? s1.getRailNode().getTrack() : null;
            if (t != forkTrack) return s1.getRailNode();
        }
        if (s2 != null) {
            letrain.track.Track t = s2.getRailNode() != null ? s2.getRailNode().getTrack() : null;
            if (t != forkTrack) return s2.getRailNode();
        }
        return null;
    }

    public void forceSegmentReset() {
        this.currentSegment = null;
    }
}
