package letrain.vehicle.rail.impl;

import letrain.itinerary.TrainActionManager;
import letrain.itinerary.WaypointCommand;
import letrain.map.Dir;
import letrain.mvp.Model;
import letrain.segments.BlockManager;
import letrain.segments.RailNode;
import letrain.segments.RailwayGraph;
import letrain.segments.Segment;
import letrain.track.CargoTypes;
import letrain.track.Track;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;
import letrain.utils.SerializationHelper;
import letrain.utils.ValidationUtils;
import letrain.vehicle.Tractor;
import letrain.vehicle.Transportable;
import letrain.vehicle.rail.Linker;
import letrain.vehicle.rail.Trailer;
import letrain.vehicle.rail.TrainEventListener;
import letrain.vehicle.rail.TrainMovementManager;
import letrain.vehicle.rail.TrainSafetyManager;
import letrain.visitor.Renderable;
import letrain.visitor.Visitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Core train entity that groups locomotives and wagons ({@link Linker}s)
 * and orchestrates movement, collisions, loading/unloading, and safety.
 *
 * <p>
 * Movement is delegated to {@link TrainMovementManager}.
 * Logistics are handled by {@link TrainLogisticsManager}.
 * Block-segment safety is managed by {@link TrainSafetyManager}.
 */
public class Train implements Trailer<RailTrack>, Renderable, Transportable, TrainActionManager, TrainMovementManager, TrainSafetyManager {
    static final int CRASH_SPEED_THRESHOLD = 5;
    public static final Logger log = LoggerFactory.getLogger(Train.class);

    enum LinkersSense {
        FRONT, BACK
    }

    ;

    private int id;
    private String name;
    private final Deque<Linker> linkers;
    private TrainCouplingManager trainCouplingManager;
    private letrain.vehicle.rail.TrainLogisticsManager logisticsManager;
    private letrain.itinerary.AutoPilot autopilot;
    private int railStationId = 0;
    private boolean stalled = false;
    private letrain.vehicle.rail.Trip trip;

    private Tractor directorLinker;
    private boolean autoMode = false;

    private transient letrain.mvp.Model model;
    private transient List<TrainEventListener> trainListeners;
    private final transient List<WaypointCommand> pendingCommands;
    private transient letrain.vehicle.rail.TrainMovementManager movementManager;
    private transient letrain.vehicle.rail.TrainSafetyManager safetyManager;
    private transient boolean isNotifying = false;
    private transient boolean pendingReverse = false;
    private transient int savedSpeedBeforeReverse = -1;
    private transient int waitTicks = 0;

    public Train(int id) {
        this.id = ValidationUtils.requirePositive(id, "train id");
        this.linkers = new LinkedList<>();
        this.trainListeners = new CopyOnWriteArrayList<>();
        this.pendingCommands = new CopyOnWriteArrayList<>();
        this.trainCouplingManager = new letrain.vehicle.rail.impl.TrainCouplingManager(this);
        this.logisticsManager = new letrain.vehicle.rail.impl.TrainLogisticsManager();
        this.movementManager = new letrain.vehicle.rail.impl.TrainMovementManager(this);
        this.safetyManager = new letrain.vehicle.rail.impl.TrainSafetyManager(this);
    }

    /**
     * Protected default constructor for Jackson deserialization.
     */
    protected Train() {
        this(1);
    }

    // TrainCouplingManager //////////////////////////////////////////////////

    public int getNumLinkersToJoin() {
        return trainCouplingManager.getNumLinkersToJoin();
    }

    public int getNumLinkersToRemove() {
        return trainCouplingManager.getNumLinkersToRemove();
    }

    // SafetyManager //////////////////////////////////////////////////////////
    public letrain.vehicle.rail.TrainSafetyManager getSafetyManager() {
        return safetyManager;
    }

    @Override
    public letrain.segments.Segment getCurrentSegment() {
        return safetyManager.getCurrentSegment();
    }

    @Override
    public letrain.segments.Segment getNextSegment() {
        return safetyManager.getNextSegment();
    }

