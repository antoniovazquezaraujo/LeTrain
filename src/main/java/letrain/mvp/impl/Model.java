package letrain.mvp.impl;

import java.time.LocalDateTime;
import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
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
import letrain.track.CargoTypes;
import letrain.track.InfrastructureManager;
import letrain.track.RailSemaphore;
import letrain.track.Sensor;
import letrain.track.Station;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;
import letrain.vehicle.impl.Cursor;
import letrain.vehicle.impl.rail.Locomotive;
import letrain.vehicle.impl.rail.Train;
import letrain.vehicle.impl.rail.Wagon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIdentityInfo(generator = ObjectIdGenerators.IntSequenceGenerator.class, property = "@id")
@com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
@JsonAutoDetect(
    fieldVisibility = JsonAutoDetect.Visibility.NONE, 
    getterVisibility = JsonAutoDetect.Visibility.NONE, 
    setterVisibility = JsonAutoDetect.Visibility.NONE, 
    isGetterVisibility = JsonAutoDetect.Visibility.NONE, 
    creatorVisibility = JsonAutoDetect.Visibility.NONE
)
public class Model implements letrain.mvp.Model {

    @com.fasterxml.jackson.annotation.JsonUnwrapped
    private VehicleRoster vehicleRoster = new VehicleRoster();

    @JsonUnwrapped
    private InfrastructureManager infrastructureManager = new InfrastructureManager();

    static Logger log = LoggerFactory.getLogger(Model.class);

    @JsonProperty("economyManager")
    @JsonDeserialize(as = letrain.economy.impl.EconomyManager.class)
    EconomyManager economyManager;
    @JsonProperty("eventLogManager")
    EventLogManager eventLogManager;

    @JsonProperty("selectedLocomotiveIndex")
    int selectedLocomotiveIndex;
    @JsonProperty("showId")
    boolean showId = false;

    @JsonProperty("groundMap")
    @JsonDeserialize(as = letrain.ground.impl.GroundMap.class)
    letrain.ground.GroundMap groundMap;
    @JsonProperty("mode")
    GameMode mode = letrain.mvp.Model.GameMode.RAILS;
    @JsonProperty("railMap")
    RailMap map;
    @JsonProperty("cursor")
    Cursor cursor;
    @JsonProperty("nextLocomotiveId")
    int nextLocomotiveId;
    @JsonProperty("nextForkId")
    int nextForkId;
    @JsonIgnore
    private transient CargoTypes selectedWagonType = CargoTypes.GOLD;
    @JsonIgnore
    private transient com.badlogic.gdx.graphics.Camera camera;

    @JsonIgnore
    private transient List<letrain.vehicle.impl.rail.TrainEventListener> trainEventListeners = new ArrayList<>();

    @Override
    public void addTrainEventListener(letrain.vehicle.impl.rail.TrainEventListener listener) {
        if (this.trainEventListeners == null) {
            this.trainEventListeners = new ArrayList<>();
        }
        this.trainEventListeners.add(listener);
        // Apply to existing trains
        for (Locomotive loco : getLocomotives()) {
            if (loco.getTrain() != null) {
                loco.getTrain().addTrainEventListener(listener);
            }
        }
    }

    @JsonProperty("nextSensorId")
    int nextSensorId;
    @JsonProperty("nextSemaphoreId")
    int nextSemaphoreId;
    @JsonProperty("nextTrainId")
    int nextTrainId;
    @JsonProperty("nextStationId")
    int nextStationId;
    @JsonProperty("program")
    String program;
    @JsonProperty("seed")
    int seed = 0;
    @JsonProperty("quantifier")
    int quantifier = 1;
    @JsonProperty("quantifierSteps")
    int quantifierSteps = 0;
    @JsonProperty("lastSaveTime")
    @JsonDeserialize(using = com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer.class)
    @com.fasterxml.jackson.databind.annotation.JsonSerialize(using = com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer.class)
    LocalDateTime lastSaveTime = null;

    @JsonIgnore
    private transient AutomationEngine automationEngine;
    @JsonIgnore
    private transient SimulationService internalSimService;

    @JsonIgnore
    private AutomationEngine getAutomationEngine() {
        if (automationEngine == null) {
            automationEngine = new AutomationEngine(this);
        }
        return automationEngine;
    }

