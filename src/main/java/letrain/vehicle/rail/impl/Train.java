package letrain.vehicle.rail.impl;

import letrain.mvp.Model;
import letrain.track.rail.RailTrack;
import letrain.utils.SerializationHelper;
import letrain.utils.ValidationUtils;
import letrain.vehicle.Tractor;
import letrain.vehicle.rail.Linker;
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
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Core train entity that groups locomotives and wagons ({@link Linker}s)
 * and orchestrates movement, collisions, loading/unloading, and safety.
 *
 * <p>
 * Movement is delegated to {@link TrainMovementManager}.
 * Logistics are handled by {@link TrainLogisticsManager}.
 * Block-segment safety is managed by {@link TrainSafetyManager}.
 */
public class Train implements Renderable {
    static final int CRASH_SPEED_THRESHOLD = 5;
    public static final Logger log = LoggerFactory.getLogger(Train.class);

    public letrain.vehicle.rail.TrainLogisticsManager getLogisticsManager() {
        return logisticsManager;
    }

    public void setLogisticsManager(letrain.vehicle.rail.TrainLogisticsManager logisticsManager) {
        this.logisticsManager = logisticsManager;
    }

    enum LinkersSense {
        FRONT, BACK
    }

    private int id;
    private String name;
    private final Deque<Linker> linkers;
    public transient TrainCouplingManager trainCouplingManager;
    private letrain.vehicle.rail.TrainLogisticsManager logisticsManager;
    private letrain.itinerary.AutoPilot autopilot;
    private int railStationId = 0;
    private boolean stalled = false;
    private letrain.vehicle.rail.Trip trip;

    private Tractor directorLinker;
    private boolean autoMode = false;

    private transient letrain.mvp.Model model;
    public transient List<TrainEventListener> scriptTrainListeners;
    public transient List<TrainEventListener> coreTrainListeners;

    public transient letrain.vehicle.rail.TrainMovementManager movementManager;
    private transient letrain.vehicle.rail.TrainSafetyManager safetyManager;
    public transient letrain.itinerary.TrainActionManager actionManager;
    private transient boolean isNotifying = false;
    public transient boolean pendingReverse = false;



    public Train(int id) {
        this.id = ValidationUtils.requirePositive(id, "train id");
        this.linkers = new LinkedList<>();
        this.scriptTrainListeners = new CopyOnWriteArrayList<>();
        this.coreTrainListeners = new CopyOnWriteArrayList<>();

        this.trainCouplingManager = new letrain.vehicle.rail.impl.TrainCouplingManager(this);
        this.setLogisticsManager(new TrainLogisticsManager(this));
        this.movementManager = new letrain.vehicle.rail.impl.TrainMovementManager(this);
        this.safetyManager = new letrain.vehicle.rail.impl.TrainSafetyManager(this);
        this.actionManager = new letrain.itinerary.impl.TrainActionManager(this);
    }

    /**
     * Protected default constructor for Jackson deserialization.
     */
    protected Train() {
        this(1);
    }

    public letrain.vehicle.rail.TrainSafetyManager getSafetyManager() {
        return safetyManager;
    }

    public int getStationId() {
        return railStationId;
    }

    public void setStationId(int railStationId) {
        this.railStationId = railStationId;
    }

    public int getSpeed() {
        if (directorLinker != null) {
            return directorLinker.getSpeed();
        }
        return 0;
    }

    public boolean isStopped() {
        return getSpeed() == 0;
    }

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
                this.actionManager.checkWaypointArrival();
                this.actionManager.acquireInitialLocks();
                this.actionManager.checkWaypointArrival();
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

    public List<TrainEventListener> getScriptTrainListeners() {
        return this.scriptTrainListeners;
    }

    public List<TrainEventListener> getCoreTrainListeners() {
        if (this.coreTrainListeners == null) {
            this.coreTrainListeners = new CopyOnWriteArrayList<>();
        }
        return this.coreTrainListeners;
    }

    public Model getModel() {
        return this.model;
    }

