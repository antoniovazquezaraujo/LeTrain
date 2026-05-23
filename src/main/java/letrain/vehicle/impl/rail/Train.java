package letrain.vehicle.impl.rail;

import java.time.LocalDateTime;
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

import letrain.map.Dir;
import letrain.mvp.Model;
import letrain.track.CargoTypes;
import letrain.track.Track;
import letrain.track.rail.RailTrack;
import letrain.utils.SerializationHelper;
import letrain.utils.ValidationUtils;
import letrain.vehicle.Transportable;
import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.Tractor;
import letrain.vehicle.impl.Trailer;
import letrain.visitor.Renderable;
import letrain.visitor.Visitor;
import letrain.itinerary.TrainActionManager;
import letrain.itinerary.WaypointCommand;
import letrain.segments.Segment;
import letrain.segments.RailwayGraph;
import letrain.segments.RailNode;
import letrain.track.rail.ForkRailTrack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Core train entity that groups locomotives and wagons ({@link Linker}s)
 * and orchestrates movement, collisions, loading/unloading, and safety.
 *
 * <p>Movement is delegated to {@link TrainMovementManager}.
 * Logistics are handled by {@link TrainLogisticsManager}.
 * Block-segment safety is managed by {@link TrainSafetyManager}.
 */
public class Train implements Trailer<RailTrack>, Renderable, Transportable, TrainActionManager {
    /** Speed threshold above which a collision destroys both trains. */
    static final int CRASH_SPEED_THRESHOLD = 5;
    private static final int MAX_LOADING_COUNT = 80; // 4.0 seconds at 20fps per wagon
    static final Logger log = LoggerFactory.getLogger(Train.class);
    protected final TrainCouplingManager trainCouplingManager = new TrainCouplingManager(this);
    protected Deque<Linker> linkers;

    public int getNumLinkersToJoin() {
        return trainCouplingManager.getNumLinkersToJoin();
    }

    public int getNumLinkersToRemove() {
        return trainCouplingManager.getNumLinkersToRemove();
    }

    private TrainLogisticsManager logisticsManager = new TrainLogisticsManager();

    private final transient TrainMovementManager movementManager = new TrainMovementManager(this);
    private transient TrainSafetyManager safetyManager = new TrainSafetyManager(this);
    int railStationId = 0;
    private boolean stalled = false;
    int id;
    private String name;

    public boolean isShuntingMode() {
        if (model == null) return false;
        List<letrain.segments.Segment> owned = model.getBlockManager().getOwnedSegments(this);
        for (letrain.segments.Segment s : owned) {
            if (model.getBlockManager().getOwners(s).size() > 1) {
                return true;
            }
        }
        return false;
    }

    public letrain.segments.Segment getCurrentSegment() {
        return safetyManager.getCurrentSegment();
    }

    public letrain.segments.Segment getNextSegment() {
        return safetyManager.getNextSegment();
    }

    public boolean hasPermissionToMove() {
        return safetyManager.hasPermissionToMove();
    }

    public void wakeUp() {
        safetyManager.resetSafetyTimer();
    }

    @Override
    public void forceSegmentReset() {
        if (safetyManager != null) {
            safetyManager.forceSegmentReset();
        }
    }

    public int getStationId() {
        return railStationId;
    }

    public void setStationId(int railStationId) {
        this.railStationId = railStationId;
    }

    public boolean isLoading() {
        return logisticsManager.isLoading();
    }

    public void setLoading(boolean isLoading) {
        logisticsManager.setLoading(isLoading);
    }

    public int getLoadingCount() {
        return logisticsManager.getLoadingCount();
    }

    public void setLoadingCount(int loadingCount) {
        logisticsManager.setLoadingCount(loadingCount);
    }

    public boolean isUnloadingDirection() {
        return logisticsManager.isUnloadingDirection();
    }

    /**
     * Returns the current speed of the director locomotive, or 0 if none.
     */
    public int getSpeed() {
        if (directorLinker != null) {
            return directorLinker.getSpeed();
        }
        return 0;
    }

