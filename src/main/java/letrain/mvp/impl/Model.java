package letrain.mvp.impl;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import letrain.command.CommandManager;
import letrain.command.LeTrainProgramLexer;
import letrain.command.LeTrainProgramParser;
import letrain.economy.impl.EconomyManager;
import letrain.ground.GroundMap;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.map.impl.RailMap;
import letrain.track.RailSemaphore;
import letrain.track.Sensor;
import letrain.track.Station;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;
import letrain.vehicle.impl.Cursor;
import letrain.vehicle.impl.rail.Locomotive;
import letrain.vehicle.impl.rail.Train;
import letrain.vehicle.impl.rail.Wagon;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Model implements Serializable, letrain.mvp.Model {
    static Logger log = LoggerFactory.getLogger(Model.class);

    public record GameModeMenuOption(
            String gameModeName,
            String gameModeDescription,
            Supplier<Boolean> enabledIf,
            Supplier<Boolean> selectedIf,
            Supplier<GameMode> doWhenSelected) {
    }

    EconomyManager economyManager;
    Locomotive selectedLocomotive;
    ForkRailTrack selectedFork;
    RailSemaphore selectedSemaphore;
    Station selectedStation;

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
    int nextSensorId;
    int nextSemaphoreId;
    int nextTrainId;
    int nextStationId;
    String program;
    int seed = 0;

    public Model() {
        this.economyManager = new EconomyManager();
        if (seed == 0) {
            seed = 1 + (int) (Math.random() * 255);
        }
        this.groundMap = new letrain.ground.impl.GroundMap(seed);
        this.cursor = new Cursor();
        this.cursor.setDir(Dir.E);
        this.cursor.setPosition(new Point(10, 10));
        this.locomotives = new ArrayList<>();
        this.wagons = new ArrayList<>();
        this.forks = new ArrayList<>();
        this.sensors = new ArrayList<>();
        this.semaphores = new ArrayList<>();
        this.stations = new ArrayList<>();
        this.map = new RailMap();
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

    public int nextSemaphoreId() {
        return ++nextSemaphoreId;
    }

    public int nextForkId() {
        return ++nextForkId;
    }

    public int nextLocomotiveId() {
        return ++nextLocomotiveId;
    }

    public int nextSensorId() {
        return ++nextSensorId;
    }

    public int nextTrainId() {
        return ++nextTrainId;
    }

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
    }

    @Override
    public void removeSensor(Sensor sensor) {
        sensors.remove(sensor);
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
        this.wagons.remove(wagon);
    }

    @Override
    public void addWagon(Wagon wagon) {
        this.wagons.add(wagon);
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
    }

    @Override
    public void removeFork(ForkRailTrack fork) {
        this.forks.remove(fork);
    }

    @Override
    public void addLocomotive(Locomotive locomotive) {
        this.locomotives.add(locomotive);
    }

    @Override
    public void removeLocomotive(Locomotive locomotive) {
        this.locomotives.remove(locomotive);
    }

    @Override
    public void moveLocomotives() {
        locomotives.forEach(locomotive -> {
            if (locomotive.update()) {
                getEconomyManager().onTrainMoved(locomotive.getTrain());
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
        for (Locomotive Locomotive : getLocomotives()) {
            if (Locomotive.getId() == id) {
                selectedLocomotive = Locomotive;
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
    public RailSemaphore getSemaphore(int id) {
        for (RailSemaphore semaphore : getSemaphores()) {
            if (semaphore.getId() == id) {
                return semaphore;
            }
        }
        return null;
    }

    @Override
    public void addSemaphore(RailSemaphore semaphore) {
        this.semaphores.add(semaphore);
    }

    @Override
    public void removeSemaphore(RailSemaphore semaphore) {
        this.semaphores.remove(semaphore);
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
    public boolean selectSemaphore(int id) {
        for (RailSemaphore semaphore : getSemaphores()) {
            if (semaphore.getId() == id) {
                selectedSemaphore = semaphore;
                return true;
            }
        }
        return false;
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
            if (locomotive.isDestroyed()) {
                removed.set(true);
            }
        });

        getLocomotives().removeIf(locomotive -> {
            if (locomotive.isDestroyed()) {
                removed.set(true);
                locomotive.getTrack().removeLinker();
            }
            return locomotive.isDestroyed();
        });

        getWagons().removeIf(wagon -> {
            if (wagon.isDestroyed()) {
                wagon.getTrack().removeLinker();
            }
            return wagon.isDestroyed();
        });

        if (removed.get()) {
            selectNextLocomotive();
        }
    }

    @Override
    public void setProgram(String program) {
        CharStream input = CharStreams.fromString(program);
        LeTrainProgramLexer lexer = new LeTrainProgramLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        LeTrainProgramParser parser = new LeTrainProgramParser(tokens);

        LeTrainProgramParser.StartContext sintaxTree = parser.start();
        CommandManager manager = new CommandManager(this);
        manager.visit(sintaxTree);
        this.program = program;
    }

    public String getProgram() {
        return this.program;
    }

    public void loadAndUnloadTrains() {
        AtomicBoolean removed = new AtomicBoolean(false);
        getLocomotives().forEach(locomotive -> {
            Train train = locomotive.getTrain();
            if (train != null) {
                if (train.isLoading()) {
                    train.load();
                }
            }
        });

    }

    @Override
    public List<Station> getStations() {
        return this.stations;
    }

    @Override
    public void addStation(Station Station) {
        this.stations.add(Station);
    }

    @Override
    public void removeStation(Station Station) {
        this.stations.remove(Station);
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
        for (Station Station : getStations()) {
            if (Station.getId() == id) {
                selectedStation = Station;
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

    public RailTrack getCursorRailTrack() {
        return getRailMap().getTrackAt(getCursor().getPosition());
    }

    public List<GameModeMenuOption> getMenuModel() {
        return Arrays.asList(
                new GameModeMenuOption(
                        "&rails",
                        "\u2190:left \u2192:right \u2191:forwd \u2193:backwd shift+\u2191:rail ctrl+\u2191:del insert:add sensor delete:delete sensor home:insert semaphore end:delete semaphore",
                        () -> true,
                        () -> (this.getMode() == GameMode.RAILS),
                        () -> (GameMode.RAILS)),
                new GameModeMenuOption(
                        "&drive",
                        "\u2190:prev \u2192:next \u2191:accel \u2193:decel space:reverse (pgup, pgdn, ctrl+pgup, ctrl+pgdn):move map",
                        () -> !this.getLocomotives().isEmpty(),
                        () -> this.getMode() == GameMode.DRIVE,
                        () -> GameMode.DRIVE),
                new GameModeMenuOption(
                        "&forks",
                        "\u2190:prev \u2192:next space:toggle #:select",
                        () -> !this.getForks().isEmpty(),
                        () -> this.getMode() == GameMode.FORKS,
                        () -> GameMode.FORKS),
                new GameModeMenuOption(
                        "&semaphores",
                        "\u2190:prev \u2192:next space:toggle #:select",
                        () -> !this.getSemaphores().isEmpty(),
                        () -> this.getMode() == GameMode.SEMAPHORES,
                        () -> GameMode.SEMAPHORES),
                new GameModeMenuOption(
                        "&trains",
                        "A-Z:locomotive a-z:wagon enter:end",
                        () -> this.getCursorRailTrack() != null,
                        () -> this.getMode() == GameMode.TRAINS,
                        () -> GameMode.TRAINS),
                new GameModeMenuOption(
                        "&link",
                        "\u2191:front \u2193:back space:link",
                        () -> !this.getLocomotives().isEmpty(),
                        () -> this.getMode() == GameMode.LINK,
                        () -> GameMode.LINK),
                new GameModeMenuOption(
                        "&unlink",
                        "\u2190:front \u2192:back \u2191:add \u2193:del space:unlink",
                        () -> !this.getLocomotives().isEmpty(),
                        () -> this.getMode() == GameMode.UNLINK,
                        () -> GameMode.UNLINK),
                new GameModeMenuOption(
                        "&persist",
                        "\u2191:load \u2193:save space:edit",
                        () -> true,
                        () -> this.getMode() == GameMode.PERSIST,
                        () -> GameMode.PERSIST),
                new GameModeMenuOption(
                        "statio&ns",
                        "\u2190:prev \u2192:next -:load/unload passengers space:clean selection backspace:del number #:select",
                        () -> !this.getStations().isEmpty(),
                        () -> this.getMode() == GameMode.STATIONS,
                        () -> GameMode.STATIONS));
    }

}
