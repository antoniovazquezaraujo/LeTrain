package letrain.mvp.impl;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import letrain.economy.EconomyManager;
import letrain.ground.GroundMap;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.map.impl.RailMap;
import letrain.mvp.impl.services.AutomationEngine;
import letrain.mvp.impl.services.ModelListenerService;
import letrain.mvp.impl.services.ModelReportService;
import letrain.mvp.impl.services.ModelSelectionService;
import letrain.mvp.impl.services.SimulationService;
import letrain.mvp.impl.services.TrackElementMovementService;
import letrain.segments.BlockManager;
import letrain.segments.TopologyService;
import letrain.segments.impl.TopologyServiceImpl;
import letrain.track.CargoTypes;
import letrain.track.RailSemaphore;
import letrain.track.Sensor;
import letrain.track.Station;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;
import letrain.vehicle.Cursor;
import letrain.vehicle.rail.CoreTrainEventListener;
import letrain.vehicle.rail.ScriptTrainEventListener;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import letrain.vehicle.rail.impl.Wagon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Model implements letrain.mvp.Model {
    static Logger log = LoggerFactory.getLogger(Model.class);

    EconomyManager economyManager;
    Locomotive selectedLocomotive;
    ForkRailTrack selectedFork;
    RailSemaphore selectedSemaphore;
    letrain.track.SpeedSignal selectedSpeedSignal;
    Station selectedStation;
    EventLogManager eventLogManager;

    @JsonIgnore
    private letrain.segments.BlockManager blockManager;

    @JsonIgnore
    private final transient letrain.utils.SimulationScheduler scheduler;

    @JsonIgnore
    private transient letrain.segments.RailwayGraph currentGraph;

    private transient boolean mapChanged = false;

    @JsonIgnore
    private int helpLevel = 0;

    @Override
    public int getHelpLevel() {
        return helpLevel;
    }

    @Override
    public void setHelpLevel(int helpLevel) {
        this.helpLevel = helpLevel;
    }

    @JsonIgnore
    public boolean isMapChanged() {
        return mapChanged;
    }

    public void setMapChanged(boolean mapChanged) {
        this.mapChanged = mapChanged;
    }

    int selectedLocomotiveIndex;
    int selectedForkIndex;
    int selectedSemaphoreIndex;
    int selectedSpeedSignalIndex;
    int selectedStationIndex;
    boolean showId = false;

    public int getSelectedLocomotiveIndex() {
        return selectedLocomotiveIndex;
    }

    public void setSelectedLocomotiveIndex(int selectedLocomotiveIndex) {
        this.selectedLocomotiveIndex = selectedLocomotiveIndex;
    }

    public int getSelectedForkIndex() {
        return selectedForkIndex;
    }

    public void setSelectedForkIndex(int selectedForkIndex) {
        this.selectedForkIndex = selectedForkIndex;
    }

    public int getSelectedSemaphoreIndex() {
        return selectedSemaphoreIndex;
    }

    public void setSelectedSemaphoreIndex(int selectedSemaphoreIndex) {
        this.selectedSemaphoreIndex = selectedSemaphoreIndex;
    }

    public int getSelectedSpeedSignalIndex() {
        return selectedSpeedSignalIndex;
    }

    public void setSelectedSpeedSignalIndex(int selectedSpeedSignalIndex) {
        this.selectedSpeedSignalIndex = selectedSpeedSignalIndex;
    }

    public int getSelectedStationIndex() {
        return selectedStationIndex;
    }

    public void setSelectedStationIndex(int selectedStationIndex) {
        this.selectedStationIndex = selectedStationIndex;
    }

    letrain.ground.GroundMap groundMap;
    GameMode mode = letrain.mvp.Model.GameMode.RAILS;
    GameMode previousMode = letrain.mvp.Model.GameMode.RAILS;
    RailMap map;
    private java.util.Map<String, letrain.map.Point> marks = new java.util.HashMap<>();
    List<Locomotive> locomotives;
    List<Wagon> wagons;
    Cursor cursor;
    List<ForkRailTrack> forks;
    List<Sensor> sensors;
    List<RailSemaphore> semaphores;
    List<Station> stations;
    int nextLocomotiveId;
    int nextForkId;
    private transient CargoTypes selectedWagonType = CargoTypes.GOLD;

    private transient List<ScriptTrainEventListener> scriptTrainEventListeners = new ArrayList<>();
    private transient List<CoreTrainEventListener> coreTrainEventListeners = new ArrayList<>();

    @Override
    public void addScriptTrainEventListener(ScriptTrainEventListener listener) {
        if (this.scriptTrainEventListeners == null) {
            this.scriptTrainEventListeners = new ArrayList<>();
        }
        this.scriptTrainEventListeners.add(listener);
        for (Locomotive loco : locomotives) {
            if (loco.getTrain() != null) {
                loco.getTrain().addScriptTrainEventListener(listener);
            }
        }
    }

    public void addCoreTrainEventListener(CoreTrainEventListener listener) {
        if (this.coreTrainEventListeners == null) {
            this.coreTrainEventListeners = new ArrayList<>();
        }
        this.coreTrainEventListeners.add(listener);
        for (Locomotive loco : locomotives) {
            if (loco.getTrain() != null) {
                loco.getTrain().addCoreTrainEventListener(listener);
            }
        }
    }

    @Override
    public void removeAllScriptTrainEventListeners() {
        if (this.scriptTrainEventListeners != null) {
            this.scriptTrainEventListeners.clear();
        }
        for (Locomotive loco : locomotives) {
            if (loco.getTrain() != null) {
                loco.getTrain().removeAllScriptTrainEventListeners();
            }
        }
    }

    int nextSensorId;
    int nextSpeedSignalId;
    int nextSemaphoreId;
    int nextTrainId;
    int nextStationId;
    String program;
    int seed = 0;
    int quantifier = 1;
    int quantifierSteps = 0;
    LocalDateTime lastSaveTime = null;

    private transient AutomationEngine automationEngine;
    private transient SimulationService internalSimService;
    private transient letrain.time.impl.SimpleGameClock gameClock =
            new letrain.time.impl.SimpleGameClock();

    private AutomationEngine getAutomationEngine() {
        if (automationEngine == null) {
            automationEngine = new AutomationEngine(this);
        }
        return automationEngine;
    }

    private SimulationService getSimulationService() {
        if (internalSimService == null) {
            internalSimService = new SimulationService(this);
        }
        return internalSimService;
    }

    private BlockManager createBlockManager() {
        letrain.segments.impl.BlockManagerImpl bmi = new letrain.segments.impl.BlockManagerImpl();
        bmi.setOnReleaseListener((releasedSegment) -> {
            if (locomotives != null) {
                for (Locomotive loco : locomotives) {
                    Train train = loco.getTrain();
                    if (train != null && train.isAutoMode()) {
                        letrain.segments.Segment nextSeg =
                                train.getSafetyManager().getNextSegment();
                        if (train.getSafetyManager().isWaitingForBlock()
                                && releasedSegment.equals(nextSeg)) {
                            train.getSafetyManager().onBlockReleased();
                        }
                    }
                }
            }
        });
        return bmi;
    }

    public Model() {
        this.scheduler = new letrain.utils.impl.SimulationScheduler();
        this.blockManager = createBlockManager();
        this.eventLogManager = new EventLogManager();
        this.economyManager = new letrain.economy.impl.EconomyManager(eventLogManager);
        this.economyManager.reloadConfig();
        this.gameClock.setDayDurationSeconds(this.economyManager.getDayDurationSeconds());
        if (seed == 0) {
            seed = 1 + (int) (Math.random() * 255);
        }
        this.groundMap = new letrain.ground.impl.GroundMap(seed, this.economyManager);
        this.cursor = new Cursor();
        this.cursor.setDir(Dir.E);

        // Always start at the origin: the world is asymmetric, so a shared (0,0) makes
        // coordinates in scenarios and the console simpler and reproducible.
        this.cursor.setPosition(new Point(0, 0));
        this.locomotives = new ArrayList<>();
        this.wagons = new ArrayList<>();
        this.forks = new ArrayList<>();
        this.sensors = new ArrayList<>();
        this.semaphores = new ArrayList<>();
        this.stations = new ArrayList<>();
        this.map = new RailMap();

        setupModelTrainEventListeners();
        this.program = "";
        selectedLocomotiveIndex = 0;
        selectedForkIndex = 0;
        selectedSemaphoreIndex = 0;
        selectedStationIndex = 0;
    }

    /**
     * Rebuilds each track's adjacency ({@code connections}) from its router routes and the
     * positions of the tracks in the rail map. Adjacency is not serialized (Jackson recursed
     * through the whole graph, so a long connected line blew the document nesting limit); it is
     * derived here after a load. Tracks created by commands already have their connections set, so
     * this only matters for deserialized models (savegame / undo checkpoint).
     */
    private void rebuildTrackConnections() {
        if (map == null) {
            return;
        }
        map.forEach(track -> {
            if (track == null || track.getRouter() == null || track.getPosition() == null) {
                return;
            }
            for (letrain.map.Dir dir : letrain.map.Dir.values()) {
                if (track.getRouter().getDir(dir) == null) {
                    continue;
                }
                letrain.map.Point neighbour = new letrain.map.Point(track.getPosition());
                neighbour.move(dir, 1);
                letrain.track.Track connected = map.getTrackAt(neighbour.getX(), neighbour.getY());
                if (connected != null) {
                    track.connect(dir, connected);
                }
            }
        });
    }

    public void postLoadInit() {
        // NOTE: the economy/settings are NOT reloaded from the local file here on purpose: a
        // savegame or an imported scenario carries its own settings, and those must win over the
        // local letrain.cfg for the world (terrain included) to be reproducible.
        rebuildTrackConnections();
        if (nextSpeedSignalId == 0) {
            for (Sensor s : getSensors()) {
                if (s instanceof letrain.track.SpeedSignal && s.getId() > nextSpeedSignalId) {
                    nextSpeedSignalId = s.getId();
                }
            }
        }
        this.blockManager = createBlockManager();
        if (this.scriptTrainEventListeners == null) {
            this.scriptTrainEventListeners = new ArrayList<>();
        } else {
            this.scriptTrainEventListeners.clear();
        }
        if (this.coreTrainEventListeners == null) {
            this.coreTrainEventListeners = new ArrayList<>();
        } else {
            this.coreTrainEventListeners.clear();
        }
        this.selectedWagonType = CargoTypes.GOLD;
        this.automationEngine = new AutomationEngine(this);
        this.internalSimService = new SimulationService(this);

        if (this.groundMap != null) {
            try {
                java.lang.reflect.Field noiseField =
                        letrain.ground.impl.GroundMap.class.getDeclaredField("noise");
                noiseField.setAccessible(true);
                noiseField.set(this.groundMap, new letrain.ground.PerlinNoise(this.seed));
                java.lang.reflect.Field ecoField =
                        letrain.ground.impl.GroundMap.class.getDeclaredField("economyManager");
                ecoField.setAccessible(true);
                ecoField.set(this.groundMap, this.economyManager);
                if (this.groundMap instanceof letrain.ground.impl.GroundMap) {
                    ((letrain.ground.impl.GroundMap) this.groundMap).rebuildCellsFromBlocks();
                }
            } catch (Exception e) {
                log.error("Error re-initializing GroundMap", e);
            }
        }

        setupModelTrainEventListeners();
        if (locomotives != null) {
            // Pass 1: Set model, post-load init, setup listeners, and claim physically occupied
            // segments
            for (Locomotive loco : locomotives) {
                Train train = loco.getTrain();
                if (train != null) {
                    train.setModel(this);
                    train.postLoadInit();
                    for (ScriptTrainEventListener l : scriptTrainEventListeners) {
                        train.addScriptTrainEventListener(l);
                    }
                    for (CoreTrainEventListener l : coreTrainEventListeners) {
                        train.addCoreTrainEventListener(l);
                    }
                    train.getSafetyManager().claimOccupiedSegments();
                }
            }
            // Pass 2: Acquire initial lookahead locks for all active autopilot trains
            for (Locomotive loco : locomotives) {
                Train train = loco.getTrain();
                if (train != null && train.isAutoMode()) {
                    letrain.segments.Segment seg = train.resolveCurrentSegmentFromGraph();
                    if (seg != null) {
                        train.notifyAutopilotSegmentEntered(seg);
                    }
                    train.getSafetyManager().acquireInitialLocks();
                }
            }
        }
        reestablishSystemListeners();
        if (this.program != null && !this.program.isEmpty()) {
            this.setProgram(this.program);
        }

        if (this.mode == letrain.mvp.Model.GameMode.COMMAND) {
            this.mode = letrain.mvp.Model.GameMode.RAILS;
            this.commandText = "";
            this.commandError = "";
        }
    }

    private void setupModelTrainEventListeners() {
        this.addCoreTrainEventListener(ModelListenerService.createCoreTrainEventListener(this));
    }

    @Override
    public int nextSemaphoreId() {
        return ++nextSemaphoreId;
    }

    @Override
    public int nextForkId() {
        return ++nextForkId;
    }

    @Override
    public int nextLocomotiveId() {
        return ++nextLocomotiveId;
    }

    @Override
    public int peekNextLocomotiveId() {
        return nextLocomotiveId + 1;
    }

    @Override
    public int nextSensorId() {
        return ++nextSensorId;
    }

    @Override
    public int nextTrainId() {
        return ++nextTrainId;
    }

    @Override
    public int peekNextTrainId() {
        return nextTrainId + 1;
    }

    @Override
    public int nextStationId() {
        return ++nextStationId;
    }

    public double getLinearDistanceBetweenStations(int startStationId, int endStationId) {
        Point from = getStation(startStationId).getPosition();
        Point to = getStation(endStationId).getPosition();
        return Math
                .sqrt(Math.pow(from.getX() - to.getX(), 2) + Math.pow(from.getY() - to.getY(), 2));
    }

    @Override
    public RailMap getRailMap() {
        return map;
    }

    @Override
    public void addTrack(Point point, RailTrack track) {
        map.addTrack(point, track);
        if (track instanceof ForkRailTrack) {
            addFork((ForkRailTrack) track);
        }
        if (track.getComponent() instanceof letrain.track.RailSemaphore) {
            addSemaphore((letrain.track.RailSemaphore) track.getComponent());
        } else if (track.getComponent() instanceof Station) {
            addStation((Station) track.getComponent());
        } else if (track.getComponent() instanceof letrain.track.Sensor) {
            addSensor((letrain.track.Sensor) track.getComponent());
        }
        mapChanged = true;
    }

    @Override
    public RailTrack removeTrack(Point point) {
        RailTrack track = map.getTrackAt(point);
        if (track != null) {
            if (track.getComponent() instanceof letrain.track.RailSemaphore) {
                removeSemaphore((letrain.track.RailSemaphore) track.getComponent());
            } else if (track.getComponent() instanceof Station) {
                removeStation((Station) track.getComponent());
            } else if (track.getComponent() instanceof letrain.track.Sensor) {
                removeSensor((letrain.track.Sensor) track.getComponent());
            }
            if (track instanceof ForkRailTrack) {
                removeFork((ForkRailTrack) track);
            }
            // Disconnect from neighbors
            for (letrain.map.Dir dir : track.getConnections()) {
                letrain.track.Track neighbor = track.getConnected(dir);
                if (neighbor != null) {
                    neighbor.disconnect(dir.inverse());
                }
            }
            map.removeTrack(point);
            mapChanged = true;
        }
        return track;
    }

    public void setRailMap(RailMap map) {
        this.map = map;
    }

    @Override
    public GroundMap getGroundMap() {
        return groundMap;
    }

    @Override
    public List<Sensor> getSensors() {
        return sensors;
    }

    @Override
    public Train getTrainFromLocomotiveId(int locomotiveId) {
        for (Locomotive locomotive : getLocomotives()) {
            if (locomotive.getId() == locomotiveId) {
                return locomotive.getTrain();
            }
        }
        return null;
    }

    @Override
    public void addSensor(Sensor sensor) {
        if (!sensors.contains(sensor)) {
            sensors.add(sensor);
            if (sensor.getTrack() != null) {
                sensor.getTrack().setComponent(sensor);
            }
            getEconomyManager().onSensorConstructed(sensor);
            setupSensorSystemListeners(sensor);
            mapChanged = true;
        }
    }

    private void setupSensorSystemListeners(Sensor sensor) {
        ModelListenerService.setupSensorSystemListeners(this, sensor);
    }

    @Override
    public void removeSensor(Sensor sensor) {
        if (sensors.remove(sensor)) {
            if (sensor.getTrack() != null) {
                sensor.getTrack().setComponent(null);
            }
            getEconomyManager().onSensorDestroyed(sensor);
            mapChanged = true;
        }
    }

    @Override
    public Sensor getSensor(int id) {
        for (Sensor sensor : getSensors()) {
            if (sensor.getId() == id) {
                return sensor;
            }
        }
        return null;
    }

    @Override
    public List<Locomotive> getLocomotives() {
        return locomotives;
    }

    @Override
    public List<Wagon> getWagons() {
        return wagons;
    }

    @Override
    public void removeWagon(Wagon wagon) {
        if (this.wagons.remove(wagon)) {
            getEconomyManager().onWagonDestroyed(wagon);
        }
    }

    @Override
    public void addWagon(Wagon wagon) {
        this.wagons.add(wagon);
        getEconomyManager().onWagonConstructed(wagon);
    }

    @Override
    public Cursor getCursor() {
        return cursor;
    }

    @Override
    public List<ForkRailTrack> getForks() {
        return this.forks;
    }

    @Override
    public void addFork(ForkRailTrack fork) {
        if (!this.forks.contains(fork)) {
            this.forks.add(fork);
            getEconomyManager().onForkConstructed(fork);
            setupForkSystemListeners(fork);
            mapChanged = true;
        }
    }

    private void setupForkSystemListeners(ForkRailTrack fork) {
        ModelListenerService.setupForkSystemListeners(this, fork);
    }

    @Override
    public void removeFork(ForkRailTrack fork) {
        if (this.forks.remove(fork)) {
            getEconomyManager().onForkDestroyed(fork);
            mapChanged = true;
        }
    }

    @Override
    public void addLocomotive(Locomotive locomotive) {
        this.locomotives.add(locomotive);
        if (locomotive.getTrain() != null) {
            locomotive.getTrain().setModel(this);
            locomotive.getTrain().rebind();
            for (ScriptTrainEventListener l : scriptTrainEventListeners) {
                locomotive.getTrain().addScriptTrainEventListener(l);
            }
            for (CoreTrainEventListener l : coreTrainEventListeners) {
                locomotive.getTrain().addCoreTrainEventListener(l);
            }
        }
        getEconomyManager().onLocomotiveConstructed(locomotive);
    }

    @Override
    public void removeLocomotive(Locomotive locomotive) {
        if (this.locomotives.remove(locomotive)) {
            if (selectedLocomotive == locomotive) {
                selectedLocomotive = null;
                selectedLocomotiveIndex = -1;
            }
            getEconomyManager().onLocomotiveDestroyed(locomotive);
        }
    }

    @Override
    public void removeDestroyedTrains() {
        getSimulationService().cleanupEntities();
    }

    @Override
    public void moveLocomotives() {
        getSimulationService().moveVehicles();
    }

    @Override
    public GameMode getMode() {
        return mode;
    }

    @JsonIgnore
    private boolean pauseEditing = false;

    @Override
    public boolean isPauseEditing() {
        return pauseEditing;
    }

    @Override
    public void setPauseEditing(boolean pauseEditing) {
        this.pauseEditing = pauseEditing;
    }

    @Override
    public void setMode(GameMode mode) {
        if (this.mode == GameMode.RAILS && mode != GameMode.RAILS && mapChanged) {
            log.info("Tabula Rasa triggered: map changed during editing.");
            blockManager.clearAll();
            currentGraph = null;
            // Re-bind all trains to the new graph and re-establish locks
            if (locomotives != null) {
                for (Locomotive loco : locomotives) {
                    if (loco.getTrain() != null) {
                        loco.getTrain().rebind();
                    }
                }
            }
            mapChanged = false;
        }
        if (this.mode != mode) {
            this.previousMode = this.mode;
        }
        this.mode = mode;
        if (mode == GameMode.FORKS && selectedFork == null && !getForks().isEmpty()) {
            selectedFork = getForks().get(0);
            selectedForkIndex = 0;
        }
    }

    @Override
    public GameMode getPreviousMode() {
        return previousMode;
    }

    @JsonIgnore
    public letrain.segments.RailwayGraph getRailwayGraph() {
        if (currentGraph == null) {
            log.info("Discovering railway topology...");
            TopologyService topologyService = new TopologyServiceImpl();
            currentGraph = topologyService.discover(getRailMap());
            log.info("Railway topology discovered successfully:\n{}", currentGraph);
        }
        return currentGraph;
    }

    @Override
    public ForkRailTrack getSelectedFork() {
        return selectedFork;
    }

    @Override
    public void setSelectedFork(ForkRailTrack selectedFork) {
        this.selectedFork = selectedFork;
    }

    @Override
    public boolean selectFork(int id) {
        return ModelSelectionService.selectFork(this, id);
    }

    @Override
    public ForkRailTrack getFork(int id) {
        for (ForkRailTrack fork : getForks()) {
            if (fork.getId() == id) {
                return fork;
            }
        }
        return null;
    }

    @Override
    public boolean selectNextFork() {
        return ModelSelectionService.selectNextFork(this);
    }

    @Override
    public boolean selectPrevFork() {
        return ModelSelectionService.selectPrevFork(this);
    }

    @Override
    public boolean selectNextLocomotive() {
        return ModelSelectionService.selectNextLocomotive(this);
    }

    @Override
    public boolean selectPrevLocomotive() {
        return ModelSelectionService.selectPrevLocomotive(this);
    }

    @Override
    public Locomotive getSelectedLocomotive() {
        return selectedLocomotive;
    }

    @Override
    public void setSelectedLocomotive(Locomotive selectedLocomotive) {
        this.selectedLocomotive = selectedLocomotive;
    }

    @Override
    public boolean selectLocomotive(int id) {
        return ModelSelectionService.selectLocomotive(this, id);
    }

    @Override
    public List<RailSemaphore> getSemaphores() {
        return this.semaphores;
    }

    @Override
    public void addSemaphore(RailSemaphore semaphore) {
        if (!this.semaphores.contains(semaphore)) {
            this.semaphores.add(semaphore);
            if (semaphore.getTrack() != null) {
                semaphore.getTrack().setComponent(semaphore);
            }
            getEconomyManager().onSemaphoreConstructed(semaphore);
            setupSemaphoreSystemListeners(semaphore);
            mapChanged = true;
        }
    }

    private void setupSemaphoreSystemListeners(RailSemaphore semaphore) {
        ModelListenerService.setupSemaphoreSystemListeners(this, semaphore);
    }

    @Override
    public void removeSemaphore(RailSemaphore semaphore) {
        if (this.semaphores.remove(semaphore)) {
            if (semaphore.getTrack() != null) {
                semaphore.getTrack().setComponent(null);
            }
            getEconomyManager().onSemaphoreDestroyed(semaphore);
            mapChanged = true;
        }
    }

    @Override
    public boolean moveSensor(Sensor sensor, Dir dir) {
        return TrackElementMovementService.moveSensor(this, sensor, dir);
    }

    @Override
    public boolean moveSensorForward(Sensor sensor) {
        return TrackElementMovementService.moveSensorForward(this, sensor);
    }

    @Override
    public boolean moveSensorBackward(Sensor sensor) {
        return TrackElementMovementService.moveSensorBackward(this, sensor);
    }

    @Override
    public RailSemaphore getSemaphoreAt(Point pos) {
        for (RailSemaphore semaphore : getSemaphores()) {
            if (semaphore.getPosition().equals(pos)) {
                return semaphore;
            }
        }
        return null;
    }

    @Override
    public boolean selectNextSemaphore() {
        return ModelSelectionService.selectNextSemaphore(this);
    }

    @Override
    public boolean selectPrevSemaphore() {
        return ModelSelectionService.selectPrevSemaphore(this);
    }

    @Override
    public RailSemaphore getSelectedSemaphore() {
        return selectedSemaphore;
    }

    @Override
    public void setSelectedSemaphore(RailSemaphore selectedSemaphore) {
        this.selectedSemaphore = selectedSemaphore;
    }

    @Override
    public letrain.track.SpeedSignal getSelectedSpeedSignal() {
        return selectedSpeedSignal;
    }

    @Override
    public void setSelectedSpeedSignal(letrain.track.SpeedSignal selectedSpeedSignal) {
        this.selectedSpeedSignal = selectedSpeedSignal;
    }

    @Override
    public boolean selectSemaphore(int id) {
        return ModelSelectionService.selectSemaphore(this, id);
    }

    public java.util.List<letrain.track.SpeedSignal> getSpeedSignals() {
        return ModelSelectionService.getSpeedSignals(this);
    }

    @Override
    public boolean selectNextSpeedSignal() {
        return ModelSelectionService.selectNextSpeedSignal(this);
    }

    @Override
    public boolean selectPrevSpeedSignal() {
        return ModelSelectionService.selectPrevSpeedSignal(this);
    }

    @Override
    public boolean selectSpeedSignal(int id) {
        return ModelSelectionService.selectSpeedSignal(this, id);
    }

    @Override
    public RailSemaphore getSemaphore(int id) {
        for (RailSemaphore semaphore : getSemaphores()) {
            if (semaphore.getId() == id) {
                return semaphore;
            }
        }
        return null;
    }

    @Override
    public void setShowId(boolean b) {
        this.showId = b;
    }

    @Override
    public boolean isShowId() {
        return this.showId;
    }

    @Override
    public List<String> setProgram(String program) {
        this.program = program;
        return getAutomationEngine().setProgram(program);
    }

    public void reestablishSystemListeners() {
        ModelListenerService.reestablishSystemListeners(this);
    }

    @Override
    public String getProgram() {
        return this.program;
    }

    @Override
    public void loadAndUnloadTrains() {
        getSimulationService().handleIndustrialActions();
    }

    @Override
    public List<Station> getStations() {
        return this.stations;
    }

    @Override
    public void addStation(Station station) {
        if (!stations.contains(station)) {
            stations.add(station);
            if (station.getTrack() != null) {
                station.getTrack().setComponent(station);
            }
            getEconomyManager().onStationConstructed();
            setupStationSystemListeners(station);
            mapChanged = true;
        }
    }

    private void setupStationSystemListeners(Station station) {
        ModelListenerService.setupStationSystemListeners(this, station);
    }

    @Override
    public void removeStation(Station station) {
        if (stations.remove(station)) {
            if (station.getTrack() != null) {
                station.getTrack().setComponent(null);
            }
            getEconomyManager().onStationDestroyed();
            mapChanged = true;
        }
    }

    @Override
    public void applyStationRoleByIndustry(Station station, Point position) {
        TrackElementMovementService.applyStationRoleByIndustry(this.groundMap, station, position);
    }

    @Override
    public Station getStation(int id) {
        for (Station station : getStations()) {
            if (station.getId() == id) {
                return station;
            }
        }
        return null;
    }

    @Override
    public Station findStationByName(String name) {
        for (Station s : getStations()) {
            if (name.equals(s.getName())) {
                return s;
            }
        }
        return null;
    }

    @Override
    public Sensor findSensorByName(String name) {
        for (Sensor s : getSensors()) {
            if (name.equals(s.getName())) {
                return s;
            }
        }
        return null;
    }

    @Override
    public Train findTrainByName(String name) {
        for (Locomotive l : getLocomotives()) {
            Train t = l.getTrain();
            if (t != null && name.equals(t.getName())) {
                return t;
            }
        }
        return null;
    }

    @Override
    public Station getSelectedStation() {
        return selectedStation;
    }

    @Override
    public void setSelectedStation(Station selectedStation) {
        this.selectedStation = selectedStation;
    }

    @Override
    public boolean selectNextStation() {
        return ModelSelectionService.selectNextStation(this);
    }

    @Override
    public boolean selectPrevStation() {
        return ModelSelectionService.selectPrevStation(this);
    }

    @Override
    public boolean selectStation(int id) {
        return ModelSelectionService.selectStation(this, id);
    }

    public void updateGroundMap(Point scrollOffset, int columns, int rows) {
        this.groundMap.renderBlock(scrollOffset.getX(), scrollOffset.getY(), columns, rows);
    }

    @Override
    public EconomyManager getEconomyManager() {
        return this.economyManager;
    }

    private boolean xRayActive = false;

    @Override
    public boolean isXRayActive() {
        return xRayActive;
    }

    @Override
    public void setXRayActive(boolean xRayActive) {
        this.xRayActive = xRayActive;
    }

    @Override
    public CargoTypes getSelectedWagonType() {
        if (selectedWagonType == null) {
            selectedWagonType = CargoTypes.GOLD;
        }
        return selectedWagonType;
    }

    @Override
    public void setSelectedWagonType(CargoTypes type) {
        this.selectedWagonType = type;
    }

    @JsonIgnore

    @Override
    public void setMark(String name, letrain.map.Point pos) {
        marks.put(name, new letrain.map.Point(pos.getX(), pos.getY()));
    }

    @Override
    public letrain.map.Point getMark(String name) {
        return marks.get(name);
    }

    @Override
    public java.util.Map<String, letrain.map.Point> getMarks() {
        return marks;
    }

    @Override
    public RailTrack getCursorRailTrack() {
        return getRailMap().getTrackAt(getCursor().getPosition());
    }

    @JsonIgnore
    @Override
    public List<GameModeMenuOption> getMenuModel() {
        return ModelReportService.createMenuModel(this);
    }

    @Override
    public int getQuantifier() {
        return quantifier;
    }

    @Override
    public void setQuantifier(int quantifier) {
        this.quantifier = quantifier;
    }

    @Override
    public int getQuantifierSteps() {
        return quantifierSteps;
    }

    @Override
    public void setQuantifierSteps(int quantifierSteps) {
        this.quantifierSteps = quantifierSteps;
    }

    @Override
    public void setLastSaveTime(LocalDateTime now) {
        this.lastSaveTime = now;
    }

    @Override
    public letrain.time.GameClock getGameClock() {
        return gameClock;
    }

    @com.fasterxml.jackson.annotation.JsonProperty("elapsedTicks")
    public long getElapsedTicks() {
        return gameClock.elapsedTicks();
    }

    @com.fasterxml.jackson.annotation.JsonProperty("elapsedTicks")
    public void setElapsedTicks(long elapsedTicks) {
        gameClock.setElapsedTicks(elapsedTicks);
    }

    @Override
    public LocalDateTime getLastSaveTime() {
        return this.lastSaveTime;
    }

    @JsonIgnore
    @Override
    public CargoTypes getStationGhostCargoType() {
        return TrackElementMovementService.getStationGhostCargoType(groundMap,
                cursor.getPosition());
    }

    @JsonIgnore
    @Override
    public CargoTypes.StationRole getStationGhostRole() {
        return TrackElementMovementService.getStationGhostRole(groundMap, cursor.getPosition());
    }

    @Override
    public EventLogManager getEventLogManager() {
        return eventLogManager;
    }

    @JsonIgnore
    private transient letrain.command.CommandJournal commandJournal;

    @Override
    public letrain.command.CommandJournal getCommandJournal() {
        if (commandJournal == null) {
            commandJournal = new letrain.command.CommandJournal();
        }
        return commandJournal;
    }

    /** Binds a presenter-owned journal so it survives model swaps (undo/redo/load). */
    @Override
    public void setCommandJournal(letrain.command.CommandJournal journal) {
        this.commandJournal = journal;
    }

    @JsonIgnore
    @Override
    public String getGameObjectsReport() {
        return ModelReportService.generateGameObjectsReport(this);
    }

    private String commandText = "";
    private String commandError = "";

    @Override
    public String getCommandText() {
        return commandText;
    }

    @Override
    public void setCommandText(String text) {
        this.commandText = text;
    }

    @Override
    public String getCommandError() {
        return commandError;
    }

    @Override
    public void setCommandError(String error) {
        this.commandError = error;
    }

    @Override
    @JsonIgnore
    public String getRailwayGraphReport() {
        return ModelReportService.generateRailwayGraphReport(this);
    }

    public void setEconomyManager(EconomyManager economyManager) {
        this.economyManager = economyManager;
    }

    public void setGroundMap(letrain.ground.GroundMap groundMap) {
        this.groundMap = groundMap;
    }

    public void setLocomotives(List<Locomotive> locomotives) {
        this.locomotives = locomotives;
    }

    public void setWagons(List<Wagon> wagons) {
        this.wagons = wagons;
    }

    public void setCursor(Cursor cursor) {
        this.cursor = cursor;
    }

    public void setForks(List<ForkRailTrack> forks) {
        this.forks = forks;
    }

    public void setSensors(List<Sensor> sensors) {
        this.sensors = sensors;
    }

    public void setSemaphores(List<RailSemaphore> semaphores) {
        this.semaphores = semaphores;
    }

    public void setStations(List<Station> stations) {
        this.stations = stations;
    }

    public void setNextLocomotiveId(int nextLocomotiveId) {
        this.nextLocomotiveId = nextLocomotiveId;
    }

    public void setNextForkId(int nextForkId) {
        this.nextForkId = nextForkId;
    }

    public void setNextSensorId(int nextSensorId) {
        this.nextSensorId = nextSensorId;
    }

    @Override
    public int nextSpeedSignalId() {
        return ++nextSpeedSignalId;
    }

    public int getNextSpeedSignalId() {
        return nextSpeedSignalId;
    }

    public void setNextSpeedSignalId(int nextSpeedSignalId) {
        this.nextSpeedSignalId = nextSpeedSignalId;
    }

    @Override
    public letrain.track.SpeedSignal getSpeedSignal(int id) {
        for (Sensor sensor : getSensors()) {
            if (sensor instanceof letrain.track.SpeedSignal && sensor.getId() == id) {
                return (letrain.track.SpeedSignal) sensor;
            }
        }
        return null;
    }

    @Override
    public letrain.track.SpeedSignal findSpeedSignalByName(String name) {
        for (Sensor sensor : getSensors()) {
            if (sensor instanceof letrain.track.SpeedSignal && name.equals(sensor.getName())) {
                return (letrain.track.SpeedSignal) sensor;
            }
        }
        return null;
    }


    public void setNextSemaphoreId(int nextSemaphoreId) {
        this.nextSemaphoreId = nextSemaphoreId;
    }

    public void setNextTrainId(int nextTrainId) {
        this.nextTrainId = nextTrainId;
    }

    public void setNextStationId(int nextStationId) {
        this.nextStationId = nextStationId;
    }

    public void setSeed(int seed) {
        this.seed = seed;
    }

    public int getSeed() {
        return seed;
    }

    /**
     * Creates a model whose procedural terrain is generated from {@code seed} (ADR-020 scenario
     * import). Use it to rebuild a fresh world that a scenario file will be replayed onto, so the
     * reconstructed infrastructure matches the original world.
     */
    public Model(int seed) {
        this();
        this.seed = seed;
        this.groundMap = new letrain.ground.impl.GroundMap(seed, this.economyManager);
    }

    public void setEventLogManager(EventLogManager eventLogManager) {
        this.eventLogManager = eventLogManager;
    }

    @Override
    @JsonIgnore
    public letrain.segments.BlockManager getBlockManager() {
        return blockManager;
    }

    @Override
    @JsonIgnore
    public letrain.utils.SimulationScheduler getScheduler() {
        return scheduler;
    }

    private letrain.track.Sensor selectedSensor;

    @Override
    public letrain.track.Sensor getSelectedSensor() {
        return selectedSensor;
    }

    @Override
    public void setSelectedSensor(letrain.track.Sensor selectedSensor) {
        this.selectedSensor = selectedSensor;
    }

    @Override
    public boolean selectSensor(int id) {
        return ModelSelectionService.selectSensor(this, id);
    }

    @Override
    public boolean selectNextSensor() {
        return ModelSelectionService.selectNextSensor(this);
    }

    @Override
    public boolean selectPrevSensor() {
        return ModelSelectionService.selectPrevSensor(this);
    }

}