    @Override
    public boolean isWaitingForBlock() {
        return false;
    }

    @Override
    public boolean hasPermissionToMove() {
        return safetyManager.hasPermissionToMove();
    }

    @Override
    public void onBlockReleased() {
        if (model != null) {
            safetyManager.onBlockReleased();
        }
    }

    @Override
    public void onBrakingInitiated(int targetSpeed) {
        this.safetyManager.onBrakingInitiated(targetSpeed);
    }

    @Override
    public void claimOccupiedSegments() {
        this.safetyManager.claimOccupiedSegments();
    }

    @Override
    public void onSegmentEntered(Segment newSegment) {
        this.safetyManager.onSegmentEntered(newSegment);
    }

    @Override
    public void onReverse() {
        this.safetyManager.onReverse();
    }

    @Override
    public Segment findNextSegment(Linker head, RailwayGraph graph) {
        return this.safetyManager.findNextSegment(head, graph);
    }

    @Override
    public void releaseOldSegments(BlockManager bm, RailwayGraph graph) {
        this.safetyManager.releaseOldSegments(bm, graph);
    }

    @Override
    public Segment findNextSegmentTopological(Linker head, RailwayGraph graph) {
        return this.safetyManager.findNextSegmentTopological(head, graph);
    }

    @Override
    public void forceSegmentReset() {
        this.safetyManager.forceSegmentReset();
    }

    @Override
    public void onEmergencyStop() {
        this.safetyManager.onEmergencyStop();
    }

    public int getStationId() {
        return railStationId;
    }

    public void setStationId(int railStationId) {
        this.railStationId = railStationId;
    }

    // LogisticsManager ////////////////////////////////////////////////////
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

    //////////////////////////////////////////////////////////////////////////////////////////////
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

    // AutoPilot ///////////////////////////////////////////////////////////////
    public boolean isAutoMode() {
        return autoMode;
    }

    public void setAutoMode(boolean autoMode) {
        this.autoMode = autoMode;
        if (!autoMode && autopilot != null) {
            autopilot.deactivate(); // Apaga el piloto automático automáticamente si pasamos a manual
        }
    }

    public void toggleAutoMode() {
        log.info("[TRAIN] toggleAutoMode() current={}", autoMode);
        if (autoMode) {
            autoMode = false;
            if (autopilot != null)
                autopilot.deactivate();
        } else if (autopilot != null && autopilot.itinerary().isPresent()) {
            autoMode = autopilot.activate();
            if (autoMode) {
                acquireInitialLocks();
                checkWaypointArrival();
            }
        }
        log.info("[TRAIN] toggleAutoMode → autoMode={}", autoMode);
    }

    public void setAutopilot(letrain.itinerary.AutoPilot ap) {
        this.autopilot = ap;
    }

    public letrain.itinerary.AutoPilot getAutopilot() {
        return autopilot;
    }

    public void notifyForkEntry(letrain.track.rail.ForkRailTrack fork) {
        // No-op: la actuación sobre desvíos es reactiva en onSegmentEntered.
    }

    public void setModel(letrain.mvp.Model model) {
        this.model = model;
    }

    List<TrainEventListener> getTrainListeners() {
        return this.trainListeners;
    }

    public Model getModel() {
        return this.model;
    }

    public boolean isPendingReverse() {
        return this.pendingReverse;
    }

    public void addTrainEventListener(TrainEventListener listener) {
        trainListeners.add(listener);
    }

    public void removeTrainEventListener(TrainEventListener listener) {
        trainListeners.remove(listener);
    }

    public void notifySpeedChanged(int speed) {
        if (speed == 0 && pendingReverse) {
            pendingReverse = false;
            Tractor dirLinker = getDirectorLinker();
            if (dirLinker != null) {
                dirLinker.toggleReversed();
                if (savedSpeedBeforeReverse != -1) {
                    dirLinker.setTargetSpeed(savedSpeedBeforeReverse);
                    savedSpeedBeforeReverse = -1;
                }
            }
        }
        for (TrainEventListener l : trainListeners) {
            l.onSpeedChanged(speed);
        }
    }

