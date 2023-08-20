package letrain.mvp.impl;

import static letrain.mvp.Model.GameMode.DRIVE;
import static letrain.mvp.Model.GameMode.FORKS;
import static letrain.mvp.Model.GameMode.LINK;
import static letrain.mvp.Model.GameMode.MENU;
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
import java.time.ZoneOffset;
import java.util.List;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import letrain.command.CommandManager;
import letrain.command.LeTrainProgramLexer;
import letrain.command.LeTrainProgramParser;
import letrain.map.Dir;
import letrain.map.Page;
import letrain.map.Point;
import letrain.mvp.Model.GameMode;
import letrain.track.Sensor;
import letrain.track.Station;
import letrain.track.rail.RailTrack;
import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.rail.Itinerary;
import letrain.vehicle.impl.rail.Locomotive;
import letrain.vehicle.impl.rail.Stop;
import letrain.vehicle.impl.rail.Train;
import letrain.vehicle.impl.rail.Wagon;
import letrain.visitor.InfoVisitor;
import letrain.visitor.RenderVisitor;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CompactPresenter implements letrain.mvp.Presenter {
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

    RailTrackMaker railTrackMaker;

    public CompactPresenter() {
        this(null);
    }

    public CompactPresenter(Model model) {
        setModel(model);
        view = new View(this);
        renderer = new RenderVisitor(view);
        informer = new InfoVisitor(view);
        railTrackMaker = new RailTrackMaker(this);
    }

    void setModel(Model model) {
        if (model != null) {
            this.model = model;
        } else {
            this.model = new Model();
        }
    }

    public void stop() {
        running = false;
    }

    public void start() {
        running = true;
        try {

            KeyStroke stroke = null;
            model.setMode(RAILS);
            model.updateGroundMap(new Point(0, 0), view.getCols(), view.getRows());
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
                railTrackMaker.makeTracks();
                renderer.visitModel(model);
                informer.visitModel(model);
                view.paint();
                model.moveLocomotives();
                if (model.getMode() == DRIVE) {
                    Locomotive selectedLocomotive = model.getSelectedLocomotive();
                    if (selectedLocomotive != null) {
                        view.setPageOfPos(selectedLocomotive.getPosition().getX(),
                                selectedLocomotive.getPosition().getY());
                    }
                }
                model.loadAndUnloadTrains();
                model.removeDestroyedTrains();
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
            model.setMode(MENU);
            return;
        } else if (keyEvent.getKeyType() == KeyType.Escape) {
            showMainDialog();
        } else if (keyEvent.getKeyType() == KeyType.Character && keyEvent.getCharacter() != ' ') {
            if (model.getMode() == TRAINS) {
                trainManagerOnChar(keyEvent);
            } else {
                switch (keyEvent.getCharacter()) {
                    case 'r':
                        model.setMode(RAILS);
                        break;
                    case 'd':
                        model.setMode(DRIVE);
                        break;
                    case 'f':
                        model.setMode(FORKS);
                        break;
                    case 's':
                        model.setMode(SEMAPHORES);
                        break;
                    case 't':
                        model.setMode(TRAINS);
                        newTrain = null;
                        break;
                    case 'l':
                        model.setMode(LINK);
                        break;
                    case 'u':
                        model.setMode(UNLINK);
                        break;
                    case 'n':
                        model.setMode(STATIONS);
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
                // Not managed here!!
                // trainManagerOnChar(keyEvent);
                break;
            case LINK:
                linkerOnChar(keyEvent);
                break;
            case UNLINK:
                unlinkerOnChar(keyEvent);
                break;
            case STATIONS:
                StationManagerOnChar(keyEvent);
                break;
        }
    }

    void StationManagerOnChar(KeyStroke keyEvent) {
        switch (keyEvent.getKeyType()) {
            case Backspace:
                StationId = StationId / 10;
                selectStation(StationId);
                break;
            case Character:
                if (keyEvent.getCharacter() == '-') {
                    Station selectedStation = model.getSelectedStation();
                    if (selectedStation != null) {
                        Linker linker = selectedStation.getTrack().getLinker();
                        if (linker != null) {
                            Train train = linker.getTrain();
                            train.startLoadUnloadProcess();
                            Stop actualStop = train.recordStopAtStation();
                            Itinerary.ItineraryState state = train.getItinerary().getState();
                            if (!state.equals(Itinerary.ItineraryState.STARTING)) {
                                int startStationId = train.getItinerary().getFirstStop().stationId();
                                int firstStopDistanceTraveled = train.getItinerary().getFirstStop().distanceTraveled();
                                int totalDistanceTraveled = actualStop.distanceTraveled() - firstStopDistanceTraveled;
                                LocalDateTime startTime = train.getItinerary().getFirstStop().stopTime();
                                LocalDateTime elapsedTime = actualStop.stopTime()
                                        .minusMinutes(startTime.toEpochSecond(ZoneOffset.UTC));
                                double linearDistanceToStart = model.getLinearDistanceBetweenStations(startStationId,
                                        actualStop.stationId());
                                model.getEconomyManager().onLoadPassengers(train, elapsedTime, totalDistanceTraveled,
                                        linearDistanceToStart);
                            }
                            if (state.equals(Itinerary.ItineraryState.AT_END)) {
                                train.getItinerary().restart(actualStop);
                            }
                        }
                    }
                } else if (keyEvent.getCharacter() == ' ') {
                    StationId = 0;
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
        }

    }

    void showMainDialog() {
        view.showMainDialog();
    }

    private void semaphoreManagerOnChar(KeyStroke keyEvent) {
        switch (keyEvent.getKeyType()) {
            case Backspace:
                semaphoreId = semaphoreId / 10;
                selectSemaphore(semaphoreId);
                break;
            case Character:
                if (keyEvent.getCharacter() == ' ') {
                    toggleSemaphore();
                    semaphoreId = 0;
                } else if (keyEvent.getCharacter() >= '0' && keyEvent.getCharacter() <= '9') {
                    semaphoreId = semaphoreId * 10 + (keyEvent.getCharacter() - '0');
                    selectSemaphore(semaphoreId);
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
        }
    }

    private void unlinkerOnChar(KeyStroke keyEvent) {
        switch (keyEvent.getKeyType()) {
            case ArrowLeft:
                selectFrontDivisionSense();
                break;
            case ArrowRight:
                selectBackDivisionSense();
                break;
            case ArrowUp:
                selectNextLink();
                break;
            case ArrowDown:
                selectPrevLink();
                break;
            case Character:
                if (keyEvent.getCharacter() == ' ') {
                    divideTrain();
                    model.setMode(GameMode.MENU);
                }
                break;
            case Delete:
                destroyLinkers();
                model.setMode(GameMode.MENU);
                break;

        }
    }

    private void linkerOnChar(KeyStroke keyEvent) {
        switch (keyEvent.getKeyType()) {
            case ArrowUp:
                selectVehiclesInFront();
                break;
            case ArrowDown:
                selectVehiclesAtBack();
                break;
            case Character:
                if (keyEvent.getCharacter() == ' ') {
                    linkSelectedVehicles();
                    model.setMode(GameMode.MENU);
                }
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

    private void forkManagerOnChar(KeyStroke keyEvent) {
        switch (keyEvent.getKeyType()) {
            case Backspace:
                forkId = forkId / 10;
                selectFork(forkId);
                break;
            case Character:
                if (keyEvent.getCharacter() == ' ') {
                    toggleFork();
                    forkId = 0;
                } else if (keyEvent.getCharacter() >= '0' && keyEvent.getCharacter() <= '9') {
                    forkId = forkId * 10 + (keyEvent.getCharacter() - '0');
                    selectFork(forkId);
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
        }
    }

    private void trainDriverOnChar(KeyStroke keyEvent) {
        this.newTrain = null;
        model.setShowId(false);
        switch (keyEvent.getKeyType()) {
            case Backspace:
                locomotiveId = locomotiveId / 10;
                selectLocomotive(locomotiveId);
                break;
            case Character:
                if (keyEvent.getCharacter() == ' ') {
                    toggleReversed();
                    locomotiveId = 0;
                } else if (keyEvent.getCharacter() >= '0' && keyEvent.getCharacter() <= '9') {
                    if (keyEvent.getCharacter() == '0' && locomotiveId == 0) {
                        model.setShowId(true);
                    } else {
                        locomotiveId = locomotiveId * 10 + (keyEvent.getCharacter() - '0');
                        selectLocomotive(locomotiveId);
                    }
                }
                break;
            case ArrowUp:
                if (model.getSelectedLocomotive() != null) {
                    if (!model.getSelectedLocomotive().getTrain().isLoading) {
                        accelerateLocomotive();
                        locomotiveId = 0;
                    }
                }
                break;
            case ArrowDown:
                decelerateLocomotive();
                locomotiveId = 0;
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
            if (model.getSelectedLocomotive().getTrain() != null) {
                model.getSelectedLocomotive().getTrain().joinLinkers();
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
        model.getSelectedLocomotive().getTrain().divideTrain(() -> model.nextTrainId());
    }

    public void selectLocomotive(int id) {
        if (model.selectLocomotive(id)) {
            setPageOfPoint(model.getSelectedLocomotive().getTrack().getPosition());
        }
    }

    /***********************************************************
     * FACTORIES
     **********************************************************/

    Train newTrain;

    private void createWagon(Dir d, String c, RailTrack track) {
        Wagon wagon = new Wagon(c);
        model.addWagon(wagon);
        track.enterLinkerFromDir(d, wagon);
    }

    private void createLocomotive(Dir d, String c, RailTrack track) {
        Locomotive locomotive = new Locomotive(model.nextLocomotiveId(), c);
        Train train = new Train(model.nextTrainId());
        train.pushBack(locomotive);
        train.setDirectorLinker(locomotive);
        model.addLocomotive(locomotive);
        track.enterLinkerFromDir(d, locomotive);
    }

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

    private void updateCursorPosition(Point newPos) {
        getModel().getCursor().setPosition(newPos);
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

    @Override
    public void onNewGame() {
    }

    @Override
    public void onSaveGame(File file) {
        if (file != null) {
            saveModel(this.model, file);
        }
    }

    @Override
    public void onLoadGame(File file) {
        if (file != null && file.exists()) {
            Model model = loadModel(file);
            if (model != null) {
                stop();
                setModel(model);
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
        for (Sensor sensor : model.getSensors()) {
            sensor.removeAllSensorEventListeners();
        }
        LeTrainProgramLexer lexer = new LeTrainProgramLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        LeTrainProgramParser parser = new LeTrainProgramParser(tokens);

        LeTrainProgramParser.StartContext sintaxTree = parser.start();
        CommandManager manager = new CommandManager(model);
        manager.visit(sintaxTree);
        model.setProgram(input.toString());
    }

    @Override
    public void onEditCommands(String content) {
        CharStream input = CharStreams.fromString(content);
        for (Sensor sensor : model.getSensors()) {
            sensor.removeAllSensorEventListeners();
        }
        LeTrainProgramLexer lexer = new LeTrainProgramLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        LeTrainProgramParser parser = new LeTrainProgramParser(tokens);

        LeTrainProgramParser.StartContext sintaxTree = parser.start();
        CommandManager manager = new CommandManager(model);
        manager.visit(sintaxTree);
        model.setProgram(content);

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
}
