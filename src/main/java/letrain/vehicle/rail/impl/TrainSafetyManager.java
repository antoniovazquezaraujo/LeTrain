package letrain.vehicle.rail.impl;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import letrain.map.Dir;
import letrain.mvp.impl.Model;
import letrain.segments.BlockManager;
import letrain.segments.PathStep;
import letrain.segments.RailwayGraph;
import letrain.segments.Segment;
import letrain.track.Track;
import letrain.track.rail.RailTrack;
import letrain.vehicle.Tractor;
import letrain.vehicle.rail.Linker;
import letrain.vehicle.rail.RailIterator;

/**
 * Gestor de seguridad y cantones del tren.
 * Controla bloqueos exclusivos y paradas automáticas por invasión de segmento.
 */
public class TrainSafetyManager implements letrain.vehicle.rail.TrainSafetyManager {

    private final Train train;
    private Segment currentSegment;
    private Segment nextSegment;

    private boolean isWaitingForBlock = false; // Única variable de estado de parada de bloque
    private int savedTargetSpeed = -1;
    private transient boolean insideFindNextSegment = false;

    public TrainSafetyManager(Train train) {
        this.train = train;
    }

    /**
     * Calcula dinámicamente si el tren tiene permiso de movimiento.
     * Un tren manual siempre tiene permiso.
     */
    @Override
    public boolean hasPermissionToMove() {
        if (!train.isAutoMode()) {
            return true;
        }
        Linker head = (Linker) train.getDirectorLinker();
        if (head == null || head.getTrack() == null) {
            log.debug("Train {} hasPermissionToMove: false (head or track is null)", train.getId());
            return false;
        }
        if (isWaitingForBlock) {
            boolean perm = train.getSpeed() > 0;
            log.debug("Train {} hasPermissionToMove: {} (isWaitingForBlock=true, speed={})", train.getId(), perm, train.getSpeed());
            return perm;
        }
        return true;
    }

    @Override
    public Segment getCurrentSegment() {
        return currentSegment;
    }

    @Override
    public Segment getNextSegment() {
        return nextSegment;
    }

    @Override
    public boolean isWaitingForBlock() {
        return isWaitingForBlock;
    }

    @Override
    public void forceSegmentReset() {
        this.currentSegment = null;
        this.nextSegment = null;
    }

    /**
     * Forzado de parada de emergencia y desactivación del piloto automático
     * en caso de invasión o conflicto de segmento.
     */
    private void initiateBraking() {
        log.info("Train {} initiating braking. Target speed: 0. Saved target speed: {}", train.getId(), savedTargetSpeed == -1 ? (train.getDirectorLinker() != null ? train.getDirectorLinker().getTargetSpeed() : -1) : savedTargetSpeed);
        isWaitingForBlock = true;
        Tractor head = train.getDirectorLinker();
        if (head != null) {
            if (savedTargetSpeed == -1) {
                savedTargetSpeed = head.getTargetSpeed();
            }
            head.setTargetSpeed(0);
        }
    }

    @Override
    public void forceEmergencyStop() {
        if (train.isAutoMode()) {
            this.isWaitingForBlock = false; // Permitimos movimiento manual
            train.setAutoMode(false); // <--- Una sola llamada. El tren se encarga del resto.

            if (train.getDirectorLinker() != null) {
                train.getDirectorLinker().setTargetSpeed(0);
            }
            savedTargetSpeed = -1;
            log.warn("Train {} deactivated autopilot and stopped due to segment conflict.",
                    train.getId());
        }
    }

