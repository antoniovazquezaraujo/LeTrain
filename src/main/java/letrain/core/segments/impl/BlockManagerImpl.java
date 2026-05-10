package letrain.core.segments.impl;

import letrain.core.segments.BlockManager;
import letrain.core.segments.Segment;
import letrain.vehicle.impl.rail.Train;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class BlockManagerImpl implements BlockManager {
    // Mapa de segmento -> lista de trenes dueños
    private final Map<Segment, List<Train>> segmentOwners = new ConcurrentHashMap<>();
    // Mapa inverso para optimizar consultas de trenes
    private final Map<Train, List<Segment>> trainSegments = new ConcurrentHashMap<>();
    
    // Nueva estructura para el Mandamiento 6 (Regla de los Dos Segmentos):
    // Mapa de Nodo (Fork) -> Mapa de Tren -> Contador de segmentos adyacentes bloqueados
    private final Map<letrain.core.segments.RailNode, Map<Train, Integer>> forkOwnershipCounts = new ConcurrentHashMap<>();
    
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
            updateForkLocks(segment, train, true);
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
            // En Shunting, no bloqueamos desvíos por software
        }
        return true;
    }

    @Override
    public void release(Train train, Segment segment) {
        List<Train> owners = segmentOwners.get(segment);
        if (owners != null) {
            if (owners.remove(train)) {
                updateForkLocks(segment, train, false);
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

    private void updateForkLocks(Segment segment, Train train, boolean lock) {
        // Un segmento tiene dos extremos (nodos)
        updateNodeLock(segment.getSteps().getFirst().getRailNode(), train, lock);
        updateNodeLock(segment.getSteps().getSecond().getRailNode(), train, lock);
    }

    private void updateNodeLock(letrain.core.segments.RailNode node, Train train, boolean lock) {
        if (!(node.getTrack() instanceof letrain.track.rail.ForkRailTrack)) {
            return;
        }
        
        letrain.track.rail.ForkRailTrack fork = (letrain.track.rail.ForkRailTrack) node.getTrack();
        
        Map<Train, Integer> counts = forkOwnershipCounts.computeIfAbsent(node, k -> new ConcurrentHashMap<>());
        int currentCount = counts.getOrDefault(train, 0);
        
        if (lock) {
            currentCount++;
        } else {
            currentCount = Math.max(0, currentCount - 1);
        }
        
        if (currentCount == 0) {
            counts.remove(train);
        } else {
            counts.put(train, currentCount);
        }
        
        // REGLA DE ORO: Un Fork solo se bloquea si UN MISMO TREN posee 2 o más segmentos que confluyen en él.
        // Esto permite que el tren pase seguro por el desvío (teniendo el de entrada y el de salida),
        // pero NO bloquea el desvío si el tren solo está en un segmento (permitiendo resolución manual).
        boolean shouldBeLocked = counts.values().stream().anyMatch(c -> c >= 2);
        fork.setLocked(shouldBeLocked);
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
        // Release all fork locks when topology is rebuilt
        for (letrain.core.segments.RailNode node : forkOwnershipCounts.keySet()) {
            if (node.getTrack() instanceof letrain.track.rail.ForkRailTrack) {
                ((letrain.track.rail.ForkRailTrack) node.getTrack()).setLocked(false);
            }
        }
        forkOwnershipCounts.clear();
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
