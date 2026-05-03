package letrain.vehicle.impl.rail;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

import letrain.map.Dir;
import letrain.track.CargoTypes;
import letrain.track.Sensor;
import letrain.track.Track;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;
import letrain.utils.SerializationHelper;
import letrain.utils.ValidationUtils;
import letrain.vehicle.Destructible;
import letrain.vehicle.Transportable;
import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.RailIterator;
import letrain.vehicle.impl.Tractor;
import letrain.vehicle.impl.Trailer;
import letrain.visitor.Renderable;
import letrain.visitor.Visitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@id")
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
public class Train implements Trailer<RailTrack>, Renderable, Transportable {
    private static final int MAX_LOADING_COUNT = 80; // 4.0 seconds at 20fps per wagon
    private static final Logger log = LoggerFactory.getLogger(Train.class);
    @com.fasterxml.jackson.annotation.JsonProperty("linkers")
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(as = java.util.LinkedList.class)
    protected Deque<Linker> linkers;

    @com.fasterxml.jackson.annotation.JsonProperty("linkersToJoin")
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(as = java.util.LinkedList.class)
    protected Deque<Linker> linkersToJoin;
    private int numLinkersToRemove = 0;
    private int numLinkersToJoin = 0;

    public int getNumLinkersToJoin() {
        return numLinkersToJoin;
    }