    public void notifySenseChanged(boolean forward) {
        for (TrainEventListener trainEventListener : trainListeners) {
            trainEventListener.onSenseChanged(forward);
        }
    }

    public void notifyLink() {
        trainListeners.forEach(l -> l.onLink(this));
    }

    public void notifyUnlink() {
        trainListeners.forEach(l -> l.onUnlink(this));
    }

    @Override
    public void notifySegmentOccupied(letrain.segments.Segment segment) {
        trainListeners.forEach(l -> l.onSegmentOccupied(this, segment));
    }

    public void notifyEnterSensor(letrain.track.Sensor sensor, boolean isForward) {
        ValidationUtils.requireNonNull(sensor, "sensor");
        if (isNotifying) {
            return;
        }
        isNotifying = true;
        try {
            trainListeners.forEach(l -> {
                if (l != sensor) {
                    l.onSensorEnter(this, isForward);
                }
            });
            checkWaypointArrival();
            if (autoMode && autopilot != null) {
                autopilot.onSegmentEntered(getCurrentSegment());
            }
        } finally {
            isNotifying = false;
        }
    }

    public void notifyExitSensor(letrain.track.Sensor sensor, boolean isForward) {
        ValidationUtils.requireNonNull(sensor, "sensor");
        if (isNotifying) {
            return;
        }
        isNotifying = true;
        try {
            trainListeners.forEach(l -> {
                if (l != sensor) {
                    l.onSensorExit(this, isForward);
                }
            });
        } finally {
            isNotifying = false;
        }
    }