    /**
     * Reclama y reserva todos los segmentos ocupados físicamente por el tren.
     * Se llama al inicializar el mapa (Tabula Rasa) o al cargar partida.
     */
    @Override
    public void claimOccupiedSegments(Model model) {
        BlockManager bm = model.getBlockManager();
        RailwayGraph graph = model.getRailwayGraph();

        bm.releaseAll(train);

        Set<Segment> segmentsToClaim = new HashSet<>();
        for (Linker linker : train.getLinkers()) {
            if (linker.getTrack() instanceof RailTrack) {
                Segment segment = graph.getSegment((RailTrack) linker.getTrack());
                if (segment != null) {
                    segmentsToClaim.add(segment);
                }
            }
        }

        // Registrar presencia física en BlockManager
        for (Segment segment : segmentsToClaim) {
            if (!bm.tryLock(train, segment)) {
                // Si falla el bloqueo exclusivo, intentamos shunting (coexistencia permitida)
                if (bm.tryShuntingLock(train, segment)) {
                    // Conflicto físico al inicializar: si algún tren es automático, se para
                    forceEmergencyStop();
                    for (Train owner : bm.getOwners(segment)) {
                        if (owner != train) {
                            owner.getSafetyManager().forceEmergencyStop();
                        }
                    }
                }
            }
        }
        log.info("Train {} safety blocks reestablished.", train.getId());
    }

    /**
     * Reserva el segmento actual y el siguiente al iniciar marcha.
     */
    @Override
    public void acquireInitialLocks(Model model) {
        BlockManager bm = model.getBlockManager();
        RailwayGraph graph = model.getRailwayGraph();
        savedTargetSpeed = -1;

        Linker head = (Linker) train.getDirectorLinker();
        log.info("Train {} acquireInitialLocks starting", train.getId());
        if (head == null || head.getTrack() == null) {
            if (train.isAutoMode()) {
                throw new IllegalStateException("Critical Safety Error: Train " + train.getId()
                        + " is in AUTO mode but has no active locomotive or track assignment!");
            }
            isWaitingForBlock = false;
            log.info("Train {} acquireInitialLocks: head or track is null, exiting", train.getId());
            return;
        }

        RailTrack headTrack = (RailTrack) head.getTrack();
        currentSegment = graph.getSegment(headTrack);
        log.info("Train {} acquireInitialLocks: currentSegment is {}", train.getId(), currentSegment != null ? currentSegment.getId() : "null");
        if (currentSegment == null) {
            isWaitingForBlock = train.isAutoMode();
            log.info("Train {} acquireInitialLocks: currentSegment is null, isWaitingForBlock set to {}", train.getId(), isWaitingForBlock);
            return;
        }

        // Notify autopilot of the current segment to initialize route calculation and align forks BEFORE locking segments
        if (train.isAutoMode() && train.getAutopilot() != null) {
            log.info("Train {} acquireInitialLocks: notifying autopilot of segment {}", train.getId(), currentSegment.getId());
            train.getAutopilot().onSegmentEntered(currentSegment);
        }

        // 1. Asegurar posesión del segmento actual
        if (!bm.getOwnedSegments(train).contains(currentSegment)) {
            boolean currentLocked = bm.tryLock(train, currentSegment);
            log.info("Train {} acquireInitialLocks: trying to lock current segment {}: {}", train.getId(), currentSegment.getId(), currentLocked);
            if (!currentLocked) {
                // Hay otro tren: Si somos automáticos, parada de emergencia
                log.warn("Train {} acquireInitialLocks: failed lock on current segment {}. Forcing emergency stop.", train.getId(), currentSegment.getId());
                forceEmergencyStop();
                for (Train owner : bm.getOwners(currentSegment)) {
                    if (owner != train) {
                        log.warn("Train {} acquireInitialLocks: also forcing emergency stop on owner {}", train.getId(), owner.getId());
                        owner.getSafetyManager().forceEmergencyStop();
                    }
                }
                if (!train.isAutoMode()) {
                    isWaitingForBlock = false;
                    return;
                }
            }
        }

        // 2. Intentar bloquear el siguiente segmento
        nextSegment = findNextSegment(head, graph);
        log.info("Train {} acquireInitialLocks: nextSegment is {}", train.getId(), nextSegment != null ? nextSegment.getId() : "null");
        if (nextSegment == null || nextSegment.equals(currentSegment)) {
            isWaitingForBlock = false;
            log.info("Train {} acquireInitialLocks: nextSegment is null or equals current, isWaitingForBlock = false", train.getId());
        } else {
            boolean locked = bm.tryLock(train, nextSegment);
            log.info("Train {} acquireInitialLocks: tryLock nextSegment {} returned {}", train.getId(), nextSegment.getId(), locked);
            if (locked) {
                isWaitingForBlock = false;
                log.info("Train {} initially locked current segment {} and next segment {}",
                        train.getId(), currentSegment.getId(), nextSegment.getId());
            } else {
                if (train.isAutoMode()) {
                    initiateBraking();
                    log.info("Train {} (AUTO) failed to lock next segment {}. Initiating  braking.", train.getId(),
                            nextSegment.getId());
                } else {
                    isWaitingForBlock = false;
                }
            }
        }
    }