    public int getNumLinkersToRemove() {
        return numLinkersToRemove;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("linkersToRemove")
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(as = java.util.LinkedList.class)
    protected Deque<Linker> linkersToRemove;
    int railStationId = 0;
    public boolean isLoading = false;
    private boolean stalled = false;
    int id;
    private boolean permissionToMove = true;
    private letrain.core.segments.Segment currentSegment;
    private letrain.core.segments.Segment nextSegment;
    private int safetyRetryTimer = 0;
    private static final int SAFETY_RETRY_TICKS = 300; // 15s at 20fps

    public boolean isShuntingMode() {
        if (model == null) return false;
        List<letrain.core.segments.Segment> owned = model.getBlockManager().getOwnedSegments(this);
        for (letrain.core.segments.Segment s : owned) {
            if (model.getBlockManager().getOwners(s).size() > 1) {
                return true;
            }
        }
        return false;
    }

    public letrain.core.segments.Segment getCurrentSegment() {
        return currentSegment;
    }

    public letrain.core.segments.Segment getNextSegment() {
        return nextSegment;
    }

    public boolean hasPermissionToMove() {
        return permissionToMove;
    }

    public void wakeUp() {
        this.safetyRetryTimer = 0;
    }

    public int getStationId() {
        return railStationId;
    }

    public void setStationId(int railStationId) {
        this.railStationId = railStationId;
    }

    public int getSpeed() {
        if (directorLinker instanceof Locomotive) {
            return ((Locomotive) directorLinker).getSpeed();
        }
        return 0;
    }

    public boolean isStopped() {
        return getSpeed() == 0;
    }

    Itinerary itinerary;

    enum LinkersSense {
        FRONT, BACK
    };

    LinkersSense linkerJoinSense;
    LinkersSense linkerDivisionSense;
    boolean joined = false;
    @com.fasterxml.jackson.databind.annotation.JsonDeserialize(as = letrain.vehicle.impl.rail.Locomotive.class)
    protected Tractor directorLinker;
    private int loadingCount;
    @JsonIgnore
    private transient boolean isNotifying = false;
    @JsonIgnore
    private transient List<TrainEventListener> trainListeners = new CopyOnWriteArrayList<>();
    @JsonIgnore
    private transient List<Wagon> currentCapableWagons = null;

    @JsonIgnore
    private transient letrain.mvp.Model model;

    public void setModel(letrain.mvp.Model model) {
        this.model = model;
    }

    public void addTrainEventListener(TrainEventListener listener) {
        if (trainListeners == null)
            trainListeners = new CopyOnWriteArrayList<>();
        trainListeners.add(listener);
    }

    public void removeTrainEventListener(TrainEventListener listener) {
        if (trainListeners == null)
            trainListeners = new CopyOnWriteArrayList<>();
        trainListeners.remove(listener);
    }

    public void notifySpeedChanged(int speed) {
        if (trainListeners != null) {
            for (TrainEventListener l : trainListeners) {
                l.onSpeedChanged(speed);
            }
        }
    }

    public void notifySenseChanged(boolean forward) {
        if (trainListeners != null) {
            for (TrainEventListener l : trainListeners) {
                l.onSenseChanged(forward);
            }
        }
    }

    public void notifyLink() {
        if (trainListeners != null) {
            trainListeners.forEach(l -> l.onLink(this));
        }
    }

    public void notifyUnlink() {
        if (trainListeners != null) {
            trainListeners.forEach(l -> l.onUnlink(this));
        }
    }

    public void onEnterSensor(letrain.track.Sensor sensor, boolean isForward) {
        ValidationUtils.requireNonNull(sensor, "sensor");
        if (isNotifying)
            return;
        isNotifying = true;
        try {
            if (trainListeners != null) {
                trainListeners.forEach(l -> {
                    if (l != sensor) {
                        l.onEnterTrain(this, isForward);
                    }
                });
            }
        } finally {
            isNotifying = false;
        }
    }

    public void onExitSensor(letrain.track.Sensor sensor, boolean isForward) {
        ValidationUtils.requireNonNull(sensor, "sensor");
        if (isNotifying)
            return;
        isNotifying = true;
        try {
            if (trainListeners != null) {
                trainListeners.forEach(l -> {
                    if (l != sensor) {
                        l.onExitTrain(this, isForward);
                    }
                });
            }
        } finally {
            isNotifying = false;
        }
    }

    public Train(int id) {
        this.id = ValidationUtils.requirePositive(id, "train id");
        this.linkers = new LinkedList<>();
        this.linkersToJoin = new LinkedList<>();
        this.linkersToRemove = new LinkedList<>();
    }

    /**
     * Protected default constructor for Jackson deserialization.
     */
    protected Train() {
        this.linkers = new LinkedList<>();
        this.linkersToJoin = new LinkedList<>();
        this.linkersToRemove = new LinkedList<>();
    }

    public int getId() {
        return this.id;
    }

    @JsonIgnore
    public void rebind() {
        if (model == null) {
            log.warn("Cannot rebind train {}: model is null", id);
            return;
        }

        letrain.core.segments.RailwayGraph graph = ((letrain.mvp.impl.Model) model).getRailwayGraph();
        letrain.core.segments.BlockManager blockManager = model.getBlockManager();

        // ADR-005: Un tren debe reclamar sus segmentos basándose en su posición física.
        // Liberamos primero lo que tengamos para evitar dejar basura en el BlockManager
        blockManager.releaseAll(this);

        Set<letrain.core.segments.Segment> segmentsToClaim = new HashSet<>();
        for (Linker linker : linkers) {
            if (linker.getTrack() instanceof letrain.track.rail.RailTrack) {
                letrain.core.segments.Segment segment = graph.getSegment((letrain.track.rail.RailTrack) linker.getTrack());
                if (segment != null) {
                    segmentsToClaim.add(segment);
                }
            }
        }

        for (letrain.core.segments.Segment segment : segmentsToClaim) {
            if (!blockManager.tryLock(this, segment)) {
                // Si falla el bloqueo normal tras una Tabula Rasa, es que hay convivencia forzada
                // El modo Shunting se detectará dinámicamente.
                blockManager.tryShuntingLock(this, segment);
            }
        }
        log.info("Train {} rebound to {} segments (Shunting: {})", id, segmentsToClaim.size(), isShuntingMode());
    }
    /**
     * Reinitializes transient fields after deserialization.
     */
    public void postLoadInit() {
        this.trainListeners = SerializationHelper.ensureListInitializedConcurrent(trainListeners);
        this.isNotifying = false;
    }

    @JsonIgnore
    public void initLinkersToJoin(boolean forwardDirection) {
        if (forwardDirection) {
            this.linkerJoinSense = LinkersSense.FRONT;
        } else {
            this.linkerJoinSense = LinkersSense.BACK;
        }
    }

    public void addLinkerToJoin() {
        if (numLinkersToJoin < linkersToJoin.size()) {
            numLinkersToJoin++;
        }
    }

    public void removeLinkerToJoin() {
        if (numLinkersToJoin > 0) {
            numLinkersToJoin--;
        }
    }

    @JsonIgnore
    public List<Linker> getSelectedLinkersToJoin() {
        if (linkersToJoin.isEmpty() || numLinkersToJoin == 0)
            return new ArrayList<>();
        // Convert deque to list to slice it
        List<Linker> all = new ArrayList<>(linkersToJoin);
        // Logic might differ based on iteration order of deque vs join sense
        // linkersToJoin is populated in order of distance from train.
        // so we just take the first N.
        return all.subList(0, numLinkersToJoin);
    }

    public void setId(int id) {
        this.id = id;
    }

    /***********************************************************
     * Trailer implementation
     **********************************************************/

    @Override
    public Deque<Linker> getLinkers() {
        return linkers;
    }

    @Override
    public Deque<Linker> getLinkersToJoin() {
        return this.linkersToJoin;
    }

    @Override
    public void pushFront(Linker linker) {
        this.linkers.addFirst(linker);
        assignDefaultDirectorLinker();
        linker.setTrain(this);
    }

    @Override
    public Linker popFront() {
        Linker linker = linkers.removeFirst();
        assignDefaultDirectorLinker();
        return linker;
    }

    @Override
    @JsonIgnore
    public Linker getFront() {
        return linkers.isEmpty() ? null : linkers.getFirst();
    }

    @Override
    public void pushBack(Linker linker) {
        this.linkers.addLast(linker);
        linker.setTrain(this);
        // assignDefaultDirectorLinker();
    }

    @Override
    public Linker popBack() {
        Linker linker = linkers.removeLast();
        // assignDefaultDirectorLinker();
        linker.setTrain(null);
        return linker;
    }

    @Override
    @JsonIgnore
    public Linker getBack() {
        return linkers.isEmpty() ? null : linkers.getLast();
    }

    @Override
    @JsonIgnore
    public boolean isEmpty() {
        return linkers.isEmpty();
    }

    @Override
    @JsonIgnore
    public int size() {
        return linkers.size();
    }

    public void assignDefaultDirectorLinker() {
        setDirectorLinker(getTractors() != null
                &&
                !getTractors().isEmpty()
                        ? (Tractor) getTractors().get(0)
                        : null);
    }

    @Override
    public void joinTrailerBack(Trailer t) {
        while (!t.isEmpty()) {
            pushBack(t.popFront());
        }
    }

    @Override
    public void joinTrailerFront(Trailer t) {
        while (!t.isEmpty()) {
            pushFront(t.popBack());
        }
    }

    @Override
    public void setDirectorLinker(Tractor linker) {
        this.directorLinker = linker;
    }

    @Override
    public Tractor getDirectorLinker() {
        return directorLinker;
    }

    @Override
    @com.fasterxml.jackson.annotation.JsonIgnore
    public List<Tractor> getTractors() {
        return linkers.stream()
                .filter(t -> Tractor.class.isAssignableFrom(t.getClass()))
                .map(t -> (Tractor) t)
                .collect(Collectors.toList());
    }

    public void notifyContact(letrain.map.Point pos, int speed) {
        this.stalled = true;
        for (TrainEventListener l : trainListeners) {
            l.onContact(this, pos, speed);
        }
    }

    public void notifyCrash(letrain.map.Point pos, int speed) {
        this.stalled = true;
        for (TrainEventListener l : trainListeners) {
            l.onCrash(this, pos, speed);
        }
    }

    public boolean isStalled() {
        return stalled;
    }

    public void setStalled(boolean stalled) {
        this.stalled = stalled;
    }

    /**
     * Resetea el temporizador de reintento de seguridad.
     * Útil cuando ocurre un evento externo (como un cambio de desvío) que podría
     * liberar el camino del tren.
     */
    public void resetSafetyTimer() {
        this.safetyRetryTimer = 0;
    }

    public boolean advance() {
        // Punto 15: Mientras se está cargando o descargando, el tren no podrá moverse.
        if (isLoading) {
            return false;
        }

        if (model != null) {
            if (!checkSafetyOptimized()) {
                // Si la seguridad falla, forzamos el frenado.
                if (directorLinker instanceof Locomotive) {
                    ((Locomotive) directorLinker).setTargetSpeed(0);
                }
                return false;
            }
        }

        boolean normalSense = true;
        if (getDirectorLinker().isReversed()) {
            normalSense = false;
        }

        setDirPushedLinkers(normalSense);
        setDirTowedLinkers(normalSense);
        return moveLinkers(normalSense);
    }

    /**
     * Implementación del Mandamiento 5 del ADR-005 con lógica de 'Permiso de Movimiento'.
     * @return true si el tren tiene permiso para avanzar por el segmento actual.
     */
    private boolean checkSafetyOptimized() {
        letrain.core.segments.BlockManager bm = model.getBlockManager();
        letrain.core.segments.RailwayGraph graph = ((letrain.mvp.impl.Model)model).getRailwayGraph();
        
        Linker head = (Linker) getDirectorLinker();
        if (!(head.getTrack() instanceof RailTrack)) return true;
        
        RailTrack headTrack = (RailTrack) head.getTrack();
        letrain.core.segments.Segment headS = graph.getSegment(headTrack);
        if (headS == null) return true;

        // 1. Detectar cambio de segmento para resetear el permiso
        if (currentSegment == null || !headS.equals(currentSegment)) {
            currentSegment = headS;
            // Asegurarnos de poseer el segmento actual (Mandamiento 1: Pisar es poseer)
            if (!bm.getOwnedSegments(this).contains(currentSegment)) {
                if (!bm.tryLock(this, currentSegment)) {
                    bm.tryShuntingLock(this, currentSegment);
                }
            }
            permissionToMove = false; // Al entrar en uno nuevo, perdemos el permiso hasta bloquear el siguiente
            nextSegment = findNextSegmentTopological(head, graph);
            safetyRetryTimer = 0; // Intentar inmediatamente
        }

        // 2. Si no tenemos permiso, intentar conseguirlo (bloquear Sn+1)
        if (!permissionToMove) {
            if (safetyRetryTimer <= 0) {
                if (nextSegment == null || nextSegment.equals(currentSegment)) {
                    permissionToMove = true; // No hay siguiente, podemos avanzar libremente
                } else {
                    boolean locked = bm.tryLock(this, nextSegment);
                    if (!locked) {
                        locked = bm.tryShuntingLock(this, nextSegment);
                    }
                    
                    if (locked) {
                        log.info("Train {} granted permission to move into {}. Next segment {} locked (Shunting: {}).", 
                            id, currentSegment.getId(), nextSegment.getId(), isShuntingMode());
                        permissionToMove = true;
                    } else {
                        log.debug("Train {} denied permission. Segment {} occupied. Retrying in 15s.", 
                            id, nextSegment.getId());
                        safetyRetryTimer = SAFETY_RETRY_TICKS;
                    }
                }
            } else {
                safetyRetryTimer--;
            }
        }

        // 3. Liberación de segmentos antiguos
        releaseOldSegments(bm, graph);

        // 4. Lógica de frenado proactivo (Mandamiento 3)
        if (!permissionToMove) {
            // No tenemos permiso para entrar en el SIGUIENTE. Pero estamos EN el actual.
            // Iniciamos frenado de servicio (targetSpeed 0)
            if (directorLinker instanceof Locomotive) {
                ((Locomotive) directorLinker).setTargetSpeed(0);
            }

            // Si hemos cruzado la frontera física hacia el siguiente sin permiso: ENTRADA ILEGAL
            Track nextTrack = headTrack.getConnected(head.getDir());
            if (nextTrack != null && (nextTrack instanceof ForkRailTrack || graph.getSegment((RailTrack)nextTrack) != headS)) {
                if (nextSegment != null) {
                    log.warn("Train {} ENTERED ILLEGALLY into segment {}. Forcing segment shunting.", id, nextSegment.getId());
                    bm.tryShuntingLock(this, nextSegment);
                    permissionToMove = true; // Obtenemos el permiso "por la fuerza"
                }
            }
        }

        return true; // Siempre permitimos el movimiento, la física decidirá si frena a tiempo o no
    }

    private letrain.core.segments.Segment findNextSegmentTopological(Linker head, letrain.core.segments.RailwayGraph graph) {
        RailTrack headTrack = (RailTrack) head.getTrack();
        letrain.core.segments.Segment s = graph.getSegment(headTrack);
        if (s == null) return null;

        // Buscamos el PathStep de salida de este segmento basado en la dirección de la locomotora
        letrain.core.segments.PathStep exitStep = s.getSteps().getFirst().getRailNode().getTrack() == headTrack 
            ? s.getSteps().getSecond() : s.getSteps().getFirst();
        
        // Pero eso asume que conocemos el sentido. Mejor: usamos el sentido real de la locomotora.
        Dir exitDir = head.getDir();
        // El nodo de salida es el que está en la dirección de movimiento
        // En un segmento atómico, solo hay un nodo de salida posible en cada dirección.
        
        // Reutilizamos la lógica del grafo para ver qué sigue al nodo en esa dirección
        List<letrain.core.segments.PathStep> nextSteps = graph.getNextSteps(new letrain.core.segments.impl.PathStepImpl(
            new letrain.core.segments.impl.RailNodeImpl(headTrack), exitDir));
        
        if (nextSteps == null || nextSteps.isEmpty()) {
            // Si no hay pasos inmediatos, es que estamos dentro del segmento. 
            // Buscamos el nodo extremo del segmento s que esté en el sentido de la marcha.
            // Para eso, sí que necesitamos "caminar" un poco o usar la topología.
            // Usemos un iterador ligero que salte directamente al siguiente nodo.
            RailIterator it = new RailIterator(headTrack, exitDir);
            while (it.advance()) {
                Track t = it.getTrack();
                letrain.core.segments.Segment nextS = graph.getSegment((RailTrack) t);
                if (nextS != null && !nextS.equals(s)) return nextS;
            }
        } else {
            return graph.getSegment(nextSteps.get(0));
        }

        return null;
    }

    private void releaseOldSegments(letrain.core.segments.BlockManager bm, letrain.core.segments.RailwayGraph graph) {
        Linker tail = getLinkers().peekLast(); // El último de la lista (asumiendo sentido normal)
        // En realidad hay que mirar todos los linkers para ver qué segmentos ocupan físicamente
        Set<letrain.core.segments.Segment> physicallyOccupied = new HashSet<>();
        for (Linker l : getLinkers()) {
            if (l.getTrack() instanceof RailTrack) {
                letrain.core.segments.Segment s = graph.getSegment((RailTrack) l.getTrack());
                if (s != null) physicallyOccupied.add(s);
            }
        }
        
        // Obtenemos lo que poseemos en el BlockManager y soltamos lo que ya no pisamos
        // NOTA: El ADR dice que hay que esperar a despejar el Fork. 
        // Como los segmentos terminan en Forks, si ya no pisamos ningún raíl de un segmento, 
        // es que ya hemos cruzado el Fork.
        List<letrain.core.segments.Segment> owned = bm.getOwnedSegments(this);
        for (letrain.core.segments.Segment s : owned) {
            if (!physicallyOccupied.contains(s)) {
                // Solo soltamos si no es el sNext que acabamos de pedir
                // (Aunque físicamente no lo pisemos aún, lo necesitamos poseer)
                // Pero sNext no estará en physicallyOccupied todavía, así que cuidado.
                
                // Mejor: Si s no está en physicallyOccupied AND no es el segmento que tiene la locomotora delante.
                letrain.core.segments.Segment sNext = findNextSegmentTopological((Linker)getDirectorLinker(), graph);
                if (sNext == null || !s.equals(sNext)) {
                    bm.release(this, s);
                    log.info("Train {} released segment {}", id, s.getId());
                }
            }
        }
    }

    public void refreshLinkersDirection() {
        boolean normalSense = true;
        if (getDirectorLinker().isReversed()) {
            normalSense = false;
        }
        setDirPushedLinkers(normalSense);
        setDirTowedLinkers(normalSense);
    }

    private void setDirPushedLinkers(boolean isNormalSense) {
        Iterator<Linker> iterator;
        if (!isNormalSense) {
            iterator = getLinkers().iterator();
        } else {
            iterator = getLinkers().descendingIterator();
        }

        Tractor tractor = getDirectorLinker();
        while (iterator.hasNext()) {
            Linker next = iterator.next();
            if (next == tractor) {
                break;
            }
        }
        Dir pushDir = ((Locomotive) tractor).getDir();
        while (iterator.hasNext()) {
            Linker next = iterator.next();
            Track nextTrack = next.getTrack();
            if (pushDir == null) {
                break;
            }
            Dir inverseEntry = pushDir.inverse();
            next.setEntryDir(inverseEntry);
            Dir nextDir = nextTrack.getDir(inverseEntry);
            if (nextDir == null) {
                break;
            }
            next.setDir(nextDir);
            pushDir = next.getDir();
        }
    }

    private void setDirTowedLinkers(boolean isNormalSense) {
        Iterator<Linker> iterator;
        if (isNormalSense) {
            iterator = getLinkers().iterator();
        } else {
            iterator = getLinkers().descendingIterator();
        }
        Tractor tractor = getDirectorLinker();
        while (iterator.hasNext()) {
            Linker next = iterator.next();
            if (next == tractor) {
                break;
            }
        }
        Track oldTrack = ((Locomotive) tractor).getTrack();
        while (iterator.hasNext()) {
            Linker next = iterator.next();
            Dir nextDir = next.getPosition().locate(oldTrack.getPosition());
            if (nextDir == null) {
                break;
            }
            next.setDir(nextDir);
            Dir nextEntry = next.getTrack().getDir(next.getDir());
            if (nextEntry == null) {
                break;
            }
            next.setEntryDir(nextEntry);
            oldTrack = next.getTrack();
        }
    }

    public boolean moveLinkers(boolean isNormalSense) {
        if (linkers.isEmpty()) {
            return false;
        }

        // Pass 1: Verify all linkers can move to their next tracks
        List<Track> targetTracks = new ArrayList<>();
        Map<Linker, Track> currentTracks = new HashMap<>(); // next -> currentTrack
        Map<Linker, Dir> entryDirsMap = new HashMap<>();
        Map<Linker, Linker> occupyingLinkerMap = new HashMap<>();

        List<Linker> movingOrder = new ArrayList<>();
        if (isNormalSense) {
            movingOrder.addAll(linkers);
        } else {
            Iterator<Linker> it = linkers.descendingIterator();
            while (it.hasNext()) {
                movingOrder.add(it.next());
            }
        }

        Linker firstLinker = movingOrder.get(0);
        Linker lastLinker = movingOrder.get(movingOrder.size() - 1);

        for (Linker linkerToMove : movingOrder) {
            Track currentTrack = linkerToMove.getTrack();
            if (currentTrack == null) {
                return false;
            }

            Dir exitDir = linkerToMove.getDir();
            Track nextTrackOfLinker = currentTrack.getConnected(exitDir);

            if (nextTrackOfLinker == null) {
                log.info("Pass 1: nextTrack is null for {}", linkerToMove);
                clearReservations(targetTracks);
                return false;
            }

            Linker occupyingL = nextTrackOfLinker.getLinker();
            if (occupyingL != null) {

                if (occupyingL.getTrain() != this) {
                    int speed = (getDirectorLinker() instanceof Locomotive)
                            ? ((Locomotive) getDirectorLinker()).getSpeed()
                            : 0;

                    if (Math.abs(speed) >= 5) {
                        crash(occupyingL, speed);
                    } else {
                        letrain.map.Point collisionPos = occupyingL.getPosition();
                        notifyContact(collisionPos, speed);
                        getLinkers().forEach(l -> {
                            if (l instanceof Locomotive) {
                                ((Locomotive) l).setTargetSpeed(0);
                            }
                        });
                        this.setStalled(true);
                        Train otherTrain = occupyingL.getTrain();
                        if (otherTrain != null) {
                            otherTrain.notifyContact(collisionPos, speed);
                        }
                    }
                    clearReservations(targetTracks);
                    return false;
                }
            }

            Dir entryDirOfLinker = linkerToMove.getDir().inverse();
            if (occupyingL == null || occupyingL.getTrain() != this) {
                if (!nextTrackOfLinker.canEnter(entryDirOfLinker, linkerToMove)) {
                    clearReservations(targetTracks);
                    return false;
                }
            }

            currentTracks.put(linkerToMove, currentTrack);
            entryDirsMap.put(linkerToMove, entryDirOfLinker);
            occupyingLinkerMap.put(linkerToMove, occupyingL);

            nextTrackOfLinker.setReservation(linkerToMove);
            targetTracks.add(nextTrackOfLinker);
        }

        // Pass 2: Actually move the linkers
        for (int i = 0; i < movingOrder.size(); i++) {
            Linker linkerToMove = movingOrder.get(i);
            Track currentTrack = currentTracks.get(linkerToMove);
            Track nextTrackOfLinker = targetTracks.get(i);
            Dir entryDirOfLinker = entryDirsMap.get(linkerToMove);

            Sensor sensorExit = currentTrack.getSensor();
            if (sensorExit != null && linkerToMove == lastLinker) {
                sensorExit.onExitTrain(this);
            }
            if (currentTrack.getSemaphore() != null && linkerToMove == lastLinker) {
                currentTrack.getSemaphore().onExitTrain(this);
            }
            if (currentTrack instanceof ForkRailTrack && linkerToMove == lastLinker) {
                ((ForkRailTrack) currentTrack).onExitTrain(this);
            }

            linkerToMove.setPreviousTrack(currentTrack);
            linkerToMove.setPreviousDir(linkerToMove.getDir());
            currentTrack.removeLinker();
            nextTrackOfLinker.enterLinkerFromDir(entryDirOfLinker, linkerToMove);
            linkerToMove.setRailsSinceStop(linkerToMove.getRailsSinceStop() + 1);

            nextTrackOfLinker.setReservation(null);

            Sensor sensorEnter = nextTrackOfLinker.getSensor();
            if (sensorEnter != null && linkerToMove == firstLinker) {
                sensorEnter.onEnterTrain(this);
            }
            if (nextTrackOfLinker.getSemaphore() != null && linkerToMove == firstLinker) {
                nextTrackOfLinker.getSemaphore().onEnterTrain(this);
            }
            if (nextTrackOfLinker instanceof ForkRailTrack && linkerToMove == firstLinker) {
                ((ForkRailTrack) nextTrackOfLinker).onEnterTrain(this);
            }
        }

        return true;
    }

    private void clearReservations(List<Track> reservedTracks) {
        for (Track t : reservedTracks) {
            t.setReservation(null);
        }
    }

    private void crash(Linker linker, int speed) {
        letrain.map.Point crashPos = linker.getPosition();
        // Only trigger crash logic if this train isn't already destroying
        boolean alreadyDestroying = false;
        for (Linker l : getLinkers()) {
            if (l instanceof Destructible && ((Destructible) l).isDestroying()) {
                alreadyDestroying = true;
                break;
            }
        }

        if (!alreadyDestroying) {
            notifyCrash(crashPos, speed);
            getLinkers().forEach(l -> {
                if (l instanceof Locomotive) {
                    ((Locomotive) l).setTargetSpeed(0);
                }
                l.destroy();
            });
        }

        // Also handle the other linker/train
        if (linker.getTrain() != null) {
            boolean otherAlreadyDestroying = false;
            for (Linker l : linker.getTrain().getLinkers()) {
                if (l instanceof Destructible && ((Destructible) l).isDestroying()) {
                    otherAlreadyDestroying = true;
                    break;
                }
            }
            if (!otherAlreadyDestroying) {
                linker.getTrain().notifyCrash(crashPos, speed);
                linker.getTrain().getLinkers().forEach(l -> {
                    if (l instanceof Locomotive) {
                        ((Locomotive) l).setTargetSpeed(0);
                    }
                    l.destroy();
                });
            }
        } else {
            log.info("crash: Destroying loose linker {} at crash position {}", linker, crashPos);
            linker.destroy();
        }
    }

    @JsonIgnore
    public Linker getFirstLinker() {
        return linkers.isEmpty() ? null : linkers.getFirst();
    }

    @JsonIgnore
    public Linker getLastLinker() {
        return linkers.isEmpty() ? null : linkers.getLast();
    }

    /***********************************************************
     * Renderable implementation
     **********************************************************/

    @Override
    public void accept(Visitor visitor) {
        visitor.visitLocomotive((Locomotive) this.getDirectorLinker());
    }

    /*
     * - Vaciamos los linkersToJoin
     * - Si solicitan forwardDirection, lastLinker es getFirst(), si no es
     * getLast(), es decir, que vamos agregar linkers en ese sentido seleccionado.
     * - En dir ponemos la dirección de "salida" del tren, es decir, la que apuntará
     * a despegarse del tren. Pero ahí necesitamos saber si el tren está invertido o
     * no.
     * - Si el tren no está invertido, la dirección de salida del primer linker es
     * la correcta, pero la del último será la inversa de su track.
     * - Si el tren está invertido es lo contrario, la que hay que invertir es la
     * primera.
     */
    @JsonIgnore
    public void updateLinkersToJoin(boolean forwardDirection) {
        linkersToJoin.clear();
        joined = false;
        Linker lastLinker = null;
        Dir dir = Dir.E;

        if (getLinkers().size() == 1) {
            lastLinker = (Linker) getDirectorLinker();
            if (forwardDirection) {
                linkerJoinSense = LinkersSense.FRONT;
                dir = lastLinker.getRealDir();
            } else {
                linkerJoinSense = LinkersSense.BACK;
                dir = lastLinker.getTrack().getDir(lastLinker.getRealDir());
            }
        } else if (getLinkers().size() > 1) {
            if (forwardDirection) {
                lastLinker = getLinkers().getFirst();
                if (lastLinker != null) {
                    dir = getLinkDir(lastLinker);
                    linkerJoinSense = LinkersSense.FRONT;
                }
            } else {
                lastLinker = getLinkers().getLast();
                if (lastLinker != null) {
                    dir = getLinkDir(lastLinker);
                    linkerJoinSense = LinkersSense.BACK;
                }
            }
        }
        if (lastLinker != null && dir != null) {
            Track nextTrack = lastLinker.getTrack().getConnected(dir);
            if (nextTrack != null) {
                // We enter nextTrack from direction 'dir'
                // RailIterator expects: current track and entry direction
                RailIterator iterator = new RailIterator(nextTrack, dir);
                Linker nextLinker = iterator.getTrack().getLinker();
                if (nextLinker != null && this != nextLinker.getTrain()) {
                    while (nextLinker != null) {
                        if (nextLinker.getTrain() != this) {
                            linkersToJoin.add(nextLinker);
                        }
                        if (!iterator.advance()) {
                            break; // Stop if we reach a dead end or error
                        }
                        nextLinker = iterator.getTrack().getLinker();
                    }
                }
            }
        }
        numLinkersToJoin = linkersToJoin.size();
    }

    public void joinLinkers() {
        if (!joined) {
            int count = 0;
            boolean linkersActuallyAdded = false;
            for (Linker linkerToJoin : linkersToJoin) {
                if (count >= numLinkersToJoin)
                    break;

                if (linkerJoinSense == LinkersSense.FRONT) {
                    this.linkers.addFirst(linkerToJoin);
                } else {
                    this.linkers.addLast(linkerToJoin);
                }

                Train oldTrain = linkerToJoin.getTrain();
                linkerToJoin.setTrain(this);
                if (oldTrain != null && linkerToJoin == oldTrain.getDirectorLinker()) {
                    oldTrain.assignDefaultDirectorLinker();
                    if (oldTrain.getDirectorLinker() == null) {
                        oldTrain.getLinkers().stream().forEach(linker -> linker.setTrain(null));
                        // Liberar bloques para evitar "ghost locks" que mantengan el modo Shunting
                        if (model != null) {
                            model.getBlockManager().releaseAll(oldTrain);
                        }
                    }
                }
                count++;
                linkersActuallyAdded = true;
            }
            linkersToJoin.clear();
            joined = true;
            if (linkersActuallyAdded) {
                notifyLink();
            }
        }
    }

    public void prepareLink(boolean forward, int count) {
        updateLinkersToJoin(forward);
        if (count > 0 && count < linkersToJoin.size()) {
            numLinkersToJoin = count;
        } else {
            numLinkersToJoin = linkersToJoin.size();
        }
    }

    public void prepareUnlink(boolean forward, int count) {
        if (getDirectorLinker() != null && getDirectorLinker().getSpeed() > 0) {
            return;
        }

        if (forward) {
            linkerDivisionSense = LinkersSense.FRONT;
        } else {
            linkerDivisionSense = LinkersSense.BACK;
        }

        int maxRemovable = Math.max(0, getLinkers().size() - 1);
        if (maxRemovable == 0) {
            numLinkersToRemove = 0;
        } else if (count <= 0) {
            numLinkersToRemove = 1;
        } else {
            numLinkersToRemove = Math.min(Math.max(1, count), maxRemovable);
        }

        updateLinkersToRemove();
    }

    private int calcInitialUnlinkCount() {
        int maxRemovable = Math.max(0, getLinkers().size() - 1);
        return maxRemovable == 0 ? 0 : 1;
    }

    public void setFrontDivisionSense() {
        if (getDirectorLinker() != null && getDirectorLinker().getSpeed() > 0) {
            return;
        }
        linkerDivisionSense = LinkersSense.FRONT;
        numLinkersToRemove = calcInitialUnlinkCount();
        updateLinkersToRemove();
    }

    public void setBackDivisionSense() {
        if (getDirectorLinker() != null && getDirectorLinker().getSpeed() > 0) {
            return;
        }
        linkerDivisionSense = LinkersSense.BACK;
        numLinkersToRemove = calcInitialUnlinkCount();
        updateLinkersToRemove();
    }

    public void resetUnlinkState() {
        if (!linkers.isEmpty() && linkers.peekLast() == getDirectorLinker()) {
            linkerDivisionSense = LinkersSense.FRONT;
        } else {
            linkerDivisionSense = LinkersSense.BACK;
        }
        numLinkersToRemove = calcInitialUnlinkCount();
        updateLinkersToRemove();
    }

    public void resetLinkState() {
        numLinkersToJoin = 0;
        linkersToJoin.clear();
    }

    public void selectNextDivisionLink() {
        if (getDirectorLinker() != null && getDirectorLinker().getSpeed() > 0) {
            return;
        }
        if (numLinkersToRemove < getLinkers().size() - 1) {
            numLinkersToRemove++;
        }
        updateLinkersToRemove();
    }

    public void selectPrevDivisionLink() {
        if (getDirectorLinker() != null && getDirectorLinker().getSpeed() > 0) {
            return;
        }
        if (numLinkersToRemove > 0) {
            numLinkersToRemove--;
        }
        updateLinkersToRemove();
    }

    private void updateLinkersToRemove() {
        linkersToRemove.clear();
        Iterator<Linker> linkerIterator = getLinkers().iterator();
        if (linkerDivisionSense == LinkersSense.FRONT) {
            linkerIterator = getLinkers().iterator();
        } else {
            linkerIterator = getLinkers().descendingIterator();
        }
        for (int n = 0; n < numLinkersToRemove; n++) {
            if (linkerIterator.hasNext()) {
                Linker next = linkerIterator.next();
                if (next != getDirectorLinker()) {
                    linkersToRemove.addLast(next);
                } else {
                    // Si nos topamos con el director, no podemos desvincularlo, así que reducimos
                    // la cuenta y paramos.
                    // Esto asume que el director no se puede desvincular de sí mismo si es el
                    // único.
                    // Pero en divideTrain se intenta separar.
                    // La lógica original tenía este check.
                    numLinkersToRemove--;
                    return;
                }
            }
        }
    }

    public void divideTrain(Supplier<Integer> nextTrainIdSupplier) {
        if (getDirectorLinker() != null && getDirectorLinker().getSpeed() > 0) {
            return;
        }

        Linker linkerToRemove = null;
        for (int n = 0; n < numLinkersToRemove; n++) {
            if (linkerDivisionSense == LinkersSense.BACK) {
                linkerToRemove = getLinkers().removeLast();
            } else {
                linkerToRemove = getLinkers().removeFirst();
            }
            linkerToRemove.setTrain(null);
            if (linkerToRemove instanceof Locomotive) {
                Train train = new Train(nextTrainIdSupplier.get());
                linkerToRemove.setTrain(train);
                train.getLinkers().add(linkerToRemove);
                train.assignDefaultDirectorLinker();
                // Inherit listeners so the Presenter keeps receiving events
                for (TrainEventListener listener : trainListeners) {
                    train.addTrainEventListener(listener);
                }
            }
        }
        linkersToRemove.clear();
        numLinkersToRemove = 0;
        notifyUnlink();
    }

    public List<Linker> destroyLinkers(Supplier<Integer> nextTrainIdSupplier) {
        if (getDirectorLinker() != null && getDirectorLinker().getSpeed() > 0) {
            return new ArrayList<>();
        }

        List<Linker> linkersToDestroy = new ArrayList<>();
        Linker linkerToRemove = null;
        for (int n = 0; n < numLinkersToRemove; n++) {
            if (linkerDivisionSense == LinkersSense.BACK) {
                linkerToRemove = getLinkers().removeLast();
            } else {
                linkerToRemove = getLinkers().removeFirst();
            }
            linkerToRemove.setTrain(null);
            if (linkerToRemove instanceof Locomotive) {
                Train train = new Train(nextTrainIdSupplier.get());
                linkerToRemove.setTrain(train);
                train.getLinkers().add(linkerToRemove);
                train.assignDefaultDirectorLinker();
                // Inherit listeners so the Presenter keeps receiving events
                for (TrainEventListener listener : trainListeners) {
                    train.addTrainEventListener(listener);
                }
            }
            linkersToDestroy.add(linkerToRemove);
        }
        linkersToRemove.clear();
        numLinkersToRemove = 0;
        return linkersToDestroy;
    }

    public Deque<Linker> getLinkersToRemove() {
        return this.linkersToRemove;
    }

    // Devuelve la dirección en la que hay un linker que no pertenece al tren
    Dir getLinkDir2(Linker linker) {
        Dir linkerDir = linker.getDir();
        Dir resultDir = linkerDir;
        Train train = getAdjacentTrain(linker, linkerDir);
        try {
            if (train != this) {
                return resultDir;
            }
            resultDir = linker.getTrack().getDir(resultDir);
            train = getAdjacentTrain(linker, resultDir);
            if (train != this) {
                return resultDir;
            }
            log.error("Error getting link dir:" + resultDir + " train:" + train);
            return null;
        } catch (Exception e) {
            log.error("Error getting link dir", e);
            return null;
        }
    }

    Dir getLinkDir(Linker linker) {
        Dir linkerDir = linker.getDir();
        Linker adjacentLinker = getAdjacentLinker(linker, linkerDir);
        if (adjacentLinker != null && adjacentLinker.getTrain() != this) {
            return linkerDir;
        }
        return null;
    }

    @JsonIgnore
    public letrain.track.Station getStationAtTrain() {
        for (letrain.vehicle.impl.Linker linker : getLinkers()) {
            letrain.track.Track track = linker.getTrack();
            if (track != null && track.getSensor() instanceof letrain.track.Station) {
                return (letrain.track.Station) track.getSensor();
            }
        }
        return null;
    }

    Linker getAdjacentLinker(Linker linker, Dir dir) {
        if (linker.getTrack().getConnected(dir) != null) {
            return linker.getTrack().getConnected(dir).getLinker();
        }
        return null;
    }

    Train getAdjacentTrain(Linker linker, Dir dir) {
        if (linker.getTrack().getConnected(dir) != null) {
            Linker connectedLinker = linker.getTrack().getConnected(dir).getLinker();
            if (connectedLinker != null) {
                return connectedLinker.getTrain();
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return "Train " + getId();
    }

    private boolean isUnloadingDirection = false; // True = Unload (Down), False = Load (Up)

    // Punto 6: El tiempo de carga depende de la cantidad de vagones cargados.
    public void startLoadProcess(letrain.track.Station station) {
        setLoading(true);
        this.isUnloadingDirection = false;
        
        this.currentCapableWagons = getCapableWagons(station, false);
        setLoadingCount(MAX_LOADING_COUNT * currentCapableWagons.size());
        
        if (loadingCount == 0) { // Si no hay vagones que puedan cargar, finaliza el proceso inmediatamente
            setLoading(false);
            this.currentCapableWagons = null;
        } else {
            station.notifyStartLoad(this);
        }
    }

    public List<Wagon> getCapableWagons(letrain.track.Station station, boolean isUnload) {
        List<Wagon> result = new ArrayList<>();
        CargoTypes stationCargo = station.getCargoType();
        for (letrain.vehicle.impl.Linker linker : getLinkers()) {
            if (linker instanceof Wagon) {
                Wagon wagon = (Wagon) linker;
                if (isUnload) {
                    // UNLOADING: Wagon must have the station's cargo
                    if (wagon.getCargoAmount() > 0 && wagon.getCargoType() == stationCargo) {
                        result.add(wagon);
                    }
                } else {
                    // LOADING: Wagon must be able to receive matching cargo AND match station specialization
                    boolean canLoadMore = !wagon.isFull() && (wagon.getCargoAmount() == 0 || wagon.getCargoType() == stationCargo);
                    if (canLoadMore && (wagon.getExclusiveCargoType() == CargoTypes.NONE || wagon.getExclusiveCargoType() == stationCargo)) {
                        result.add(wagon);
                    }
                }
            }
        }
        return result;
    }

    // Punto 9: El tiempo de descarga depende de la cantidad de vagones descargados.
    public void startUnloadProcess(letrain.track.Station station) {
        setLoading(true);
        this.isUnloadingDirection = true;
        
        this.currentCapableWagons = getCapableWagons(station, true);
        setLoadingCount(MAX_LOADING_COUNT * currentCapableWagons.size());
        
        if (loadingCount == 0) { // Si no hay vagones que puedan descargar, finaliza el proceso inmediatamente
            setLoading(false);
            this.currentCapableWagons = null;
        } else {
            station.notifyStartUnload(this);
        }
    }

    public void endLoadUnloadProcess() {
        setLoading(false);
        setLoadingCount(0);
        this.currentCapableWagons = null;
    }

    public boolean performIndustrialAction(letrain.track.Station station) {
        if (getDirectorLinker().getSpeed() != 0)
            return false;

        boolean anyActionTaken = false;
        double totalDistance = 0;
        int deliveryCount = 0;

        if (getLinkers().isEmpty())
            return false;
        
        if (currentCapableWagons == null || currentCapableWagons.isEmpty()) {
            currentCapableWagons = getCapableWagons(station, isUnloadingDirection);
        }

        if (currentCapableWagons.isEmpty())
            return false;

        int numCapableWagons = currentCapableWagons.size();
        int totalTicks = MAX_LOADING_COUNT * numCapableWagons;
        int currentTickInTotal = totalTicks - loadingCount; // 1 to totalTicks
        int wagonIndex = (currentTickInTotal - 1) / MAX_LOADING_COUNT;

        if (wagonIndex >= numCapableWagons)
            return false;

        Wagon wagon = currentCapableWagons.get(wagonIndex);
        int wagonTick = (currentTickInTotal - 1) % MAX_LOADING_COUNT; // 0 to 79

        if (station.getRole() == letrain.track.CargoTypes.StationRole.PRODUCER) {
            // LOADING
            // No global check anymore, we only care about capable wagons

            // PRECISION SYNC: Reach exactly 50 at tick 80
            int targetCargo = ((wagonTick + 1) * 50) / MAX_LOADING_COUNT;

            if (wagon.getCargoAmount() < targetCargo && !wagon.isFull()) {
                int toLoad = targetCargo - wagon.getCargoAmount();
                int taken = station.takeExportCargo(toLoad);
                if (taken > 0) {
                    wagon.load(taken);
                    wagon.setCargoType(station.getCargoType());
                    wagon.setLoadingPoint(station.getTrack().getPosition());
                    anyActionTaken = true;
                }
            }
        } else if (station.getRole() == letrain.track.CargoTypes.StationRole.CONSUMER) {
            // UNLOADING: Reach exactly 0 at tick 80
            int targetRemaining = 50 - ((wagonTick + 1) * 50) / MAX_LOADING_COUNT;

            if (wagon.getCargoAmount() > targetRemaining && wagon.getCargoType() == station.getCargoType()) {
                int toUnload = wagon.getCargoAmount() - targetRemaining;
                wagon.unload(toUnload);
                station.receiveImportCargo(toUnload);

                if (wagon.getLoadingPoint() != null) {
                    totalDistance += letrain.map.Point.distance(wagon.getLoadingPoint(),
                            station.getTrack().getPosition());
                    deliveryCount++;
                }
                if (wagon.getCargoAmount() == 0) {
                    wagon.setCargoType(letrain.track.CargoTypes.NONE);
                    wagon.setLoadingPoint(null);
                }
                anyActionTaken = true;
            }
        }

        if (anyActionTaken && deliveryCount > 0) {
            // Pay reward (placeholder for EconomyManager update)
            // economyManager.onCargoDelivered(totalAmount, totalDistance)
        }

        return anyActionTaken;
    }

    public int getLoadingCount() {
        return loadingCount;
    }

    public void setLoadingCount(int loadingCount) {
        this.loadingCount = loadingCount;
    }

    public boolean isLoading() {
        return isLoading;
    }

    public int getRailStationId() {
        return railStationId;
    }

    public void setLoading(boolean isLoading) {
        this.isLoading = isLoading;
    }

    @JsonIgnore
    public boolean isUnloadingDirection() {
        return isUnloadingDirection;
    }

    @JsonIgnore
    public int getDistanceTraveled() {
        if (getDirectorLinker() == null) {
            return 0;
        }
        return getDirectorLinker().getDistanceTraveled();
    }

    public Stop recordStopAtStation() {
        Stop stop = new Stop(railStationId, LocalDateTime.now(), getDistanceTraveled());
        if (this.itinerary == null) {
            this.itinerary = new Itinerary();
        }
        this.itinerary.addStop(stop);
        return stop;
    }

    public Itinerary getItinerary() {
        return this.itinerary;
    }

    public void syncLinkersPosition() {
        if (linkers != null) {
            linkers.forEach(linker -> linker.syncPosition());
        }
    }

    // Método auxiliar para determinar el tipo de carga general del tren
    @JsonIgnore
    public CargoTypes getTrainCargoType() {
        CargoTypes firstCargoType = CargoTypes.NONE;
        for (Linker linker : linkers) {
            if (linker instanceof Wagon) {
                Wagon wagon = (Wagon) linker;
                if (wagon.getCargoAmount() > 0) {
                    if (firstCargoType == CargoTypes.NONE) {
                        firstCargoType = wagon.getCargoType();
                    } else if (firstCargoType != wagon.getCargoType()) {
                        return null; // Indica carga mixta
                    }
                }
            }
        }
        return firstCargoType;
    }
}
