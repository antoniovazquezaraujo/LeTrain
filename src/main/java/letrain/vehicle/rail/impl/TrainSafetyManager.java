package letrain.vehicle.rail.impl;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import letrain.itinerary.Waypoint;
import letrain.map.Dir;
import letrain.mvp.Model;
import letrain.segments.BlockManager;
import letrain.segments.Port;
import letrain.segments.PortType;
import letrain.segments.RailNode;
import letrain.segments.RailwayGraph;
import letrain.segments.Segment;
import letrain.track.Track;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;
import letrain.utils.Pair;
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
        Linker head = train.getPhysicalFront();
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

    @Override
    public void onEmergencyStop() {
        this.isWaitingForBlock = false;
    }

    /**
     * Reclama y reserva todos los segmentos ocupados físicamente por el tren.
     * Se llama al inicializar el mapa (Tabula Rasa) o al cargar partida.
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
                if (track.getSensor() != null) {
                    train.notifyEnterSensor(track.getSensor(), true);
                }
            }
        }

        // Registrar presencia física en BlockManager
        for (Segment segment : segmentsToClaim) {
            if (!bm.tryLock(train, segment)) {
                // Si falla el bloqueo exclusivo, intentamos shunting (coexistencia permitida)
                if (bm.tryShuntingLock(train, segment)) {
                    // Conflicto físico al inicializar: si algún tren es automático, se para
                    train.movementManager.forceEmergencyStop();
                    for (Train owner : bm.getOwners(segment)) {
                        if (owner != train) {
                            owner.movementManager.forceEmergencyStop();
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

        // 1. Asegurar posesión del segmento actual
        if (!bm.getOwnedSegments(train).contains(currentSegment)) {
            boolean currentLocked = bm.tryLock(train, currentSegment);
            log.info("Train {} acquireInitialLocks: trying to lock current segment {}: {}", train.getId(), currentSegment.getId(), currentLocked);
            if (!currentLocked) {
                // Hay otro tren: Si somos automáticos, parada de emergencia
                log.warn("Train {} acquireInitialLocks: failed lock on current segment {}. Forcing emergency stop.", train.getId(), currentSegment.getId());
                train.movementManager.forceEmergencyStop();
                for (Train owner : bm.getOwners(currentSegment)) {
                    if (owner != train) {
                        log.warn("Train {} acquireInitialLocks: also forcing emergency stop on owner {}", train.getId(), owner.getId());
                        owner.movementManager.forceEmergencyStop();
                    }
                }
                if (!train.isAutoMode()) {
                    isWaitingForBlock = false;
                    return;
                }
            }
        }

        // 2. Intentar bloquear el siguiente segmento
        boolean shouldLockNext = true;
        if (train.isAutoMode()) {
            letrain.itinerary.AutoPilot ap = train.getAutopilot();
            if (ap.mode() == letrain.itinerary.AutoPilot.Mode.WAITING || ap.mode() == letrain.itinerary.AutoPilot.Mode.IDLE) {
                shouldLockNext = false;
            }
        }
        if (train.getDirectorLinker() != null && train.getDirectorLinker().getTargetSpeed() == 0) {
            shouldLockNext = false;
        }

        if (!shouldLockNext) {
            nextSegment = null;
            isWaitingForBlock = false;
            log.info("Train {} acquireInitialLocks: skipping next segment lock (train is stopping or waiting), isWaitingForBlock = false", train.getId());
        } else {
            nextSegment = findNextSegment(head, graph);
            log.info("Train {} acquireInitialLocks: nextSegment is {}", train.getId(), nextSegment != null ? nextSegment.getId() : "null");
            if (nextSegment == null || nextSegment.equals(currentSegment)) {
                isWaitingForBlock = false;
                log.info("Train {} acquireInitialLocks: nextSegment is null or equals current, isWaitingForBlock = false", train.getId());
            } else {
                boolean locked = bm.tryLock(train, nextSegment);
                if (!locked) {
                    locked = tryAlternativeSegment(this.train.getModel());
                }
                log.info("Train {} acquireInitialLocks: tryLock nextSegment {} returned {}", train.getId(), nextSegment.getId(), locked);
                if (locked) {
                    isWaitingForBlock = false;
                    log.info("Train {} initially locked current segment {} and next segment {}",
                            train.getId(), currentSegment.getId(), nextSegment.getId());
                } else {
                    if (train.isAutoMode()) {
                        train.movementManager.initiateBraking();
                        isWaitingForBlock = true;
                        log.info("Train {} (AUTO) failed to lock next segment {}. isWaitingForBlock=true.", train.getId(),
                                nextSegment.getId());
                    } else {
                        isWaitingForBlock = false;
                    }
                }
            }
        }
    }



    @Override
    public void onForkEntered(letrain.track.rail.ForkRailTrack fork) {
        log.info("Train {} onForkEntered: fork={}", train.getId(), fork.getId());
        if (nextSegment != null) {
            onSegmentEntered(nextSegment);
        } else {
            if (this.train.getModel() != null) {
                RailwayGraph graph = this.train.getModel().getRailwayGraph();
                if (graph != null) {
                    Segment newSegment = graph.getSegment(fork);
                    if (newSegment != null && !newSegment.equals(currentSegment)) {
                        onSegmentEntered(newSegment);
                    }
                }
            }
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

        if (node == null) {
            Linker tail = train.getLinkers().getLast();
            if (tail != null && tail.getTrack() instanceof RailTrack) {
                Segment tailSegment = graph.getSegment((RailTrack) tail.getTrack());
                if (tailSegment != null) {
                    var tailPorts = tailSegment.getPorts();
                    if (tailPorts != null) {
                        if (tailPorts.getFirst() != null && tailPorts.getFirst().getNode().getTrack() == fork) {
                            node = tailPorts.getFirst().getNode();
                        } else if (tailPorts.getSecond() != null && tailPorts.getSecond().getNode().getTrack() == fork) {
                            node = tailPorts.getSecond().getNode();
                        }
                    }
                }
            }
        }

        if (node == null) {
            log.warn("Train {} onForkExited: could not find RailNode for fork {}", train.getId(), fork.getId());
            return;
        }

        Linker tail = train.getLinkers().getLast();
        if (tail == null || !(tail.getTrack() instanceof RailTrack)) {
            return;
        }
        Segment tailSegment = graph.getSegment((RailTrack) tail.getTrack());
        if (tailSegment == null) {
            return;
        }

        Port exitPort = null;
        var tailPorts = tailSegment.getPorts();
        if (tailPorts != null) {
            if (tailPorts.getFirst() != null && tailPorts.getFirst().getNode().equals(node)) {
                exitPort = tailPorts.getFirst();
            } else if (tailPorts.getSecond() != null && tailPorts.getSecond().getNode().equals(node)) {
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
                        bm.release(train, s);
                        log.info("Train {} released abandoned segment {} upon exiting fork {}", train.getId(), s.getId(), fork.getId());
                    }
                }
            }
        } else {
            Set<Segment> physicallyOccupied = new HashSet<>();
            for (Linker l : train.getLinkers()) {
                if (l.getTrack() instanceof RailTrack) {
                    Segment s = graph.getSegment((RailTrack) l.getTrack());
                    if (s != null) {
                        physicallyOccupied.add(s);
                    }
                }
            }

            for (Segment s : owned) {
                if (!physicallyOccupied.contains(s) && (nextSegment == null || !s.equals(nextSegment))) {
                    bm.release(train, s);
                    log.info("Train {} released segment {} (fallback exit release)", train.getId(), s.getId());
                }
            }
        }
    }

    /**
     * Entrada física a un nuevo segmento.
     */
    @Override
    public void onSegmentEntered(Segment newSegment) {
        if (newSegment != null && newSegment.equals(currentSegment)) {
            return;
        }
        BlockManager bm = this.train.getModel().getBlockManager();
        RailwayGraph graph = this.train.getModel().getRailwayGraph();
        Linker head = train.getPhysicalFront();

        log.info("Train {} onSegmentEntered: newSegment={}", train.getId(), newSegment != null ? newSegment.getId() : "null");
        currentSegment = newSegment;
        isWaitingForBlock = false;

        // 1. Asegurar posesión del segmento al que acabamos de entrar
        if (!bm.getOwnedSegments(train).contains(currentSegment)) {
            boolean entryLocked = bm.tryLock(train, currentSegment);
            log.info("Train {} onSegmentEntered: tryLock current segment {} returned {}", train.getId(), currentSegment.getId(), entryLocked);
            if (!entryLocked) {
                // Invasión de segmento
                log.warn("Train {} onSegmentEntered: failed lock on current segment {}. Forcing emergency stop.", train.getId(), currentSegment.getId());
                // Se para el invasor (si es automático)
                train.movementManager.forceEmergencyStop();
                for (Train owner : bm.getOwners(currentSegment)) {
                    if (owner != train) {
                        log.warn("Train {} onSegmentEntered: also forcing emergency stop on owner {}", train.getId(), owner.getId());
                        // Se paran los automáticos invadidos
                        owner.movementManager.forceEmergencyStop();
                    }
                }
                if (!train.isAutoMode()) {
                    isWaitingForBlock = false;
                }
            }
        }

        // 2. Intentar reservar el siguiente segmento
        boolean shouldLockNext = true;
        if (train.isAutoMode()) {
            letrain.itinerary.AutoPilot ap = train.getAutopilot();
            if (ap.mode() == letrain.itinerary.AutoPilot.Mode.WAITING || ap.mode() == letrain.itinerary.AutoPilot.Mode.IDLE) {
                shouldLockNext = false;
            }
        }
        if (train.getDirectorLinker() != null && train.getDirectorLinker().getTargetSpeed() == 0) {
            shouldLockNext = false;
        }

        if (!shouldLockNext) {
            nextSegment = null;
            isWaitingForBlock = false;
            log.info("Train {} onSegmentEntered: skipping next segment lock (train is stopping or waiting), isWaitingForBlock = false", train.getId());
        } else {
            nextSegment = findNextSegment(head, graph);
            log.info("Train {} onSegmentEntered: nextSegment is {}", train.getId(), nextSegment != null ? nextSegment.getId() : "null");
            if (nextSegment == null || nextSegment.equals(currentSegment)) {
                isWaitingForBlock = false;
                log.info("Train {} onSegmentEntered: nextSegment is null or equals current, isWaitingForBlock = false", train.getId());
            } else {
                boolean locked = bm.tryLock(train, nextSegment);
                if (!locked) {
                    locked = tryAlternativeSegment(this.train.getModel());
                }
                log.info("Train {} onSegmentEntered: tryLock nextSegment {} returned {}", train.getId(), nextSegment.getId(), locked);
                if (locked) {
                    log.info("Train {} locked next segment {} upon entry to {}",
                            train.getId(), nextSegment.getId(), currentSegment.getId());
                    isWaitingForBlock = false;
                } else {
                    if (train.isAutoMode()) {
                        train.movementManager.initiateBraking();
                        isWaitingForBlock = true;
                        log.info("Train {} (AUTO) next segment {} is blocked. isWaitingForBlock=true.", train.getId(),
                                nextSegment.getId());
                    } else {
                        isWaitingForBlock = false;
                    }
                }
            }
        }

        // 3. Release is now event-driven on fork exit
    }

    @Override
    public void onBlockReleased() {
        log.info("Train {} onBlockReleased: isAutoMode={}, isWaitingForBlock={}, nextSegment={}",
                train.getId(), train.isAutoMode(), isWaitingForBlock, nextSegment != null ? nextSegment.getId() : "null");
        if (train.isAutoMode() && isWaitingForBlock && nextSegment != null) {
            BlockManager bm = this.train.getModel().getBlockManager();
            boolean locked = bm.tryLock(train, nextSegment);
            if (!locked) {
                locked = tryAlternativeSegment(this.train.getModel());
            }
            log.info("Train {} onBlockReleased: tryLock nextSegment {} returned {}", train.getId(), nextSegment.getId(), locked);
            if (locked) {
                log.info("Train {} (AUTO) successfully woke up and locked segment  {}", train.getId(),
                        nextSegment.getId());
                isWaitingForBlock = false;
                train.restoreSpeed();
            }
        }
    }

    /**
     * Inversión de marcha.
     */
    @Override
    public void onReverse() {
        BlockManager bm = this.train.getModel().getBlockManager();
        RailwayGraph graph = this.train.getModel().getRailwayGraph();
        Linker head = train.getPhysicalFront();

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
                    train.movementManager.initiateBraking();
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
            log.info("Train {} findNextSegment: apMode={}, topological={}", train.getId(),
                    ap.mode(), topological != null ? topological.getId() : "null");
            if (ap.mode() == letrain.itinerary.AutoPilot.Mode.FOLLOWING || ap.mode() == letrain.itinerary.AutoPilot.Mode.WAITING) {
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
    public Segment findNextSegmentTopological(Linker head, RailwayGraph graph) {
        if (!(head.getTrack() instanceof RailTrack)) {
            log.info("Train {} findNextSegmentTopological: head track is not RailTrack", train.getId());
            return null;
        }
        RailTrack headTrack = (RailTrack) head.getTrack();
        Segment s = currentSegment;
        if (s == null) {
            s = graph.getSegment(headTrack);
        }
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
                RailTrack rt = (RailTrack) t;
                if (!graph.containsTrack(s, rt)) {
                    Segment nextS = graph.getSegment(rt);
                    if (nextS != null) {
                        log.info("Train {} findNextSegmentTopological: found segment {} after iterating", train.getId(), nextS.getId());
                        return nextS;
                    }
                }
            }
        }
        log.info("Train {} findNextSegmentTopological: next segment not found topographically", train.getId());
        return null;
    }

    private boolean tryAlternativeSegment(Model model) {
        if (!train.isAutoMode() || nextSegment == null) {
            return false;
        }
        if (segmentHasPendingWaypoints(nextSegment)) {
            log.info("Train {} tryAlternativeSegment: nextSegment {} has pending waypoints, cannot bypass.", train.getId(), nextSegment.getId());
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
            if ((altNode1.equals(node1) && altNode2.equals(node2)) ||
                (altNode1.equals(node2) && altNode2.equals(node1))) {
                sAlt = s;
                break;
            }
        }

        if (sAlt == null) {
            log.info("Train {} tryAlternativeSegment: no parallel alternative segment found for {}", train.getId(), nextSegment.getId());
            return false;
        }

        BlockManager bm = model.getBlockManager();
        boolean locked = bm.tryLock(train, sAlt);
        if (locked) {
            log.info("Train {} successfully locked alternative segment {} instead of blocked segment {}",
                    train.getId(), sAlt.getId(), nextSegment.getId());
            Segment oldNext = nextSegment;
            nextSegment = sAlt;
            letrain.itinerary.AutoPilot ap = train.getAutopilot();
            ap.replaceRouteSegment(oldNext, sAlt);
            return true;
        }

        log.info("Train {} tryAlternativeSegment: alternative segment {} is also blocked.", train.getId(), sAlt.getId());
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
        if (graph == null) return null;
        letrain.map.Point pos = null;
        switch (wp.type()) {
            case STATION:
                letrain.track.Station st = this.train.getModel().getStation(wp.targetId());
                if (st != null) pos = st.getPosition();
                break;
            case SENSOR:
                letrain.track.Sensor sensor = this.train.getModel().getSensor(wp.targetId());
                if (sensor != null) pos = sensor.getPosition();
                break;
        }
        if (pos == null) return null;
        letrain.track.rail.RailTrack track = this.train.getModel().getRailMap().getTrackAt(pos);
        return track != null ? graph.getSegment(track) : null;
    }
}