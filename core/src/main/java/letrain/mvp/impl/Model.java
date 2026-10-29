package letrain.mvp.impl;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
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

    /**
     * Temporal trigger registry and scheduler (ADR-022 phase 3, F3a). Transient like the scheduler:
     * it is rebuilt on load and repopulated when the saved program is re-applied ({@code
     * postLoadInit}).
     */
    @JsonIgnore
    private transient letrain.time.TemporalTriggerService temporalTriggerService;

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

    /**
     * Seeded RNG for simulation-time randomness (cargo consumption/regeneration, and any future
     * stochastic gameplay). It is the single source of chance for the world so a game is
     * reproducible: same {@link #seed seed} ⇒ same sequence of events (ADR-020 journal/replay and
     * scenario rebuilds).
     *
     * <p>
     * It is {@code transient} and NOT serialized: the state is re-derived from {@link #seed} on
     * load ({@link #postLoadInit()}) and on world creation. A savegame therefore resumes the
     * <em>same</em> deterministic stream as a fresh replay of its world, which is exactly what
     * ADR-020 needs; it does not continue the mid-air RNG state it had when saved. Do not hold a
     * reference to this object across a reseed/load: always call {@link #getSimulationRandom()}.
     */
    @JsonIgnore
    private transient java.util.Random simulationRandom;

    /**
     * Settings snapshot read from a savegame's {@code settings} section (concept §6); applied to
     * the economy manager in {@link #postLoadInit()}. Null on live models and on savegames written
     * before the section existed.
     */
    @JsonIgnore
    private transient java.util.Map<String, String> loadedSettings;

    /**
     * Reserved entry inside the savegame's {@code settings} section that carries the keys fixed by
     * the scenario's {@code configuration { }} (D10, S3a). It is not a catalogue setting: the
     * settings dialog never shows it and a loaded game rebuilds its lock from it.
     */
    static final String SCENARIO_KEYS_SETTING = "scenario.keys";

    /**
     * Catalogue keys the current scenario's {@code configuration { }} defines (D10): a live apply
     * refuses them with "defined by scenario". Serialized inside the settings section (see
     * {@link #SCENARIO_KEYS_SETTING}) so the lock survives a save/load round-trip; empty on live
     * games built from the New Game dialog and on savegames written before S3a.
     */
    @JsonIgnore
    private transient java.util.Set<String> scenarioSettingKeys = new java.util.LinkedHashSet<>();

    /**
     * Effective settings snapshot, serialized with the savegame so a game carries its own rules.
     * Computed on demand from the economy manager, so it always matches the live configuration
     * (scenario settings included), plus the scenario-locked keys entry when there is one.
     */
    @JsonProperty("settings")
    public java.util.Map<String, String> getSettings() {
        if (economyManager == null) {
            return java.util.Map.of();
        }
        java.util.Map<String, String> settings =
                new java.util.LinkedHashMap<>(economyManager.effectiveConfig());
        if (!scenarioSettingKeys.isEmpty()) {
            settings.put(SCENARIO_KEYS_SETTING, String.join(",", scenarioSettingKeys));
        }
        return settings;
    }

    /** Receives a savegame's settings section; {@link #postLoadInit()} applies it. */
    @JsonProperty("settings")
    public void setSettings(java.util.Map<String, String> settings) {
        this.loadedSettings = settings;
        this.scenarioSettingKeys = parseScenarioKeys(settings);
    }

    /** Scenario-locked keys carried by a settings section (empty on old saves or without lock). */
    private static java.util.Set<String> parseScenarioKeys(java.util.Map<String, String> settings) {
        java.util.Set<String> keys = new java.util.LinkedHashSet<>();
        if (settings == null) {
            return keys;
        }
        String raw = settings.get(SCENARIO_KEYS_SETTING);
        if (raw == null) {
            return keys;
        }
        for (String key : raw.split(",")) {
            String trimmed = key.trim();
            if (!trimmed.isEmpty()) {
                keys.add(trimmed);
            }
        }
        return keys;
    }

    /**
     * Applies a batch of catalogue settings to the running game (S3a, concept §5.2). LIVE and
     * REAPPLY keys reach the economy manager; when the batch carries
     * {@code time.dayDurationSeconds} the clock is re-synchronised and the displayed instant is
     * preserved (the counter is rebased to the new scale, so the calendar never jumps), and
     * {@code world.latitude} re-syncs the solar cycle. NEW_GAME keys (start day/time, seed,
     * terrain...) and scenario-fixed keys are rejected with their reason, as are unknown keys and
     * invalid values; a rejected key is never applied.
     *
     * @param settings catalogue keys to apply to the current game
     * @return the applied keys and the rejections with their reason, for the settings dialog
     */
    @Override
    public letrain.settings.SettingsApplyResult applyLiveSettings(
            java.util.Map<String, String> settings) {
        java.util.Set<String> applied = new java.util.LinkedHashSet<>();
        java.util.Map<String, String> rejections = new java.util.LinkedHashMap<>();
        if (settings == null || settings.isEmpty()) {
            return new letrain.settings.SettingsApplyResult(applied, rejections);
        }
        java.util.Map<String, String> toApply = new java.util.LinkedHashMap<>();
        for (java.util.Map.Entry<String, String> entry : settings.entrySet()) {
            String key = entry.getKey();
            java.util.Optional<letrain.settings.SettingDefinition> definition =
                    letrain.settings.SettingsCatalog.definition(key);
            if (definition.isEmpty()) {
                rejections.put(key, letrain.settings.SettingsApplyResult.UNKNOWN_SETTING);
            } else if (scenarioSettingKeys.contains(key)) {
                rejections.put(key, letrain.settings.SettingsApplyResult.DEFINED_BY_SCENARIO);
            } else if (definition.get()
                    .applyState() == letrain.settings.SettingApplyState.NEW_GAME) {
                rejections.put(key, letrain.settings.SettingsApplyResult.REQUIRES_NEW_GAME);
            } else {
                try {
                    toApply.put(key,
                            letrain.settings.SettingsCatalog.validate(key, entry.getValue()));
                } catch (IllegalArgumentException e) {
                    rejections.put(key, e.getMessage());
                }
            }
        }
        if (toApply.isEmpty() || economyManager == null) {
            return new letrain.settings.SettingsApplyResult(applied, rejections);
        }
        // The counter means game time under the old scale: capture the displayed instant and
        // rebase the clock after the change, so changing the day length never jumps the calendar.
        letrain.time.GameTime displayed = gameClock.now();
        economyManager.applyConfig(toApply);
        syncClockWith(toApply);
        if (toApply.containsKey("time.dayDurationSeconds")) {
            gameClock.setTime(displayed);
        }
        applied.addAll(toApply.keySet());
        return new letrain.settings.SettingsApplyResult(applied, rejections);
    }

    /**
     * Applies a scenario's {@code configuration { }} to a freshly rebuilt world and remembers its
     * keys (D10, S3a); the presenters call this instead of {@code applyConfig} directly. The
     * scenario rules win over the local {@code letrain.cfg} for the clock too: day length, latitude
     * and, since the world is new, the creation-time origin.
     */
    @Override
    public void applyScenarioConfiguration(java.util.Map<String, String> configuration) {
        if (configuration == null || configuration.isEmpty()) {
            return;
        }
        economyManager.applyConfig(configuration);
        scenarioSettingKeys = new java.util.LinkedHashSet<>(configuration.keySet());
        gameClock.setDayDurationSeconds(economyManager.getDayDurationSeconds());
        gameClock.setLatitude(economyManager.getLatitude());
        gameClock.setStartTime(clockOrigin());
    }

    /**
     * Catalogue keys the current scenario fixes (D10); empty when the game has no scenario lock.
     */
    @Override
    public java.util.Set<String> getScenarioSettingKeys() {
        return java.util.Collections
                .unmodifiableSet(new java.util.LinkedHashSet<>(scenarioSettingKeys));
    }

    private transient AutomationEngine automationEngine;
    private transient SimulationService internalSimService;
    private transient letrain.time.impl.SimpleGameClock gameClock =
            new letrain.time.impl.SimpleGameClock();
    /** Visible message sink for DSL problem notices; wired by the clients (see Model). */
    private transient java.util.function.BiConsumer<String, String> userMessageSink;

    /**
     * D1 ordering fix (O3): notices produced before the client wires its sink (a savegame is loaded
     * by {@code postLoadInit} before its presenter exists) wait here and are flushed by
     * {@link #setUserMessageSink}, so a rejection never stays log-only.
     */
    private transient List<PendingUserMessage> pendingUserMessages;

    /**
     * Result of the last program load attempt: false when {@link #setProgram} kept a text the
     * engine rejected. The text is kept anyway (the editor shows it so the player can fix it); this
     * only marks it as not applied.
     */
    private transient boolean programValid = true;

    /** A visible DSL notice produced before the client wired its sink (D1/O3). */
    private record PendingUserMessage(String title, String text) {
    }

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
            if (locomotives == null) {
                return;
            }
            // ADR-022 phase 2d: the freed canton goes to the first waiter by arrival turn (FIFO),
            // not to the oldest locomotive. Ties break by locomotive id (the monotonic turn makes
            // real ties impossible; the tie-break only orders waiters without a turn). A train
            // with several locomotives appears once.
            List<Locomotive> waiters = new ArrayList<>();
            java.util.Set<Train> seen = new java.util.HashSet<>();
            for (Locomotive loco : locomotives) {
                Train train = loco.getTrain();
                if (train == null || !train.isAutoMode()) {
                    continue;
                }
                if (train.getSafetyManager().isWaitingForBlock()
                        && releasedSegment.equals(train.getSafetyManager().getNextSegment())
                        && seen.add(train)) {
                    waiters.add(loco);
                }
            }
            waiters.sort(Comparator.comparingLong(
                    (Locomotive loco) -> bmi.getWaitTurn(loco.getTrain()).orElse(Long.MAX_VALUE))
                    .thenComparingInt(Locomotive::getId));
            for (Locomotive loco : waiters) {
                Train train = loco.getTrain();
                if (train.getSafetyManager().isWaitingForBlock()
                        && releasedSegment.equals(train.getSafetyManager().getNextSegment())) {
                    train.getSafetyManager().onBlockReleased();
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
        this.gameClock.setLatitude(this.economyManager.getLatitude());
        this.gameClock.setStartTime(clockOrigin());
        if (seed == 0) {
            seed = 1 + (int) (Math.random() * 255);
        }
        reseedSimulationRandom();
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

    /**
     * Post-load initialization of one train: model, transient state, shared listeners and physical
     * claim of the cantons it occupies (ADR-022 phase 2f: wagon-only trains too).
     */
    private void initializeTrainAfterLoad(Train train) {
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

    public void postLoadInit() {
        // NOTE: the economy/settings are NOT reloaded from the local file here on purpose: a
        // savegame or an imported scenario carries its own settings, and those must win over the
        // local letrain.cfg for the world (terrain included) to be reproducible. Savegames
        // predating the "settings" section fall back to their serialized legacy thresholds.
        applySavedSettings();
        rebuildTrackConnections();
        // Re-derive the simulation RNG from the restored seed: a loaded world replays the same
        // deterministic chance stream as a fresh build of that seed (ADR-020).
        reseedSimulationRandom();
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
                letrain.ground.impl.GroundMap impl = (letrain.ground.impl.GroundMap) this.groundMap;
                impl.initGenerator(this.seed, this.economyManager);
                impl.rebuildCellsFromBlocks();
            } catch (Exception e) {
                log.error("Error re-initializing GroundMap", e);
            }
        }

        setupModelTrainEventListeners();
        java.util.Set<Train> initializedTrains = new java.util.HashSet<>();
        if (locomotives != null) {
            // Pass 1: Set model, post-load init, setup listeners, and claim physically occupied
            // segments. Trains are initialized once even with several locomotives.
            for (Locomotive loco : locomotives) {
                Train train = loco.getTrain();
                if (train != null && initializedTrains.add(train)) {
                    initializeTrainAfterLoad(train);
                }
            }
        }
        if (wagons != null) {
            // A detached wagon part has no locomotive to reach it: without this it would lose the
            // canton it physically occupies after a load (ADR-022 phase 2f review).
            for (Wagon wagon : wagons) {
                Train train = wagon.getTrain();
                if (train != null && initializedTrains.add(train)) {
                    initializeTrainAfterLoad(train);
                }
            }
        }
        if (locomotives != null) {
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
            // Single load path, shared with the clients' program files. The text is parsed
            // strictly: ADR-022 does not migrate the old comma-less waypoint syntax. A rejected
            // text is kept (the editor shows it so the player can fix it) and the rejection is
            // reported to the visible channel. At this point the presenter does not exist yet
            // (the sink is wired after this model is applied), so the notice is queued and
            // delivered by setUserMessageSink (O3). A valid program's semantic warnings follow
            // the same route: setProgramFromDisk wires the engine to reportUserMessage (D1).
            List<String> errors = this.setProgramFromDisk(this.program);
            if (errors != null && !errors.isEmpty()) {
                reportUserMessage("Program",
                        "Saved program not applied; fix it in the program editor:\n"
                                + String.join("\n", errors));
            }
        }

        if (this.mode == letrain.mvp.Model.GameMode.COMMAND) {
            this.mode = letrain.mvp.Model.GameMode.RAILS;
            this.commandText = "";
            this.commandError = "";
        }
    }

    /**
     * Applies the settings snapshot carried by a savegame (its "settings" section, concept §6)
     * before the world generator is rebuilt, so the loaded terrain matches the saved one. A
     * savegame without the section (written before it existed) falls back to its serialized legacy
     * thresholds, which keeps its historical look.
     *
     * <p>
     * The running clock is re-synced from the loaded settings (#761): only the keys the section
     * actually carries are applied, so a savegame played with another day length or latitude keeps
     * them even when the local {@code letrain.cfg} differs. Savegames without those keys keep the
     * clock wired from the cfg/defaults, exactly as before. The counter ({@code elapsedTicks}) is
     * never touched; the origin ({@code time.startDay} / {@code time.startTime}, S2b) is
     * re-anchored so the loaded game continues at the saved instant.
     */
    private void applySavedSettings() {
        if (economyManager == null) {
            return;
        }
        if (loadedSettings != null && !loadedSettings.isEmpty()) {
            economyManager.applyConfig(loadedSettings);
            syncClockWith(loadedSettings);
        } else {
            economyManager.fallBackToLegacyTerrain();
        }
        gameClock.setStartTime(clockOrigin());
    }

    /**
     * Re-syncs the running clock with the settings just applied (the constructor had wired it from
     * the local cfg): day length and latitude, each only when the batch carries its key, so a
     * savegame or live apply without them keeps the historical cfg/defaults fallback (#761). Never
     * touches the counter; the origin is re-anchored by the callers that need it.
     */
    private void syncClockWith(java.util.Map<String, String> settings) {
        if (economyManager == null) {
            return;
        }
        if (settings.containsKey("time.dayDurationSeconds")) {
            gameClock.setDayDurationSeconds(economyManager.getDayDurationSeconds());
        }
        if (settings.containsKey("world.latitude")) {
            gameClock.setLatitude(economyManager.getLatitude());
        }
    }

    /**
     * Creation-time origin ({@code time.startDay} / {@code time.startTime}) the economy settings
     * ask for: day 1 at 08:00 unless a new game, cfg or savegame says otherwise (S2b).
     */
    private letrain.time.GameTime clockOrigin() {
        return letrain.time.GameTime.ofMinuteOfDay(economyManager.getStartDay(),
                economyManager.getStartMinuteOfDay());
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
        if (mode == GameMode.DRIVE && selectedLocomotive == null) {
            // Entering Drive with nothing selected lands on the first available locomotive (issue
            // #700): the HUD data, the camera follow and the zone ambience all read the selection.
            selectFirstLocomotive();
        }
    }

    @Override
    public GameMode getPreviousMode() {
        return previousMode;
    }

    @Override
    public GameMode getEffectiveMode() {
        return mode == GameMode.COMMAND ? previousMode : mode;
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
    public boolean selectFirstLocomotive() {
        return ModelSelectionService.selectFirstLocomotive(this);
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
        // The text is stored even when it does not parse: the editor must show what the player
        // wrote so it can be fixed. `programValid` marks that the engine rejected it (nothing was
        // applied).
        this.program = program;
        return markProgramApplied(getAutomationEngine().setProgram(program));
    }

    @Override
    public List<String> setProgramFromDisk(String program) {
        // The disk path (savegame, program file) routes the program's warnings through the model's
        // visible channel: a savegame is re-applied by postLoadInit before the presenter exists,
        // so reportUserMessage queues the notice until the client wires its sink (D1).
        this.program = program;
        return markProgramApplied(getAutomationEngine().setProgramFromDisk(program));
    }

    private List<String> markProgramApplied(List<String> errors) {
        this.programValid = errors == null || errors.isEmpty();
        return errors;
    }

    @Override
    public boolean isProgramValid() {
        return this.programValid;
    }

    public void reestablishSystemListeners() {
        ModelListenerService.reestablishSystemListeners(this);
    }

    @Override
    public String getProgram() {
        return this.program;
    }

    @Override
    public void setUserMessageSink(java.util.function.BiConsumer<String, String> sink) {
        this.userMessageSink = sink;
        if (sink == null || pendingUserMessages == null || pendingUserMessages.isEmpty()) {
            return;
        }
        // Flush what was produced while no presenter existed (D1/O3). Detach the list first so a
        // sink callback that reports another problem cannot loop over the same messages.
        List<PendingUserMessage> pending = pendingUserMessages;
        pendingUserMessages = null;
        for (PendingUserMessage message : pending) {
            sink.accept(message.title(), message.text());
        }
    }

    /**
     * D1: reports a DSL problem through the visible message channel. When the client has not wired
     * its sink yet (a savegame is loaded by {@code postLoadInit} before its presenter exists) the
     * notice is queued and delivered by {@link #setUserMessageSink}, so no problem stays log-only.
     */
    @Override
    public void reportUserMessage(String title, String text) {
        if (text == null || text.isEmpty()) {
            return;
        }
        if (userMessageSink != null) {
            userMessageSink.accept(title, text);
            return;
        }
        if (pendingUserMessages == null) {
            pendingUserMessages = new ArrayList<>();
        }
        pendingUserMessages.add(new PendingUserMessage(title, text));
    }

    @Override
    public java.util.function.BiConsumer<String, String> getUserMessageSink() {
        return this.userMessageSink;
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
    public boolean removeMark(String name) {
        return marks.remove(name) != null;
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
    private String commandNotice = "";

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
    public String getCommandNotice() {
        return commandNotice;
    }

    @Override
    public void setCommandNotice(String notice) {
        this.commandNotice = notice;
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
        reseedSimulationRandom();
    }

    public int getSeed() {
        return seed;
    }

    /**
     * Seeded RNG for simulation-time randomness (cargo, future stochastic gameplay). Lazily reseeds
     * from {@link #seed} if needed, so callers always get a deterministic stream. See the field
     * javadoc for the savegame/replay contract.
     */
    @Override
    public java.util.Random getSimulationRandom() {
        if (simulationRandom == null) {
            reseedSimulationRandom();
        }
        return simulationRandom;
    }

    /** Re-derives the simulation RNG from {@link #seed}; called on creation and on load. */
    private void reseedSimulationRandom() {
        this.simulationRandom = new java.util.Random(seed);
    }

    /**
     * Creates a model whose procedural terrain is generated from {@code seed} (ADR-020 scenario
     * import). Use it to rebuild a fresh world that a scenario file will be replayed onto, so the
     * reconstructed infrastructure matches the original world.
     */
    public Model(int seed) {
        this();
        this.seed = seed;
        reseedSimulationRandom();
        this.groundMap = new letrain.ground.impl.GroundMap(seed, this.economyManager);
    }

    /**
     * Creates a fresh game from the New Game dialog (S2, issue #746): a world generated from
     * {@code seed} whose settings were validated against {@link letrain.settings.SettingsCatalog}
     * and applied before any terrain is generated, so the calibrated terrain proportions and the
     * economy match the form. It reuses the same machinery as the scenario/load path: a seeded
     * model plus {@code EconomyManager.applyConfig}, followed by re-wiring the day clock (which the
     * constructor had set from {@code letrain.cfg}): day duration, latitude and the S2b origin
     * ({@code time.startDay} / {@code time.startTime}, day 1 08:00 by default).
     *
     * @param seed generation seed (callers resolve the literal {@code random} before calling)
     * @param settings validated catalogue values; unknown keys are ignored
     * @throws IllegalArgumentException when a setting is malformed or out of range
     */
    public static Model newGame(int seed, java.util.Map<String, String> settings) {
        java.util.Map<String, String> validated =
                letrain.settings.SettingsCatalog.validateAll(settings);
        Model model = new Model(seed);
        model.getEconomyManager().applyConfig(validated);
        model.getGameClock()
                .setDayDurationSeconds(model.getEconomyManager().getDayDurationSeconds());
        model.getGameClock().setLatitude(model.getEconomyManager().getLatitude());
        model.getGameClock().setStartTime(model.clockOrigin());
        return model;
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

    @Override
    public letrain.time.TemporalTriggerService getTemporalTriggerService() {
        if (temporalTriggerService == null) {
            temporalTriggerService =
                    new letrain.time.impl.TemporalTriggerServiceImpl(gameClock, scheduler);
        }
        return temporalTriggerService;
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