    @JsonIgnore
    private SimulationService getSimulationService() {

        if (internalSimService == null) {
            internalSimService = new SimulationService(this);
        }
        return internalSimService;
    }

    public Model() {
        this.eventLogManager = new EventLogManager();
        this.economyManager = new letrain.economy.impl.EconomyManager(eventLogManager);
        this.economyManager.reloadConfig(); // Initial load
        if (seed == 0) {
            seed = 1 + (int) (Math.random() * 255);
        }
        this.groundMap = new letrain.ground.impl.GroundMap(seed, this.economyManager);
        this.cursor = new Cursor();
        this.cursor.setDir(Dir.E);

        // Randomize starting position far from (0,0) to avoid symmetry artifacts
        int minOffset = 10000;
        int maxOffset = 100000;
        int offsetX = (minOffset + (int) (Math.random() * (maxOffset - minOffset))) * (Math.random() > 0.5 ? 1 : -1);
        int offsetY = (minOffset + (int) (Math.random() * (maxOffset - minOffset))) * (Math.random() > 0.5 ? 1 : -1);
        this.cursor.setPosition(new Point(offsetX, offsetY));
        this.map = new RailMap();

        // Economy: Handle train crashes
        this.addTrainEventListener(new letrain.vehicle.impl.rail.TrainEventListener() {
            @Override
            public void onCrash(Train train, letrain.map.Point pos, int speed) {
                eventLogManager.addEntry("CRASH! Train " + train.getId() + " crashed!");
                getEconomyManager().onTrainCrashed(train);
            }

            @Override
            public void onContact(Train train, letrain.map.Point pos, int speed) {
                eventLogManager.addEntry("Train " + train.getId() + " contact (speed=" + speed + ")");
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
        if (!getLocomotives().isEmpty()) {
            vehicleRoster.setSelectedLocomotive(getLocomotives().get(selectedLocomotiveIndex));
        }
    }

    /**
     * Reinitializes transient fields after deserialization.
     */
    public void postLoadInit() {
        if (this.trainEventListeners == null) {
            this.trainEventListeners = new ArrayList<>();
        } else {
            this.trainEventListeners.clear();
        }
        this.selectedWagonType = CargoTypes.GOLD;
        
        // Re-initialize transient services
        this.automationEngine = new AutomationEngine(this);
        this.internalSimService = new SimulationService(this);
        this.infrastructureManager.postLoadInit();

        // Re-initialize GroundMap
        if (this.groundMap != null) {
            // We need to re-inject economyManager and noise into groundMap
            // since they are transient/circular.
            // Using reflection or a dedicated init method if available.
            // But GroundMap regenerates terrain based on seed.
            // Let's assume we need to re-setup the PerlinNoise.
            try {
                java.lang.reflect.Field noiseField = letrain.ground.impl.GroundMap.class.getDeclaredField("noise");
                noiseField.setAccessible(true);
                noiseField.set(this.groundMap, new letrain.ground.PerlinNoise(this.seed));
                
                java.lang.reflect.Field ecoField = letrain.ground.impl.GroundMap.class.getDeclaredField("economyManager");
                ecoField.setAccessible(true);
                ecoField.set(this.groundMap, this.economyManager);
            } catch (Exception e) {
                log.error("Error re-initializing GroundMap", e);
            }
        }

        // Re-add system listeners (this populates trainEventListeners)
        setupModelTrainEventListeners();
        
        if (getLocomotives() != null) {
            for (Locomotive loco : getLocomotives()) {
                Train train = loco.getTrain();
                if (train != null) {
                    train.postLoadInit();
                    // Re-attach station listener if train is in a station
                    int stationId = train.getStationId();
                    if (stationId != 0) {
                        Station station = getStation(stationId);
                        if (station != null) {
                            train.addTrainEventListener(station);
                        }
                    }
                    // Re-attach model event listeners to restored trains
                    for (letrain.vehicle.impl.rail.TrainEventListener l : trainEventListeners) {
                        train.addTrainEventListener(l);
                    }
                }
            }
        }
        reestablishSystemListeners();
    }

    private void setupModelTrainEventListeners() {
        this.addTrainEventListener(new letrain.vehicle.impl.rail.TrainEventListener() {
            @Override
            public void onCrash(Train train, letrain.map.Point pos, int speed) {
                eventLogManager.addEntry("CRASH! Train " + train.getId() + " crashed!");
                getEconomyManager().onTrainCrashed(train);
            }

            @Override
            public void onContact(Train train, letrain.map.Point pos, int speed) {
                eventLogManager.addEntry("Train " + train.getId() + " contact (speed=" + speed + ")");
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
    public int nextSensorId() {
        return ++nextSensorId;
    }

    @Override
    public int nextTrainId() {
        return ++nextTrainId;
    }

    @Override
    public int nextStationId() {
        return ++nextStationId;
    }

    public double getLinearDistanceBetweenStations(int startStationId, int endStationId) {
        Point from = getStation(startStationId).getPosition();
        Point to = getStation(endStationId).getPosition();
        return Math.sqrt(Math.pow(from.getX() - to.getX(), 2) + Math.pow(from.getY() - to.getY(), 2));
    }

    @Override
    public RailMap getRailMap() {
        return map;
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
        return infrastructureManager.getSensors();
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
        infrastructureManager.addSensor(sensor);
        getEconomyManager().onSensorConstructed(sensor);
        setupSensorSystemListeners(sensor);
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
        if (infrastructureManager.getSensors().remove(sensor)) {
            getEconomyManager().onSensorDestroyed(sensor);
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
    public List<Locomotive> getLocomotives() { return vehicleRoster.getLocomotives(); }

    @Override
    public List<Wagon> getWagons() { return vehicleRoster.getWagons(); }

    @Override
    public void removeWagon(Wagon wagon) {
        if (this.getWagons().remove(wagon)) {
            getEconomyManager().onWagonDestroyed(wagon);
        }
    }

    @Override
    public void addWagon(Wagon wagon) {
        vehicleRoster.addWagon(wagon);
    }

    @Override
    public Cursor getCursor() {
        return cursor;
    }

    @Override
    public List<ForkRailTrack> getForks() {
        return this.infrastructureManager.getForks();
    }

    @Override
    public void addFork(ForkRailTrack fork) {
        this.infrastructureManager.addFork(fork);
        getEconomyManager().onForkConstructed(fork);
        setupForkSystemListeners(fork);
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
                eventLogManager.addEntry("Fork " + id + " set to " + (normal ? "Normal" : "Alternative"));
            }

            @Override
            public void onExitTrain(Train train, boolean isForward) {
                eventLogManager.addEntry("Train " + train.getId() + " exited Fork " + id);
            }
        });
    }

    @Override
    public void removeFork(ForkRailTrack fork) {
        if (this.infrastructureManager.getForks().remove(fork)) {
            getEconomyManager().onForkDestroyed(fork);
        }
    }

    @Override
    public void addLocomotive(Locomotive locomotive) {
        vehicleRoster.addLocomotive(locomotive);
        if (vehicleRoster.getSelectedLocomotive() == null) { vehicleRoster.setSelectedLocomotive(locomotive); }
    }

    @Override
    public void removeLocomotive(Locomotive locomotive) {
        vehicleRoster.removeLocomotive(locomotive);
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
        this.mode = mode;
        if (mode == GameMode.FORKS && infrastructureManager.getSelectedFork() == null && !getForks().isEmpty()) {
            infrastructureManager.setSelectedFork(getForks().get(0));
        }
    }

    @Override
    public ForkRailTrack getSelectedFork() {
        return infrastructureManager.getSelectedFork();
    }

    @Override
    public void setSelectedFork(ForkRailTrack selectedFork) {
        infrastructureManager.setSelectedFork(selectedFork);
    }

    @Override
    public boolean selectFork(int id) {
        for (ForkRailTrack fork : getForks()) {
            if (fork.getId() == id) {
                infrastructureManager.setSelectedFork(fork);
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
        return infrastructureManager.selectNextFork();

    }

    @Override
    public boolean selectPrevFork() {
        return infrastructureManager.selectPrevFork();
    }

    @Override
    public boolean selectNextLocomotive() {
        return vehicleRoster.selectNextLocomotive();
    }

    @Override
    public boolean selectPrevLocomotive() {
        return vehicleRoster.selectPrevLocomotive();
    }

    @Override
    public Locomotive getSelectedLocomotive() { return vehicleRoster.getSelectedLocomotive(); }

    @Override
    public void setSelectedLocomotive(Locomotive selectedLocomotive) { vehicleRoster.setSelectedLocomotive(selectedLocomotive); }

    @Override
    public boolean selectLocomotive(int id) {
        for (Locomotive loco : getLocomotives()) {
            if (loco.getId() == id) {
                vehicleRoster.setSelectedLocomotive(loco);
                selectedLocomotiveIndex = getLocomotives().indexOf(loco);
                return true;
            }
        }
        return false;
    }

    @Override
    public List<RailSemaphore> getSemaphores() {
        return this.infrastructureManager.getSemaphores();
    }

    @Override
    public void addSemaphore(RailSemaphore semaphore) {
        this.infrastructureManager.addSemaphore(semaphore);
        getEconomyManager().onSemaphoreConstructed(semaphore);
        RailTrack track = map.getTrackAt(semaphore.getPosition());
        if (track != null) {
            track.setSemaphore(semaphore);
        }
        setupSemaphoreSystemListeners(semaphore);
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
        if (this.infrastructureManager.getSemaphores().remove(semaphore)) {
            getEconomyManager().onSemaphoreDestroyed(semaphore);
            RailTrack track = map.getTrackAt(semaphore.getPosition());
            if (track != null) {
                track.setSemaphore(null);
            }
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
        return infrastructureManager.selectNextSemaphore();
    }

    @Override
    public boolean selectPrevSemaphore() {
        return infrastructureManager.selectPrevSemaphore();
    }

    @Override
    public RailSemaphore getSelectedSemaphore() {
        return infrastructureManager.getSelectedSemaphore();
    }

    @Override
    public void setSelectedSemaphore(RailSemaphore selectedSemaphore) {
        infrastructureManager.setSelectedSemaphore(selectedSemaphore);
    }

    @Override
    public boolean selectSemaphore(int id) {
        for (RailSemaphore semaphore : getSemaphores()) {
            if (semaphore.getId() == id) {
                infrastructureManager.setSelectedSemaphore(semaphore);
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
        infrastructureManager.getSensors().forEach(this::setupSensorSystemListeners);
        infrastructureManager.getForks().forEach(this::setupForkSystemListeners);
        infrastructureManager.getStations().forEach(this::setupStationSystemListeners);
        infrastructureManager.getSemaphores().forEach(this::setupSemaphoreSystemListeners);
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
        return this.infrastructureManager.getStations();
    }

    @Override
    public void addStation(Station station) {
        infrastructureManager.addStation(station);
        getEconomyManager().onStationConstructed();
        setupStationSystemListeners(station);
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
            public void onLoad(Train train) {
            }

            @Override
            public void onUnload(Train train) {
            }

            @Override
            public void onLink(Train train) {
                eventLogManager.addEntry("Train " + train.getId() + " linked at Station " + id);
            }

            @Override
            public void onUnlink(Train train) {
                eventLogManager.addEntry("Train " + train.getId() + " unlinked at Station " + id);
            }

            @Override
            public void onStartLoad(Train train) {
                eventLogManager.addEntry("Train " + train.getId() + " starting Load at Station " + id);
            }

            @Override
            public void onEndLoad(Train train) {
                eventLogManager.addEntry("Train " + train.getId() + " ended Load at Station " + id);
            }

            @Override
            public void onStartUnload(Train train) {
                eventLogManager.addEntry("Train " + train.getId() + " starting Unload at Station " + id);
            }

            @Override
            public void onEndUnload(Train train) {
                eventLogManager.addEntry("Train " + train.getId() + " ended Unload at Station " + id);
            }
        });
    }

    @Override
    public void removeStation(Station Station) {
        if (infrastructureManager.getStations().remove(Station)) {
            getEconomyManager().onStationDestroyed();
        }
    }

    @Override
    public Station getStation(int id) {
        for (Station Station : getStations()) {
            if (Station.getId() == id) {
                return Station;
            }
        }
        return null;
    }

    @Override
    public Station getSelectedStation() {
        return infrastructureManager.getSelectedStation();
    }

    @Override
    public void setSelectedStation(Station selectedStation) {
        infrastructureManager.setSelectedStation(selectedStation);
    }

    @Override
    public boolean selectNextStation() {
        return infrastructureManager.selectNextStation();
    }

    @Override
    public boolean selectPrevStation() {
        return infrastructureManager.selectPrevStation();
    }

    @Override
    public boolean selectStation(int id) {
        for (Station station : getStations()) {
            if (station.getId() == id) {
                infrastructureManager.setSelectedStation(station);
                return true;
            }
        }
        return false;
    }

    public void updateGroundMap(Point mapScrollPage, int columns, int rows) {
        this.groundMap.renderBlock(mapScrollPage.getX() * columns, mapScrollPage.getY() * rows, columns, rows);
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
        if (selectedWagonType == null)
            selectedWagonType = CargoTypes.GOLD;
        return selectedWagonType;
    }

    @Override
    public void setSelectedWagonType(CargoTypes type) {
        this.selectedWagonType = type;
    }

    @Override
    @JsonIgnore
    public RailTrack getCursorRailTrack() {
        return getRailMap().getTrackAt(getCursor().getPosition());
    }

    @Override
    @JsonIgnore
    public List<GameModeMenuOption> getMenuModel() {
        return Arrays.asList(
                new GameModeMenuOption(
                        "&rails",
                        "[Left/Right]:Rotate [Up/Down]:Move [Shift+Up]:Add rail [Ctrl+Up]:Remove rail [Ctrl/Shift+Down]:Remove rail [Ins]:Add sensor [Home]:Add semaphore [W]:Add station [#]:Steps [Space]:Reset steps",
                        () -> true,
                        () -> (this.getMode() == GameMode.RAILS),
                        () -> (GameMode.RAILS)),
                new GameModeMenuOption(
                        "&drive",
                        "[Left/Right]:Select [m]:Motor On/Off [Up]:Accel [Down]:Decel [Space]:Reverse [Enter]:Load/Unload [#]:Select by ID",
                        () -> !this.getLocomotives().isEmpty(),
                        () -> this.getMode() == GameMode.DRIVE,
                        () -> GameMode.DRIVE),
                new GameModeMenuOption(
                        "&forks",
                        "[Left/Right]:Select [Space]:Toggle [#]:Select by ID",
                        () -> !this.getForks().isEmpty(),
                        () -> this.getMode() == GameMode.FORKS,
                        () -> GameMode.FORKS),
                new GameModeMenuOption(
                        "&semaphores",
                        "[Left/Right]:Select [Space]:Toggle [#]:Select by ID",
                        () -> !this.getSemaphores().isEmpty(),
                        () -> this.getMode() == GameMode.SEMAPHORES,
                        () -> GameMode.SEMAPHORES),
                new GameModeMenuOption(
                        "&trains",
                        "[A-Z]: LOCOMOTIVE | [a-z]: WAGON | [ENTER]: FINISH",
                        () -> this.getCursorRailTrack() != null,
                        () -> this.getMode() == GameMode.TRAINS,
                        () -> GameMode.TRAINS),
                new GameModeMenuOption(
                        "&link",
                        "[Up/Down]:Front/Back [Left/Right]:Select/Unselect wagons [Space]:Link",
                        () -> !this.getLocomotives().isEmpty(),
                        () -> this.getMode() == GameMode.LINK,
                        () -> GameMode.LINK),
                new GameModeMenuOption(
                        "&unlink",
                        "[Up/Down]:Front/Back [Left/Right]:Select/Unselect wagons [Space]:Unlink",
                        () -> !this.getLocomotives().isEmpty(),
                        () -> this.getMode() == GameMode.UNLINK,
                        () -> GameMode.UNLINK),
                new GameModeMenuOption(
                        "&program",
                        "Integrated Development Environment (Apply/Save/Load/Cancel)",
                        () -> true,
                        () -> this.getMode() == GameMode.PROGRAM,
                        () -> GameMode.PROGRAM),
                new GameModeMenuOption(
                        "statio&ns",
                        "[Left/Right]:Select [#]:Select by ID",
                        () -> !this.getStations().isEmpty(),
                        () -> this.getMode() == GameMode.STATIONS,
                        () -> GameMode.STATIONS));
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

    @Override
    public CargoTypes getStationGhostCargoType() {
        Integer terrain = groundMap.findClosestIndustry(cursor.getPosition(), 5);
        if (terrain != null) {
            return CargoTypes.IndustryMapper.getCargoForTerrain(terrain);
        }
        return CargoTypes.NONE;
    }

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

    @Override
    @JsonIgnore
    public String getGameObjectsReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("--- TRAINS ---\n");
        java.util.Set<Train> processedTrains = new java.util.HashSet<>();
        for (Locomotive loco : getLocomotives()) {
            Train train = loco.getTrain();
            if (train != null && !processedTrains.contains(train)) {
                processedTrains.add(train);
                sb.append("Train ID: ").append(train.getId()).append("\n");
                int wagonCount = 0;
                for (letrain.vehicle.impl.Linker l : train.getLinkers()) {
                    if (l instanceof Wagon)
                        wagonCount++;
                }
                sb.append("  Wagons: ").append(wagonCount).append("\n");
                if (train.getDirectorLinker() != null) {
                    if (train.getDirectorLinker() instanceof letrain.vehicle.impl.Linker) {
                        sb.append("  Pos: ")
                                .append(((letrain.vehicle.impl.Linker) train.getDirectorLinker()).getPosition())
                                .append("\n");
                    }
                    sb.append("  Speed: ").append(train.getDirectorLinker().getSpeed()).append("\n");
                }
                if (train.isLoading()) {
                    sb.append("  State: LOADING at Station ").append(train.getStationAtTrain().getId()).append("\n");
                } else if (train.isStalled()) {
                    sb.append("  State: STALLED\n");
                } else {
                    sb.append("  State: CRUIZING\n");
                }
                // Cargo info
                for (letrain.vehicle.impl.Linker linker : train.getLinkers()) {
                    if (linker instanceof Wagon) {
                        Wagon w = (Wagon) linker;
                        if (w.getCargoAmount() > 0) {
                            sb.append("    Wagon: ").append(w.getCargoType()).append(" (").append(w.getCargoAmount())
                                    .append("/").append(w.getMaxCapacity()).append(")\n");
                        }
                    }
                }
            }
        }

        sb.append("\n--- STATIONS ---\n");
        for (Station s : infrastructureManager.getStations()) {
            sb.append("Station ").append(s.getId()).append(": ").append(s.getRole()).append(" ")
                    .append(s.getCargoType());
            sb.append(" (").append(s.getStorage()).append("/").append(s.getMaxStorage()).append(")");
            sb.append(" @ ").append(s.getPosition()).append("\n");
        }

        sb.append("\n--- SENSORS ---\n");
        for (Sensor s : infrastructureManager.getSensors()) {
            if (!(s instanceof Station)) {
                sb.append("Sensor ").append(s.getId()).append(" @ ").append(s.getPosition()).append("\n");
            }
        }

        sb.append("\n--- FORKS ---\n");
        for (ForkRailTrack f : infrastructureManager.getForks()) {
            sb.append("Fork ").append(f.getId()).append(" @ ").append(f.getPosition())
                    .append(" (").append(f.isUsingAlternativeRoute() ? "Alternative" : "Normal").append(")\n");
        }

        sb.append("\n--- SEMAPHORES ---\n");
        for (RailSemaphore s : infrastructureManager.getSemaphores()) {
            sb.append("Semaphore ").append(s.getId()).append(" @ ").append(s.getPosition())
                    .append(" (").append(s.isOpen() ? "OPEN" : "CLOSED").append(")\n");
        }
        return sb.toString();
    }

    @Override
    @JsonIgnore
    public com.badlogic.gdx.graphics.Camera getCamera() {
        return camera;
    }

    @Override
    @JsonIgnore
    public void setCamera(com.badlogic.gdx.graphics.Camera camera) {
        this.camera = camera;
    }

    public void setEconomyManager(EconomyManager economyManager) {
        this.economyManager = economyManager;
    }

    public void setGroundMap(letrain.ground.GroundMap groundMap) {
        this.groundMap = groundMap;
    }

    public void setLocomotives(List<Locomotive> locomotives) { vehicleRoster.setLocomotives(locomotives); }

    public void setWagons(List<Wagon> wagons) { vehicleRoster.setWagons(wagons); }

    public void setCursor(Cursor cursor) {
        this.cursor = cursor;
    }

    public void setNextLocomotiveId(int nextLocomotiveId) {
        this.nextLocomotiveId = nextLocomotiveId;
    }

    public void setNextTrainId(int nextTrainId) {
        this.nextTrainId = nextTrainId;
    }

    public void setSeed(int seed) {
        this.seed = seed;
    }

    public void setEventLogManager(EventLogManager eventLogManager) {
        this.eventLogManager = eventLogManager;
    }
}
