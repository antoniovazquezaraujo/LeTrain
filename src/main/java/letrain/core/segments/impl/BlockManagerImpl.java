package letrain.core.segments.impl;

import letrain.core.segments.BlockManager;
import letrain.core.segments.Segment;
import letrain.vehicle.impl.rail.Train;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

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
            updateForkLocks(segment, true);
        }
        return true;
    }

    @Override
    public boolean tryShuntingLock(Train train, Segment segment) {
        List<Train> owners = segmentOwners.computeIfAbsent(segment, k -> new CopyOnWriteArrayList<>());
        
        if (!owners.contains(train)) {
            owners.add(train);
            registerTrainSegment(train, segment);
            // En Shunting, el anclaje se relaja según ADR-005, 
            // pero el bloqueo FÍSICO (vehículo encima) sigue mandando.
            // No activamos updateForkLocks aquí.
        }
        return true;
    }

    @Override
    public void release(Train train, Segment segment) {
        List<Train> owners = segmentOwners.get(segment);
        if (owners != null) {
            owners.remove(train);
            if (owners.isEmpty()) {
                segmentOwners.remove(segment);
                updateForkLocks(segment, false);
                if (onReleaseListener != null) {
                    onReleaseListener.run();
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

    private void updateForkLocks(Segment segment, boolean lock) {
        // Los Forks que definen un segmento son sus extremos (RailNodes)
        segment.getSteps().getFirst().getRailNode().getOutSteps().stream()
            .map(ps -> ps.getRailNode().getTrack())
            .filter(t -> t instanceof letrain.track.rail.ForkRailTrack)
            .forEach(f -> ((letrain.track.rail.ForkRailTrack)f).setLocked(lock));

        segment.getSteps().getSecond().getRailNode().getOutSteps().stream()
            .map(ps -> ps.getRailNode().getTrack())
            .filter(t -> t instanceof letrain.track.rail.ForkRailTrack)
            .forEach(f -> ((letrain.track.rail.ForkRailTrack)f).setLocked(lock));
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
