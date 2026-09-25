package letrain.vehicle.rail.impl;

import java.util.HashSet;
import java.util.List;
import java.util.OptionalInt;
import java.util.Set;
import letrain.itinerary.Waypoint;
import letrain.map.Dir;
import letrain.mvp.Model;
import letrain.segments.BlockManager;
import letrain.segments.Port;
import letrain.segments.RailNode;
import letrain.segments.RailwayGraph;
import letrain.segments.Segment;
import letrain.track.Track;
import letrain.track.rail.RailTrack;
import letrain.utils.Pair;
import letrain.vehicle.rail.Linker;
import letrain.vehicle.rail.RailIterator;

/**
 * Gestor de seguridad y cantones del tren. Controla bloqueos exclusivos y paradas automáticas por
 * invasión de segmento.
 */
public class TrainSafetyManager implements letrain.vehicle.rail.TrainSafetyManager {

    private final Train train;
    private Segment currentSegment;
    private Segment nextSegment;

    private boolean isWaitingForBlock = false; // Única variable de estado de parada de bloque
    private transient boolean insideFindNextSegment = false;

    /** Guard de seguridad para caminatas topológicas en bucles puros (issue #633). */
    static final int MAX_TOPOLOGY_WALK_ITERATIONS = 10000;

    /**
     * Frenada programada hacia la frontera del cantón (issue #633): vías que quedan hasta la vía de
     * parada (la última antes del nodo frontera); -1 = no hay plan. Va decreciendo en
     * {@link #onRailAdvanced()}.
     */
    private int railsToStop = -1;

    /**
     * Tick de simulación en que se creó el plan. Si nace durante un avance (entrada en
     * desvío/segmento), ese mismo avance no debe descontar: {@code onRailAdvanced()} corre tras
     * {@code moveLinkers}, en el mismo tick.
     */
    private long planCreatedAtTick = -1;

    /**
     * La espera actual ya mandó frenar con {@code initiateBraking()} (hay velocidad que restaurar).
     */
    private boolean brakedForBlock = false;

    /** La curva de frenado capó el target durante el plan (hay velocidad deseada que restaurar). */
    private boolean targetCapped = false;

    /**
     * Instante (tick de simulación) en que la cabeza del tren atravesó la última curva. -1 indica
     * que no hay historial (tren parado/invertido/recién creado) y por tanto nunca descarrila por
     * intervalo de curvas. Único estado de la regla de descarrilamiento (issue #350).
     */
    private long lastCurveTick = -1;

    public TrainSafetyManager(Train train) {
        this.train = train;
    }