    public boolean isStopped() {
        return getSpeed() == 0;
    }

    Trip trip;

    enum LinkersSense {
        FRONT, BACK
    };

    protected Tractor directorLinker;
    private int loadingCount;
    private transient boolean isNotifying = false;
    private transient List<TrainEventListener> trainListeners = new CopyOnWriteArrayList<>();
    private transient List<Wagon> currentCapableWagons = null;

    // ── Autonomous driving (ADR-008) ──────────────────────────────
    private letrain.itinerary.AutoPilot autopilot;
    private boolean autoMode = false;

    public boolean isAutoMode() { return autoMode; }

    public void setAutoMode(boolean on) { this.autoMode = on; }

    public void toggleAutoMode() {
        log.info("[TRAIN] toggleAutoMode() current={}", autoMode);
        if (autoMode) {
            autoMode = false;
            if (autopilot != null) autopilot.deactivate();
        } else if (autopilot != null && autopilot.itinerary().isPresent()) {
            autoMode = autopilot.activate();
        }
        log.info("[TRAIN] toggleAutoMode → autoMode={}", autoMode);
    }

    public void setAutopilot(letrain.itinerary.AutoPilot ap) { this.autopilot = ap; }
    public letrain.itinerary.AutoPilot getAutopilot() { return autopilot; }

    public void notifyForkEntry(letrain.track.rail.ForkRailTrack fork) {
        if (autopilot != null) autopilot.onForkEntered(fork);
    }

    private transient letrain.mvp.Model model;

