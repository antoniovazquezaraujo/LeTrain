package letrain.segments.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import letrain.segments.BlockManager;
import letrain.segments.Segment;
import letrain.vehicle.rail.impl.Train;

public class BlockManagerImpl implements BlockManager {
    // Mapa de segmento -> lista de trenes dueños
    private final Map<Segment, List<Train>> segmentOwners = new ConcurrentHashMap<>();
    // Mapa inverso para optimizar consultas de trenes
    private final Map<Train, List<Segment>> trainSegments = new ConcurrentHashMap<>();

    private Runnable onReleaseListener;

    public void setOnReleaseListener(Runnable listener) {
        this.onReleaseListener = listener;
    }

    @Override
    public boolean tryLock(Train train, Segment segment) {
        List<Train> owners = segmentOwners.computeIfAbsent(segment, k -> new CopyOnWriteArrayList<>());

        if (!owners.isEmpty() && !owners.contains(train)) {
            return false;
        }

        if (!owners.contains(train)) {
            owners.add(train);
            registerTrainSegment(train, segment);
        }
        return true;
    }

    @Override
    public boolean tryShuntingLock(Train train, Segment segment) {
        List<Train> owners = segmentOwners.computeIfAbsent(segment, k -> new CopyOnWriteArrayList<>());

        // ADR-005 REFINEMENT: Only allow coexistence if all current owners are stopped.
        for (Train owner : owners) {
            if (owner != train && owner.getSpeed() != 0) {
                return false;
            }
        }

        if (!owners.contains(train)) {
            owners.add(train);
            registerTrainSegment(train, segment);
        }
        return true;
    }

    @Override
    public void release(Train train, Segment segment) {
        List<Train> owners = segmentOwners.get(segment);
        if (owners != null) {
            if (owners.remove(train)) {
                if (owners.isEmpty()) {
                    segmentOwners.remove(segment);
                    if (onReleaseListener != null) {
                        onReleaseListener.run();
                    }
                }
            }
        }

        List<Segment> segments = trainSegments.get(train);
        if (segments != null) {
            segments.remove(segment);
            if (segments.isEmpty()) {
                trainSegments.remove(train);
            }
        }
    }

    @Override
    public void releaseAll(Train train) {
        if (train == null) return;
        List<Segment> owned = trainSegments.get(train);
        if (owned != null) {
            // Use a copy to avoid ConcurrentModificationException
            List<Segment> copy = new ArrayList<>(owned);
            for (Segment s : copy) {
                release(train, s);
            }
        }
    }

    @Override
    public List<Train> getOwners(Segment segment) {
        return segmentOwners.getOrDefault(segment, Collections.emptyList());
    }

    @Override
    public boolean canExitShunting(Train train) {
        List<Segment> occupied = trainSegments.getOrDefault(train, Collections.emptyList());

        for (Segment s : occupied) {
            List<Train> owners = segmentOwners.get(s);
            if (owners != null && owners.size() > 1) {
                // Hay convivencia en al menos un segmento ocupado por el tren
                return false;
            }
        }
        return true;
    }

    @Override
    public void clearAll() {
        segmentOwners.clear();
        trainSegments.clear();
    }

    private void registerTrainSegment(Train train, Segment segment) {
        trainSegments.computeIfAbsent(train, k -> new CopyOnWriteArrayList<>()).add(segment);
    }

    @Override
    public List<Segment> getOwnedSegments(Train train) {
        return trainSegments.getOrDefault(train, Collections.emptyList());
    }

    @Override
    public Set<Segment> getAllLockedSegments() {
        return segmentOwners.keySet();
    }
}