    public int getId() {
        return this.id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void rebind() {
        if (model == null) {
            log.warn("Cannot rebind train {}: model is null", id);
            return;
        }
        // Delegamos enteramente la reclamación física de cantones al safetyManager
        safetyManager.claimOccupiedSegments();
        if (isAutoMode()) {
            safetyManager.acquireInitialLocks();
        }
    }

    /**
     * Reinitializes transient fields after deserialization.
     */
    public void postLoadInit() {
        this.trainListeners = SerializationHelper.ensureListInitializedConcurrent(trainListeners);
        this.isNotifying = false;
        this.safetyManager = new letrain.vehicle.rail.impl.TrainSafetyManager(this);
        this.movementManager = new letrain.vehicle.rail.impl.TrainMovementManager(this);
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

    public Linker getPhysicalFront() {
        boolean normalSense = true;
        if (getDirectorLinker() != null && getDirectorLinker().isReversed()) {
            normalSense = false;
        }
        return normalSense ? getFront() : getBack();
    }

    @Override
    public List<Tractor> getTractors() {
        return linkers.stream()
                .filter(Tractor.class::isInstance)
                .map(Tractor.class::cast)
                .toList();
    }

    /**
     * Notifies listeners of a low-speed contact (speed &lt;
     * {@link #CRASH_SPEED_THRESHOLD}).
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
     * Notifies listeners of a high-speed crash (speed &ge;
     * {@link #CRASH_SPEED_THRESHOLD}).
     * Stalls the train. Actual destruction is handled by the caller or
     * {@link TrainMovementManager}.
     */
    public void notifyCrash(letrain.map.Point pos, int speed) {
        this.stalled = true;
        for (TrainEventListener l : trainListeners) {
            l.onCrash(this, pos, speed);
        }
    }

    /**
     * @return true if the train is stalled from a collision or dead-end.
     */
    public boolean isStalled() {
        return stalled;
    }

    public void setStalled(boolean stalled) {
        this.stalled = stalled;
    }

    public void resetSafetyTimer() {
        if (model != null) {
            safetyManager.onBlockReleased();
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
        return movementManager.advance();
    }

    public void refreshLinkersDirection() {
        movementManager.refreshLinkersDirection();
    }

    @Override
    public void initiateBraking() {
        this.movementManager.initiateBraking();
    }

    @Override
    public void restoreSpeed(int speed) {
        this.movementManager.restoreSpeed(speed);

    }

    public boolean moveLinkers(boolean isNormalSense) {
        return movementManager.moveLinkers(isNormalSense);
    }

    @Override
    public void crash(Linker linker, int speed) {
        this.movementManager.crash(linker, speed);

    }

    @Override
    public void correctDirection(Linker linker) {
        this.movementManager.correctDirection(linker);

    }

    @Override
    public void clearReservations(List<Track> reservedTracks) {
        this.movementManager.clearReservations(reservedTracks);
    }

    @Override
    public void forceEmergencyStop() {
        this.movementManager.forceEmergencyStop();
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

    public letrain.vehicle.rail.Trip getTrip() {
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
        log.info("Train {} executeCommand kind={}", id, command.kind());
        switch (command.kind()) {
            case LOAD:
                letrain.track.Station loadStation = getStationAtTrain();
                log.info("Train {} executeCommand LOAD: station={}", id, loadStation != null ? loadStation.getId() : "null");
                if (loadStation != null) {
                    startLoadProcess(loadStation);
                }
                break;
            case UNLOAD:
                letrain.track.Station unloadStation = getStationAtTrain();
                log.info("Train {} executeCommand UNLOAD: station={}", id, unloadStation != null ? unloadStation.getId() : "null");
                if (unloadStation != null) {
                    startUnloadProcess(unloadStation);
                }
                break;
            case REVERSE:
                Tractor dirLinker = getDirectorLinker();
                log.info("Train {} executeCommand REVERSE: head={}", id, dirLinker != null ? dirLinker.getId() : "null");
                if (dirLinker != null) {
                    if (dirLinker.getSpeed() > 0) {
                        savedSpeedBeforeReverse = dirLinker.getTargetSpeed();
                        dirLinker.setTargetSpeed(0);
                        pendingReverse = true;
                        log.info("Train {} executeCommand REVERSE: speed > 0, set target speed to 0 and pendingReverse=true", id);
                    } else {
                        dirLinker.toggleReversed();
                        pendingReverse = false;
                        log.info("Train {} executeCommand REVERSE: speed is 0, toggled reverse", id);
                    }
                }
                break;
            case SPEED:
                Tractor speedLinker = getDirectorLinker();
                log.info("Train {} executeCommand SPEED: targetSpeed={}", id, command.targetSpeed());
                if (speedLinker != null) {
                    savedSpeedBeforeReverse = -1;
                    speedLinker.setSpeed(command.targetSpeed());
                    if (command.targetSpeed() > 0 && model != null) {
                        log.info("Train {} executeCommand SPEED > 0: acquiring initial locks", id);
                        safetyManager.acquireInitialLocks();
                    }
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void ensureForkRoute(Segment from, Segment to) {
        if (getModel() == null)
            return;
        RailwayGraph graph = getModel().getRailwayGraph();
        if (graph == null)
            return;

        // Find the fork between 'from' and 'to' and set the correct route
        var fromSteps = from.getSteps();
        var toSteps = to.getSteps();
        if (fromSteps == null || toSteps == null)
            return;

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
            log.debug("[FORK] ensureForkRoute {}->{}: shared node is not a fork ({})", from.getId(), to.getId(),
                    node.getTrack());
            return;
        }

        // Use the fork node's outSteps directly (getNextSteps goes to wrong node)
        for (var step : node.getOutSteps()) {
            Segment nextSeg = graph.getSegment(step);
            log.debug("[FORK] ensureForkRoute {}->{}: outStep dir={} seg={}", from.getId(), to.getId(), step.getDir(),
                    nextSeg != null ? nextSeg.getId() : "null");
            if (nextSeg != null && nextSeg.equals(to)) {
                boolean altNeeded = isAlternativeRouteNeeded(fork, step.getDir());
                log.info("[FORK] ensureForkRoute {}->{}: MATCH fork={} altNeeded={} currentAlt={}", from.getId(),
                        to.getId(), fork.getId(), altNeeded, fork.isUsingAlternativeRoute());
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

    @Override
    public void scheduleResume(int ticks) {
        if (model != null && model.getScheduler() != null) {
            model.getScheduler().schedule(ticks, () -> {
                this.resumeWaiting();
            });
        }
    }

    public void resumeWaiting() {
        log.info("Train {} resumeWaiting from wait", id);
        this.waitTicks = 0;
        if (autopilot != null) {
            autopilot.resumeWaiting();
        }
        runPendingCommands();
        acquireInitialLocks();
    }

    private void runPendingCommands() {
        while (!pendingCommands.isEmpty()) {
            WaypointCommand cmd = pendingCommands.remove(0);
            if (cmd.kind() == WaypointCommand.Kind.WAIT) {
                this.waitTicks = cmd.seconds() * WaypointCommand.TICKS_PER_SECOND;
                if (autopilot != null) {
                    ((letrain.itinerary.impl.AutoPilotImpl) autopilot).setMode(letrain.itinerary.AutoPilot.Mode.WAITING);
                }
                this.scheduleResume(this.waitTicks);
                return;
            } else {
                this.executeCommand(cmd);
            }
        }

        if (autopilot != null && autopilot.itinerary().isPresent()) {
            letrain.itinerary.Itinerary itin = autopilot.itinerary().get();
            itin.advance();
            autopilot.clearRoute();
            if (itin.state() == letrain.itinerary.Itinerary.State.DONE) {
                log.info("Train {} itinerary DONE → IDLE", id);
                autopilot.deactivate();
                return;
            }

            itin.currentWaypoint().ifPresent(wp -> {
                if (railStationId == wp.targetId()) {
                    log.info("Train {} consecutive waypoint reached", id);
                    pendingCommands.clear();
                    pendingCommands.addAll(wp.commands());
                    runPendingCommands();
                }
            });
        }
    }

    public void checkWaypointArrival() {
        if (!autoMode || autopilot == null || autopilot.mode() != letrain.itinerary.AutoPilot.Mode.FOLLOWING) {
            return;
        }
        Optional<letrain.itinerary.Itinerary> itinOpt = autopilot.itinerary();
        if (itinOpt.isEmpty()) {
            return;
        }
        letrain.itinerary.Itinerary itin = itinOpt.get();
        Optional<letrain.itinerary.Waypoint> wpOpt = itin.currentWaypoint();
        if (wpOpt.isEmpty()) {
            return;
        }
        letrain.itinerary.Waypoint wp = wpOpt.get();
        letrain.itinerary.AutoPilotContext ctx = new TrainAutoPilotContext(this);
        if (ctx.isAtTarget(wp)) {
            log.info("Train {} ARRIVED at wp {}", id, wp.targetId());
            pendingCommands.clear();
            pendingCommands.addAll(wp.commands());
            runPendingCommands();
        }
    }

    public void notifySegmentEntered(letrain.segments.Segment newSegment) {
        checkWaypointArrival();
        if (autoMode && autopilot != null) {
            log.info("Train {} notifySegmentEntered: notifying autopilot", id);
            autopilot.onSegmentEntered(newSegment);
        }
        if (safetyManager != null && model != null) {
            safetyManager.onSegmentEntered(newSegment);
        }
    }

    @Override
    public void acquireInitialLocks() {
        checkWaypointArrival();
        if (model != null && autoMode && autopilot != null) {
            Linker head = getPhysicalFront();
            if (head != null && head.getTrack() instanceof RailTrack) {
                letrain.segments.Segment currentSeg = model.getRailwayGraph().getSegment((RailTrack) head.getTrack());
                if (currentSeg != null) {
                    log.info("Train {} acquireInitialLocks: notifying autopilot of segment {}", id, currentSeg.getId());
                    autopilot.onSegmentEntered(currentSeg);
                }
            }
        }
        if (safetyManager != null && model != null) {
            safetyManager.acquireInitialLocks();
        }
    }
}
