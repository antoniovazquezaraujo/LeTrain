package letrain.mvp.impl.terminal;

import static letrain.mvp.Model.GameMode.DRIVE;
import static letrain.mvp.Model.GameMode.FORKS;
import static letrain.mvp.Model.GameMode.LINK;
import static letrain.mvp.Model.GameMode.MENU;
import static letrain.mvp.Model.GameMode.PROGRAM;
import static letrain.mvp.Model.GameMode.RAILS;
import static letrain.mvp.Model.GameMode.SEMAPHORES;
import static letrain.mvp.Model.GameMode.STATIONS;
import static letrain.mvp.Model.GameMode.TRAINS;
import static letrain.mvp.Model.GameMode.UNLINK;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import letrain.map.Dir;
import letrain.map.Page;
import letrain.map.Point;
import letrain.mvp.Model;
import letrain.mvp.impl.GameSaveService;
import letrain.mvp.impl.RailTrackMaker;
import letrain.mvp.impl.SimulationController;
import letrain.track.CargoTypes;
import letrain.track.Station;
import letrain.track.rail.RailTrack;
import letrain.vehicle.rail.Linker;
import letrain.vehicle.rail.CoreTrainEventListener;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import letrain.vehicle.rail.impl.Wagon;
import letrain.visitor.terminal.InfoVisitor;
import letrain.visitor.terminal.RenderVisitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TerminalPresenter implements letrain.mvp.Presenter, CoreTrainEventListener {
    Logger log = LoggerFactory.getLogger(TerminalPresenter.class);

    Model model;
    TerminalView view;
    private final RenderVisitor renderer;
    private final InfoVisitor informer;
    boolean running;

    int forkId;
    int semaphoreId;
    int locomotiveId;
    int StationId;

    private long forkInputTimeout = 0;
    private long semaphoreInputTimeout = 0;
    private long stationInputTimeout = 0;
    private long locomotiveInputTimeout = 0;

    private final Map<letrain.mvp.Model.GameMode, ModeKeyHandler> modeKeyHandlers = new EnumMap<>(
            letrain.mvp.Model.GameMode.class);

    private interface ModeKeyHandler {
        void handle(KeyStroke keyEvent);
    }

    RailTrackMaker railTrackMaker;
    letrain.audio.AudioController audioController;
    SimulationController simulationController;
    private final GameSaveService gameSaveService;

    public TerminalPresenter() {
        this(null);
    }

    public TerminalPresenter(Model model) {
        setModel(model);
        view = new TerminalView(this);
        renderer = new RenderVisitor(view);
        informer = new InfoVisitor(view);
        railTrackMaker = new RailTrackMaker(this);
        audioController = new letrain.audio.AudioController(this.model);
        simulationController = new SimulationController(this.model, audioController, railTrackMaker);
        this.gameSaveService = new GameSaveService();
        initModeKeyHandlers();
    }

    private void initModeKeyHandlers() {
        modeKeyHandlers.put(RAILS, keyEvent -> railTrackMaker.onChar(keyEvent));
        modeKeyHandlers.put(DRIVE, keyEvent -> trainDriverOnChar(keyEvent));
        modeKeyHandlers.put(FORKS, keyEvent -> handleForksModeKey(keyEvent));
        modeKeyHandlers.put(SEMAPHORES, keyEvent -> handleSemaphoresModeKey(keyEvent));
        modeKeyHandlers.put(TRAINS, keyEvent -> {
            if (keyEvent.getKeyType() == KeyType.Backspace) {
                deleteVehicle();
            } else if (keyEvent.getKeyType() == KeyType.Character) {
                handleTrainsModeKey(keyEvent);
            }
        });
        modeKeyHandlers.put(LINK, keyEvent -> handleLinkModeKey(keyEvent));
        modeKeyHandlers.put(UNLINK, keyEvent -> handleUnlinkModeKey(keyEvent));
        modeKeyHandlers.put(STATIONS, keyEvent -> handleStationsModeKey(keyEvent));
        modeKeyHandlers.put(PROGRAM, keyEvent -> handleProgramModeKey(keyEvent));
        modeKeyHandlers.put(MENU, keyEvent -> {
            // no-op in menu mode
        });
        modeKeyHandlers.put(letrain.mvp.Model.GameMode.LOAD_TRAINS, keyEvent -> {
            // no-op
        });
    }

    void setModel(Model model) {
        if (model != null) {
            this.model = model;
        } else {
            this.model = new letrain.mvp.impl.Model();
        }
        // Re-create audio controller for the new model
        if (this.audioController != null) {
            this.audioController.stop();
        }
        this.audioController = new letrain.audio.AudioController(this.model);
        this.simulationController = new SimulationController(this.model, audioController, railTrackMaker);

        // Register this as global listener for all present and future trains
        this.model.addCoreTrainEventListener(this);
    }

    public void stop() {
        running = false;
        if (audioController != null) {
            audioController.stop();
        }
        if (view != null) {
            view.stop();
        }
    }

    public void start() {
        running = true;
        try {

            KeyStroke stroke = null;
            model.setMode(RAILS);
            letrain.map.Point startPos = model.getCursor().getPosition();
            view.centerOn(startPos.getX(), startPos.getY());
            model.updateGroundMap(view.getScrollOffset(), view.getCols(), view.getRows());
            while (running) {
                stroke = null;
                stroke = view.readKey();
                if (view.isEndOfGame(stroke)) {
                    break;
                }
                if (null != stroke) {
                    onChar(stroke);
                    while (stroke != null) {
                        stroke = view.readKey();
                    }
                }
                simulationController.tick();
                if (audioController != null) {
                    if (model.getMode() == DRIVE && model.getSelectedLocomotive() != null) {
                        Point pos = model.getSelectedLocomotive().getPosition();
                        audioController.setListenerPosition((float) pos.getX(), (float) pos.getY(), 0, 0);
                    } else {
                        Point pos = model.getCursor().getPosition();
                        audioController.setListenerPosition((float) pos.getX(), (float) pos.getY(), 0, 0);
                    }
                    audioController.update();
                }
                renderer.visitModel(model);
                informer.visitModel(model);
                view.paint();
                if (model.getMode() == DRIVE) {
                    Locomotive selectedLocomotive = model.getSelectedLocomotive();
                    if (selectedLocomotive != null) {
                        view.setPageOfPos(selectedLocomotive.getPosition().getX(),
                                selectedLocomotive.getPosition().getY());
                    }
                }

                updateTimeouts();

                Thread.sleep(50);
                view.clear();
            }
        } catch (Exception e) {
            log.error("Error in main loop", e);
        }
    }

    /***********************************************************
     * Presenter implementation
     **********************************************************/
    @Override
    public letrain.mvp.View getView() {
        return view;
    }

    @Override
    public letrain.mvp.Model getModel() {
        return model;
    }

    @Override
    public letrain.audio.AudioController getAudioController() {
        return audioController;
    }

    /***********************************************************
     * GameViewListener implementation
     *********************************************************
     * @param mode
     */
    @Override
    public void onGameModeSelected(letrain.mvp.Model.GameMode mode) {
        // Avisamos al anterior y al nuevo
    }
    // [r:Rails d:Drive f:Forks t:Trains l:Link u:Unlink

    @Override
    public void onChar(KeyStroke keyEvent) {
        if (keyEvent.getKeyType() == KeyType.F1) {
            log.info("\n" + model.getRailwayGraphReport());
            return;
        }

        boolean isAMenuKey = true;
        if (keyEvent.getKeyType() == KeyType.Enter) {
            // In DRIVE mode, Enter is for loading/unloading, not for switching to MENU.
            // The logic is handled inside trainDriverOnChar.
            if (model.getMode() != DRIVE) {
                model.setMode(MENU);
                return;
            }
        } else if (keyEvent.getKeyType() == KeyType.Escape) {
            view.showExitDialog();
        } else if (handleModeHotkey(keyEvent)) {
            return;
        }

        ModeKeyHandler handler = modeKeyHandlers.get(model.getMode());
        if (handler != null) {
            handler.handle(keyEvent);
        }
    }

    private boolean handleModeHotkey(KeyStroke keyEvent) {
        if (keyEvent.getKeyType() != KeyType.Character || keyEvent.getCharacter() == ' ') {
            return false;
        }

        if (model.getMode() == TRAINS) {
            handleTrainsModeKey(keyEvent);
            return true;
        }

        switch (keyEvent.getCharacter()) {
            case 'r':
                model.setMode(RAILS);
                return true;
            case 'd':
                if (!model.getLocomotives().isEmpty()) {
                    model.setMode(DRIVE);
                    return true;
                }
                return false;
            case 'f':
                if (!model.getForks().isEmpty()) {
                    model.setMode(FORKS);
                    return true;
                }
                return false;
            case 's':
                if (!model.getSemaphores().isEmpty()) {
                    model.setMode(SEMAPHORES);
                    return true;
                }
                return false;
            case 't':
                if (model.getCursorRailTrack() != null) {
                    model.setMode(TRAINS);
                    newTrain = null;
                    return true;
                }
                return false;
            case 'l':
                if (model.canEnterLinkUnlinkMode()) {
                    model.setMode(LINK);
                    if (model.getSelectedLocomotive() != null
                            && model.getSelectedLocomotive().getTrain() != null) {
                        Train train = model.getSelectedLocomotive().getTrain();
                        train.getTrainCouplingManager().resetLinkState(train);
                    }
                    return true;
                }
                return false;
            case 'u':
                if (model.canEnterLinkUnlinkMode()) {
                    model.setMode(UNLINK);
                    if (model.getSelectedLocomotive() != null
                            && model.getSelectedLocomotive().getTrain() != null) {
                        Train train = model.getSelectedLocomotive().getTrain();
                        train.getTrainCouplingManager().resetUnlinkState(train);
                    }
                    return true;
                }
                return false;
            case 'n':
                if (!model.getStations().isEmpty()) {
                    model.setMode(STATIONS);
                    return true;
                }
                return false;
            case 'p':
                model.setMode(PROGRAM);
                view.showIDE();
                return true;
            default:
                return false;
        }
    }

    @Override
    public void onKeyUp(KeyStroke keyEvent) {
        if (model.getMode() == RAILS) {
            railTrackMaker.onKeyUp(keyEvent);
        }
    }

    void handleProgramModeKey(KeyStroke keyEvent) {
        if (keyEvent.getKeyType() == KeyType.Character && keyEvent.getCharacter() == ' ') {
            view.showIDE();
        } else if (keyEvent.getKeyType() == KeyType.F12) {
            view.showIDE();
        }
    }

    void handleStationsModeKey(KeyStroke keyEvent) {
        switch (keyEvent.getKeyType()) {
            case Backspace:
                StationId = StationId / 10;
                selectStation(StationId);
                break;
            case Character:
                if (keyEvent.getCharacter() == '-') {
                    // Legacy manual load
                    /*
                     * Station selectedStation = model.getSelectedStation();
                     * if (selectedStation != null) {
                     * Linker linker = selectedStation.getTrack().getLinker();
                     * if (linker != null) {
                     * Train train = linker.getTrain();
                     * train.getTrip().restart(actualStop);
                     * }
                     * }
                     * }
                     */
                } else if (keyEvent.getCharacter() == ' ') {
                    if (StationId > 0) {
                        selectStation(StationId);
                        StationId = 0;
                        stationInputTimeout = 0;
                    }
                    // Unified Industrial Action (Space bar)
                    Station selectedStation = model.getSelectedStation();
                    if (selectedStation != null) {
                        letrain.vehicle.rail.Linker linker = selectedStation.getTrack().getLinker();
                        if (linker != null && linker.getTrain() != null) {
                            Train train = linker.getTrain();
                            train.getLogisticsManager().performIndustrialAction(selectedStation);
                        }
                    }
                } else if (keyEvent.getCharacter() >= '0' && keyEvent.getCharacter() <= '9') {
                    StationId = StationId * 10 + (keyEvent.getCharacter() - '0');
                    selectStation(StationId);
                }
                break;
            case ArrowLeft:
                selectPrevStation();
                break;
            case ArrowRight:
                selectNextStation();
                break;
            default:
                break;
        }
    }

    private void handleSemaphoresModeKey(KeyStroke keyEvent) {
        switch (keyEvent.getKeyType()) {
            case Backspace:
                semaphoreId = semaphoreId / 10;
                selectSemaphore(semaphoreId);
                break;
            case Character:
                if (keyEvent.getCharacter() == ' ') {
                    if (semaphoreId > 0) {
                        selectSemaphore(semaphoreId);
                        semaphoreId = 0;
                        semaphoreInputTimeout = 0;
                    }
                    toggleSemaphore();
                    semaphoreId = 0;
                } else if (keyEvent.getCharacter() >= '0' && keyEvent.getCharacter() <= '9') {
                    semaphoreId = semaphoreId * 10 + (keyEvent.getCharacter() - '0');
                    semaphoreInputTimeout = System.currentTimeMillis() + 1000;
                }
                break;
            case ArrowUp:
                toggleSemaphore();
                semaphoreId = 0;
                break;
            case ArrowDown:
                toggleSemaphore();
                semaphoreId = 0;
                break;
            case ArrowLeft:
                selectPrevSemaphore();
                break;
            case ArrowRight:
                selectNextSemaphore();
                break;
            default:
                break;
        }
    }

    private void handleUnlinkModeKey(KeyStroke keyEvent) {
        switch (keyEvent.getKeyType()) {
            case ArrowUp:
                selectFrontDivisionSense();
                break;
            case ArrowDown:
                selectBackDivisionSense();
                break;
            case ArrowLeft:
                selectPrevLink();
                break;
            case ArrowRight:
                selectNextLink();
                break;
            case Character:
                if (keyEvent.getCharacter() == ' ') {
                    divideTrain();
                    model.setMode(MENU);
                }
                break;
            case Enter:
                divideTrain();
                model.setMode(MENU);
                break;
            case Delete:
                destroyLinkers();
                model.setMode(MENU);
                break;
            default:
                break;
        }
    }

    private void handleLinkModeKey(KeyStroke keyEvent) {
        switch (keyEvent.getKeyType()) {
            case ArrowUp:
                selectVehiclesInFront();
                break;
            case ArrowDown:
                selectVehiclesAtBack();
                break;
            case ArrowLeft:
                if (model.getSelectedLocomotive() != null && model.getSelectedLocomotive().getTrain() != null) {
                    Train train = model.getSelectedLocomotive().getTrain();
                    if (train.getNumLinkersToJoin() > 0) {
                        train.setNumLinkersToJoin(train.getNumLinkersToJoin() - 1);
                    }
                }
                break;
            case ArrowRight:
                if (model.getSelectedLocomotive() != null && model.getSelectedLocomotive().getTrain() != null) {
                    Train train = model.getSelectedLocomotive().getTrain();
                    if (train.getNumLinkersToJoin() < train.getLinkersToJoin().size()) {
                        train.setNumLinkersToJoin(train.getNumLinkersToJoin() + 1);
                    }
                }
                break;
            case Character:
                if (keyEvent.getCharacter() == ' ') {
                    linkSelectedVehicles();
                    model.setMode(MENU);
                }
                break;
            case Enter:
                linkSelectedVehicles();
                model.setMode(MENU);
                break;
            default:
                break;
        }
    }

    private void handleTrainsModeKey(KeyStroke keyEvent) {
        if (model.getRailMap().getTrackAt(model.getCursor().getPosition()) == null) {
            return;
        }
        String c = keyEvent.getCharacter().toString();
        if (c.isEmpty()) {
            return;
        }
        if (c.matches("[123]")) {
            if (c.equals("1")) model.setSelectedWagonType(letrain.track.CargoTypes.GOLD);
            else if (c.equals("2")) model.setSelectedWagonType(letrain.track.CargoTypes.COAL);
            else if (c.equals("3")) model.setSelectedWagonType(letrain.track.CargoTypes.RUBY);
            return;
        }

        if (!c.matches("([A-Za-z])?")) {
            return;
        }
        RailTrack track = model.getRailMap().getTrackAt(model.getCursor().getPosition());
        if (track.getLinker() != null) {
            log.warn("Can't add a train to a track with a linker");
            return;
        }
        Dir cursorDir = Dir.E;
        if (c.toUpperCase().equals(c)) {
            Locomotive locomotive = new Locomotive(model.nextLocomotiveId(), c);
            Train train = new Train(model.nextTrainId());
            train.pushBack(locomotive);
            train.addCoreTrainEventListener(this);
            train.setDirectorLinker(locomotive);
            model.addLocomotive(locomotive);
            model.getEconomyManager().onLocomotiveConstructed(locomotive);
            track.enterLinkerFromDir(model.getCursor().getDir().inverse(), locomotive);
            train.getSafetyManager().claimOccupiedSegments();
            cursorDir = locomotive.getDir();
        } else {
            Wagon wagon = new Wagon(c);
            wagon.setExclusiveCargoType(model.getSelectedWagonType());
            model.addWagon(wagon);
            model.getEconomyManager().onWagonConstructed(wagon);
            track.enterLinkerFromDir(model.getCursor().getDir().inverse(), wagon);
            if (wagon.getTrain() != null) {
                wagon.getTrain().getSafetyManager().claimOccupiedSegments();
            }
            cursorDir = wagon.getDir();
        }
        Point newPos = new Point(model.getCursor().getPosition());
        newPos.move(cursorDir, 1);
        model.getCursor().setDir(cursorDir);
        model.getCursor().setPosition(newPos);
    }

    private void deleteVehicle() {
        letrain.map.Dir cursorDir = model.getCursor().getDir();
        // Move back to the previous track
        model.getCursor().getPosition().move(cursorDir.inverse());

        letrain.track.rail.RailTrack track = model.getRailMap().getTrackAt(model.getCursor().getPosition());
        if (track != null && track.getLinker() != null) {
            letrain.vehicle.rail.Linker linker = track.getLinker();
            if (linker instanceof Locomotive) {
                model.removeLocomotive((Locomotive) linker);
            } else if (linker instanceof Wagon) {
                model.removeWagon((Wagon) linker);
            }
            track.removeLinker();

            // Restore proper cursor direction before curve
            letrain.map.Dir entryDir = track.getRouter().getDir(cursorDir);
            if (entryDir != null) {
                model.getCursor().setDir(entryDir.inverse());
            }
        } else {
            // Restore cursor if nothing was deleted
            model.getCursor().getPosition().move(cursorDir);
        }
    }

    private void handleForksModeKey(KeyStroke keyEvent) {
        switch (keyEvent.getKeyType()) {
            case Backspace:
                forkId = forkId / 10;
                selectFork(forkId);
                break;
            case Character:
                if (keyEvent.getCharacter() == ' ') {
                    if (forkId > 0) {
                        selectFork(forkId);
                        forkId = 0;
                        forkInputTimeout = 0;
                    }
                    toggleFork();
                    forkId = 0;
                } else if (keyEvent.getCharacter() >= '0' && keyEvent.getCharacter() <= '9') {
                    forkId = forkId * 10 + (keyEvent.getCharacter() - '0');
                    selectFork(forkId);
                    forkInputTimeout = System.currentTimeMillis() + 1000;
                }
                break;
            case ArrowUp:
                toggleFork();
                forkId = 0;
                break;
            case ArrowDown:
                toggleFork();
                forkId = 0;
                break;
            case ArrowLeft:
                selectPrevFork();
                break;
            case ArrowRight:
                selectNextFork();
                break;
            default:
                break;
        }
    }

    private void trainDriverOnChar(KeyStroke keyEvent) {
        model.setShowId(false);
        switch (keyEvent.getKeyType()) {
            case Backspace:
                locomotiveId = locomotiveId / 10;
                selectLocomotive(locomotiveId);
                break;
            case ArrowUp:
                if (model.getSelectedLocomotive() != null) {
                    Locomotive loco = model.getSelectedLocomotive();
                    if (loco.isEngineOn() && !loco.getTrain().getLogisticsManager().isLoading()) {
                        accelerateLocomotive();
                        locomotiveId = 0;
                    }
                }
                break;
            case ArrowDown:
                if (model.getSelectedLocomotive() != null) {
                    Locomotive loco = model.getSelectedLocomotive();
                    if (loco.isEngineOn() && !loco.getTrain().getLogisticsManager().isLoading()) {
                        decelerateLocomotive();
                        locomotiveId = 0;
                    }
                }
                break;
            case ArrowLeft:
                selectPrevLocomotive();
                locomotiveId = 0;
                break;
            case ArrowRight:
                selectNextLocomotive();
                locomotiveId = 0;
                break;
            case PageUp:
                if (keyEvent.isCtrlDown()) {
                    mapPageRight();
                } else {
                    mapPageUp();
                }
                break;
            case PageDown:
                if (keyEvent.isCtrlDown()) {
                    mapPageLeft();
                } else {
                    mapPageDown();
                }
                break;
            case Character:
                if (keyEvent.getCharacter() == ' ') {
                    if (locomotiveId > 0) {
                        selectLocomotive(locomotiveId);
                        locomotiveId = 0;
                        locomotiveInputTimeout = 0;
                    }
                    // Space bar now only toggles reverse when stopped
                    toggleReversed();
                    locomotiveId = 0;
                } else if (keyEvent.getCharacter() >= '0' && keyEvent.getCharacter() <= '9') {
                    if (keyEvent.getCharacter() == '0' && locomotiveId == 0) {
                        model.setShowId(true);
                    } else {
                        locomotiveId = locomotiveId * 10 + (keyEvent.getCharacter() - '0');
                        selectLocomotive(locomotiveId);
                    }
                } else if (keyEvent.getCharacter() == 'm') {
                    Locomotive loco = model.getSelectedLocomotive();
                    if (loco != null) {
                        if (!loco.isEngineOn()) {
                            audioController.startEngine(loco);
                        } else if (loco.getSpeed() == 0 && loco.getTargetSpeed() == 0) {
                            audioController.stopEngineWithSound(loco.getId(), loco);
                        }
                    }
                }
                break;
            case Enter:
                // Enter key now handles loading/unloading at a station, or switches to menu
                if (model.getSelectedLocomotive() != null && model.getSelectedLocomotive().getSpeed() == 0) { // Solo si
                                                                                                              // el tren
                                                                                                              // está
                                                                                                              // detenido
                    Train train = model.getSelectedLocomotive().getTrain();
                    Station station = train.getLogisticsManager().getStationAtTrain();
                    if (station != null) {
                        if (train.getLogisticsManager().isLoading()) { // Si ya está cargando/descargando, lo termina
                            train.getLogisticsManager().endLoadUnloadProcess();
                        } else {
                            CargoTypes trainCargoType = train.getLogisticsManager().getTrainCargoType();
                            if (trainCargoType != CargoTypes.NONE
                                    && station.getRole() == CargoTypes.StationRole.CONSUMER) {
                                train.getLogisticsManager().startUnloadProcess(station);
                                train.recordStopAtStation();
                            } else if (trainCargoType == CargoTypes.NONE
                                    && station.getRole() == CargoTypes.StationRole.PRODUCER) {
                                train.getLogisticsManager().startLoadProcess(station);
                                train.recordStopAtStation();
                            }
                        }
                        return; // Consume the event
                    }
                }
                // If not on a station or not stopped, Enter should switch to menu
                model.setMode(MENU);
                break;
            default:
                break;
        }
    }

    private void destroyLinkers() {
        if (model.getSelectedLocomotive() != null) {
            Train train = model.getSelectedLocomotive().getTrain();

            List<Linker> linkersToDestroy = train.getTrainCouplingManager().destroyLinkers(train, () -> model.nextTrainId());
            for (Linker linker : linkersToDestroy) {
                if (linker instanceof Locomotive) {
                    model.removeLocomotive((Locomotive) linker);
                } else {
                    model.removeWagon((Wagon) linker);
                }
                linker.getTrack().removeLinker();
            }
        }
    }

    private void toggleReversed() {
        if (model.getSelectedLocomotive() != null) {
            if (model.getSelectedLocomotive().getSpeed() == 0) {
                model.getSelectedLocomotive().toggleReversed();
            }
        }
    }

    private void linkSelectedVehicles() {
        if (model.getSelectedLocomotive() != null) {
            Locomotive loco = model.getSelectedLocomotive();
            if (loco.getTrain() != null) {
                Train train = loco.getTrain();
                if (!train.getLinkersToJoin().isEmpty() && train.getNumLinkersToJoin() > 0) {
                    train.getTrainCouplingManager().joinLinkers(train);
                }
                model.setMode(letrain.mvp.Model.GameMode.MENU);
            }
        }
    }

    private void selectVehiclesAtBack() {
        if (model.getSelectedLocomotive() != null &&
                model.getSelectedLocomotive().getTrain() != null) {
            Train train = model.getSelectedLocomotive().getTrain();

            train.getTrainCouplingManager().updateLinkersToJoin(train, false);
        }
    }

    private void selectVehiclesInFront() {
        if (model.getSelectedLocomotive() != null) {
            if (model.getSelectedLocomotive().getTrain() != null) {
                Train train = model.getSelectedLocomotive().getTrain();

                train.getTrainCouplingManager().updateLinkersToJoin(train, true);
            } else {
                // handle error
            }
        } else {
            // handle error
        }
    }

    private void selectFrontDivisionSense() {
        Train train = model.getSelectedLocomotive().getTrain();
        train.getTrainCouplingManager().setFrontDivisionSense(train);
    }

    private void selectBackDivisionSense() {
        Train train = model.getSelectedLocomotive().getTrain();
        train.getTrainCouplingManager().setBackDivisionSense(train);
    }

    private void selectNextLink() {
        Train train = model.getSelectedLocomotive().getTrain();
        train.getTrainCouplingManager().selectNextDivisionLink(train);
    }

    private void selectPrevLink() {
        Train train = model.getSelectedLocomotive().getTrain();
        train.getTrainCouplingManager().selectPrevDivisionLink(train);
    }

    private void divideTrain() {
        Locomotive loco = model.getSelectedLocomotive();
        if (loco != null && loco.getTrain() != null) {
            Train train = loco.getTrain();

            train.getTrainCouplingManager().divideTrain(train, () -> model.nextTrainId());
            audioController.playOneShot("link",
                    (float) loco.getPosition().getX(),
                    (float) loco.getPosition().getY());
        }
    }

    public void selectLocomotive(int id) {
        if (model.selectLocomotive(id)) {
            setPageOfPoint(model.getSelectedLocomotive().getTrack().getPosition());
        }
    }

    Train newTrain;

    /***********************************************************
     * FORKS
     **********************************************************/

    private void selectNextFork() {
        if (model.selectNextFork()) {
            setPageOfPoint(model.getSelectedFork().getPosition());
        }
    }

    private void selectPrevFork() {
        if (model.selectPrevFork()) {
            setPageOfPoint(model.getSelectedFork().getPosition());
        }
    }

    private void selectFork(int id) {
        if (model.selectFork(id)) {
            setPageOfPoint(model.getSelectedFork().getPosition());
        }
    }

    private void toggleFork() {
        if (model.getSelectedFork() != null) {
            model.getSelectedFork().flipRoute();
            audioController.playOneShot("fork",
                    (float) model.getSelectedFork().getPosition().getX(),
                    (float) model.getSelectedFork().getPosition().getY());
        }
    }

    /***********************************************************
     * SEMAPHORES
     **********************************************************/

    private void selectNextSemaphore() {
        if (model.selectNextSemaphore()) {
            setPageOfPoint(model.getSelectedSemaphore().getPosition());
        }

    }

    private void selectPrevSemaphore() {
        if (model.selectPrevSemaphore()) {
            setPageOfPoint(model.getSelectedSemaphore().getPosition());
        }
    }

    private void selectSemaphore(int id) {
        if (model.selectSemaphore(id)) {
            setPageOfPoint(model.getSelectedSemaphore().getPosition());
        }
    }

    private void toggleSemaphore() {
        if (model.getSelectedSemaphore() != null) {
            model.getSelectedSemaphore().setOpen(!model.getSelectedSemaphore().isOpen());
        }
    }

    /***********************************************************
     * TRAINS
     **********************************************************/

    private void selectNextLocomotive() {
        if (model.selectNextLocomotive()) {
            setPageOfPoint(model.getSelectedLocomotive().getTrack().getPosition());
        }
    }

    private void selectPrevLocomotive() {
        if (model.selectPrevLocomotive()) {
            setPageOfPoint(model.getSelectedLocomotive().getTrack().getPosition());
        }
    }

    private void decelerateLocomotive() {
        if (model.getSelectedLocomotive() == null)
            return;
        model.getSelectedLocomotive().decSpeed();
    }

    private void accelerateLocomotive() {
        if (model.getSelectedLocomotive() == null)
            return;
        model.getSelectedLocomotive().incSpeed();
    }

    private void mapPageDown() {
        view.clear();
        Point offset = view.getScrollOffset();
        view.setScrollOffset(new Point(offset.getX(), offset.getY() + view.getRows()));
        view.clear();
    }

    private void mapPageLeft() {
        view.clear();
        Point offset = view.getScrollOffset();
        view.setScrollOffset(new Point(offset.getX() - view.getCols(), offset.getY()));
        view.clear();

    }

    private void mapPageUp() {
        view.clear();
        Point offset = view.getScrollOffset();
        view.setScrollOffset(new Point(offset.getX(), offset.getY() - view.getRows()));
        view.clear();
    }

    private void mapPageRight() {
        view.clear();
        Point offset = view.getScrollOffset();
        view.setScrollOffset(new Point(offset.getX() + view.getCols(), offset.getY()));
        view.clear();

    }

    void setPageOfPoint(Point point) {
        if (point != null) {
            view.centerOn(point.getX(), point.getY());
        }
    }

    /***********************************************************
     * StationS
     **********************************************************/

    private void selectNextStation() {
        if (model.selectNextStation()) {
            setPageOfPoint(model.getSelectedStation().getPosition());
        }
    }

    private void selectPrevStation() {
        if (model.selectPrevStation()) {
            setPageOfPoint(model.getSelectedStation().getPosition());
        }
    }

    private void selectStation(int id) {
        if (model.selectStation(id)) {
            setPageOfPoint(model.getSelectedStation().getPosition());
        }
    }

    public File changeExtension(File originalFile, String newExtension) {
        String directory = originalFile.getParent();
        String fileName = originalFile.getName();
        int lastDotIndex = fileName.lastIndexOf('.');

        String baseName = fileName;
        if (lastDotIndex > 0) {
            baseName = fileName.substring(0, lastDotIndex);
        }
        String newFileName = baseName + "." + newExtension;
        return new File(directory, newFileName);
    }

    @Override
    public void onNewGame() {
    }

    @Override
    public void onSaveGame(File file) {
        if (file != null) {
            boolean ok = gameSaveService.save(this.model, file);
            if (!ok) {
                view.showMessage("Save Error", "Could not save game to\n" + file.getAbsolutePath());
            }
        }
    }

    @Override
    public void onLoadGame(File file) {
        if (file != null && file.exists()) {
            try {
                java.util.Optional<letrain.mvp.impl.Model> optionalModel = gameSaveService.load(file);
                if (optionalModel.isPresent()) {
                    letrain.mvp.impl.Model loadedModel = optionalModel.get();
                    // Just replace the model and let the existing loop continue
                    setModel(loadedModel);
                    // View specific listener
                    loadedModel.addCoreTrainEventListener(this);
                } else {
                    view.showMessage("Load Error", "Could not load game from\n" + file.getAbsolutePath());
                }
            } catch (Exception e) {
                log.error("Serious error while loading game in 2D", e);
                view.showMessage("Load Error", "Exception: " + e.getMessage());
            }
        }
    }

    @Override
    public void onSaveCommands(File file) {
        if (file != null) {
            saveProgram(this.model.getProgram(), file);
        }
    }

    @Override
    public void onLoadCommands(File file) {
        String input = loadProgram(file);
        if (input == null) {
            return;
        }
        List<String> errors = model.setProgram(input);
        handleScriptErrors(errors);
    }

    @Override
    public void onEditCommands(String content) {
        List<String> errors = model.setProgram(content);
        handleScriptErrors(errors);
    }

    private void handleScriptErrors(List<String> errors) {
        if (errors != null && !errors.isEmpty()) {
            String combinedErrors = String.join("\n", errors);
            view.showMessage("Script Errors", combinedErrors);
        }
    }

    @Override
    public void onExitGame() {
        stop();
        System.exit(0);
    }

    @Override
    public void onPlay() {
        // start();
    }


    String loadProgram(File file) {
        try {
            return new String(java.nio.file.Files.readAllBytes(file.toPath()), java.nio.charset.StandardCharsets.UTF_8);
        } catch (IOException ex) {
            log.error("Error loading program", ex);
            return null;
        }
    }

    void saveProgram(String content, File file) {
        try (FileOutputStream fos = new FileOutputStream(file)) {
            fos.write(content.getBytes());
        } catch (IOException ex) {
            log.error("Error saving model", ex);
        }

    }

    @Override
    public String getProgram() {
        return model.getProgram();
    }

    @Override
    public void setProgram(String program) {
        model.setProgram(program);
    }

    @Override
    public String getGameObjectsReport() {
        return model.getGameObjectsReport();
    }

    @Override
    public java.util.List<String> getEventLogEntries() {
        return model.getEventLogManager().getEntries();
    }

    public Point getActiveFocusPoint() {
        if ((model.getMode() == DRIVE || model.getMode() == LINK || model.getMode() == UNLINK)
                && model.getSelectedLocomotive() != null) {
            return model.getSelectedLocomotive().getPosition();
        } else if (model.getMode() == FORKS && model.getSelectedFork() != null) {
            return model.getSelectedFork().getPosition();
        } else if (model.getMode() == SEMAPHORES && model.getSelectedSemaphore() != null) {
            return model.getSelectedSemaphore().getPosition();
        } else if (model.getMode() == STATIONS && model.getSelectedStation() != null) {
            return model.getSelectedStation().getPosition();
        } else if (model.getCursor() != null) {
            return model.getCursor().getPosition();
        }
        return new Point(0, 0);
    }

    @Override
    public void onScreenResized(int columns, int rows) {
        Point focus = getActiveFocusPoint();
        if (focus != null) {
            view.centerOn(focus.getX(), focus.getY());
        }
        Point offset = view.getScrollOffset();
        model.updateGroundMap(offset, columns, rows);
    }

    @Override
    public void onMapPageChanged(Point scrollOffset, int columns, int rows) {
        model.updateGroundMap(scrollOffset, columns, rows);
    }

    private void updateTimeouts() {
        long now = System.currentTimeMillis();
        if (forkInputTimeout > 0 && now > forkInputTimeout) {
            model.selectFork(forkId);
            forkId = 0;
            forkInputTimeout = 0;
        }
        if (semaphoreInputTimeout > 0 && now > semaphoreInputTimeout) {
            model.selectSemaphore(semaphoreId);
            semaphoreId = 0;
            semaphoreInputTimeout = 0;
        }
        if (stationInputTimeout > 0 && now > stationInputTimeout) {
            model.selectStation(StationId);
            StationId = 0;
            stationInputTimeout = 0;
        }
        if (locomotiveInputTimeout > 0 && now > locomotiveInputTimeout) {
            model.selectLocomotive(locomotiveId);
            locomotiveId = 0;
            locomotiveInputTimeout = 0;
        }
    }

    @Override
    public void onContact(Train train, letrain.map.Point pos, int speed) {
        if (audioController != null && pos != null) {
            audioController.playOneShot("contact", (float) pos.getX(), (float) pos.getY());
            // Immediately stop audio for locomotives involved in the contact
            for (Locomotive loco : model.getLocomotives()) {
                if (loco.getTrain() != null && (loco.getSpeed() > 0 || loco.getTargetSpeed() > 0)) {
                    if (Point.distance(loco.getPosition(), pos) < 2.0) {
                        audioController.stopSynthesizer(loco.getId());
                    }
                }
            }
        }
    }

    @Override
    public void onLink(Train train) {
        if (audioController != null) {
            Linker firstLinker = train.getLinkers().peekFirst();
            if (firstLinker != null) {
                letrain.map.Point pos = firstLinker.getPosition();
                if (pos != null) {
                    audioController.playOneShot("link", (float) pos.getX(), (float) pos.getY());
                }
            }
        }
    }

    @Override
    public void onUnlink(Train train) {
        if (audioController != null) {
            Linker firstLinker = train.getLinkers().peekFirst();
            if (firstLinker != null) {
                letrain.map.Point pos = firstLinker.getPosition();
                if (pos != null) {
                    audioController.playOneShot("link", (float) pos.getX(), (float) pos.getY());
                }
            }
        }
    }

    @Override
    public void onCrash(Train train, letrain.map.Point pos, int speed) {
        if (audioController != null && pos != null) {
            audioController.playOneShot("explosion", (float) pos.getX(), (float) pos.getY());
            // Immediately stop audio for all locomotives involved in the crash
            for (Locomotive loco : model.getLocomotives()) {
                if (loco.isDestroying()) {
                    audioController.stopSynthesizer(loco.getId());
                }
            }
        }
    }
}