    /**
     * Entrada física a un nuevo segmento.
     */
    @Override
    public void onSegmentEntered(Model model, Segment newSegment) {
        BlockManager bm = model.getBlockManager();
        RailwayGraph graph = model.getRailwayGraph();
        Linker head = (Linker) train.getDirectorLinker();

        log.info("Train {} onSegmentEntered: newSegment={}", train.getId(), newSegment != null ? newSegment.getId() : "null");
        currentSegment = newSegment;
        isWaitingForBlock = false;
        savedTargetSpeed = -1;

        if (train.isAutoMode() && train.getAutopilot() != null) {
            log.info("Train {} onSegmentEntered: notifying autopilot", train.getId());
            train.getAutopilot().onSegmentEntered(newSegment);
        }

        // 1. Asegurar posesión del segmento al que acabamos de entrar
        if (!bm.getOwnedSegments(train).contains(currentSegment)) {
            boolean entryLocked = bm.tryLock(train, currentSegment);
            log.info("Train {} onSegmentEntered: tryLock current segment {} returned {}", train.getId(), currentSegment.getId(), entryLocked);
            if (!entryLocked) {
                // Invasión de segmento
                log.warn("Train {} onSegmentEntered: failed lock on current segment {}. Forcing emergency stop.", train.getId(), currentSegment.getId());
                forceEmergencyStop(); // Se para el invasor (si es automático)
                for (Train owner : bm.getOwners(currentSegment)) {
                    if (owner != train) {
                        log.warn("Train {} onSegmentEntered: also forcing emergency stop on owner {}", train.getId(), owner.getId());
                        owner.getSafetyManager().forceEmergencyStop(); // Se paran los automáticos invadidos
                    }
                }
                if (!train.isAutoMode()) {
                    isWaitingForBlock = false;
                }
            }
        }

        // 2. Intentar reservar el siguiente segmento
        nextSegment = findNextSegment(head, graph);
        log.info("Train {} onSegmentEntered: nextSegment is {}", train.getId(), nextSegment != null ? nextSegment.getId() : "null");
        if (nextSegment == null || nextSegment.equals(currentSegment)) {
            isWaitingForBlock = false;
            log.info("Train {} onSegmentEntered: nextSegment is null or equals current, isWaitingForBlock = false", train.getId());
        } else {
            boolean locked = bm.tryLock(train, nextSegment);
            log.info("Train {} onSegmentEntered: tryLock nextSegment {} returned {}", train.getId(), nextSegment.getId(), locked);
            if (locked) {
                log.info("Train {} locked next segment {} upon entry to {}",
                        train.getId(), nextSegment.getId(), currentSegment.getId());
                isWaitingForBlock = false;
            } else {
                if (train.isAutoMode()) {
                    initiateBraking();
                    log.info("Train {} (AUTO) next segment {} is blocked. Initiating braking.  ", train.getId(),
                            nextSegment.getId());
                } else {
                    isWaitingForBlock = false;
                }
            }
        }

        // 3. Liberar tramos que la cola ya ha abandonado
        releaseOldSegments(bm, graph);
    }

