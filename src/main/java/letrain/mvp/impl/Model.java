package letrain.mvp.impl;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import letrain.command.CommandManager;
import letrain.command.LeTrainProgramLexer;
import letrain.command.LeTrainProgramParser;
import letrain.economy.impl.EconomyManager;
import letrain.ground.GroundMap;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.map.impl.RailMap;
import letrain.track.CargoTypes;
import letrain.track.RailSemaphore;
import letrain.track.Sensor;
import letrain.track.Station;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;
import letrain.vehicle.impl.Cursor;
import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.rail.Locomotive;
import letrain.vehicle.impl.rail.Stop;
import letrain.vehicle.impl.rail.Train;
import letrain.vehicle.impl.rail.Wagon;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Model implements Serializable, letrain.mvp.Model {
    static Logger log = LoggerFactory.getLogger(Model.class);

    EconomyManager economyManager;
    Locomotive selectedLocomotive;
    ForkRailTrack selectedFork;
    RailSemaphore selectedSemaphore;
    Station selectedStation;
    EventLogManager eventLogManager;

    int selectedLocomotiveIndex;
    int selectedForkIndex;
    int selectedSemaphoreIndex;
    int selectedStationIndex;
    boolean showId = false;

    letrain.ground.GroundMap groundMap;
    GameMode mode = letrain.mvp.Model.GameMode.RAILS;
    RailMap map;
    List<Locomotive> locomotives;
    List<Wagon> wagons;
    Cursor cursor;
    List<ForkRailTrack> forks;
    List<Sensor> sensors;
    List<RailSemaphore> semaphores;
    List<Station> stations;
    int nextLocomotiveId;
    int nextForkId;

    private final List<letrain.vehicle.impl.rail.TrainEventListener> trainEventListeners = new ArrayList<>();

    @Override
    public void addTrainEventListener(letrain.vehicle.impl.rail.TrainEventListener listener) {
        this.trainEventListeners.add(listener);
        // Apply to existing trains
        for (Locomotive loco : locomotives) {
            if (loco.getTrain() != null) {
                loco.getTrain().addTrainEventListener(listener);
            }
        }
    }

    int nextSensorId;
    int nextSemaphoreId;
    int nextTrainId;
    int nextStationId;
    String program;
    int seed = 0;
    int quantifier = 1;
    int quantifierSteps = 0;
    LocalDateTime lastSaveTime = null;

    public Model() {
        this.eventLogManager = new EventLogManager();
        this.economyManager = new EconomyManager(eventLogManager);
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
        this.locomotives = new ArrayList<>();
        this.wagons = new ArrayList<>();
        this.forks = new ArrayList<>();
        this.sensors = new ArrayList<>();
        this.semaphores = new ArrayList<>();
        this.stations = new ArrayList<>();
        this.map = new RailMap();

        // Economy: Handle train crashes
        this.addTrainEventListener(new letrain.vehicle.impl.rail.TrainEventListener() {
            @Override
            public void onCrash(Train train, letrain.map.Point pos) {
                getEconomyManager().onTrainCrashed(train);
            }
        });
        this.program = "";
        selectedLocomotiveIndex = 0;
        if (!getLocomotives().isEmpty()) {
            selectedLocomotive = getLocomotives().get(selectedLocomotiveIndex);
        }
        selectedForkIndex = 0;
        if (!getForks().isEmpty()) {
            selectedFork = getForks().get(selectedForkIndex);
        }
        selectedSemaphoreIndex = 0;
        if (!getSemaphores().isEmpty()) {
            selectedSemaphore = getSemaphores().get(selectedSemaphoreIndex);
        }
        selectedStationIndex = 0;
        if (!getStations().isEmpty()) {
            selectedStation = getStations().get(selectedStationIndex);
        }
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
        sensors.add(sensor);
        getEconomyManager().onSensorConstructed(sensor);
        sensor.addSystemSensorEventListener(new letrain.track.SensorEventListener() {
            @Override
            public void onEnterTrain(Train train, boolean isForward) {
                eventLogManager.addEntry("Train " + train.getId() + " entered Sensor " + sensor.getId()
                        + (isForward ? " (forward)" : " (backward)"));
            }

            @Override
            public void onExitTrain(Train train, boolean isForward) {
                eventLogManager.addEntry("Train " + train.getId() + " exited Sensor " + sensor.getId());
            }
        });
    }

    @Override
    public void removeSensor(Sensor sensor) {
        if (sensors.remove(sensor)) {
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
        this.forks.add(fork);
        getEconomyManager().onForkConstructed(fork);
        fork.addSystemForkEventListener(new letrain.track.ForkEventListener() {
            @Override
            public void onEnterTrain(Train train, boolean isForward) {
                eventLogManager.addEntry("Train " + train.getId() + " entered Fork " + fork.getId()
                        + (isForward ? " (forward)" : " (backward)"));
            }

            @Override
            public void onDirectionChanged(boolean normal) {
                eventLogManager.addEntry("Fork " + fork.getId() + " set to " + (normal ? "Normal" : "Alternative"));
            }

            @Override
            public void onExitTrain(Train train, boolean isForward) {
                eventLogManager.addEntry("Train " + train.getId() + " exited Fork " + fork.getId());
            }
        });
    }

    @Override
    public void removeFork(ForkRailTrack fork) {
        if (this.forks.remove(fork)) {
            getEconomyManager().onForkDestroyed(fork);
        }
    }

    @Override
    public void addLocomotive(Locomotive locomotive) {
        this.locomotives.add(locomotive);
        if (locomotive.getTrain() != null) {
            for (letrain.vehicle.impl.rail.TrainEventListener l : trainEventListeners) {
                locomotive.getTrain().addTrainEventListener(l);
            }
        }
        getEconomyManager().onLocomotiveConstructed(locomotive);
    }

    @Override
    public void removeLocomotive(Locomotive locomotive) {
        if (this.locomotives.remove(locomotive)) {
            getEconomyManager().onLocomotiveDestroyed(locomotive);
        }
    }

    @Override
    public void moveLocomotives() {
        locomotives.forEach(locomotive -> {
            if (locomotive.update()) {
                // Only charge fuel and notify movement for the director to avoid multiple
                // charges per train
                if (locomotive.isDirectorLinker()) {
                    getEconomyManager().onTrainMoved(locomotive.getTrain());
                    getEconomyManager().chargeFuel(locomotive.getTrain()); // Fuel cost per meter
                }
            }
        });
    }

    @Override
    public GameMode getMode() {
        return mode;
    }

    @Override
    public void setMode(GameMode mode) {
        this.mode = mode;
        if (mode == GameMode.FORKS && selectedFork == null && !getForks().isEmpty()) {
            selectedFork = getForks().get(0);
            selectedForkIndex = 0;
        }
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
        } while (!selectedLocomotive.isDirectorLinker() && selectedLocomotiveIndex < getLocomotives().size());
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
        this.semaphores.add(semaphore);
        getEconomyManager().onSemaphoreConstructed(semaphore);
        RailTrack track = map.getTrackAt(semaphore.getPosition());
        if (track != null) {
            track.setSemaphore(semaphore);
        }
        semaphore.addSystemSemaphoreEventListener(new letrain.track.SemaphoreEventListener() {
            @Override
            public void onOpen() {
                eventLogManager.addEntry("Semaphore " + semaphore.getId() + " opened");
            }

            @Override
            public void onClosed() {
                eventLogManager.addEntry("Semaphore " + semaphore.getId() + " closed");
            }

            @Override
            public void onEnterTrain(Train train, boolean isForward) {
                eventLogManager.addEntry("Train " + train.getId() + " entered Semaphore " + semaphore.getId()
                        + (isForward ? " (forward)" : " (backward)"));
            }

            @Override
            public void onExitTrain(Train train, boolean isForward) {
                eventLogManager.addEntry("Train " + train.getId() + " exited Semaphore " + semaphore.getId());
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
    public void removeDestroyedTrains() {
        AtomicBoolean removed = new AtomicBoolean(false);

        getLocomotives().forEach(locomotive -> {
            locomotive.updateDestroyTimer();
            if (locomotive.isDestroyed()) {
                removed.set(true);
            }
        });

        getLocomotives().removeIf(locomotive -> {
            if (locomotive.isDestroyed()) {
                locomotive.getTrack().removeLinker();
                return true;
            }
            return false;
        });

        getWagons().removeIf(wagon -> {
            wagon.updateDestroyTimer();
            if (wagon.isDestroyed()) {
                wagon.getTrack().removeLinker();
                return true;
            }
            return false;
        });

        if (removed.get()) {
            selectNextLocomotive();
        }
    }

    @Override
    public List<String> setProgram(String program) {
        this.program = program;
        clearAllAutomationListeners();
        log.info("Setting new automation program. Available stations:");
        getStations()
                .forEach(s -> log.info(" - Station {}: Role={}, Cargo={}", s.getId(), s.getRole(), s.getCargoType()));
        List<String> errors = new java.util.ArrayList<>();

        try {
            CharStream input = CharStreams.fromString(program);
            LeTrainProgramLexer lexer = new LeTrainProgramLexer(input);
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            LeTrainProgramParser parser = new LeTrainProgramParser(tokens);

            parser.removeErrorListeners();
            parser.addErrorListener(new org.antlr.v4.runtime.BaseErrorListener() {
                @Override
                public void syntaxError(org.antlr.v4.runtime.Recognizer<?, ?> recognizer, Object offendingSymbol,
                        int line,
                        int charPositionInLine, String msg, org.antlr.v4.runtime.RecognitionException e) {
                    String errorMsg = "Syntax error at line " + line + ":" + charPositionInLine + " " + msg;
                    log.error(errorMsg);
                    errors.add(errorMsg);
                }
            });

            LeTrainProgramParser.StartContext sintaxTree = parser.start();
            CommandManager manager = new CommandManager(this);
            manager.visit(sintaxTree);
        } catch (Exception e) {
            log.error("Error parsing or executing automation program", e);
            errors.add("Critical error: " + e.getMessage());
        }
        return errors;
    }

    private void clearAllAutomationListeners() {
        for (Sensor sensor : sensors) {
            sensor.removeAllSensorEventListeners();
        }
        for (Station station : stations) {
            station.removeAllStationEventListeners();
        }
        for (ForkRailTrack fork : forks) {
            fork.removeAllForkEventListeners();
        }
        for (RailSemaphore semaphore : semaphores) {
            semaphore.removeAllSemaphoreEventListeners();
        }
    }

    @Override
    public String getProgram() {
        return this.program;
    }

    @Override
    public void loadAndUnloadTrains() {
        // Regenerate cargo at all stations
        if (Math.random() < 0.05) { // Slow regeneration
            getStations().forEach(Station::regenerateCargo);
        }

        // We will detect wagon cargo state changes to trigger economy events
        class CargoState {
            CargoTypes type;
            int amount;

            CargoState(CargoTypes t, int a) {
                type = t;
                amount = a;
            }
        }
        java.util.Map<Wagon, CargoState> wagonsPrevState = new java.util.HashMap<>();
        getWagons().forEach(w -> {
            wagonsPrevState.put(w, new CargoState(w.getCargoType(), w.getCargoAmount()));
        });

        java.util.Set<Train> processedTrains = new java.util.HashSet<>();
        getLocomotives().forEach(locomotive -> {
            Train train = locomotive.getTrain();
            if (train != null && !processedTrains.contains(train)) {
                processedTrains.add(train);

                if (train.isLoading()) {
                    int count = train.getLoadingCount();
                    if (count > 0) {
                        train.setLoadingCount(count - 1);
                        Station station = train.getStationAtTrain();
                        if (station != null) {
                            train.performIndustrialAction(station);
                        }
                    } else {
                        Station station = train.getStationAtTrain();
                        log.info("Loading process ended for train {}. Station: {}. Unloading: {}",
                                train.getId(), station != null ? station.getId() : "null",
                                train.isUnloadingDirection());
                        if (station != null) {
                            if (train.isUnloadingDirection()) {
                                station.notifyEndUnload(train);
                            } else {
                                station.notifyEndLoad(train);
                            }
                        }
                        train.endLoadUnloadProcess();
                    }
                }

                // Track changes for EACH wagon in THIS train
                for (Linker linker : train.getLinkers()) {
                    if (linker instanceof Wagon) {
                        Wagon wagon = (Wagon) linker;
                        CargoState prevState = wagonsPrevState.get(wagon);
                        if (prevState == null)
                            continue;

                        int currentAmount = wagon.getCargoAmount();
                        if (currentAmount > prevState.amount) {
                            // LOADING: flat fee on FIRST load only
                            if (prevState.amount == 0) {
                                getEconomyManager().onLoadCargo(wagon);
                            }
                        } else if (currentAmount < prevState.amount) {
                            // UNLOADING: PAY PER UNIT!
                            int unitsUnloaded = prevState.amount - currentAmount;
                            int distance = 0;
                            if (train.getItinerary() != null && train.getItinerary().getFirstStop() != null) {
                                Stop startStop = train.getItinerary().getFirstStop();
                                distance = train.getDistanceTraveled() - startStop.distanceTraveled();
                            }
                            // Important: use previous state's type for the payment
                            getEconomyManager().onUnloadCargo(wagon, prevState.type, unitsUnloaded,
                                    Math.max(0, distance));
                        }
                    }
                }
            }
        });
    }

    @Override
    public List<Station> getStations() {
        return this.stations;
    }

    @Override
    public void addStation(Station station) {
        stations.add(station);
        getEconomyManager().onStationConstructed();
        station.addSystemStationEventListener(new letrain.track.StationEventListener() {
            @Override
            public void onEnterTrain(Train train, boolean isForward) {
                eventLogManager.addEntry("Train " + train.getId() + " entered Station " + station.getId());
            }

            @Override
            public void onExitTrain(Train train, boolean isForward) {
                eventLogManager.addEntry("Train " + train.getId() + " exited Station " + station.getId());
            }

            @Override
            public void onLoad(Train train) {
            }

            @Override
            public void onUnload(Train train) {
            }

            @Override
            public void onStartLoad(Train train) {
                eventLogManager.addEntry("Train " + train.getId() + " started loading at Station " + station.getId());
            }

            @Override
            public void onEndLoad(Train train) {
                eventLogManager.addEntry("Train " + train.getId() + " finished loading at Station " + station.getId());
            }

            @Override
            public void onStartUnload(Train train) {
                eventLogManager.addEntry("Train " + train.getId() + " started unloading at Station " + station.getId());
            }

            @Override
            public void onEndUnload(Train train) {
                eventLogManager
                        .addEntry("Train " + train.getId() + " finished unloading at Station " + station.getId());
            }
        });
    }

    @Override
    public void removeStation(Station Station) {
        if (stations.remove(Station)) {
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

    public void updateGroundMap(Point mapScrollPage, int columns, int rows) {
        this.groundMap.renderBlock(mapScrollPage.getX() * columns, mapScrollPage.getY() * rows, columns, rows);
    }

    @Override
    public EconomyManager getEconomyManager() {
        return this.economyManager;
    }

    @Override
    public RailTrack getCursorRailTrack() {
        return getRailMap().getTrackAt(getCursor().getPosition());
    }

    @Override
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
                        "[Left/Right]:Select [Up]:Accel [Down]:Decel [Space]:Reverse [Enter]:Load/Unload [#]:Select by ID",
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
                        "&persist",
                        "[Up]:Load [Down]:Save [Space]:Edit",
                        () -> true,
                        () -> this.getMode() == GameMode.PERSIST,
                        () -> GameMode.PERSIST),
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
}