    public boolean isPendingReverse() {
        return this.pendingReverse;
    }

    public void addScriptTrainEventListener(TrainEventListener listener) {
        if (scriptTrainListeners == null) {
            scriptTrainListeners = new CopyOnWriteArrayList<>();
        }
        scriptTrainListeners.add(listener);
    }

    public void removeScriptTrainEventListener(TrainEventListener listener) {
        if (scriptTrainListeners != null) {
            scriptTrainListeners.remove(listener);
        }
    }

    public void addCoreTrainEventListener(TrainEventListener listener) {
        if (coreTrainListeners == null) {
            coreTrainListeners = new CopyOnWriteArrayList<>();
        }
        coreTrainListeners.add(listener);
    }

    public void removeCoreTrainEventListener(TrainEventListener listener) {
        if (coreTrainListeners != null) {
            coreTrainListeners.remove(listener);
        }
    }

    public void removeAllScriptTrainEventListeners() {
        if (scriptTrainListeners != null) {
            scriptTrainListeners.clear();
        }
    }

    public void notifySpeedChanged(int speed) {
        if (speed == 0 && pendingReverse) {
            pendingReverse = false;
            Tractor dirLinker = getDirectorLinker();
            if (dirLinker != null) {
                dirLinker.toggleReversed();
                if (this.actionManager.getSavedSpeedBeforeReverse() != -1) {
                    dirLinker.setTargetSpeed(this.actionManager.getSavedSpeedBeforeReverse());
                    this.actionManager.setSavedSpeedBeforeReverse(-1);
                }
            }
        }
        if (scriptTrainListeners != null) {
            for (TrainEventListener l : scriptTrainListeners) {
                l.onSpeedChanged(speed);
            }
        }
        if (coreTrainListeners != null) {
            for (TrainEventListener l : coreTrainListeners) {
                l.onSpeedChanged(speed);
            }
        }
    }

    public void notifySenseChanged(boolean forward) {
        if (scriptTrainListeners != null) {
            for (TrainEventListener trainEventListener : scriptTrainListeners) {
                trainEventListener.onSenseChanged(forward);
            }
        }
        if (coreTrainListeners != null) {
            for (TrainEventListener trainEventListener : coreTrainListeners) {
                trainEventListener.onSenseChanged(forward);
            }
        }
    }

    public void notifyLink() {
        if (scriptTrainListeners != null) {
            scriptTrainListeners.forEach(l -> l.onLink(this));
        }
        if (coreTrainListeners != null) {
            coreTrainListeners.forEach(l -> l.onLink(this));
        }
    }

    public void notifyUnlink() {
        if (scriptTrainListeners != null) {
            scriptTrainListeners.forEach(l -> l.onUnlink(this));
        }
        if (coreTrainListeners != null) {
            coreTrainListeners.forEach(l -> l.onUnlink(this));
        }
    }