    @Override
    public void wakeUp(Model model) {
        log.info("Train {} wakeUp: isAutoMode={}, isWaitingForBlock={}, nextSegment={}",
                train.getId(), train.isAutoMode(), isWaitingForBlock, nextSegment != null ? nextSegment.getId() : "null");
        if (train.isAutoMode() && isWaitingForBlock && nextSegment != null) {
            BlockManager bm = model.getBlockManager();
            boolean locked = bm.tryLock(train, nextSegment);
            log.info("Train {} wakeUp: tryLock nextSegment {} returned {}", train.getId(), nextSegment.getId(), locked);
            if (locked) {
                log.info("Train {} (AUTO) successfully woke up and locked segment  {}", train.getId(),
                        nextSegment.getId());
                isWaitingForBlock = false;
                if (savedTargetSpeed != -1) {
                    Tractor head = train.getDirectorLinker();
                    if (head != null) {
                        log.info("Train {} wakeUp: restoring savedTargetSpeed {} to head", train.getId(), savedTargetSpeed);
                        head.setTargetSpeed(savedTargetSpeed);
                    }
                    savedTargetSpeed = -1;
                }
            }
        }
    }

    /**
     * Inversión de marcha.
     */
    @Override
    public void onReverse(Model model) {
        BlockManager bm = model.getBlockManager();
        RailwayGraph graph = model.getRailwayGraph();
        Linker head = (Linker) train.getDirectorLinker();

        if (head == null)
            return;

        if (nextSegment != null && !nextSegment.equals(currentSegment)) {
            bm.release(train, nextSegment);
        }

        nextSegment = findNextSegment(head, graph);
        isWaitingForBlock = false;
        savedTargetSpeed = -1;

        if (nextSegment == null || nextSegment.equals(currentSegment)) {
            isWaitingForBlock = false;
        } else {
            boolean locked = bm.tryLock(train, nextSegment);
            if (locked) {
                isWaitingForBlock = false;
            } else {
                if (train.isAutoMode()) {
                    initiateBraking();
                } else {
                    isWaitingForBlock = false;
                }
            }
        }
    }

    @Override
    public Segment findNextSegment(Linker head, RailwayGraph graph) {
        if (insideFindNextSegment) {
            return findNextSegmentTopological(head, graph);
        }
        insideFindNextSegment = true;
        try {
            Segment topological = findNextSegmentTopological(head, graph);
            letrain.itinerary.AutoPilot ap = train.getAutopilot();
            log.info("Train {} findNextSegment: ap={}, apMode={}, topological={}", train.getId(),
                    ap != null ? "present" : "null", ap != null ? ap.mode() : "N/A",
                    topological != null ? topological.getId() : "null");
            if (ap != null && ap.mode() == letrain.itinerary.AutoPilot.Mode.FOLLOWING) {
                // Consultamos la ruta real planificada del piloto automático
                List<Segment> route = ap.currentRoute();
                int index = route.indexOf(currentSegment);
                log.info("Train {} findNextSegment: ap route index={}, routeSize={}", train.getId(), index, route.size());
                // Auto-reverse disabled: no reverse command issued when physical next segment is missing.
                // Existing logic for when we have a valid index into the route
                if (index >= 0 && index + 1 < route.size()) {
                    Segment routeNext = route.get(index + 1);
                    if (routeNext != null && (topological == null || !topological.equals(routeNext))) {
                        log.warn("Train {} findNextSegment mismatch: route next is {}, but physical next is {}. Failsafe: using physical next to prevent crash!",
                                train.getId(), routeNext.getId(), topological != null ? topological.getId() : "null");
                        if (train.isAutoMode() && !train.isPendingReverse() && !isMovingTowardsRoute(currentSegment, topological, routeNext, graph)) {
                            log.info("Train {} auto-reversing due to route mismatch", train.getId());
                            train.executeCommand(letrain.itinerary.WaypointCommand.REVERSE);
                            // Reset flag and recompute topological segment after reversal
                            insideFindNextSegment = false;
                            return findNextSegmentTopological(head, graph);
                        }
                        return topological;
                    }
                    log.info("Train {} findNextSegment: returning next segment from route: {}", train.getId(), routeNext.getId());
                    return routeNext;
                }
            }
                // Auto-reverse disabled: start‑off‑track reverse logic removed.
            log.info("Train {} findNextSegment: returning next topological segment: {}", train.getId(), topological != null ? topological.getId() : "null");
            return topological;
        } finally {
            insideFindNextSegment = false;
        }
    }

