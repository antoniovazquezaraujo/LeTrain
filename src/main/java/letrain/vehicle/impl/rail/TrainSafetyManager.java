package letrain.vehicle.impl.rail;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import letrain.map.Dir;
import letrain.mvp.impl.Model;
import letrain.segments.BlockManager;
import letrain.segments.RailwayGraph;
import letrain.segments.Segment;
import letrain.track.Track;
import letrain.track.rail.RailTrack;
import letrain.vehicle.impl.Linker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gestor de seguridad y cantones del tren.
 * Controla bloqueos exclusivos y paradas automáticas por invasión de segmento.
 */
public class TrainSafetyManager {
    private static final Logger log = LoggerFactory.getLogger(TrainSafetyManager.class);

    private final Train train;
    private Segment currentSegment;
    private Segment nextSegment;

    private boolean isWaitingForBlock = false; // Única variable de estado de parada de bloque

    public TrainSafetyManager(Train train) {
        this.train = train;
    }

    /**
     * Calcula dinámicamente si el tren tiene permiso de movimiento.
     * Un tren manual siempre tiene permiso.
     */
    public boolean hasPermissionToMove() {
        if (!train.isAutoMode()) {
            return true;
        }
        Linker head = (Linker) train.getDirectorLinker();
        if (head == null || head.getTrack() == null) {
            return false;
        }
        return !isWaitingForBlock;
    }

    public Segment getCurrentSegment() {
        return currentSegment;
    }

    public Segment getNextSegment() {
        return nextSegment;
    }

    public boolean isWaitingForBlock() {
        return isWaitingForBlock;
    }

    public void forceSegmentReset() {
        this.currentSegment = null;
        this.nextSegment = null;
    }

    /**
     * Forzado de parada de emergencia y desactivación del piloto automático
     * en caso de invasión o conflicto de segmento.
     */
    public void forceEmergencyStop() {
        if (train.isAutoMode()) {
            this.isWaitingForBlock = false; // Permitimos movimiento manual
            train.setAutoMode(false); // <--- Una sola llamada. El tren se encarga del resto.

            if (train.getDirectorLinker() != null) {
                train.getDirectorLinker().setTargetSpeed(0);
            }
            log.warn("Train {} deactivated autopilot and stopped due to segment conflict.",
                    train.getId());
        }
    }

