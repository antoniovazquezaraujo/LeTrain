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

    @Override
    public boolean tryLock(Train train, Segment segment) {
        List<Train> owners = segmentOwners.computeIfAbsent(segment, k -> new CopyOnWriteArrayList<>());
        
        // Si ya hay dueños (incluso si soy yo mismo para re-bloqueo), no permitimos bloqueo exclusivo
        // Nota: Si soy yo mismo, el ADR no especifica si debemos permitir el tryLock de nuevo.
        // Asumimos que si ya lo tengo, el éxito es trivial.
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
        // En modo Shunting, permitimos compartir siempre.
        // El ADR menciona "Adicionalmente, ningún tren debe estar moviéndose". 
        // Como el BlockManager no conoce velocidades, esa validación debe hacerse en el llamador (AutoPilot/Train).
        List<Train> owners = segmentOwners.computeIfAbsent(segment, k -> new CopyOnWriteArrayList<>());
        
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
            owners.remove(train);
            if (owners.isEmpty()) {
                segmentOwners.remove(segment);
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
}
