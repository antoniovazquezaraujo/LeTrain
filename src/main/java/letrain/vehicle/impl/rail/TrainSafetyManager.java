package letrain.vehicle.impl.rail;

import letrain.core.segments.BlockManager;
import letrain.core.segments.RailwayGraph;
import letrain.core.segments.Segment;
import letrain.core.segments.impl.PathStepImpl;
import letrain.core.segments.impl.RailNodeImpl;
import letrain.map.Dir;
import letrain.mvp.impl.Model;
import letrain.track.Track;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;
import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.RailIterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
                if (!bm.tryLock(train, currentSegment)) {
                    bm.tryShuntingLock(train, currentSegment);
                }
            }
            permissionToMove = false; 
            nextSegment = findNextSegmentTopological(head, graph);
            safetyRetryTimer = 0; // Try immediately
        }

        // 2. If no permission, try to acquire it (lock Sn+1)
        if (!permissionToMove) {
            if (safetyRetryTimer <= 0) {
                if (nextSegment == null || nextSegment.equals(currentSegment)) {
                    permissionToMove = true; // No next segment, move freely
                } else {
                    boolean locked = bm.tryLock(train, nextSegment);
                    if (!locked) {
                        locked = bm.tryShuntingLock(train, nextSegment);
                    }
                    
                    if (locked) {
                        log.info("Train {} granted permission to move into {}. Next segment {} locked (Shunting: {}).", 
                            train.getId(), currentSegment.getId(), nextSegment.getId(), train.isShuntingMode());
                        permissionToMove = true;
                    } else {
                        log.debug("Train {} denied permission. Segment {} occupied. Retrying in 15s.", 
                            train.getId(), nextSegment.getId());
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
            Track nextTrack = headTrack.getConnected(head.getDir());
            if (nextTrack != null && (nextTrack instanceof ForkRailTrack || graph.getSegment((RailTrack)nextTrack) != headS)) {
                if (nextSegment != null) {
                    log.warn("Train {} ENTERED ILLEGALLY into segment {}. Forcing segment shunting.", train.getId(), nextSegment.getId());
                    bm.tryShuntingLock(train, nextSegment);
                    permissionToMove = true; // Force permission
                }
            }
        }

        return true;
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
        List<letrain.core.segments.PathStep> nextSteps = graph.getNextSteps(new PathStepImpl(
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
}