    /**
     * Comprueba si el tren tiene autorización de seguridad para moverse. En modo manual siempre
     * tiene permiso. En modo automático, si está en espera de bloque (isWaitingForBlock), permite
     * el avance por inercia mientras decelera (speed > 0) dentro del cantón actual, pero prohíbe
     * cruzar la frontera al siguiente cantón.
     */
    @Override
    public boolean hasPermissionToMove() {
        if (!train.isAutoMode()) {
            return true;
        }
        if (isWaitingForBlock && train.getSpeed() == 0) {
            return false;
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
        cancelScheduledStop();
        brakedForBlock = false;
    }

    @Override
    public void cancelBlockWait() {
        clearBlockWait();
    }

    @Override
    public void claimSharedPresence() {
        letrain.mvp.Model model = this.train.getModel();
        if (model == null || model.getBlockManager() == null || model.getRailwayGraph() == null) {
            return;
        }
        BlockManager bm = model.getBlockManager();
        RailwayGraph graph = model.getRailwayGraph();
        for (Linker linker : train.getLinkers()) {
            if (linker.getTrack() instanceof RailTrack track) {
                Segment segment = graph.getSegment(track);
                if (segment != null) {
                    bm.addOwner(train, segment);
                }
                letrain.track.Sensor sensor = track.getComponent();
                if (sensor != null && !train.getActiveSensors().contains(sensor)) {
                    train.notifyEnterSensor(sensor, true);
                }
            }
        }
    }

    @Override
    public void onEmergencyStop() {
        this.isWaitingForBlock = true;
        // The emergency brake replaces any boundary plan: it is stopping anyway.
        cancelScheduledStop();
    }

    /**
     * Reclama y reserva todos los segmentos ocupados físicamente por el tren. Se llama al
     * inicializar el mapa (Tabula Rasa) o al cargar partida.
     */
    @Override
    public void claimOccupiedSegments() {

        BlockManager bm = this.train.getModel().getBlockManager();
        RailwayGraph graph = this.train.getModel().getRailwayGraph();

        bm.releaseAll(train);

        Set<Segment> segmentsToClaim = new HashSet<>();
        for (Linker linker : train.getLinkers()) {
            if (linker.getTrack() instanceof RailTrack) {
                RailTrack track = (RailTrack) linker.getTrack();
                Segment segment = graph.getSegment(track);
                if (segment != null) {
                    segmentsToClaim.add(segment);
                }
                if (track.getComponent() instanceof letrain.track.Sensor) {
                    train.notifyEnterSensor((letrain.track.Sensor) track.getComponent(), true);
                }
            }
        }

        // Registrar presencia física en BlockManager
        for (Segment segment : segmentsToClaim) {
            if (!bm.tryLock(train, segment)) {
                // Conflicto físico al inicializar: si algún tren es automático, se para y pasa a
                // manual
                train.getMovementManager().forceEmergencyStop();
                train.setAutoMode(false);
                for (Train owner : bm.getOwners(segment)) {
                    if (owner != train) {
                        owner.getMovementManager().forceEmergencyStop();
                        owner.setAutoMode(false);
                    }
                }
            }
        }
        log.info("Train {} safety blocks reestablished.", train.getId());
    }

    /** Reserva el segmento actual y el siguiente al iniciar marcha. */
    @Override
    public void acquireInitialLocks() {
        BlockManager bm = this.train.getModel().getBlockManager();
        RailwayGraph graph = this.train.getModel().getRailwayGraph();
        Linker head = train.getPhysicalFront();
        log.info("Train {} acquireInitialLocks starting", train.getId());
        if (head == null || head.getTrack() == null) {
            if (train.isAutoMode()) {
                throw new IllegalStateException("Critical Safety Error: Train " + train.getId()
                        + " is in AUTO mode but has no active locomotive or track assignment!");
            }
            clearBlockWait();
            log.info("Train {} acquireInitialLocks: head or track is null, exiting", train.getId());
            return;
        }

        RailTrack headTrack = (RailTrack) head.getTrack();
        currentSegment = graph.getSegment(headTrack);
        log.info("Train {} acquireInitialLocks: currentSegment is {}", train.getId(),
                currentSegment != null ? currentSegment.getId() : "null");
        if (currentSegment == null) {
            isWaitingForBlock = train.isAutoMode();
            log.info(
                    "Train {} acquireInitialLocks: currentSegment is null, isWaitingForBlock set to {}",
                    train.getId(), isWaitingForBlock);
            return;
        }

        // 1. Asegurar posesión del segmento actual
        if (!bm.getOwnedSegments(train).contains(currentSegment)) {
            boolean currentLocked = bm.tryLock(train, currentSegment);
            log.info("Train {} acquireInitialLocks: trying to lock current segment {}: {}",
                    train.getId(), currentSegment.getId(), currentLocked);
            if (!currentLocked) {
                // Hay otro tren: Si somos automáticos, parada de emergencia
                log.warn(
                        "Train {} acquireInitialLocks: failed lock on current segment {}. Forcing emergency stop.",
                        train.getId(), currentSegment.getId());
                train.getMovementManager().forceEmergencyStop();
                train.setAutoMode(false);
                for (Train owner : bm.getOwners(currentSegment)) {
                    if (owner != train) {
                        log.warn(
                                "Train {} acquireInitialLocks: also forcing emergency stop on owner {}",
                                train.getId(), owner.getId());
                        owner.getMovementManager().forceEmergencyStop();
                        owner.setAutoMode(false);
                    }
                }
                if (!train.isAutoMode()) {
                    clearBlockWait();
                    return;
                }
            }
        }

        // 2. Intentar bloquear el siguiente segmento
        boolean shouldLockNext = true;
        if (train.isAutoMode()) {
            letrain.itinerary.AutoPilot ap = train.getAutopilot();
            if (ap.mode() == letrain.itinerary.AutoPilot.Mode.WAITING
                    || ap.mode() == letrain.itinerary.AutoPilot.Mode.IDLE) {
                shouldLockNext = false;
            }
        }
        // A train that is really stopping does not lock ahead. A target 0 with a deferred speed
        // (block/schedule wait gate) still wants to move later: keep the wait alive so the release
        // wakes it (ADR-022 phase 2f review M2).
        if (train.getDirectorLinker() != null && train.getDirectorLinker().getTargetSpeed() == 0
                && !train.hasSavedTargetSpeed()) {
            shouldLockNext = false;
        }

        if (!shouldLockNext) {
            nextSegment = null;
            clearBlockWait();
            log.info(
                    "Train {} acquireInitialLocks: skipping next segment lock (train is stopping or waiting), isWaitingForBlock = false",
                    train.getId());
        } else {
            nextSegment = findNextSegment(head, graph);
            log.info("Train {} acquireInitialLocks: nextSegment is {}", train.getId(),
                    nextSegment != null ? nextSegment.getId() : "null");
            if (nextSegment == null || nextSegment.equals(currentSegment)) {
                clearBlockWait();
                log.info(
                        "Train {} acquireInitialLocks: nextSegment is null or equals current, isWaitingForBlock = false",
                        train.getId());
            } else {
                boolean locked = bm.tryLock(train, nextSegment);
                if (!locked) {
                    locked = tryAlternativeSegment(this.train.getModel());
                }
                log.info("Train {} acquireInitialLocks: tryLock nextSegment {} returned {}",
                        train.getId(), nextSegment.getId(), locked);
                if (locked) {
                    clearBlockWait();
                    log.info("Train {} initially locked current segment {} and next segment {}",
                            train.getId(), currentSegment.getId(), nextSegment.getId());
                } else {
                    scheduleStopAtBoundary(nextSegment);
                }
            }
        }
    }

    @Override
    public void onForkEntered(letrain.track.rail.ForkRailTrack fork) {
        log.info("Train {} onForkEntered: fork={}", train.getId(), fork.getId());
        if (this.train.getModel() != null) {
            RailwayGraph graph = this.train.getModel().getRailwayGraph();
            if (graph != null) {
                Linker head = train.getPhysicalFront();
                if (head != null) {
                    Segment newSegment = findNextSegmentTopological(head, graph);
                    if (newSegment != null && !newSegment.equals(currentSegment)) {
                        onSegmentEntered(newSegment);
                        return;
                    }
                }
                Segment newSegment = graph.getSegment(fork);
                if (newSegment != null && !newSegment.equals(currentSegment)) {
                    onSegmentEntered(newSegment);
                    return;
                }
            }
        }
        if (nextSegment != null) {
            onSegmentEntered(nextSegment);
        }
    }

    @Override
    public void onSegmentExited(Segment oldSegment) {
        if (oldSegment == null || this.train.getModel() == null) {
            return;
        }
        BlockManager bm = this.train.getModel().getBlockManager();
        if (bm == null) {
            return;
        }
        if (!oldSegment.equals(nextSegment) && !oldSegment.equals(currentSegment)) {
            bm.release(train, oldSegment);
            log.info("Train {} onSegmentExited: released segment {}", train.getId(),
                    oldSegment.getId());
        }
    }

    @Override
    public void onForkExited(letrain.track.rail.ForkRailTrack fork) {
        if (this.train.getModel() == null) {
            return;
        }
        BlockManager bm = this.train.getModel().getBlockManager();
        RailwayGraph graph = this.train.getModel().getRailwayGraph();
        if (bm == null || graph == null) {
            return;
        }

        List<Segment> owned = bm.getOwnedSegments(train);
        if (owned.isEmpty()) {
            return;
        }

        RailNode node = null;
        for (Segment s : owned) {
            var ports = s.getPorts();
            if (ports != null) {
                if (ports.getFirst() != null && ports.getFirst().getNode().getTrack() == fork) {
                    node = ports.getFirst().getNode();
                    break;
                }
                if (ports.getSecond() != null && ports.getSecond().getNode().getTrack() == fork) {
                    node = ports.getSecond().getNode();
                    break;
                }
            }
        }

        if (node != null) {
            Linker tail = train.getPhysicalRear();
            if (tail != null && tail.getTrack() instanceof RailTrack) {
                Segment tailSegment = graph.getSegment((RailTrack) tail.getTrack());
                if (tailSegment != null) {
                    Port exitPort = null;
                    var tailPorts = tailSegment.getPorts();
                    if (tailPorts != null) {
                        if (tailPorts.getFirst() != null
                                && tailPorts.getFirst().getNode().equals(node)) {
                            exitPort = tailPorts.getFirst();
                        } else if (tailPorts.getSecond() != null
                                && tailPorts.getSecond().getNode().equals(node)) {
                            exitPort = tailPorts.getSecond();
                        }
                    }

                    if (exitPort != null) {
                        List<Port> otherPorts = new java.util.ArrayList<>();
                        for (Port p : node.getPorts()) {
                            if (!p.equals(exitPort)) {
                                otherPorts.add(p);
                            }
                        }

                        for (Segment s : owned) {
                            var sPorts = s.getPorts();
                            if (sPorts != null) {
                                Port p1 = sPorts.getFirst();
                                Port p2 = sPorts.getSecond();
                                if (otherPorts.contains(p1) || otherPorts.contains(p2)) {
                                    if (!s.equals(nextSegment) && !s.equals(currentSegment)) {
                                        bm.release(train, s);
                                        log.info(
                                                "Train {} onForkExited: released abandoned branch segment {} upon exiting fork {}",
                                                train.getId(), s.getId(), fork.getId());
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    /** Entrada física a un nuevo segmento. */
    @Override
    public void onSegmentEntered(Segment newSegment) {
        if (newSegment != null && newSegment.equals(currentSegment)) {
            return;
        }
        BlockManager bm = this.train.getModel().getBlockManager();
        RailwayGraph graph = this.train.getModel().getRailwayGraph();
        Linker head = train.getPhysicalFront();

        log.info("Train {} onSegmentEntered: newSegment={}", train.getId(),
                newSegment != null ? newSegment.getId() : "null");
        currentSegment = newSegment;
        clearBlockWait();

        // 1. Asegurar posesión del segmento al que acabamos de entrar
        if (!bm.getOwnedSegments(train).contains(currentSegment)) {
            boolean entryLocked = bm.tryLock(train, currentSegment);
            log.info("Train {} onSegmentEntered: tryLock current segment {} returned {}",
                    train.getId(), currentSegment.getId(), entryLocked);
            if (!entryLocked) {
                if (isShuntingMissionTarget(currentSegment)) {
                    // Shunting into the canton of our own detached part: share it instead of
                    // invading (ADR-022 phase 2f). The physical checks still stop the train before
                    // any vehicle.
                    bm.addOwner(train, currentSegment);
                    log.info(
                            "Train {} onSegmentEntered: shunting maneuver shares canton {} with its own detached part",
                            train.getId(), currentSegment.getId());
                } else {
                    log.warn(
                            "Train {} onSegmentEntered: failed lock on current segment {}. Invasión: iniciando frenada.",
                            train.getId(), currentSegment.getId());
                    cancelScheduledStop();
                    brakedForBlock = true;
                    // Direct brake: Train.brake() would save the current target, which may be the
                    // one the braking curve already capped, over the desired speed (issue #633).
                    Locomotive invasionDirector = directorLocomotive();
                    if (invasionDirector != null) {
                        invasionDirector.setTargetSpeedDirect(0);
                    } else {
                        train.getMovementManager().initiateBraking();
                    }
                    train.setPendingManualMode(true);
                    for (Train owner : bm.getOwners(currentSegment)) {
                        if (owner != train) {
                            log.warn("Train {} onSegmentEntered: frenando también el tren {}",
                                    train.getId(), owner.getId());
                            owner.getMovementManager().initiateBraking();
                        }
                    }
                    isWaitingForBlock = true;
                    return;
                }
            }
        }

        // 2. Intentar reservar el siguiente segmento
        boolean shouldLockNext = true;
        if (train.isAutoMode()) {
            letrain.itinerary.AutoPilot ap = train.getAutopilot();
            if (ap.mode() == letrain.itinerary.AutoPilot.Mode.WAITING
                    || ap.mode() == letrain.itinerary.AutoPilot.Mode.IDLE) {
                shouldLockNext = false;
            }
        }
        // A train that is really stopping does not lock ahead. A target 0 with a deferred speed
        // (block/schedule wait gate) still wants to move later: keep the wait alive so the release
        // wakes it (ADR-022 phase 2f review M2).
        if (train.getDirectorLinker() != null && train.getDirectorLinker().getTargetSpeed() == 0
                && !train.hasSavedTargetSpeed()) {
            shouldLockNext = false;
        }

        if (!shouldLockNext) {
            nextSegment = null;
            clearBlockWait();
            log.info(
                    "Train {} onSegmentEntered: skipping next segment lock (train is stopping or waiting), isWaitingForBlock = false",
                    train.getId());
        } else {
            nextSegment = findNextSegment(head, graph);
            log.info("Train {} onSegmentEntered: nextSegment is {}", train.getId(),
                    nextSegment != null ? nextSegment.getId() : "null");
            if (nextSegment == null || nextSegment.equals(currentSegment)) {
                clearBlockWait();
                log.info(
                        "Train {} onSegmentEntered: nextSegment is null or equals current, isWaitingForBlock = false",
                        train.getId());
            } else {
                boolean locked = bm.tryLock(train, nextSegment);
                if (!locked) {
                    locked = tryAlternativeSegment(this.train.getModel());
                }
                log.info("Train {} onSegmentEntered: tryLock nextSegment {} returned {}",
                        train.getId(), nextSegment.getId(), locked);
                if (locked) {
                    log.info("Train {} locked next segment {} upon entry to {}", train.getId(),
                            nextSegment.getId(), currentSegment.getId());
                    clearBlockWait();
                } else {
                    scheduleStopAtBoundary(nextSegment);
                }
            }
        }

        // 3. Release is now event-driven on fork exit
    }

    @Override
    public void onBlockReleased() {
        log.info("Train {} onBlockReleased: isAutoMode={}, isWaitingForBlock={}, nextSegment={}",
                train.getId(), train.isAutoMode(), isWaitingForBlock,
                nextSegment != null ? nextSegment.getId() : "null");
        if (train.isAutoMode()) {
            BlockManager bm = this.train.getModel().getBlockManager();
            RailwayGraph graph = this.train.getModel().getRailwayGraph();
            Linker head = train.getPhysicalFront();
            if (nextSegment == null && head != null && graph != null) {
                nextSegment = findNextSegment(head, graph);
            }
            if (nextSegment != null && !bm.getOwnedSegments(train).contains(nextSegment)) {
                boolean locked = bm.tryLock(train, nextSegment);
                if (!locked) {
                    locked = tryAlternativeSegment(this.train.getModel());
                }
                log.info("Train {} onBlockReleased: tryLock nextSegment {} returned {}",
                        train.getId(), nextSegment.getId(), locked);
                if (locked) {
                    log.info("Train {} (AUTO) successfully woke up and locked segment {}",
                            train.getId(), nextSegment.getId());
                    boolean restore = brakedForBlock || targetCapped || train.hasSavedTargetSpeed();
                    clearBlockWait();
                    if (restore) {
                        // Restore the speed desired before the plan, the newest order the wait gate
                        // stored, or a brake's saved speed. A wait that neither braked, capped nor
                        // deferred anything leaves the target untouched.
                        train.restoreSpeed();
                    }
                }
            } else if (isWaitingForBlock && nextSegment != null
                    && bm.getOwnedSegments(train).contains(nextSegment)) {
                boolean restore = brakedForBlock || targetCapped || train.hasSavedTargetSpeed();
                clearBlockWait();
                if (restore) {
                    train.restoreSpeed();
                }
            }
        }
    }

    /** Inversión de marcha. */
    @Override
    public void onReverse() {
        resetDerailmentHistory();
        BlockManager bm = this.train.getModel().getBlockManager();
        RailwayGraph graph = this.train.getModel().getRailwayGraph();
        Linker head = train.getPhysicalFront();

        if (head == null) {
            return;
        }

        if (nextSegment != null && !nextSegment.equals(currentSegment)) {
            bm.release(train, nextSegment);
        }

        nextSegment = findNextSegment(head, graph);
        clearBlockWait();

        if (nextSegment == null || nextSegment.equals(currentSegment)) {
            clearBlockWait();
        } else {
            boolean locked = bm.tryLock(train, nextSegment);
            if (locked) {
                clearBlockWait();
            } else {
                scheduleStopAtBoundary(nextSegment);
            }
        }
    }

    @Override
    public void onCurveCrossed(long simTick) {
        this.lastCurveTick = simTick;
    }

    @Override
    public long getLastCurveTick() {
        return lastCurveTick;
    }

    @Override
    public boolean shouldDerailOnCurve(long nowSimTick, int minCurveIntervalTicks) {
        return lastCurveTick >= 0 && (nowSimTick - lastCurveTick) < minCurveIntervalTicks;
    }

    @Override
    public void resetDerailmentHistory() {
        this.lastCurveTick = -1;
    }

    private boolean isForkOccupied(Segment from, Segment to, RailwayGraph graph) {
        if (from == null || to == null || graph == null) {
            return false;
        }
        var fromPorts = from.getPorts();
        var toPorts = to.getPorts();
        if (fromPorts != null && toPorts != null) {
            for (letrain.segments.Port pFrom : new letrain.segments.Port[] {fromPorts.getFirst(),
                    fromPorts.getSecond()}) {
                if (pFrom == null) {
                    continue;
                }
                for (letrain.segments.Port pTo : new letrain.segments.Port[] {toPorts.getFirst(),
                        toPorts.getSecond()}) {
                    if (pTo == null) {
                        continue;
                    }
                    if (pFrom.getNode().equals(pTo.getNode())) {
                        var node = pFrom.getNode();
                        if (node != null && node
                                .getTrack() instanceof letrain.track.rail.ForkRailTrack fork) {
                            return fork.getLinker() != null;
                        }
                    }
                }
            }
        }
        return false;
    }

    public Segment findNextSegment(Linker head, RailwayGraph graph) {
        if (insideFindNextSegment) {
            return findNextSegmentTopological(head, graph);
        }
        insideFindNextSegment = true;
        try {
            Segment topological = findNextSegmentTopological(head, graph);
            letrain.itinerary.AutoPilot ap = train.getAutopilot();
            log.info("Train {} findNextSegment: apMode={}, topological={}", train.getId(),
                    ap.mode(), topological != null ? topological.getId() : "null");
            if (ap.mode() == letrain.itinerary.AutoPilot.Mode.FOLLOWING
                    || ap.mode() == letrain.itinerary.AutoPilot.Mode.WAITING) {
                // Consultamos la ruta real planificada del piloto automático
                List<Segment> route = ap.currentRoute();
                int index = currentSegment == null ? -1 : route.indexOf(currentSegment);
                log.info("Train {} findNextSegment: ap route index={}, routeSize={}", train.getId(),
                        index, route.size());
                if (index >= 0 && index + 1 < route.size()) {
                    Segment routeNext = route.get(index + 1);
                    if (routeNext != null
                            && (topological == null || !topological.equals(routeNext))) {
                        // Si la aguja física NO está ocupada, el autopiloto la alineará a tiempo,
                        // por lo que no disparamos el failsafe de desviación física.
                        if (isForkOccupied(currentSegment, routeNext, graph)) {
                            log.warn(
                                    "Train {} findNextSegment mismatch: route next is {}, but physical next is {} and fork is occupied. Failsafe: using physical next to prevent crash!",
                                    train.getId(), routeNext.getId(),
                                    topological != null ? topological.getId() : "null");
                            return topological;
                        }
                    }
                    log.info("Train {} findNextSegment: returning next segment from route: {}",
                            train.getId(), routeNext.getId());
                    return routeNext;
                }
            }
            log.info("Train {} findNextSegment: returning next topological segment: {}",
                    train.getId(), topological != null ? topological.getId() : "null");
            return topological;
        } finally {
            insideFindNextSegment = false;
        }
    }

    @Override
    public Segment findNextSegmentTopological(Linker head, RailwayGraph graph) {
        if (!(head.getTrack() instanceof RailTrack)) {
            log.info("Train {} findNextSegmentTopological: head track is not RailTrack",
                    train.getId());
            return null;
        }
        RailTrack headTrack = (RailTrack) head.getTrack();
        Segment s = currentSegment;
        if (s == null) {
            s = graph.getSegment(headTrack);
        }
        if (s == null) {
            log.info("Train {} findNextSegmentTopological: graph.getSegment(headTrack) is null",
                    train.getId());
            return null;
        }

        // 1. Obtener la dirección física de salida real del tren
        Dir exitDir = head.getRealDir();
        log.info("Train {} findNextSegmentTopological: start headTrack={}, exitDir={}",
                train.getId(), headTrack, exitDir);

        // 2. Avanzar virtualmente por las vías físicas en la dirección del movimiento
        // hasta encontrar un cantón diferente al actual (respeta desvíos y curvas).
        RailIterator it = new RailIterator(headTrack, exitDir);
        int maxIterations = 10000; // Evita bucles infinitos en circuitos cerrados puros
        while (it.advance() && maxIterations-- > 0) {
            Track t = it.getTrack();
            if (t instanceof RailTrack) {
                RailTrack rt = (RailTrack) t;
                if (!graph.containsTrack(s, rt)) {
                    Segment nextS = graph.getSegment(rt);
                    if (nextS != null) {
                        log.info(
                                "Train {} findNextSegmentTopological: found segment {} after iterating",
                                train.getId(), nextS.getId());
                        return nextS;
                    }
                }
            }
        }
        log.info("Train {} findNextSegmentTopological: next segment not found topographically",
                train.getId());
        return null;
    }

    @Override
    public OptionalInt railsToBoundary() {
        RailwayGraph graph = train.getModel() != null ? train.getModel().getRailwayGraph() : null;
        return railsToBoundary(train.getPhysicalFront(), currentSegment, graph);
    }

    /**
     * Pure walker behind {@link #railsToBoundary()}: starting from {@code head}'s track and real
     * direction, advances rail by rail with a {@link RailIterator} (same pattern as
     * {@link #findNextSegmentTopological}) while the landed rail still belongs to {@code segment}.
     *
     * <p>
     * Returns the number of advances that stay inside the segment. {@code 0} means the next advance
     * already lands outside (the head is on the last rail of the segment in its travel direction;
     * the boundary node/fork rail counts as inside because the topology registers it in both
     * adjacent segments). {@code empty} means there is no reachable boundary: the head is not on a
     * rail, has no direction, there is no segment/graph, the way ends before leaving the segment
     * (dead end), or the {@link #MAX_TOPOLOGY_WALK_ITERATIONS} guard tripped (pure loop). When
     * {@code segment} is null the head's own segment is used as fallback.
     */
    public static OptionalInt railsToBoundary(Linker head, Segment segment, RailwayGraph graph) {
        if (head == null || graph == null || !(head.getTrack() instanceof RailTrack headTrack)) {
            return OptionalInt.empty();
        }
        Segment target = segment != null ? segment : graph.getSegment(headTrack);
        if (target == null) {
            return OptionalInt.empty();
        }
        Dir exitDir = head.getRealDir();
        if (exitDir == null) {
            return OptionalInt.empty();
        }

        RailIterator iterator = new RailIterator(headTrack, exitDir);
        int railsInside = 0;
        int guard = MAX_TOPOLOGY_WALK_ITERATIONS;
        while (guard-- > 0 && iterator.advance()) {
            Track next = iterator.getTrack();
            if (!(next instanceof RailTrack nextRail) || !graph.containsTrack(target, nextRail)) {
                return OptionalInt.of(railsInside);
            }
            railsInside++;
        }
        return OptionalInt.empty();
    }

    /**
     * La cabeza ha avanzado una vía: descuenta el plan, aplica la curva de frenado (capa el target
     * a la velocidad que aún cabe en las vías que quedan) y frena cuando ya no cabe más (issue
     * #633). Una sola llamada por vía y tren: solo la locomotora directora mueve el tren.
     */
    @Override
    public void onRailAdvanced() {
        if (railsToStop < 0) {
            return;
        }
        if (train.getSimulationTick() == planCreatedAtTick) {
            // The plan was created by the advance that landed the head on this rail (segment/fork
            // entry): that move must not consume a rail of the countdown.
            return;
        }
        railsToStop--;
        applyBrakingCurve();
        if (brakingRailsFromCurrentState() > railsToStop) {
            railsToStop = -1;
            engageBoundaryBrake();
        }
    }

    /** Distancia de frenada exacta desde el estado actual (o la de crucero si no hay directora). */
    private int brakingRailsFromCurrentState() {
        Locomotive director = directorLocomotive();
        return director != null ? director.brakingRailsFromCurrentState()
                : Locomotive.brakingRails(train.getSpeed());
    }

    /**
     * Curva de frenado (issue #633): mientras hay plan de frontera, capa el target de la locomotora
     * directora a la velocidad máxima que todavía puede parar dentro de las vías que quedan
     * ({@link Locomotive#maxSpeedForRails}). Solo baja el target: un tren que venía acelerando no
     * se escapa de su distancia de frenada. La velocidad deseada queda guardada para restaurarla al
     * liberarse el bloque.
     */
    private void applyBrakingCurve() {
        Locomotive director = directorLocomotive();
        if (director == null || railsToStop < 0) {
            return;
        }
        int safeSpeed = Locomotive.maxSpeedForRails(railsToStop);
        if (director.getTargetSpeed() > safeSpeed) {
            director.setTargetSpeedDirect(safeSpeed);
            targetCapped = true;
            log.info(
                    "Train {} (AUTO) braking curve caps the target to {} ({} rails left to the boundary stop)",
                    train.getId(), safeSpeed, railsToStop);
        }
    }

    /**
     * Frena para el plan de frontera sin pisar la velocidad deseada guardada: usa el setter directo
     * para no pasar por el gate de espera ni dejar que {@code Train.brake()} guarde la velocidad ya
     * capada (issue #633).
     */
    private void engageBoundaryBrake() {
        brakedForBlock = true;
        Locomotive director = directorLocomotive();
        if (director != null) {
            director.setTargetSpeedDirect(0);
        }
        log.info("Train {} (AUTO) boundary brake engaged at the end of the segment", train.getId());
    }

    /**
     * Fallo al bloquear el siguiente cantón (issue #633): decide cuándo frenar para que el tren se
     * detenga al final de su cantón actual en vez de nada más entrar en él.
     *
     * <ul>
     * <li>Tren manual: no espera bloques (como hasta ahora).
     * <li>Tren parado ({@code speed == 0}): no hay nada que programar, espera donde está.
     * <li>Rodando con frontera conocida y distancia suficiente: programa la frenada para cuando
     * queden {@code brakingRails(speed)} vías; hasta entonces rueda sin frenar.
     * <li>Rodando sin distancia suficiente o con frontera desconocida: frena ya (comportamiento
     * anterior). No hay muro artificial: si no da tiempo, el tren entra en el bloque ocupado y
     * actúa el manejo de invasión/colisión existente.
     * </ul>
     *
     * <p>
     * La última vía de un cantón es su nodo frontera (desvío/extremo), que la topología comparte
     * con el cantón siguiente: pisarla dispara {@code onForkEntered -> onSegmentEntered(next)} y,
     * si ese bloque sigue bloqueado, la invasión. Por eso el objetivo es parar <b>una vía
     * antes</b>: la última vía completamente dentro del cantón.
     */
    private void scheduleStopAtBoundary(Segment blockedSegment) {
        if (!train.isAutoMode()) {
            clearBlockWait();
            return;
        }
        if (isShuntingMissionTarget(blockedSegment)) {
            // ADR-022 phase 2f: a waypoint maneuver may enter the canton where its destination is
            // (the part of its own train it must reach). The physical collision checks still guard
            // the movement; the mission stops at its target.
            log.info(
                    "Train {} (AUTO) shunting maneuver may enter blocked segment {}: it is the mission target",
                    train.getId(), segmentId(blockedSegment));
            clearBlockWait();
            return;
        }
        isWaitingForBlock = true;
        int speed = train.getSpeed();
        if (speed == 0) {
            // Parado: se queda esperando donde está (frenar ya era no-op y sigue siéndolo).
            log.info("Train {} (AUTO) next segment {} blocked while stopped: waiting in place",
                    train.getId(), segmentId(blockedSegment));
            brakeNowForBlock();
            return;
        }
        OptionalInt boundary = railsToBoundary();
        if (boundary.isEmpty()) {
            log.warn(
                    "Train {} (AUTO) next segment {} blocked and rails to boundary unknown: braking now",
                    train.getId(), segmentId(blockedSegment));
            brakeNowForBlock();
            return;
        }
        int brakingRails = Locomotive.brakingRails(speed);
        int railsToBoundary = boundary.getAsInt();
        if (railsToBoundary <= brakingRails) {
            log.info(
                    "Train {} (AUTO) next segment {} blocked: {} rails to the boundary node <= {} braking rails (speed {}), braking now",
                    train.getId(), segmentId(blockedSegment), railsToBoundary, brakingRails, speed);
            brakeNowForBlock();
            return;
        }
        // Plan: the train must halt on the last rail before the boundary node, B-1 advances ahead.
        // The countdown is decremented after each advance and the advance that reaches the safe
        // limit is already the first braking move, so the halt lands exactly there. The live
        // braking curve caps the target while rolling so an accelerating train cannot outrun its
        // stopping distance (issue #633).
        rememberDesiredTarget();
        railsToStop = railsToBoundary - 1;
        planCreatedAtTick = train.getSimulationTick();
        applyBrakingCurve();
        log.info(
                "Train {} (AUTO) next segment {} blocked: boundary stop planned {} rails ahead ({} to the boundary node)",
                train.getId(), segmentId(blockedSegment), railsToStop, railsToBoundary);
    }

    /**
     * True when the blocked segment is the destination of the active shunting mission and every
     * other occupant is our own detached part (a train with no locomotive). An unrelated train must
     * keep the block exclusivity, so the maneuver waits for it instead of invading (ADR-022 phase
     * 2f).
     *
     * <p>
     * The itinerary maneuver is the choreographed case. The coupling approach ({@code stop on
     * contact}, issue #645) is also accepted as a loose order: performing the run-around by hand
     * from the console/script is the same shunting movement, and the safety criterion (no
     * locomotive among the other occupants) does not depend on the order's origin.
     */
    private boolean isShuntingMissionTarget(Segment segment) {
        if (segment == null || train.getAutopilot() == null) {
            return false;
        }
        letrain.itinerary.AutoPilot autopilot = train.getAutopilot();
        letrain.itinerary.TrainMission mission = autopilot.mission().orElse(null);
        if (mission == null || !mission.isActive()) {
            return false;
        }
        if (!mission.isItineraryManeuver()
                && mission.kind() != letrain.itinerary.TrainMission.Kind.ON_CONTACT) {
            return false;
        }
        if (!autopilot.missionTargetSegment().map(segment::equals).orElse(false)) {
            return false;
        }
        BlockManager bm = train.getModel() != null ? train.getModel().getBlockManager() : null;
        if (bm == null) {
            return false;
        }
        for (Train owner : bm.getOwners(segment)) {
            if (owner != train && !owner.getLocomotives().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Frena ya para la espera actual y recuerda que hay velocidad que restaurar al liberarse. */
    private void brakeNowForBlock() {
        brakedForBlock = true;
        train.getMovementManager().initiateBraking();
    }

    /**
     * Guarda la velocidad deseada del tren para restaurarla al liberarse el bloque. La curva solo
     * baja el target aplicado, así que este valor no se pisa mientras dure el plan.
     */
    private void rememberDesiredTarget() {
        Locomotive director = directorLocomotive();
        if (director != null && director.getTargetSpeed() > 0) {
            train.setSavedTargetSpeed(director.getTargetSpeed());
        }
    }

    /** Cancela el plan de frenada pendiente (el tren puede seguir rodando con su velocidad). */
    private void cancelScheduledStop() {
        railsToStop = -1;
        planCreatedAtTick = -1;
    }

    /**
     * Termina la espera de bloque: cancela cualquier plan de frenada y olvida la frenada aplicada.
     * No restaura velocidad por sí solo: los caminos que continúan la marcha lo hacen aparte.
     */
    private void clearBlockWait() {
        cancelScheduledStop();
        brakedForBlock = false;
        targetCapped = false;
        isWaitingForBlock = false;
    }

    private Locomotive directorLocomotive() {
        return train.getDirectorLinker() instanceof Locomotive locomotive ? locomotive : null;
    }

    private static String segmentId(Segment segment) {
        return segment != null ? segment.getId() : "null";
    }

    private boolean tryAlternativeSegment(Model model) {
        if (!train.isAutoMode() || nextSegment == null) {
            return false;
        }
        if (segmentHasPendingWaypoints(nextSegment)) {
            log.info(
                    "Train {} tryAlternativeSegment: nextSegment {} has pending waypoints, cannot bypass.",
                    train.getId(), nextSegment.getId());
            return false;
        }
        RailwayGraph graph = this.train.getModel().getRailwayGraph();
        Pair<Port, Port> ports = nextSegment.getPorts();
        if (ports == null || ports.getFirst() == null || ports.getSecond() == null) {
            return false;
        }
        RailNode node1 = ports.getFirst().getNode();
        RailNode node2 = ports.getSecond().getNode();
        if (node1 == null || node2 == null) {
            return false;
        }

        Segment sAlt = null;
        for (Port port : node1.getPorts()) {
            Segment s = graph.getSegment(port);
            if (s == null || s.equals(nextSegment)) {
                continue;
            }
            Pair<Port, Port> altPorts = s.getPorts();
            if (altPorts == null || altPorts.getFirst() == null || altPorts.getSecond() == null) {
                continue;
            }
            RailNode altNode1 = altPorts.getFirst().getNode();
            RailNode altNode2 = altPorts.getSecond().getNode();
            if ((altNode1.equals(node1) && altNode2.equals(node2))
                    || (altNode1.equals(node2) && altNode2.equals(node1))) {
                sAlt = s;
                break;
            }
        }

        if (sAlt == null) {
            log.info("Train {} tryAlternativeSegment: no parallel alternative segment found for {}",
                    train.getId(), nextSegment.getId());
            return false;
        }

        BlockManager bm = model.getBlockManager();
        boolean locked = bm.tryLock(train, sAlt);
        if (locked) {
            log.info(
                    "Train {} successfully locked alternative segment {} instead of blocked segment {}",
                    train.getId(), sAlt.getId(), nextSegment.getId());
            Segment oldNext = nextSegment;
            nextSegment = sAlt;
            letrain.itinerary.AutoPilot ap = train.getAutopilot();
            ap.replaceRouteSegment(oldNext, sAlt);
            return true;
        }

        log.info("Train {} tryAlternativeSegment: alternative segment {} is also blocked.",
                train.getId(), sAlt.getId());
        return false;
    }

    private boolean segmentHasPendingWaypoints(Segment segment) {
        letrain.itinerary.AutoPilot ap = train.getAutopilot();
        java.util.Optional<letrain.itinerary.Itinerary> itinOpt = ap.itinerary();
        if (itinOpt.isEmpty()) {
            return false;
        }
        letrain.itinerary.Itinerary itin = itinOpt.get();
        int currentIndex = ap.currentWaypointIndex();
        List<letrain.itinerary.Waypoint> waypoints = itin.waypoints();
        for (int i = currentIndex; i < waypoints.size(); i++) {
            letrain.itinerary.Waypoint wp = waypoints.get(i);
            Segment wpSeg = getWaypointSegment(wp);
            if (segment.equals(wpSeg)) {
                return true;
            }
        }
        return false;
    }

    private Segment getWaypointSegment(Waypoint wp) {
        RailwayGraph graph = this.train.getModel().getRailwayGraph();
        if (graph == null) {
            return null;
        }
        letrain.map.Point pos = null;
        switch (wp.type()) {
            case STATION:
                letrain.track.Station st = this.train.getModel().getStation(wp.targetId());
                if (st != null) {
                    pos = st.getPosition();
                }
                break;
            case SENSOR:
                letrain.track.Sensor sensor = this.train.getModel().getSensor(wp.targetId());
                if (sensor != null) {
                    pos = sensor.getPosition();
                }
                break;
        }
        if (pos == null) {
            return null;
        }
        letrain.track.rail.RailTrack track = this.train.getModel().getRailMap().getTrackAt(pos);
        return track != null ? graph.getSegment(track) : null;
    }
}