    public void notifyEnterSensor(letrain.track.Sensor sensor, boolean isForward) {
        ValidationUtils.requireNonNull(sensor, "sensor");
        if (isNotifying) {
            return;
        }
        isNotifying = true;
        try {
            if (scriptTrainListeners != null) {
                scriptTrainListeners.forEach(l -> {
                    if (l != sensor) {
                        l.onSensorEnter(this, isForward);
                    }
                });
            }
            if (coreTrainListeners != null) {
                coreTrainListeners.forEach(l -> {
                    if (l != sensor) {
                        l.onSensorEnter(this, isForward);
                    }
                });
            }
            this.actionManager.checkWaypointArrival();
            if (autoMode && autopilot != null) {
                autopilot.onSegmentEntered(safetyManager.getCurrentSegment());
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
            if (scriptTrainListeners != null) {
                scriptTrainListeners.forEach(l -> {
                    if (l != sensor) {
                        l.onSensorExit(this, isForward);
                    }
                });
            }
            if (coreTrainListeners != null) {
                coreTrainListeners.forEach(l -> {
                    if (l != sensor) {
                        l.onSensorExit(this, isForward);
                    }
                });
            }
        } finally {
            isNotifying = false;
        }
    }

    public void notifySegmentOccupied(letrain.segments.Segment segment) {
        if (scriptTrainListeners != null) {
            scriptTrainListeners.forEach(l -> l.onSegmentOccupied(this, segment));
        }
        if (coreTrainListeners != null) {
            coreTrainListeners.forEach(l -> l.onSegmentOccupied(this, segment));
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
        this.scriptTrainListeners = SerializationHelper.ensureListInitializedConcurrent(scriptTrainListeners);
        this.coreTrainListeners = SerializationHelper.ensureListInitializedConcurrent(coreTrainListeners);
        this.isNotifying = false;
        this.trainCouplingManager = new letrain.vehicle.rail.impl.TrainCouplingManager(this);
        this.safetyManager = new letrain.vehicle.rail.impl.TrainSafetyManager(this);
        this.movementManager = new letrain.vehicle.rail.impl.TrainMovementManager(this);
        if (this.autopilot != null) {
            if (this.autopilot instanceof letrain.itinerary.impl.AutoPilotImpl) {
                ((letrain.itinerary.impl.AutoPilotImpl) this.autopilot).reinitialize(
                        new TrainAutoPilotContext(this), this.actionManager);
            }
            if (getModel() != null && getModel().getRailwayGraph() != null) {
                this.autopilot.setPathfinder(
                        new letrain.itinerary.AStarPathfinder(getModel().getRailwayGraph()));
            }
        }
    }

    public void setId(int id) {
        this.id = id;
    }

    /***********************************************************
     * Train physical layout and vehicle details
     **********************************************************/

    public Deque<Linker> getLinkers() {
        return linkers;
    }

    public Deque<Linker> getLinkersToJoin() {
        return this.trainCouplingManager.getLinkersToJoin();
    }

    public void pushFront(Linker linker) {
        this.linkers.addFirst(linker);
        assignDefaultDirectorLinker();
        linker.setTrain(this);
    }

    public void pushBack(Linker linker) {
        this.linkers.addLast(linker);
        linker.setTrain(this);
    }

    public Linker getFront() {
        return linkers.isEmpty() ? null : linkers.getFirst();
    }

    public Linker getBack() {
        return linkers.isEmpty() ? null : linkers.getLast();
    }

    public boolean isEmpty() {
        return linkers.isEmpty();
    }

    public int size() {
        return linkers.size();
    }

    public void assignDefaultDirectorLinker() {
        List<Tractor> tractors = getTractors();
        setDirectorLinker(tractors.isEmpty() ? null : tractors.get(0));
    }

    public void setDirectorLinker(Tractor linker) {
        this.directorLinker = linker;
    }

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
        if (scriptTrainListeners != null) {
            for (TrainEventListener l : scriptTrainListeners) {
                l.onContact(this, pos, speed);
            }
        }
        if (coreTrainListeners != null) {
            for (TrainEventListener l : coreTrainListeners) {
                l.onContact(this, pos, speed);
            }
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
        if (scriptTrainListeners != null) {
            for (TrainEventListener l : scriptTrainListeners) {
                l.onCrash(this, pos, speed);
            }
        }
        if (coreTrainListeners != null) {
            for (TrainEventListener l : coreTrainListeners) {
                l.onCrash(this, pos, speed);
            }
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

    int calcInitialUnlinkCount() {
        int maxRemovable = Math.max(0, getLinkers().size() - 1);
        return maxRemovable == 0 ? 0 : 1;
    }

    @Override
    public String toString() {
        return "Train " + getId();
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


    public void notifySegmentEntered(letrain.segments.Segment newSegment) {
        this.actionManager.checkWaypointArrival();
        if (autoMode && autopilot != null) {
            log.info("Train {} notifySegmentEntered: notifying autopilot", id);
            autopilot.onSegmentEntered(newSegment);
        }
        if (safetyManager != null && model != null) {
            safetyManager.onSegmentEntered(newSegment);
        }
    }

}