    public void setModel(letrain.mvp.Model model) {
        this.model = model;
    }
    List<TrainEventListener> getTrainListeners() {
        return this.trainListeners;
    }
    public Model getModel(){
        return this.model;
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

    @Override
    public void notifySegmentOccupied(letrain.segments.Segment segment) {
        if (trainListeners != null) {
            trainListeners.forEach(l -> l.onSegmentOccupied(this, segment));
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
        this.trainCouplingManager.linkersToJoin = new LinkedList<>();
        this.trainCouplingManager.linkersToRemove = new LinkedList<>();
    }

    /**
     * Protected default constructor for Jackson deserialization.
     */
    protected Train() {
        this.linkers = new LinkedList<>();
        this.trainCouplingManager.linkersToJoin = new LinkedList<>();
        this.trainCouplingManager.linkersToRemove = new LinkedList<>();
    }

    public int getId() {
        return this.id;
    }

    public String getName() { return name; }

    public void setName(String name) { this.name = name; }

    public void rebind() {
        if (model == null) {
            log.warn("Cannot rebind train {}: model is null", id);
            return;
        }

        letrain.segments.RailwayGraph graph = model.getRailwayGraph();
        letrain.segments.BlockManager blockManager = model.getBlockManager();

        if (graph == null || blockManager == null) {
            return;
        }

        // ADR-005: Un tren debe reclamar sus segmentos basándose en su posición física.
        // Liberamos primero lo que tengamos para evitar dejar basura en el BlockManager
        blockManager.releaseAll(this);

        Set<letrain.segments.Segment> segmentsToClaim = new HashSet<>();
        for (Linker linker : linkers) {
            if (linker.getTrack() instanceof letrain.track.rail.RailTrack) {
                letrain.segments.Segment segment = graph.getSegment((letrain.track.rail.RailTrack) linker.getTrack());
                if (segment != null) {
                    segmentsToClaim.add(segment);
                }
            }
        }

        // Lock the segments we found
        for (letrain.segments.Segment segment : segmentsToClaim) {
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
        if (this.safetyManager == null) {
            this.safetyManager = new TrainSafetyManager(this);
        }
        if (this.autopilot != null) {
            if (this.autopilot instanceof letrain.itinerary.impl.AutoPilotImpl) {
                ((letrain.itinerary.impl.AutoPilotImpl) this.autopilot).reinitialize(
                    new TrainAutoPilotContext(this), this);
            }
            if (getModel() != null && getModel().getRailwayGraph() != null) {
                this.autopilot.setPathfinder(
                    new letrain.itinerary.AStarPathfinder(getModel().getRailwayGraph()));
            }
        }
    }

    public void initLinkersToJoin(boolean forwardDirection) {
        if (forwardDirection) {
            this.trainCouplingManager.linkerJoinSense = LinkersSense.FRONT;
        } else {
            this.trainCouplingManager.linkerJoinSense = LinkersSense.BACK;
        }
    }

    public void addLinkerToJoin() {
        if (trainCouplingManager.getNumLinkersToJoin() < trainCouplingManager.linkersToJoin.size()) {
            trainCouplingManager.numLinkersToJoin = trainCouplingManager.getNumLinkersToJoin() + 1;
        }
    }

    public void removeLinkerToJoin() {
        if (trainCouplingManager.getNumLinkersToJoin() > 0) {
            trainCouplingManager.numLinkersToJoin = trainCouplingManager.getNumLinkersToJoin() - 1;
        }
    }

    public List<Linker> getSelectedLinkersToJoin() {
        // Convert deque to list to slice it
        // Logic might differ based on iteration order of deque vs join sense
        // linkersToJoin is populated in order of distance from train.
        // so we just take the first N.
        return trainCouplingManager.getSelectedLinkersToJoin();
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
        return this.trainCouplingManager.linkersToJoin;
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
    public Linker getBack() {
        return linkers.isEmpty() ? null : linkers.getLast();
    }

    @Override
    public boolean isEmpty() {
        return linkers.isEmpty();
    }

    @Override
    public int size() {
        return linkers.size();
    }

    public void assignDefaultDirectorLinker() {
        List<Tractor> tractors = getTractors();
        setDirectorLinker(tractors.isEmpty() ? null : tractors.get(0));
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
    public List<Tractor> getTractors() {
        return linkers.stream()
                .filter(Tractor.class::isInstance)
                .map(Tractor.class::cast)
                .toList();
    }

    /**
     * Notifies listeners of a low-speed contact (speed &lt; {@link #CRASH_SPEED_THRESHOLD}).
     * Stalls the train and sets all tractor speeds to 0.
     */
    public void notifyContact(letrain.map.Point pos, int speed) {
        // Immediately stop all locomotives
        getTractors().forEach(t -> {
            t.setCurrentSpeed(0);
            t.setTargetSpeed(0);
        });
        for (TrainEventListener l : trainListeners) {
            l.onContact(this, pos, speed);
        }
    }

    /**
     * Notifies listeners of a high-speed crash (speed &ge; {@link #CRASH_SPEED_THRESHOLD}).
     * Stalls the train. Actual destruction is handled by the caller or
     * {@link TrainMovementManager}.
     */
    public void notifyCrash(letrain.map.Point pos, int speed) {
        this.stalled = true;
        for (TrainEventListener l : trainListeners) {
            l.onCrash(this, pos, speed);
        }
    }

    /** @return true if the train is stalled from a collision or dead-end. */
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
        if (safetyManager != null) {
            safetyManager.resetSafetyTimer();
        }
    }

    /**
     * Advances the train by one cell if conditions allow.
     * Handles direction, safety checks, and delegates movement to
     * {@link TrainMovementManager#moveLinkers(boolean)}.
     *
     * @return true if the train moved, false otherwise
     */
    public boolean advance() {
        // AutoPilot mode — let it control speed/direction, then move normally.
        // Return true even if tick() says false, to avoid locomotive punishing
        // the train with an abrupt speed=0 (freno en seco).
        // AutoPilot tick is handled in Locomotive.update; skip here to avoid double processing.

        // Punto 15: Mientras se está cargando o descargando, el tren no podrá moverse.
        if (isLoading()) {
            return false;
        }
        // Punto 15: Mientras se está cargando o descargando, el tren no podrá moverse.
        if (isLoading()) {
            return false;
        }

        // Prevent any movement while the train is stalled from a collision or dead-end.
        if (isStalled()) {
            // Ensure tractors stay at idle when stalled.
            if (directorLinker != null) {
                directorLinker.setTargetSpeed(0);
            }
            return false;
        }

        // Block movement only if the shared segment is NOT the one we physically
        // occupy. If we share the current segment (shunting), allow creeping apart.
        if (model != null && isShuntingMode()) {
            letrain.segments.RailwayGraph graph = model.getRailwayGraph();
            // Use the director linker's track to determine the current physical segment
            if (getDirectorLinker() instanceof letrain.vehicle.impl.Linker dirLinker) {
                letrain.track.Track headTrack = dirLinker.getTrack();
                letrain.segments.Segment currentSeg = (headTrack instanceof letrain.track.rail.RailTrack rt && graph != null)
                    ? graph.getSegment(rt) : null;
                letrain.segments.BlockManager bm = model.getBlockManager();
                for (letrain.segments.Segment s : bm.getOwnedSegments(this)) {
                    if (s.equals(currentSeg)) continue;
                    for (Train owner : bm.getOwners(s)) {
                        if (owner != this && owner.getSpeed() != 0) {
                            if (directorLinker != null) {
                                directorLinker.setTargetSpeed(0);
                            }
                            return false;
                        }
                    }
                }
            }
        }

        if (model != null) {
            if (!safetyManager.checkSafety((letrain.mvp.impl.Model) model)) {
                // Si la seguridad falla, forzamos el frenado.
                if (directorLinker != null) {
                    directorLinker.setTargetSpeed(0);
                }
                return false;
            }
        }

        boolean normalSense = true;
        if (getDirectorLinker().isReversed()) {
            normalSense = false;
        }

        // Save linker directions before attempting to move.
        // If moveLinkers fails (collision, blocked), we must restore them
        // so the renderer draws wagons at their correct positions.
        Map<Linker, letrain.map.Dir> savedDirs = new HashMap<>();
        Map<Linker, letrain.map.Dir> savedEntryDirs = new HashMap<>();
        for (Linker l : getLinkers()) {
            savedDirs.put(l, l.getDir());
            savedEntryDirs.put(l, l.getEntryDir());
        }

        setDirPushedLinkers(normalSense);
        setDirTowedLinkers(normalSense);
        boolean moved = moveLinkers(normalSense);

        if (!moved || isStalled()) {
            // Movement failed or train crashed/stalled — restore original
            // directions. Without this, setDirTowedLinkers leaves wagons
            // pointing forward, causing the renderer to interpolate them
            // into the locomotive when the train has actually stopped.
            // Exception: the first linker's direction was already corrected
            // by moveLinkers() for the new position. Restoring it would
            // break the direction on curves after a crash.
            Linker first = getLinkers().isEmpty() ? null : getLinkers().getFirst();
            for (Linker l : getLinkers()) {
                if (isStalled() && l == first) continue; // skip first linker on crash
                letrain.map.Dir savedDir = savedDirs.get(l);
                letrain.map.Dir savedEntry = savedEntryDirs.get(l);
                if (savedDir != null) {
                    l.setDir(savedDir);
                }
                if (savedEntry != null) {
                    l.setEntryDir(savedEntry);
                }
            }
        }

        return moved;
    }

    public void refreshLinkersDirection() {
        if (getDirectorLinker() == null || ((Locomotive) getDirectorLinker()).getTrack() == null) {
            return;
        }
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
            // Find the actual physical connection from oldTrack to the wagon's track,
            // instead of using geometric Point.locate() which gives diagonal directions
            // on curves that don't match any track router entry.
            Dir nextDir = null;
            Track wagonTrack = next.getTrack();
            for (Dir conn : oldTrack.getConnections()) {
                if (oldTrack.getConnected(conn) == wagonTrack) {
                    nextDir = conn.inverse(); // direction from wagon toward oldTrack
                    break;
                }
            }
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
        return movementManager.moveLinkers(isNormalSense);
    }

    public Linker getFirstLinker() {
        return linkers.isEmpty() ? null : linkers.getFirst();
    }

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
    public void updateLinkersToJoin(boolean forwardDirection) {

        trainCouplingManager.updateLinkersToJoin(forwardDirection);
    }

    public void joinLinkers() {
        trainCouplingManager.joinLinkers();
    }

    public void prepareLink(boolean forward, int count) {
        trainCouplingManager.prepareLink(forward, count);
    }

    public void prepareUnlink(boolean forward, int count) {

        trainCouplingManager.prepareUnlink(forward, count);
    }

    int calcInitialUnlinkCount() {
        int maxRemovable = Math.max(0, getLinkers().size() - 1);
        return maxRemovable == 0 ? 0 : 1;
    }

    public void setFrontDivisionSense() {
        trainCouplingManager.setFrontDivisionSense();
    }

    public void setBackDivisionSense() {
        trainCouplingManager.setBackDivisionSense();
    }

    public void resetUnlinkState() {
        trainCouplingManager.resetUnlinkState();
    }

    public void resetLinkState() {
        trainCouplingManager.resetLinkState();
    }

    public void selectNextDivisionLink() {
        trainCouplingManager.selectNextDivisionLink();
    }

    public void selectPrevDivisionLink() {
        trainCouplingManager.selectPrevDivisionLink();
    }

    private void updateLinkersToRemove() {
        trainCouplingManager.updateLinkersToRemove();
    }

    public void divideTrain(Supplier<Integer> nextTrainIdSupplier) {

        trainCouplingManager.divideTrain(nextTrainIdSupplier);
    }

    public List<Linker> destroyLinkers(Supplier<Integer> nextTrainIdSupplier) {

        return trainCouplingManager.destroyLinkers(nextTrainIdSupplier);
    }

    public Deque<Linker> getLinkersToRemove() {
        return this.trainCouplingManager.linkersToRemove;
    }

    Dir getLinkDir(Linker linker) {
        // If the adjacent linker belongs to the same train, walk through
        // the train's linkers following track directions to find the exit
        // where a different train might be. This handles multi-linker
        // trains where the end linker points toward the train center.
        return trainCouplingManager.getLinkDir(linker);
    }

    public letrain.track.Station getStationAtTrain() {
        return logisticsManager.getStationAtTrain(this);
    }

    Linker getAdjacentLinker(Linker linker, Dir dir) {
        return trainCouplingManager.getAdjacentLinker(linker, dir);
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

    // Punto 6: El tiempo de carga depende de la cantidad de vagones cargados.
    public void startLoadProcess(letrain.track.Station station) {
        logisticsManager.startLoadProcess(this, station);
    }

    public List<Wagon> getCapableWagons(letrain.track.Station station, boolean isUnload) {
        return logisticsManager.getCapableWagons(this, station, isUnload);
    }

    // Punto 9: El tiempo de descarga depende de la cantidad de vagones descargados.
    public void startUnloadProcess(letrain.track.Station station) {
        logisticsManager.startUnloadProcess(this, station);
    }

    public void endLoadUnloadProcess() {
        logisticsManager.endLoadUnloadProcess();
    }

    public boolean performIndustrialAction(letrain.track.Station station) {
        return logisticsManager.performIndustrialAction(this, station);
    }

    public int getDistanceTraveled() {
        if (getDirectorLinker() == null) {
            return 0;
        }
        return getDirectorLinker().getDistanceTraveled();
    }

    public Stop recordStopAtStation() {
        Stop stop = new Stop(railStationId, LocalDateTime.now(), getDistanceTraveled());
        if (this.trip == null) {
            this.trip = new Trip();
        }
        this.trip.addStop(stop);
        return stop;
    }

    public Trip getTrip() {
        return this.trip;
    }

    public void syncLinkersPosition() {
        if (linkers != null) {
            linkers.forEach(linker -> linker.syncPosition());
        }
    }

    // Método auxiliar para determinar el tipo de carga general del tren
    public CargoTypes getTrainCargoType() {
        return logisticsManager.getTrainCargoType(this);
    }

    @Override
    public void executeCommand(WaypointCommand command) {
        if (command == null) {
            return;
        }
        switch (command.kind()) {
            case LOAD:
                letrain.track.Station loadStation = getStationAtTrain();
                if (loadStation != null) {
                    startLoadProcess(loadStation);
                }
                break;
            case UNLOAD:
                letrain.track.Station unloadStation = getStationAtTrain();
                if (unloadStation != null) {
                    startUnloadProcess(unloadStation);
                }
                break;
            case REVERSE:
                Tractor dirLinker = getDirectorLinker();
                if (dirLinker != null) {
                    dirLinker.toggleReversed();
                }
                break;
            case SPEED:
                Tractor speedLinker = getDirectorLinker();
                if (speedLinker != null) {
                    speedLinker.setSpeed(command.targetSpeed());
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void ensureForkRoute(Segment from, Segment to) {
        if (getModel() == null) return;
        RailwayGraph graph = getModel().getRailwayGraph();
        if (graph == null) return;

        // Find the fork between 'from' and 'to' and set the correct route
        var fromSteps = from.getSteps();
        var toSteps = to.getSteps();
        if (fromSteps == null || toSteps == null) return;

        var f1 = fromSteps.getFirst();
        var f2 = fromSteps.getSecond();
        var t1 = toSteps.getFirst();
        var t2 = toSteps.getSecond();

        RailNode node = null;
        if (f1 != null && f1.getRailNode() != null) {
            var n1 = f1.getRailNode();
            if ((t1 != null && n1.equals(t1.getRailNode())) || (t2 != null && n1.equals(t2.getRailNode()))) {
                node = n1;
            }
        }
        if (node == null && f2 != null && f2.getRailNode() != null) {
            var n2 = f2.getRailNode();
            if ((t1 != null && n2.equals(t1.getRailNode())) || (t2 != null && n2.equals(t2.getRailNode()))) {
                node = n2;
            }
        }

        if (node == null) {
            log.warn("[FORK] ensureForkRoute {}->{}: no shared node found", from.getId(), to.getId());
            return;
        }
        if (!(node.getTrack() instanceof ForkRailTrack fork)) {
            log.debug("[FORK] ensureForkRoute {}->{}: shared node is not a fork ({})", from.getId(), to.getId(), node.getTrack());
            return;
        }

        // Use the fork node's outSteps directly (getNextSteps goes to wrong node)
        for (var step : node.getOutSteps()) {
            Segment nextSeg = graph.getSegment(step);
            log.debug("[FORK] ensureForkRoute {}->{}: outStep dir={} seg={}", from.getId(), to.getId(), step.getDir(), nextSeg != null ? nextSeg.getId() : "null");
            if (nextSeg != null && nextSeg.equals(to)) {
                boolean altNeeded = isAlternativeRouteNeeded(fork, step.getDir());
                log.info("[FORK] ensureForkRoute {}->{}: MATCH fork={} altNeeded={} currentAlt={}", from.getId(), to.getId(), fork.getId(), altNeeded, fork.isUsingAlternativeRoute());
                if (fork.isUsingAlternativeRoute() != altNeeded) {
                    fork.flipRoute();
                }
                return;
            }
        }
        log.warn("[FORK] ensureForkRoute {}->{}: no outStep leads to target seg", from.getId(), to.getId());
    }

    private boolean isAlternativeRouteNeeded(ForkRailTrack fork, letrain.map.Dir targetDir) {
        // Check if the targetDir is the alternative route of the fork
        var alt = fork.getRouter().getAlternativeRoute();
        return alt != null && alt.getValue() == targetDir;
    }
}