    /**
     * Reclama y reserva todos los segmentos ocupados físicamente por el tren.
     * Se llama al inicializar el mapa (Tabula Rasa) o al cargar partida.
     */
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
    public void acquireInitialLocks(Model model) {
        BlockManager bm = model.getBlockManager();
        RailwayGraph graph = model.getRailwayGraph();

        Linker head = (Linker) train.getDirectorLinker();
        if (head == null || head.getTrack() == null) {
            if (train.isAutoMode()) {
                throw new IllegalStateException("Critical Safety Error: Train " + train.getId()
                        + " is in AUTO mode but has no active locomotive or track assignment!");
            }
            isWaitingForBlock = false;
            return;
        }

        RailTrack headTrack = (RailTrack) head.getTrack();
        currentSegment = graph.getSegment(headTrack);
        if (currentSegment == null) {
            isWaitingForBlock = train.isAutoMode();
            return;
        }

        // 1. Asegurar posesión del segmento actual
        if (!bm.getOwnedSegments(train).contains(currentSegment)) {
            if (!bm.tryLock(train, currentSegment)) {
                // Hay otro tren: Si somos automáticos, parada de emergencia
                forceEmergencyStop();
                for (Train owner : bm.getOwners(currentSegment)) {
                    if (owner != train) {
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
        if (nextSegment == null || nextSegment.equals(currentSegment)) {
            isWaitingForBlock = false;
        } else {
            boolean locked = bm.tryLock(train, nextSegment);
            if (locked) {
                isWaitingForBlock = false;
                log.info("Train {} initially locked current segment {} and next segment {}",
                        train.getId(), currentSegment.getId(), nextSegment.getId());
            } else {
                if (train.isAutoMode()) {
                    isWaitingForBlock = true;
                    if (train.getDirectorLinker() != null) {
                        train.getDirectorLinker().setTargetSpeed(0);
                    }
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
    public void onSegmentEntered(Model model, Segment newSegment) {
        BlockManager bm = model.getBlockManager();
        RailwayGraph graph = model.getRailwayGraph();
        Linker head = (Linker) train.getDirectorLinker();

        currentSegment = newSegment;
        isWaitingForBlock = false;

        // 1. Asegurar posesión del segmento al que acabamos de entrar
        if (!bm.getOwnedSegments(train).contains(currentSegment)) {
            if (!bm.tryLock(train, currentSegment)) {
                // Invasión de segmento
                forceEmergencyStop(); // Se para el invasor (si es automático)
                for (Train owner : bm.getOwners(currentSegment)) {
                    if (owner != train) {
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
        if (nextSegment == null || nextSegment.equals(currentSegment)) {
            isWaitingForBlock = false;
        } else {
            boolean locked = bm.tryLock(train, nextSegment);
            if (locked) {
                log.info("Train {} locked next segment {} upon entry to {}",
                        train.getId(), nextSegment.getId(), currentSegment.getId());
                isWaitingForBlock = false;
            } else {
                if (train.isAutoMode()) {
                    isWaitingForBlock = true;
                    if (train.getDirectorLinker() != null) {
                        train.getDirectorLinker().setTargetSpeed(0);
                    }
                    log.debug("Train {} (AUTO) next segment {} is blocked. Initiating braking.  ", train.getId(),
                            nextSegment.getId());
                } else {
                    isWaitingForBlock = false;
                }
            }
        }

        // 3. Liberar tramos que la cola ya ha abandonado
        releaseOldSegments(bm, graph);
    }

    /**
     * Despertar reactivo (Block Released).
     */
    public void wakeUp(Model model) {
        if (train.isAutoMode() && isWaitingForBlock && nextSegment != null) {
            BlockManager bm = model.getBlockManager();
            boolean locked = bm.tryLock(train, nextSegment);
            if (locked) {
                log.info("Train {} (AUTO) successfully woke up and locked segment  {}", train.getId(),
                        nextSegment.getId());
                isWaitingForBlock = false;
            }
        }
    }

    /**
     * Inversión de marcha.
     */
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

        if (nextSegment == null || nextSegment.equals(currentSegment)) {
            isWaitingForBlock = false;
        } else {
            boolean locked = bm.tryLock(train, nextSegment);
            if (locked) {
                isWaitingForBlock = false;
            } else {
                if (train.isAutoMode()) {
                    isWaitingForBlock = true;
                    if (train.getDirectorLinker() != null) {
                        train.getDirectorLinker().setTargetSpeed(0);
                    }
                } else {
                    isWaitingForBlock = false;
                }
            }
        }
    }

    private Segment findNextSegment(Linker head, RailwayGraph graph) {
        letrain.itinerary.AutoPilot ap = train.getAutopilot();
        if (ap != null && ap.mode() == letrain.itinerary.AutoPilot.Mode.FOLLOWING) {
            // Consultamos la ruta real planificada del piloto automático
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

    private Segment findNextSegmentTopological(Linker head, RailwayGraph graph) {
        if (!(head.getTrack() instanceof RailTrack)) {
            return null;
        }
        RailTrack headTrack = (RailTrack) head.getTrack();
        Segment s = graph.getSegment(headTrack);
        if (s == null) {
            return null;
        }

        // 1. Obtener la dirección física de salida real del tren
        Dir exitDir = head.getRealDir();

        // 2. Avanzar virtualmente por las vías físicas en la dirección del movimiento
        // hasta encontrar un cantón diferente al actual (respeta desvíos y curvas).
        letrain.vehicle.impl.RailIterator it = new letrain.vehicle.impl.RailIterator(headTrack, exitDir);
        int maxIterations = 10000; // Evita bucles infinitos en circuitos cerrados puros
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
}