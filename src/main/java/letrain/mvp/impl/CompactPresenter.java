package letrain.mvp.impl;

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
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.LocalDateTime;
import java.util.List;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import letrain.map.Dir;
import letrain.map.Page;
import letrain.map.Point;
import letrain.track.CargoTypes;
import letrain.track.Station;
import letrain.track.rail.RailTrack;
import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.rail.Locomotive;
import letrain.vehicle.impl.rail.Train;
import letrain.vehicle.impl.rail.Wagon;
import letrain.visitor.InfoVisitor;
import letrain.visitor.RenderVisitor;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CompactPresenter implements letrain.mvp.Presenter, letrain.vehicle.impl.rail.TrainEventListener {
    Logger log = LoggerFactory.getLogger(CompactPresenter.class);

    Model model;
    private final letrain.mvp.View view;
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

    RailTrackMaker railTrackMaker;
    letrain.audio.AudioController audioController;
    SimulationController simulationController;

    public CompactPresenter() {
        this(null);
    }

    public CompactPresenter(Model model) {
        setModel(model);
        view = new View(this);
        renderer = new RenderVisitor(view);
        informer = new InfoVisitor(view);
        railTrackMaker = new RailTrackMaker(this);
        audioController = new letrain.audio.AudioController(this.model);
        simulationController = new SimulationController(this.model, audioController, railTrackMaker);
    }

    void setModel(Model model) {
        if (model != null) {
            this.model = model;
        } else {
            this.model = new Model();
        }
        // Re-create audio controller for the new model
        if (this.audioController != null) {
            this.audioController.stop();
        }
        this.audioController = new letrain.audio.AudioController(this.model);
        this.simulationController = new SimulationController(this.model, audioController, railTrackMaker);

        // Register this as global listener for all present and future trains
        this.model.addTrainEventListener(this);
    }

    public void stop() {
        running = false;
    }

    public void start() {
        running = true;
        try {

            KeyStroke stroke = null;
            model.setMode(RAILS);
            letrain.map.Point startPos = model.getCursor().getPosition();
            model.updateGroundMap(startPos, view.getCols(), view.getRows());
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
        } else if (keyEvent.getKeyType() == KeyType.Character && keyEvent.getCharacter() != ' ') {
            if (model.getMode() == TRAINS) {
                trainManagerOnChar(keyEvent);
            } else {
                switch (keyEvent.getCharacter()) {
                    case 'r':
                        model.setMode(RAILS);
                        break;
                    case 'd':
                        if (!model.getLocomotives().isEmpty()) {
                            model.setMode(DRIVE);
                        }
                        break;
                    case 'f':
                        if (!model.getForks().isEmpty()) {
                            model.setMode(FORKS);
                        }
                        break;
                    case 's':
                        if (!model.getSemaphores().isEmpty()) {
                            model.setMode(SEMAPHORES);
                        }
                        break;
                    case 't':
                        if (model.getCursorRailTrack() != null) {
                            model.setMode(TRAINS);
                        }
                        newTrain = null;
                        break;
                    case 'l':
                        if (!model.getLocomotives().isEmpty()) {
                            model.setMode(LINK);
                            if (model.getSelectedLocomotive() != null
                                    && model.getSelectedLocomotive().getTrain() != null) {
                                model.getSelectedLocomotive().getTrain().resetLinkState();
                            }
                        }
                        break;
                    case 'u':
                        if (!model.getLocomotives().isEmpty()) {
                            model.setMode(UNLINK);
                            if (model.getSelectedLocomotive() != null
                                    && model.getSelectedLocomotive().getTrain() != null) {
                                model.getSelectedLocomotive().getTrain().resetUnlinkState();
                            }
                        }
                        break;
                    case 'n':
                        if (!model.getStations().isEmpty()) {
                            model.setMode(STATIONS);
                        }
                        break;
                    case 'p':
                        model.setMode(PROGRAM);
                        view.showIDE();
                        break;
                    default:
                        isAMenuKey = false;
                        break;

                }
                if (isAMenuKey) {
                    return;
                }
            }
        }

        switch (model.getMode()) {
            case RAILS:
                railTrackMaker.onChar(keyEvent);
                break;
            case DRIVE:
                trainDriverOnChar(keyEvent);
                break;
            case FORKS:
                forkManagerOnChar(keyEvent);
                break;
            case SEMAPHORES:
                semaphoreManagerOnChar(keyEvent);
                break;
            case TRAINS:
                if (keyEvent.getKeyType() == KeyType.Backspace) {
                    deleteVehicle();
                } else if (keyEvent.getKeyType() == KeyType.Character) {
                    trainManagerOnChar(keyEvent);
                }
                break;
            case LINK:
                linkerOnChar(keyEvent);
                break;
            case UNLINK:
                unlinkerOnChar(keyEvent);
                break;
            case STATIONS:
                stationManagerOnChar(keyEvent);
                break;
            case PROGRAM:
                programManagerOnChar(keyEvent);
                break;
            case LOAD_TRAINS:
            case MENU:
                break;
        }
    }

    @Override
    public void onKeyUp(KeyStroke keyEvent) {
        if (model.getMode() == RAILS) {
            railTrackMaker.onKeyUp(keyEvent);
        }
    }

    void programManagerOnChar(KeyStroke keyEvent) {
        if (keyEvent.getKeyType() == KeyType.Character && keyEvent.getCharacter() == ' ') {
            view.showIDE();
        } else if (keyEvent.getKeyType() == KeyType.F12) {
            view.showIDE();
        }
    }

    void stationManagerOnChar(KeyStroke keyEvent) {
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
                     * train.getItinerary().restart(actualStop);
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
                        letrain.vehicle.impl.Linker linker = selectedStation.getTrack().getLinker();
                        if (linker != null && linker.getTrain() != null) {
                            linker.getTrain().performIndustrialAction(selectedStation);
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

    private void semaphoreManagerOnChar(KeyStroke keyEvent) {
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

    private void unlinkerOnChar(KeyStroke keyEvent) {
        log.info("Unlinker key: " + keyEvent.getKeyType() + " char: " + keyEvent.getCharacter()); // DEBUG
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

    private void linkerOnChar(KeyStroke keyEvent) {
        log.info("Linker key: " + keyEvent.getKeyType() + " char: " + keyEvent.getCharacter()); // DEBUG
        switch (keyEvent.getKeyType()) {
            case ArrowUp:
                selectVehiclesInFront();
                break;
            case ArrowDown:
                selectVehiclesAtBack();
                break;
            case ArrowLeft:
                if (model.getSelectedLocomotive() != null && model.getSelectedLocomotive().getTrain() != null) {
                    model.getSelectedLocomotive().getTrain().removeLinkerToJoin();
                    log.info("Removed linker to join. Count: "
                            + model.getSelectedLocomotive().getTrain().getNumLinkersToJoin()); // DEBUG
                }
                break;
            case ArrowRight:
                if (model.getSelectedLocomotive() != null && model.getSelectedLocomotive().getTrain() != null) {
                    model.getSelectedLocomotive().getTrain().addLinkerToJoin();
                    log.info("Added linker to join. Count: "
                            + model.getSelectedLocomotive().getTrain().getNumLinkersToJoin()); // DEBUG
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

    private void trainManagerOnChar(KeyStroke keyEvent) {
        if (model.getRailMap().getTrackAt(model.getCursor().getPosition()) == null) {
            return;
        }
        String c = keyEvent.getCharacter().toString();
        if (c.isEmpty()) {
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
            train.addTrainEventListener(this);
            train.setDirectorLinker(locomotive);
            model.addLocomotive(locomotive);
            model.getEconomyManager().onLocomotiveConstructed(locomotive);
            track.enterLinkerFromDir(model.getCursor().getDir().inverse(), locomotive);
            cursorDir = locomotive.getDir();
        } else {
            Wagon wagon = new Wagon(c);
            model.addWagon(wagon);
            model.getEconomyManager().onWagonConstructed(wagon);
            track.enterLinkerFromDir(model.getCursor().getDir().inverse(), wagon);
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
            letrain.vehicle.impl.Linker linker = track.getLinker();
            if (linker instanceof letrain.vehicle.impl.rail.Locomotive) {
                model.removeLocomotive((letrain.vehicle.impl.rail.Locomotive) linker);
            } else if (linker instanceof letrain.vehicle.impl.rail.Wagon) {
                model.removeWagon((letrain.vehicle.impl.rail.Wagon) linker);
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

    private void forkManagerOnChar(KeyStroke keyEvent) {
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
            // case Character:
            // if (keyEvent.getCharacter() == ' ') {
            // toggleReversed();
            // locomotiveId = 0;
            // } else if (keyEvent.getCharacter() >= '0' && keyEvent.getCharacter() <= '9')
            // {
            // if (keyEvent.getCharacter() == '0' && locomotiveId == 0) {
            // model.setShowId(true);
            // } else {
            // locomotiveId = locomotiveId * 10 + (keyEvent.getCharacter() - '0');
            // selectLocomotive(locomotiveId);
            // }
            // }
            // break;
            case ArrowUp:
                if (model.getSelectedLocomotive() != null) {
                    Locomotive loco = model.getSelectedLocomotive();
                    if (loco.isEngineOn() && !loco.getTrain().isLoading) {
                        accelerateLocomotive();
                        locomotiveId = 0;
                    }
                }
                break;
            case ArrowDown:
                if (model.getSelectedLocomotive() != null) {
                    Locomotive loco = model.getSelectedLocomotive();
                    if (loco.isEngineOn() && !loco.getTrain().isLoading) {
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
                    Station station = train.getStationAtTrain();
                    if (station != null) {
                        if (train.isLoading()) { // Si ya está cargando/descargando, lo termina
                            train.endLoadUnloadProcess();
                        } else {
                            CargoTypes trainCargoType = train.getTrainCargoType();
                            if (trainCargoType != CargoTypes.NONE
                                    && station.getRole() == CargoTypes.StationRole.CONSUMER) {
                                train.startUnloadProcess(station);
                                train.recordStopAtStation();
                            } else if (trainCargoType == CargoTypes.NONE
                                    && station.getRole() == CargoTypes.StationRole.PRODUCER) {
                                train.startLoadProcess(station);
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
            List<Linker> linkersToDestroy = model.getSelectedLocomotive().getTrain()
                    .destroyLinkers(() -> model.nextTrainId());
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
                    train.joinLinkers();
                }
                model.setMode(letrain.mvp.Model.GameMode.MENU);
            }
        }
    }

    private void selectVehiclesAtBack() {
        if (model.getSelectedLocomotive() != null &&
                model.getSelectedLocomotive().getTrain() != null) {
            model.getSelectedLocomotive().getTrain().setLinkersToJoin(false);
        }
    }

    private void selectVehiclesInFront() {
        if (model.getSelectedLocomotive() != null) {
            if (model.getSelectedLocomotive().getTrain() != null) {
                model.getSelectedLocomotive().getTrain().setLinkersToJoin(true);
            } else {
                // handle error
            }
        } else {
            // handle error
        }
    }

    private void selectFrontDivisionSense() {
        model.getSelectedLocomotive().getTrain().setFrontDivisionSense();
    }

    private void selectBackDivisionSense() {
        model.getSelectedLocomotive().getTrain().setBackDivisionSense();
    }

    private void selectNextLink() {
        model.getSelectedLocomotive().getTrain().selectNextDivisionLink();
    }

    private void selectPrevLink() {
        model.getSelectedLocomotive().getTrain().selectPrevDivisionLink();
    }

    private void divideTrain() {
        Locomotive loco = model.getSelectedLocomotive();
        if (loco != null && loco.getTrain() != null) {
            loco.getTrain().divideTrain(() -> model.nextTrainId());
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
        Point p = view.getMapScrollPage();
        p.setY(p.getY() + 1);
        view.setMapScrollPage(p);
        view.clear();
    }

    private void mapPageLeft() {
        view.clear();
        Point p = view.getMapScrollPage();
        p.setX(p.getX() - 1);
        view.setMapScrollPage(p);
        view.clear();

    }

    private void mapPageUp() {
        view.clear();
        Point p = view.getMapScrollPage();
        p.setY(p.getY() - 1);
        view.setMapScrollPage(p);
        view.clear();
    }

    private void mapPageRight() {
        view.clear();
        Point p = view.getMapScrollPage();
        p.setX(p.getX() + 1);
        view.setMapScrollPage(p);
        view.clear();

    }

    void setPageOfPoint(Point p) {
        view.setPageOfPos(p.getX(), p.getY());
        Page page = p.getPage();
        railTrackMaker.setCursorPage(page);
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
            File mapFile = changeExtension(file, "ltr");
            saveModel(this.model, mapFile);
        }
    }

    @Override
    public void onLoadGame(File file) {
        if (file != null && file.exists()) {
            File mapFile = changeExtension(file, "ltr");
            Model loadedModel = loadModel(mapFile);
            if (loadedModel != null) {
                stop();
                setModel(loadedModel);
                // Register this as listener for all trains in the loaded model
                for (Locomotive loco : loadedModel.getLocomotives()) {
                    if (loco.getTrain() != null) {
                        loco.getTrain().addTrainEventListener(this);
                    }
                }
                // Re-establish script listeners
                if (loadedModel.getProgram() != null && !loadedModel.getProgram().isEmpty()) {
                    loadedModel.setProgram(loadedModel.getProgram());
                }
                // Re-attach stations as listeners to trains they are hosting
                for (Locomotive loco : loadedModel.getLocomotives()) {
                    Train train = loco.getTrain();
                    if (train != null && train.getStationId() != 0) {
                        for (Station station : loadedModel.getStations()) {
                            if (station.getId() == train.getStationId()) {
                                train.addTrainEventListener(station);
                                break;
                            }
                        }
                    }
                }
                start();
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
        CharStream input = loadProgram(file);
        if (input == null) {
            return;
        }
        List<String> errors = model.setProgram(input.toString());
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

    void saveModel(Model model, File file) {
        model.setLastSaveTime(LocalDateTime.now());
        try (FileOutputStream fos = new FileOutputStream(file);
                ObjectOutputStream oos = new ObjectOutputStream(fos)) {
            oos.writeObject(model);
        } catch (IOException ex) {
            log.error("Error saving model", ex);
        }
    }

    Model loadModel(File file) {
        try (FileInputStream fis = new FileInputStream(file);
                ObjectInputStream ois = new ObjectInputStream(fis)) {
            Model model = (Model) ois.readObject();
            return model;
        } catch (IOException | ClassNotFoundException ex) {
            log.error("Error loading model", ex);
            return null;
        }
    }

    CharStream loadProgram(File file) {
        try (FileInputStream fis = new FileInputStream(file)) {
            CharStream program = null;
            try {
                program = CharStreams.fromFileName(file.getName());
            } catch (IOException e) {
                e.printStackTrace();
            }
            return program;
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
    public void onMapPageChanged(Point mapScrollPage, int columns, int rows) {
        model.updateGroundMap(mapScrollPage, columns, rows);

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
            audioController.playOneShot("link", (float) pos.getX(), (float) pos.getY());
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
            audioController.playOneShot("link", 0, 0); // Position is less critical for link
        }
    }

    @Override
    public void onUnlink(Train train) {
        if (audioController != null) {
            audioController.playOneShot("link", 0, 0);
        }
    }

    @Override
    public void onCrash(Train train, letrain.map.Point pos, int speed) {
        if (audioController != null && pos != null) {
            audioController.playOneShot("link", (float) pos.getX(), (float) pos.getY());
            // Immediately stop audio for all locomotives involved in the crash
            for (Locomotive loco : model.getLocomotives()) {
                if (loco.isDestroying()) {
                    audioController.stopSynthesizer(loco.getId());
                }
            }
        }
    }
}
