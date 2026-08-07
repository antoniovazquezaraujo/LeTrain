package letrain.vehicle.rail.impl;

import letrain.mvp.Model;
import letrain.utils.SerializationHelper;
import letrain.utils.ValidationUtils;
import letrain.vehicle.Tractor;
import letrain.vehicle.rail.Linker;
import letrain.vehicle.rail.CoreTrainEventListener;
import letrain.vehicle.rail.ScriptTrainEventListener;
import letrain.vehicle.rail.TrainEventDispatcher;
import letrain.vehicle.rail.TrainMovementManager;
import letrain.vehicle.rail.TrainSafetyManager;
import letrain.visitor.Renderable;
import letrain.visitor.Visitor;
import letrain.itinerary.Waypoint;
import letrain.track.Station;
import letrain.track.Sensor;
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

    enum LinkersSense {
        FRONT, BACK
    }

    private int id;
    private String name;
    private final Deque<Linker> linkers;
    private transient TrainCouplingManager trainCouplingManager;
    private letrain.vehicle.rail.TrainLogisticsManager logisticsManager;
    private letrain.itinerary.AutoPilot autopilot;
    private int railStationId = 0;
    private boolean stalled = false;
    private letrain.vehicle.rail.Trip trip;

    private Tractor directorLinker;

    private transient letrain.mvp.Model model;
    private transient TrainEventDispatcher eventDispatcher;

    private transient letrain.vehicle.rail.TrainMovementManager movementManager;
    private transient letrain.vehicle.rail.TrainSafetyManager safetyManager;
    private transient letrain.itinerary.TrainActionManager actionManager;
    private transient boolean isNotifying = false;
    private transient boolean pendingReverse = false;
    private transient boolean pendingManualMode = false;

    public TrainCouplingManager getTrainCouplingManager() {
        return trainCouplingManager;
    }

    public letrain.vehicle.rail.TrainMovementManager getMovementManager() {
        return movementManager;
    }

    public letrain.itinerary.TrainActionManager getActionManager() {
        return actionManager;
    }

    // Transient coupling menu selection state
    private transient Deque<Linker> linkersToJoin = new LinkedList<>();
    private transient int numLinkersToJoin = 0;
    private transient Deque<Linker> linkersToRemove = new LinkedList<>();
    private transient int numLinkersToRemove = 0;
    public transient LinkersSense linkerJoinSense;
    public transient LinkersSense linkerDivisionSense;
    public transient boolean joined = false;
    private int savedSpeedBeforeReverse = -1;
    private int savedTargetSpeed = -1;
    private transient java.util.Set<Sensor> activeSensors = new java.util.HashSet<>();

    public void setSavedTargetSpeed(int speed) {
        this.savedTargetSpeed = speed;
    }

    public Train(int id) {
        this.id = ValidationUtils.requirePositive(id, "train id");
        this.linkers = new LinkedList<>();
        this.eventDispatcher = new TrainEventDispatcherImpl(this);
        this.activeSensors = new java.util.HashSet<>();

        this.trainCouplingManager = new letrain.vehicle.rail.impl.TrainCouplingManager();
        this.setLogisticsManager(new TrainLogisticsManager(this));
        this.movementManager = new letrain.vehicle.rail.impl.TrainMovementManager(this);
        this.safetyManager = new letrain.vehicle.rail.impl.TrainSafetyManager(this);
        this.actionManager = new letrain.itinerary.impl.TrainActionManager(this);
        this.addCoreTrainEventListener(this.actionManager);
        this.autopilot = new letrain.itinerary.impl.AutoPilotImpl(this, this.actionManager);
    }

    /**
     * Protected default constructor for Jackson deserialization.
     */
    protected Train() {
        this(1);
    }
    public letrain.vehicle.rail.TrainLogisticsManager getLogisticsManager() {
        return logisticsManager;
    }

    public void setLogisticsManager(letrain.vehicle.rail.TrainLogisticsManager logisticsManager) {
        this.logisticsManager = logisticsManager;
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

    public java.util.Set<letrain.track.Sensor> getActiveSensors() {
        return activeSensors;
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
        return autopilot.mode() != letrain.itinerary.AutoPilot.Mode.IDLE;
    }

    public void setAutoMode(boolean autoMode) {
        if (autoMode) {
            autopilot.activate();
            this.checkInitialWaypoint();
        } else {
            autopilot.deactivate();
        }
    }

    public void toggleAutoMode() {
        boolean currentAutoMode = isAutoMode();
        log.info("[TRAIN] toggleAutoMode() current={}", currentAutoMode);
        if (currentAutoMode) {
            autopilot.deactivate();
        } else if (autopilot.itinerary().isPresent()) {
            boolean activated = autopilot.activate();
            if (activated) {
                this.checkInitialWaypoint();
                letrain.segments.Segment seg = resolveCurrentSegmentFromGraph();
                if (seg != null) {
                    notifyAutopilotSegmentEntered(seg);
                }
                this.safetyManager.acquireInitialLocks();
            }
        }
        log.info("[TRAIN] toggleAutoMode → autoMode={}", isAutoMode());
    }

    public void setAutopilot(letrain.itinerary.AutoPilot ap) {
        this.autopilot = ap;
    }

    public letrain.itinerary.AutoPilot getAutopilot() {
        return autopilot;
    }

    public void setModel(letrain.mvp.Model model) {
        this.model = model;
    }

    public List<ScriptTrainEventListener> getScriptTrainListeners() {
        return this.eventDispatcher.getScriptTrainListeners();
    }

    public List<CoreTrainEventListener> getCoreTrainListeners() {
        return this.eventDispatcher.getCoreTrainListeners();
    }

    public Model getModel() {
        return this.model;
    }

    public boolean isPendingReverse() {
        return this.pendingReverse;
    }

    public boolean isPendingManualMode() {
        return this.pendingManualMode;
    }

    public void setPendingManualMode(boolean pending) {
        this.pendingManualMode = pending;
    }

    public void addScriptTrainEventListener(ScriptTrainEventListener listener) {
        this.eventDispatcher.addScriptTrainEventListener(listener);
    }

    public void removeScriptTrainEventListener(ScriptTrainEventListener listener) {
        this.eventDispatcher.removeScriptTrainEventListener(listener);
    }

    public void addCoreTrainEventListener(CoreTrainEventListener listener) {
        this.eventDispatcher.addCoreTrainEventListener(listener);
    }

    public void removeCoreTrainEventListener(CoreTrainEventListener listener) {
        this.eventDispatcher.removeCoreTrainEventListener(listener);
    }

    public void removeAllScriptTrainEventListeners() {
        this.eventDispatcher.removeAllScriptTrainEventListeners();
    }

    private void guardNotify(Runnable block) {
        if (isNotifying) return;
        isNotifying = true;
        try {
            block.run();
        } finally {
            isNotifying = false;
        }
    }

    private void setSavedSpeedBeforeReverse(int speed) {
        this.savedSpeedBeforeReverse = speed;
    }

    public void setSpeed(int speed) {
        Tractor speedLinker = getDirectorLinker();
        if (speedLinker != null) {
            int oldSpeed = speedLinker.getTargetSpeed();
            this.setSavedSpeedBeforeReverse(-1);
            speedLinker.setSpeed(speed);
            if (speed > 0 && oldSpeed == 0 && getModel() != null) {
                letrain.segments.Segment seg = resolveCurrentSegmentFromGraph();
                if (seg != null) {
                    notifyAutopilotSegmentEntered(seg);
                }
                getSafetyManager().acquireInitialLocks();
            }
        }
    }

    public void reverse() {
        Tractor dirLinker = getDirectorLinker();
        if (dirLinker != null) {
            if (dirLinker.getSpeed() > 0) {
                this.setSavedSpeedBeforeReverse(dirLinker.getTargetSpeed());
                dirLinker.setTargetSpeed(0);
                this.pendingReverse = true;
            } else {
                dirLinker.toggleReversed();
                this.pendingReverse = false;
            }
        }
    }

    public void load() {
        if (logisticsManager != null) {
            letrain.track.Station loadStation = logisticsManager.getStationAtTrain();
            if (loadStation != null) {
                logisticsManager.startLoadProcess(loadStation);
            }
        }
    }

    public void unload() {
        if (logisticsManager != null) {
            letrain.track.Station unloadStation = logisticsManager.getStationAtTrain();
            if (unloadStation != null) {
                logisticsManager.startUnloadProcess(unloadStation);
            }
        }
    }

    public void notifySpeedChanged(int speed) {
        guardNotify(() -> {
            if (speed == 0 && pendingReverse) {
                pendingReverse = false;
                Tractor dirLinker = getDirectorLinker();
                if (dirLinker != null) {
                    dirLinker.toggleReversed();
                    if (this.savedSpeedBeforeReverse != -1) {
                        int targetSpeed = this.savedSpeedBeforeReverse;
                        dirLinker.setTargetSpeed(targetSpeed);
                        this.savedSpeedBeforeReverse = -1;
                        if (getModel() != null) {
                            letrain.segments.Segment seg = resolveCurrentSegmentFromGraph();
                            if (seg != null) {
                                notifyAutopilotSegmentEntered(seg);
                            }
                            getSafetyManager().acquireInitialLocks();
                        }
                    }
                }
            }
            this.eventDispatcher.notifySpeedChanged(speed);
        });
    }

    public void notifySenseChanged(boolean forward) {
        guardNotify(() -> this.eventDispatcher.notifySenseChanged(forward));
    }

    public void notifyLink() {
        guardNotify(() -> this.eventDispatcher.notifyLink());
    }

    public void notifyUnlink() {
        guardNotify(() -> this.eventDispatcher.notifyUnlink());
    }

    public void notifyEnterSensor(Sensor sensor, boolean isForward) {
        guardNotify(() -> {
            this.eventDispatcher.notifyEnterSensor(isForward);
            if (sensor != null) {
                if (this.activeSensors == null) {
                    this.activeSensors = new java.util.HashSet<>();
                }
                this.activeSensors.add(sensor);
                if (sensor instanceof letrain.track.Station) {
                    this.setStationId(sensor.getId());
                }
            }
            this.checkAndNotifyWaypointReached();
            if (isAutoMode()) {
                autopilot.onSegmentEntered(safetyManager.getCurrentSegment());
            }
        });
    }

    public void notifyExitSensor(Sensor sensor, boolean isForward) {
        guardNotify(() -> {
            this.eventDispatcher.notifyExitSensor(isForward);
            if (sensor != null && this.activeSensors != null) {
                this.activeSensors.remove(sensor);
                if (sensor instanceof letrain.track.Station && this.getStationId() == sensor.getId()) {
                    this.setStationId(0);
                }
            }
        });
    }

    public void notifyLoadingFinished() {
        guardNotify(() -> {
            this.eventDispatcher.notifyLoadingFinished();
        });
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
            letrain.segments.Segment seg = resolveCurrentSegmentFromGraph();
            if (seg != null) {
                notifyAutopilotSegmentEntered(seg);
            }
            safetyManager.acquireInitialLocks();
        }
    }

    /**
     * Reinitializes transient fields after deserialization.
     */
    public void postLoadInit() {
        this.activeSensors = new java.util.HashSet<>();
        if (this.eventDispatcher == null) {
            this.eventDispatcher = new TrainEventDispatcherImpl(this);
        } else {
            java.util.List<letrain.vehicle.rail.CoreTrainEventListener> coreList = new java.util.ArrayList<>(this.eventDispatcher.getCoreTrainListeners());
            for (letrain.vehicle.rail.CoreTrainEventListener l : coreList) {
                this.eventDispatcher.removeCoreTrainEventListener(l);
            }
            java.util.List<letrain.vehicle.rail.ScriptTrainEventListener> scriptList = new java.util.ArrayList<>(this.eventDispatcher.getScriptTrainListeners());
            for (letrain.vehicle.rail.ScriptTrainEventListener l : scriptList) {
                this.eventDispatcher.removeScriptTrainEventListener(l);
            }
        }
        this.eventDispatcher.postLoadInit();
        this.isNotifying = false;
        this.trainCouplingManager = new letrain.vehicle.rail.impl.TrainCouplingManager();
        this.safetyManager = new letrain.vehicle.rail.impl.TrainSafetyManager(this);
        this.movementManager = new letrain.vehicle.rail.impl.TrainMovementManager(this);
        this.actionManager = new letrain.itinerary.impl.TrainActionManager(this);
        this.addCoreTrainEventListener(this.actionManager);
        if (this.autopilot == null) {
            this.autopilot = new letrain.itinerary.impl.AutoPilotImpl(this, this.actionManager);
        } else if (this.autopilot instanceof letrain.itinerary.impl.AutoPilotImpl) {
            ((letrain.itinerary.impl.AutoPilotImpl) this.autopilot).reinitialize(
                    this, this.actionManager);
        }
        if (getModel() != null && getModel().getRailwayGraph() != null) {
            this.autopilot.setPathfinder(
                    new letrain.itinerary.AStarPathfinder(getModel().getRailwayGraph(), getModel().getBlockManager(), this));
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
        return linkersToJoin;
    }

    public void setLinkersToJoin(Deque<Linker> linkersToJoin) {
        this.linkersToJoin = linkersToJoin;
    }

    public Deque<Linker> getLinkersToRemove() {
        return linkersToRemove;
    }

    public void setLinkersToRemove(Deque<Linker> linkersToRemove) {
        this.linkersToRemove = linkersToRemove;
    }

    public int getNumLinkersToJoin() {
        return numLinkersToJoin;
    }

    public void setNumLinkersToJoin(int numLinkersToJoin) {
        this.numLinkersToJoin = numLinkersToJoin;
    }

    public int getNumLinkersToRemove() {
        return numLinkersToRemove;
    }

    public void setNumLinkersToRemove(int numLinkersToRemove) {
        this.numLinkersToRemove = numLinkersToRemove;
    }

    public LinkersSense getLinkerJoinSense() {
        return linkerJoinSense;
    }

    public void setLinkerJoinSense(LinkersSense linkerJoinSense) {
        this.linkerJoinSense = linkerJoinSense;
    }

    public LinkersSense getLinkerDivisionSense() {
        return linkerDivisionSense;
    }

    public void setLinkerDivisionSense(LinkersSense linkerDivisionSense) {
        this.linkerDivisionSense = linkerDivisionSense;
    }

    public boolean isJoined() {
        return joined;
    }

    public void setJoined(boolean joined) {
        this.joined = joined;
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
        boolean normalSense = getDirectorLinker() == null || !getDirectorLinker().isReversed();
        return normalSense ? getFront() : getBack();
    }

    public Linker getPhysicalRear() {
        boolean normalSense = getDirectorLinker() == null || !getDirectorLinker().isReversed();
        return normalSense ? getBack() : getFront();
    }

    public List<Tractor> getTractors() {
        return linkers.stream()
                .filter(Tractor.class::isInstance)
                .map(Tractor.class::cast)
                .toList();
    }

    /**
     * Sets target speed to 0 on the director linker (gradual stop).
     * Saves the current target speed so it can be restored later.
     */
    public void brake() {
        if (getDirectorLinker() != null) {
            if (getDirectorLinker().getTargetSpeed() > 0) {
                savedTargetSpeed = getDirectorLinker().getTargetSpeed();
            }
            getDirectorLinker().setTargetSpeed(0);
        }
    }

    /**
     * Stops all tractors immediately (speed = 0).
     */
    public void emergencyStop() {
        if (getDirectorLinker() != null) {
            savedTargetSpeed = getDirectorLinker().getTargetSpeed();
        }
        getTractors().forEach(t -> {
            t.setCurrentSpeed(0);
            t.setTargetSpeed(0);
            if (t instanceof Locomotive) {
                ((Locomotive) t).setForceIdleSound(true);
            }
        });
        if (pendingReverse) {
            pendingReverse = false;
            Tractor dirLinker = getDirectorLinker();
            if (dirLinker != null) {
                dirLinker.toggleReversed();
                if (this.savedSpeedBeforeReverse != -1) {
                    int targetSpeed = this.savedSpeedBeforeReverse;
                    dirLinker.setTargetSpeed(targetSpeed);
                    this.savedSpeedBeforeReverse = -1;
                    if (getModel() != null) {
                        letrain.segments.Segment seg = resolveCurrentSegmentFromGraph();
                        if (seg != null) {
                            notifyAutopilotSegmentEntered(seg);
                        }
                        getSafetyManager().acquireInitialLocks();
                    }
                }
            }
        }
    }

    /**
     * Restores the target speed saved before the last brake/emergencyStop.
     */
    public void restoreSpeed() {
        if (savedTargetSpeed > 0 && getDirectorLinker() != null) {
            getDirectorLinker().setTargetSpeed(savedTargetSpeed);
            savedTargetSpeed = -1;
        } else if (getDirectorLinker() != null && getDirectorLinker().getTargetSpeed() == 0 && isAutoMode()) {
            getDirectorLinker().setTargetSpeed(3);
        }
    }

    /**
     * Notifies listeners of a crash and destroys all linkers.
     */
    public void crashDestroy(letrain.map.Point pos, int speed) {
        guardNotify(() -> {
            this.stalled = true;
            this.eventDispatcher.notifyCrash(pos, speed);
        });
        getLinkers().forEach(l -> {
            if (l instanceof Locomotive) {
                ((Locomotive) l).setCurrentSpeed(0);
                ((Locomotive) l).setTargetSpeed(0);
                ((Locomotive) l).setForceIdleSound(true);
            }
            l.destroy();
        });
        if (getModel() != null) {
            getModel().getBlockManager().releaseAll(this);
        }
    }

    /**
     * Notifies listeners of a low-speed contact (speed &lt;
     * {@link #CRASH_SPEED_THRESHOLD}).
     * Stalls the train and sets all tractor speeds to 0.
     */
    public void notifyContact(letrain.map.Point pos, int speed) {
        guardNotify(() -> {
            emergencyStop();
            this.eventDispatcher.notifyContact(pos, speed);
        });
    }

    /**
     * Notifies listeners of a high-speed crash (speed &ge;
     * {@link #CRASH_SPEED_THRESHOLD}).
     * Stalls the train. Actual destruction is handled by the caller or
     * {@link TrainMovementManager}.
     */
    public void notifyCrash(letrain.map.Point pos, int speed) {
        guardNotify(() -> {
            this.stalled = true;
            this.eventDispatcher.notifyCrash(pos, speed);
        });
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


    public letrain.segments.Segment resolveCurrentSegmentFromGraph() {
        if (model == null || model.getRailwayGraph() == null) return null;
        letrain.vehicle.rail.Linker head = getPhysicalFront();
        if (head == null || !(head.getTrack() instanceof letrain.track.rail.RailTrack)) return null;
        return model.getRailwayGraph().getSegment((letrain.track.rail.RailTrack) head.getTrack());
    }

    public void notifyAutopilotSegmentEntered(letrain.segments.Segment newSegment) {
        if (isAutoMode() && autopilot != null) {
            log.info("Train {} notifyAutopilotSegmentEntered: notifying autopilot", id);
            autopilot.onSegmentEntered(newSegment);
        }
    }

    public void checkAndNotifyWaypointReached() {
        if (isAutoMode()) {
            letrain.itinerary.AutoPilot ap = getAutopilot();
            if (ap != null && ap.mode() == letrain.itinerary.AutoPilot.Mode.FOLLOWING) {
                ap.currentWaypoint().ifPresent(wp -> {
                    if (isCurrentlyOn(wp)) {
                        this.eventDispatcher.notifyWaypointReached(wp);
                    }
                });
            }
        }
    }

    private void checkInitialWaypoint() {
        if (isAutoMode()) {
            letrain.itinerary.AutoPilot ap = getAutopilot();
            if (ap != null && ap.mode() == letrain.itinerary.AutoPilot.Mode.FOLLOWING) {
                ap.currentWaypoint().ifPresent(wp -> {
                    if (wp.type() == Waypoint.Type.STATION && this.getStationId() == wp.targetId()) {
                        letrain.track.Station st = this.model.getStation(wp.targetId());
                        if (st != null) {
                            if (this.activeSensors == null) {
                                this.activeSensors = new java.util.HashSet<>();
                            }
                            this.activeSensors.add(st);
                        }
                        this.eventDispatcher.notifyWaypointReached(wp);
                    }
                });
            }
        }
    }

    public boolean isCurrentlyOn(Waypoint wp) {
        if (this.activeSensors == null) {
            return false;
        }
        for (Sensor s : this.activeSensors) {
            if (wp.type() == Waypoint.Type.SENSOR && !(s instanceof Station) && s.getId() == wp.targetId()) {
                return true;
            } else if (wp.type() == Waypoint.Type.STATION && s instanceof Station && s.getId() == wp.targetId()) {
                return true;
            }
        }
        return false;
    }

}