    @Override
    public void releaseOldSegments(BlockManager bm, RailwayGraph graph) {
        Set<Segment> physicallyOccupied = new HashSet<>();
        for (Linker l : train.getLinkers()) {
            if (l.getTrack() instanceof RailTrack) {
                Segment s = graph.getSegment((RailTrack) l.getTrack());
                if (s != null)
                    physicallyOccupied.add(s);
            }
        }

        List<Segment> owned = bm.getOwnedSegments(train);
        for (Segment s : owned) {
            if (!physicallyOccupied.contains(s)) {
                Segment sNext = findNextSegmentTopological((Linker) train.getDirectorLinker(),
                        graph);
                if (sNext == null || !s.equals(sNext)) {
                    bm.release(train, s);
                    log.debug("Train {} released segment {}", train.getId(), s.getId());
                }
            }
        }
    }

    @Override
    public Segment findNextSegmentTopological(Linker head, RailwayGraph graph) {
        if (!(head.getTrack() instanceof RailTrack)) {
            log.info("Train {} findNextSegmentTopological: head track is not RailTrack", train.getId());
            return null;
        }
        RailTrack headTrack = (RailTrack) head.getTrack();
        Segment s = graph.getSegment(headTrack);
        if (s == null) {
            log.info("Train {} findNextSegmentTopological: graph.getSegment(headTrack) is null", train.getId());
            return null;
        }

        // 1. Obtener la dirección física de salida real del tren
        Dir exitDir = head.getRealDir();
        log.info("Train {} findNextSegmentTopological: start headTrack={}, exitDir={}", train.getId(), headTrack, exitDir);

        // 2. Avanzar virtualmente por las vías físicas en la dirección del movimiento
        // hasta encontrar un cantón diferente al actual (respeta desvíos y curvas).
        RailIterator it = new RailIterator(headTrack, exitDir);
        int maxIterations = 10000; // Evita bucles infinitos en circuitos cerrados puros
        while (it.advance() && maxIterations-- > 0) {
            Track t = it.getTrack();
            if (t instanceof RailTrack) {
                Segment nextS = graph.getSegment((RailTrack) t);
                if (nextS != null && !nextS.equals(s)) {
                    log.info("Train {} findNextSegmentTopological: found segment {} after iterating", train.getId(), nextS.getId());
                    return nextS;
                }
            }
        }
        log.info("Train {} findNextSegmentTopological: next segment not found topographically", train.getId());
        return null;
    }

    private boolean isMovingTowardsRoute(Segment current, Segment topological, Segment routeNext, RailwayGraph graph) {
        if (routeNext == null) return true;
        var steps = current.getSteps();
        if (steps == null) return true;

        PathStep end1 = steps.getFirst();
        PathStep end2 = steps.getSecond();

        boolean routeNextAtEnd1 = isNeighborAtEnd(end1, routeNext, graph);
        boolean routeNextAtEnd2 = isNeighborAtEnd(end2, routeNext, graph);

        boolean topoAtEnd1 = topological != null && isNeighborAtEnd(end1, topological, graph);
        boolean topoAtEnd2 = topological != null && isNeighborAtEnd(end2, topological, graph);

        if (routeNextAtEnd1 && topoAtEnd1) {
            return true; // Both route and physical next are at End1
        }
        if (routeNextAtEnd2 && topoAtEnd2) {
            return true; // Both route and physical next are at End2
        }

        // If physical next (topological) is null, we should only consider moving towards route
        // when there is also no expected route segment. If a routeNext exists, it's a mismatch.
        if (topological == null) {
            return routeNext == null;
        }

        return false;
    }

    private boolean isNeighborAtEnd(PathStep end, Segment target, RailwayGraph graph) {
        if (end == null || target == null) return false;
        for (PathStep next : graph.getNextSteps(end)) {
            Segment n = graph.getSegment(next);
            if (target.equals(n)) {
                return true;
            }
        }
        return false;
    }
}