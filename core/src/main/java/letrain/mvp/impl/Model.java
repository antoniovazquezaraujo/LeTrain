package letrain.mvp.impl;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import letrain.economy.EconomyManager;
import letrain.ground.GroundMap;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.map.impl.RailMap;
import letrain.mvp.impl.services.AutomationEngine;
import letrain.mvp.impl.services.SimulationService;
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
        if (seed == 0) {
            seed = 1 + (int) (Math.random() * 255);
        }
        this.groundMap = new letrain.ground.impl.GroundMap(seed, this.economyManager);
        this.cursor = new Cursor();
        this.cursor.setDir(Dir.E);

        int minOffset = 10000;
        int maxOffset = 100000;
        int offsetX = (minOffset + (int) (Math.random() * (maxOffset - minOffset)))
                * (Math.random() > 0.5 ? 1 : -1);
        int offsetY = (minOffset + (int) (Math.random() * (maxOffset - minOffset)))
                * (Math.random() > 0.5 ? 1 : -1);
        this.cursor.setPosition(new Point(offsetX, offsetY));
        this.locomotives = new ArrayList<>();
        this.wagons = new ArrayList<>();
        this.forks = new ArrayList<>();
        this.sensors = new ArrayList<>();
        this.semaphores = new ArrayList<>();
        this.stations = new ArrayList<>();
        this.map = new RailMap();

        this.addCoreTrainEventListener(new CoreTrainEventListener() {
            @Override
            public void onCrash(Train train, Point pos, int speed) {
                eventLogManager.addEntry("CRASH! Train " + train.getId() + " crashed!");
                getEconomyManager().onTrainCrashed(train);
            }

            @Override
            public void onContact(Train train, Point pos, int speed) {
                eventLogManager
                        .addEntry("Train " + train.getId() + " contact (speed=" + speed + ")");
            }

            @Override
            public void onLink(Train train) {
                eventLogManager.addEntry("Train " + train.getId() + " linked");
            }

            @Override
            public void onUnlink(Train train) {
                eventLogManager.addEntry("Train " + train.getId() + " unlinked");
            }
        });
        this.program = "";
        selectedLocomotiveIndex = 0;
        selectedForkIndex = 0;
        selectedSemaphoreIndex = 0;
        selectedStationIndex = 0;
    }

    public void postLoadInit() {
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
        this.addCoreTrainEventListener(new CoreTrainEventListener() {
            @Override
            public void onCrash(Train train, Point pos, int speed) {
                eventLogManager.addEntry("CRASH! Train " + train.getId() + " crashed!");
                getEconomyManager().onTrainCrashed(train);
            }

            @Override
            public void onContact(Train train, Point pos, int speed) {
                eventLogManager
                        .addEntry("Train " + train.getId() + " contact (speed=" + speed + ")");
            }

            @Override
            public void onLink(Train train) {
                eventLogManager.addEntry("Train " + train.getId() + " linked");
            }

            @Override
            public void onUnlink(Train train) {
                eventLogManager.addEntry("Train " + train.getId() + " unlinked");
            }
        });
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
        if (track.getSensor() != null) {
            if (track.getSensor() instanceof Station) {
                addStation((Station) track.getSensor());
            } else {
                addSensor(track.getSensor());
            }
        }
        if (track.getSemaphore() != null) {
            addSemaphore(track.getSemaphore());
        }
        mapChanged = true;
    }

    @Override
    public RailTrack removeTrack(Point point) {
        RailTrack track = map.getTrackAt(point);
        if (track != null) {
            if (track.getSensor() != null) {
                if (track.getSensor() instanceof Station) {
                    removeStation((Station) track.getSensor());
                } else {
                    removeSensor(track.getSensor());
                }
            }
            if (track.getSemaphore() != null) {
                removeSemaphore(track.getSemaphore());
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
                sensor.getTrack().setSensor(sensor);
            }
            getEconomyManager().onSensorConstructed(sensor);
            setupSensorSystemListeners(sensor);
            mapChanged = true;
        }
    }

    private void setupSensorSystemListeners(Sensor sensor) {
        final int id = sensor.getId();
        sensor.addSystemSensorEventListener(new letrain.track.SensorEventListener() {
            @Override
            public void onEnterTrain(Train train, boolean isForward) {
                eventLogManager.addEntry("Train " + train.getId() + " entered Sensor " + id
                        + (isForward ? " (forward)" : " (backward)"));
            }

            @Override
            public void onExitTrain(Train train, boolean isForward) {
                eventLogManager.addEntry("Train " + train.getId() + " exited Sensor " + id);
            }
        });
    }

    @Override
    public void removeSensor(Sensor sensor) {
        if (sensors.remove(sensor)) {
            if (sensor.getTrack() != null) {
                sensor.getTrack().setSensor(null);
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
        final int id = fork.getId();
        fork.addSystemForkEventListener(new letrain.track.ForkEventListener() {
            @Override
            public void onEnterTrain(Train train, boolean isForward) {
                eventLogManager.addEntry("Train " + train.getId() + " entered Fork " + id
                        + (isForward ? " (forward)" : " (backward)"));
            }

            @Override
            public void onDirectionChanged(boolean normal) {
                eventLogManager
                        .addEntry("Fork " + id + " set to " + (normal ? "Normal" : "Alternative"));
                // Despertar a todos los trenes cuando cambia un desvío
                for (Locomotive loco : locomotives) {
                    if (loco.getTrain() != null) {
                        loco.getTrain().resetSafetyTimer();
                    }
                }
            }

            @Override
            public void onExitTrain(Train train, boolean isForward) {
                eventLogManager.addEntry("Train " + train.getId() + " exited Fork " + id);
            }
        });
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
        for (ForkRailTrack fork : getForks()) {
            if (fork.getId() == id) {
                selectedFork = fork;
                selectedForkIndex = forks.indexOf(fork);
                return true;
            }
        }
        return false;
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
        if (getForks().isEmpty()) {
            return false;
        }
        selectedForkIndex++;
        if (selectedForkIndex >= getForks().size()) {
            selectedForkIndex = 0;
        }
        selectedFork = getForks().get(selectedForkIndex);
        return true;
    }

    @Override
    public boolean selectPrevFork() {
        if (getForks().isEmpty()) {
            return false;
        }
        selectedForkIndex--;
        if (selectedForkIndex < 0) {
            selectedForkIndex = getForks().size() - 1;
        }
        selectedFork = getForks().get(selectedForkIndex);
        return true;
    }

    @Override
    public boolean selectNextLocomotive() {
        if (getLocomotives().isEmpty()) {
            return false;
        }
        do {
            selectedLocomotiveIndex++;
            if (selectedLocomotiveIndex >= getLocomotives().size()) {
                selectedLocomotiveIndex = 0;
            }
            selectedLocomotive = getLocomotives().get(selectedLocomotiveIndex);
        } while (!selectedLocomotive.isDirectorLinker()
                && selectedLocomotiveIndex < getLocomotives().size());
        return true;
    }

    @Override
    public boolean selectPrevLocomotive() {
        if (getLocomotives().isEmpty()) {
            return false;
        }
        do {
            selectedLocomotiveIndex--;
            if (selectedLocomotiveIndex < 0) {
                selectedLocomotiveIndex = getLocomotives().size() - 1;
            }
            selectedLocomotive = getLocomotives().get(selectedLocomotiveIndex);
        } while (!selectedLocomotive.isDirectorLinker() && selectedLocomotiveIndex >= 0);
        return true;
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
        for (Locomotive loco : locomotives) {
            if (loco.getId() == id) {
                selectedLocomotive = loco;
                selectedLocomotiveIndex = locomotives.indexOf(loco);
                return true;
            }
        }
        return false;
    }

    @Override
    public List<RailSemaphore> getSemaphores() {
        return this.semaphores;
    }

    @Override
    public void addSemaphore(RailSemaphore semaphore) {
        if (!this.semaphores.contains(semaphore)) {
            this.semaphores.add(semaphore);
            getEconomyManager().onSemaphoreConstructed(semaphore);
            RailTrack track = map.getTrackAt(semaphore.getPosition());
            if (track != null) {
                track.setSemaphore(semaphore);
            }
            setupSemaphoreSystemListeners(semaphore);
            mapChanged = true;
        }
    }

    private void setupSemaphoreSystemListeners(RailSemaphore semaphore) {
        final int id = semaphore.getId();
        semaphore.addSystemSemaphoreEventListener(new letrain.track.SemaphoreEventListener() {
            @Override
            public void onOpen() {
                eventLogManager.addEntry("Semaphore " + id + " opened");
            }

            @Override
            public void onClosed() {
                eventLogManager.addEntry("Semaphore " + id + " closed");
            }

            @Override
            public void onEnterTrain(Train train, boolean isForward) {
                eventLogManager.addEntry("Train " + train.getId() + " entered Semaphore " + id
                        + (isForward ? " (forward)" : " (backward)"));
            }

            @Override
            public void onExitTrain(Train train, boolean isForward) {
                eventLogManager.addEntry("Train " + train.getId() + " exited Semaphore " + id);
            }
        });
    }

    @Override
    public void removeSemaphore(RailSemaphore semaphore) {
        if (this.semaphores.remove(semaphore)) {
            getEconomyManager().onSemaphoreDestroyed(semaphore);
            RailTrack track = map.getTrackAt(semaphore.getPosition());
            if (track != null) {
                track.setSemaphore(null);
            }
            mapChanged = true;
        }
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
        if (getSemaphores().isEmpty()) {
            return false;
        }
        selectedSemaphoreIndex++;
        if (selectedSemaphoreIndex >= getSemaphores().size()) {
            selectedSemaphoreIndex = 0;
        }
        selectedSemaphore = getSemaphores().get(selectedSemaphoreIndex);
        return true;
    }

    @Override
    public boolean selectPrevSemaphore() {
        if (getSemaphores().isEmpty()) {
            return false;
        }
        selectedSemaphoreIndex--;
        if (selectedSemaphoreIndex < 0) {
            selectedSemaphoreIndex = getSemaphores().size() - 1;
        }
        selectedSemaphore = getSemaphores().get(selectedSemaphoreIndex);
        return true;
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
        for (RailSemaphore semaphore : getSemaphores()) {
            if (semaphore.getId() == id) {
                selectedSemaphore = semaphore;
                selectedSemaphoreIndex = semaphores.indexOf(semaphore);
                return true;
            }
        }
        return false;
    }

    public java.util.List<letrain.track.SpeedSignal> getSpeedSignals() {
        return sensors.stream().filter(s -> s instanceof letrain.track.SpeedSignal)
                .map(s -> (letrain.track.SpeedSignal) s)
                .collect(java.util.stream.Collectors.toList());
    }

    @Override
    public boolean selectNextSpeedSignal() {
        java.util.List<letrain.track.SpeedSignal> sigs = getSpeedSignals();
        if (sigs.isEmpty()) {
            return false;
        }
        selectedSpeedSignalIndex++;
        if (selectedSpeedSignalIndex >= sigs.size()) {
            selectedSpeedSignalIndex = 0;
        }
        selectedSpeedSignal = sigs.get(selectedSpeedSignalIndex);
        return true;
    }

    @Override
    public boolean selectPrevSpeedSignal() {
        java.util.List<letrain.track.SpeedSignal> sigs = getSpeedSignals();
        if (sigs.isEmpty()) {
            return false;
        }
        selectedSpeedSignalIndex--;
        if (selectedSpeedSignalIndex < 0) {
            selectedSpeedSignalIndex = sigs.size() - 1;
        }
        selectedSpeedSignal = sigs.get(selectedSpeedSignalIndex);
        return true;
    }

    @Override
    public boolean selectSpeedSignal(int id) {
        java.util.List<letrain.track.SpeedSignal> sigs = getSpeedSignals();
        for (int i = 0; i < sigs.size(); i++) {
            if (sigs.get(i).getId() == id) {
                selectedSpeedSignal = sigs.get(i);
                selectedSpeedSignalIndex = i;
                return true;
            }
        }
        return false;
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
        if (sensors != null) {
            sensors.forEach(this::setupSensorSystemListeners);
        }
        if (forks != null) {
            forks.forEach(this::setupForkSystemListeners);
        }
        if (stations != null) {
            stations.forEach(this::setupStationSystemListeners);
        }
        if (semaphores != null) {
            semaphores.forEach(this::setupSemaphoreSystemListeners);
        }
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
                station.getTrack().setSensor(station);
            }
            getEconomyManager().onStationConstructed();
            setupStationSystemListeners(station);
            mapChanged = true;
        }
    }

    private void setupStationSystemListeners(Station station) {
        final int id = station.getId();
        station.addSystemStationEventListener(new letrain.track.StationEventListener() {
            @Override
            public void onEnterTrain(Train train, boolean isForward) {
                eventLogManager.addEntry("Train " + train.getId() + " entered Station " + id);
            }

            @Override
            public void onExitTrain(Train train, boolean isForward) {
                eventLogManager.addEntry("Train " + train.getId() + " exited Station " + id);
            }

            @Override
            public void onLoad(Train train) {}

            @Override
            public void onUnload(Train train) {}

            @Override
            public void onStartLoad(Train train) {
                eventLogManager
                        .addEntry("Train " + train.getId() + " starting Load at Station " + id);
            }

            @Override
            public void onEndLoad(Train train) {
                eventLogManager.addEntry("Train " + train.getId() + " ended Load at Station " + id);
            }

            @Override
            public void onStartUnload(Train train) {
                eventLogManager
                        .addEntry("Train " + train.getId() + " starting Unload at Station " + id);
            }

            @Override
            public void onEndUnload(Train train) {
                eventLogManager
                        .addEntry("Train " + train.getId() + " ended Unload at Station " + id);
            }
        });
    }

    @Override
    public void removeStation(Station station) {
        if (stations.remove(station)) {
            if (station.getTrack() != null) {
                station.getTrack().setSensor(null);
            }
            getEconomyManager().onStationDestroyed();
            mapChanged = true;
        }
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
        if (getStations().isEmpty()) {
            return false;
        }
        selectedStationIndex++;
        if (selectedStationIndex >= getStations().size()) {
            selectedStationIndex = 0;
        }
        selectedStation = getStations().get(selectedStationIndex);
        return true;
    }

    @Override
    public boolean selectPrevStation() {
        if (getStations().isEmpty()) {
            return false;
        }
        selectedStationIndex--;
        if (selectedStationIndex < 0) {
            selectedStationIndex = getStations().size() - 1;
        }
        selectedStation = getStations().get(selectedStationIndex);
        return true;
    }

    @Override
    public boolean selectStation(int id) {
        for (Station station : getStations()) {
            if (station.getId() == id) {
                selectedStation = station;
                selectedStationIndex = stations.indexOf(station);
                return true;
            }
        }
        return false;
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
        return Arrays.asList(new GameModeMenuOption("&Rails",
                "[⏴⏵⏶⏷/hjkl]:Move [Shift]:Add rail [Ctrl]:Remove rail [Ins]:Add sensor [Home]:Add sem [Del]:Add speed [End]:Add station [#]:Steps [Space]:Reset steps",
                () -> true, () -> (this.getMode() == GameMode.RAILS), () -> (GameMode.RAILS)),
                new GameModeMenuOption("&Add",
                        "[s]:Station [e]:Sensor [m]:Semaphore [g]:Speed Signal",
                        () -> true, () -> this.getMode() == GameMode.ADD, () -> GameMode.ADD),
                new GameModeMenuOption("&Drive",
                        "[⏴⏵/hl]:Select [o]:Locate [m]:Motor [⏶/k]:Accel [⏷/j]:Decel [Space]:Rev [Enter]:Load [#]:ID",
                        () -> !this.getLocomotives().isEmpty(),
                        () -> this.getMode() == GameMode.DRIVE, () -> GameMode.DRIVE),
                new GameModeMenuOption("&Forks",
                        "[⏴⏵/hl]:Select [o]:Locate [Space]:Toggle [#]:ID",
                        () -> !this.getForks().isEmpty(), () -> this.getMode() == GameMode.FORKS,
                        () -> GameMode.FORKS),
                new GameModeMenuOption("&Semaphores",
                        "[⏴⏵/hl]:Select [o]:Locate [Space]:Toggle [#]:ID",
                        () -> !this.getSemaphores().isEmpty(),
                        () -> this.getMode() == GameMode.SEMAPHORES, () -> GameMode.SEMAPHORES),
                new GameModeMenuOption("S&ensors",
                        "[⏴⏵/hl]:Select [o]:Locate [#]:ID",
                        () -> getSensors().stream().anyMatch(s -> s.getClass() == letrain.track.Sensor.class),
                        () -> this.getMode() == GameMode.SENSORS, () -> GameMode.SENSORS),
                new GameModeMenuOption("Si&gnals",
                        "[⏴⏵/hl]:Select [m]:Max/Min [⏶⏷/kj]:Limit [Space]:Invert",
                        () -> !getSpeedSignals().isEmpty(),
                        () -> this.getMode() == GameMode.SPEED_SIGNALS,
                        () -> GameMode.SPEED_SIGNALS),
                new GameModeMenuOption("&Trains",
                        "[A-Z]: LOCOMOTIVE | [a-z]: WAGON | [ENTER]: FINISH",
                        () -> this.getCursorRailTrack() != null,
                        () -> this.getMode() == GameMode.TRAINS, () -> GameMode.TRAINS),
                new GameModeMenuOption("&Couple",
                        "[⏶⏷/kj]:Front/Back [⏴⏵/hl]:Sel wagons [o]:Locate [Space]:Couple",
                        () -> this.canEnterLinkMode(), () -> this.getMode() == GameMode.LINK,
                        () -> GameMode.LINK),
                new GameModeMenuOption("&Uncouple",
                        "[⏶⏷/kj]:Front/Back [⏴⏵/hl]:Sel wagons [o]:Locate [Space]:Uncouple",
                        () -> this.canEnterUnlinkMode(), () -> this.getMode() == GameMode.UNLINK,
                        () -> GameMode.UNLINK),
                new GameModeMenuOption("&Program",
                        "Integrated Development Environment (Apply/Save/Load/Cancel)", () -> true,
                        () -> this.getMode() == GameMode.PROGRAM, () -> GameMode.PROGRAM),
                new GameModeMenuOption("Statio&ns",
                        "[⏴⏵/hl]:Select [o]:Locate [#]:ID",
                        () -> !this.getStations().isEmpty(),
                        () -> this.getMode() == GameMode.STATIONS, () -> GameMode.STATIONS));
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
    public LocalDateTime getLastSaveTime() {
        return this.lastSaveTime;
    }

    @JsonIgnore
    @Override
    public CargoTypes getStationGhostCargoType() {
        Integer terrain = groundMap.findClosestIndustry(cursor.getPosition(), 5);
        if (terrain != null) {
            return CargoTypes.IndustryMapper.getCargoForTerrain(terrain);
        }
        return CargoTypes.NONE;
    }

    @JsonIgnore
    @Override
    public CargoTypes.StationRole getStationGhostRole() {
        Integer terrain = groundMap.findClosestIndustry(cursor.getPosition(), 5);
        if (terrain != null) {
            return CargoTypes.IndustryMapper.getRoleForTerrain(terrain);
        }
        return CargoTypes.StationRole.GENERIC;
    }

    @Override
    public EventLogManager getEventLogManager() {
        return eventLogManager;
    }

    @JsonIgnore
    @Override
    public String getGameObjectsReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("--- TRAINS ---\n");
        java.util.Set<Train> processedTrains = new java.util.HashSet<>();
        for (Locomotive loco : locomotives) {
            Train train = loco.getTrain();
            if (train != null && !processedTrains.contains(train)) {
                processedTrains.add(train);
                sb.append("Train ID: ").append(train.getId()).append("\n");
                sb.append("  Segments Owned: ");
                java.util.List<letrain.segments.Segment> owned =
                        getBlockManager().getOwnedSegments(train);
                for (letrain.segments.Segment s : owned)
                    sb.append(s.getId()).append(" ");
                sb.append("\n");

                sb.append("  Current Segment: ")
                        .append(train.getSafetyManager().getCurrentSegment() != null
                                ? train.getSafetyManager().getCurrentSegment().getId()
                                : "None")
                        .append("\n");
                sb.append("  Next Segment: ")
                        .append(train.getSafetyManager().getNextSegment() != null
                                ? train.getSafetyManager().getNextSegment().getId()
                                : "None")
                        .append("\n");
                if (!train.getSafetyManager().hasPermissionToMove()
                        && train.getSafetyManager().getNextSegment() != null) {
                    java.util.List<Train> blockers =
                            getBlockManager().getOwners(train.getSafetyManager().getNextSegment());
                    sb.append("  Permission: WAITING (Blocked by: ");
                    if (blockers.isEmpty()) {
                        sb.append("Logic/Retry Timer");
                    } else {
                        for (Train b : blockers)
                            sb.append("Train ").append(b.getId()).append(" ");
                    }
                    sb.append(")\n");
                } else {
                    sb.append("  Permission: ").append(
                            train.getSafetyManager().hasPermissionToMove() ? "GRANTED" : "WAITING")
                            .append("\n");
                }

                int wagonCount = 0;
                for (letrain.vehicle.rail.Linker l : train.getLinkers()) {
                    if (l instanceof Wagon) {
                        wagonCount++;
                    }
                }
                sb.append("  Wagons: ").append(wagonCount).append("\n");
                if (train.getDirectorLinker() != null) {
                    if (train.getDirectorLinker() instanceof letrain.vehicle.rail.Linker)
                        sb.append("  Pos: ")
                                .append(((letrain.vehicle.rail.Linker) train.getDirectorLinker())
                                        .getPosition())
                                .append("\n");
                    sb.append("  Speed: ").append(train.getDirectorLinker().getSpeed())
                            .append("\n");
                }
                if (train.getLogisticsManager().isLoading())
                    sb.append("  State: LOADING at Station ")
                            .append(train.getLogisticsManager().getStationAtTrain().getId())
                            .append("\n");
                else if (train.isStalled())
                    sb.append("  State: STALLED\n");
                else
                    sb.append("  State: CRUIZING\n");
                for (letrain.vehicle.rail.Linker linker : train.getLinkers()) {
                    if (linker instanceof Wagon) {
                        Wagon w = (Wagon) linker;
                        if (w.getCargoAmount() > 0)
                            sb.append("    Wagon: ").append(w.getCargoType()).append(" (")
                                    .append(w.getCargoAmount()).append("/")
                                    .append(w.getMaxCapacity()).append(")\n");
                    }
                }
            }
        }
        sb.append("\n--- STATIONS ---\n");
        for (Station s : stations) {
            sb.append("Station ").append(s.getId()).append(": ").append(s.getRole()).append(" ")
                    .append(s.getCargoType()).append(" (").append(s.getStorage()).append("/")
                    .append(s.getMaxStorage()).append(") @ ").append(s.getPosition()).append("\n");
        }
        sb.append("\n--- SENSORS ---\n");
        for (Sensor s : sensors) {
            if (!(s instanceof Station))
                sb.append("Sensor ").append(s.getId()).append(" @ ").append(s.getPosition())
                        .append("\n");
        }
        sb.append("\n--- FORKS ---\n");
        for (ForkRailTrack f : forks) {
            sb.append("Fork ").append(f.getId()).append(" @ ").append(f.getPosition()).append(" (")
                    .append(f.isUsingAlternativeRoute() ? "Alternative" : "Normal").append(")\n");
        }
        sb.append("\n--- SEMAPHORES ---\n");
        for (RailSemaphore s : semaphores) {
            sb.append("Semaphore ").append(s.getId()).append(" @ ").append(s.getPosition())
                    .append(" (").append(s.isOpen() ? "OPEN" : "CLOSED").append(")\n");
        }
        return sb.toString();
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
        StringBuilder sb = new StringBuilder();
        sb.append(getRailwayGraph().toString());

        sb.append("\n\n--- SEGMENT OWNERSHIP ---\n");
        letrain.segments.BlockManager bm = getBlockManager();
        java.util.Set<letrain.segments.Segment> segments = bm.getAllLockedSegments();

        if (segments.isEmpty()) {
            sb.append("No active segment locks.\n");
        } else {
            for (letrain.segments.Segment s : segments) {
                java.util.List<Train> owners = bm.getOwners(s);
                if (!owners.isEmpty()) {
                    sb.append("Segment ").append(s.getId()).append(" owned by: ");
                    for (Train train : owners) {
                        sb.append("Train ").append(train.getId()).append(" ");
                    }
                    sb.append("\n");
                }
            }
        }

        sb.append("\n").append(getGameObjectsReport());
        return sb.toString();
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
        letrain.track.Sensor s = getSensor(id);
        if (s != null && s.getClass() == letrain.track.Sensor.class) {
            setSelectedSensor(s);
            return true;
        }
        return false;
    }

    @Override
    public boolean selectNextSensor() {
        java.util.List<letrain.track.Sensor> pureSensors = getSensors().stream().filter(s -> s.getClass() == letrain.track.Sensor.class).collect(java.util.stream.Collectors.toList());
        if (pureSensors.isEmpty()) return false;
        if (selectedSensor == null) {
            selectedSensor = pureSensors.get(0);
            return true;
        }
        int i = pureSensors.indexOf(selectedSensor);
        if (i < pureSensors.size() - 1) {
            selectedSensor = pureSensors.get(i + 1);
        } else {
            selectedSensor = pureSensors.get(0);
        }
        return true;
    }

    @Override
    public boolean selectPrevSensor() {
        java.util.List<letrain.track.Sensor> pureSensors = getSensors().stream().filter(s -> s.getClass() == letrain.track.Sensor.class).collect(java.util.stream.Collectors.toList());
        if (pureSensors.isEmpty()) return false;
        if (selectedSensor == null) {
            selectedSensor = pureSensors.get(pureSensors.size() - 1);
            return true;
        }
        int i = pureSensors.indexOf(selectedSensor);
        if (i > 0) {
            selectedSensor = pureSensors.get(i - 1);
        } else {
            selectedSensor = pureSensors.get(pureSensors.size() - 1);
        }
        return true;
    }

}
