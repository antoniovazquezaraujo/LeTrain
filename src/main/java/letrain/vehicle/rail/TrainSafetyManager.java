package letrain.vehicle.rail;

import letrain.mvp.impl.Model;
import letrain.segments.BlockManager;
import letrain.segments.RailwayGraph;
import letrain.segments.Segment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public interface TrainSafetyManager {
    Logger log = LoggerFactory.getLogger(letrain.vehicle.rail.impl.TrainSafetyManager.class);

    boolean hasPermissionToMove();

    Segment getCurrentSegment();

    Segment getNextSegment();

    boolean isWaitingForBlock();

    void forceSegmentReset();

    void forceEmergencyStop();

    void claimOccupiedSegments(Model model);

    void acquireInitialLocks(Model model);

    void onSegmentEntered(Model model, Segment newSegment);

    void wakeUp(Model model);

    void onReverse(Model model);

    Segment findNextSegment(Linker head, RailwayGraph graph);

    void releaseOldSegments(BlockManager bm, RailwayGraph graph);

    Segment findNextSegmentTopological(Linker head, RailwayGraph graph);
}
