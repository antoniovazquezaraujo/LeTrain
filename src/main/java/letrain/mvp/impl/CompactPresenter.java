package letrain.mvp.impl;

import static letrain.mvp.Model.GameMode.DIVIDE_TRAINS;
import static letrain.mvp.Model.GameMode.FORKS;
import static letrain.mvp.Model.GameMode.LINK_TRAINS;
import static letrain.mvp.Model.GameMode.LOAD_TRAINS;
import static letrain.mvp.Model.GameMode.LOCOMOTIVES;
import static letrain.mvp.Model.GameMode.MAKE_TRAINS;
import static letrain.mvp.Model.GameMode.TRACKS;

import java.io.IOException;
import java.util.List;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;

import letrain.map.Dir;
import letrain.map.Point;
import letrain.mvp.GameViewListener;
import letrain.track.rail.RailTrack;
import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.rail.Locomotive;
import letrain.vehicle.impl.rail.Train;
import letrain.vehicle.impl.rail.Wagon;
import letrain.visitor.InfoVisitor;
import letrain.visitor.RenderVisitor;

public class CompactPresenter implements GameViewListener, letrain.mvp.Presenter {
    public enum TrackType {
        NORMAL_TRACK,
        STOP_TRACK,
        TRAIN_FACTORY_GATE,
        TUNNEL_GATE
    }

    private final letrain.mvp.Model model;
    private final letrain.mvp.View view;
    private final RenderVisitor renderer;
    private final InfoVisitor informer;

    RailTrackMaker maker;
    private TerminalScreen screen;

    public CompactPresenter() {
        this(null);
    }

    public CompactPresenter(Model model) {
        if (model != null) {
            this.model = model;
        } else {
            this.model = new Model();
        }
        view = new View(this);
        renderer = new RenderVisitor(view);
        informer = new InfoVisitor(view);
        maker = new RailTrackMaker(model, view);
    }

    public void start() {
        DefaultTerminalFactory defaultTerminalFactory = new DefaultTerminalFactory();
        try {
            Terminal terminal = defaultTerminalFactory.createTerminal();
            terminal.setCursorVisible(false);
            this.screen = new TerminalScreen(terminal);
            this.screen.setCursorPosition(null);

            view.setScreen(this.screen);
            this.screen.startScreen();
            KeyStroke stroke = null;
            while (true) {
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
                renderer.visitModel(model);
                informer.visitModel(model);
                view.paint();
                model.moveLocomotives();
                Thread.sleep(100);
                view.clear();
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (this.screen != null) {
                try {
                    this.screen.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
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

    @Override
    public void onChar(KeyStroke keyEvent) {
        switch (keyEvent.getKeyType()) {
            case F1:
                model.setMode(TRACKS);
                break;
            case F2:
                model.setMode(LOCOMOTIVES);
                break;
            case F3:
                model.setMode(FORKS);
                break;
            case F4:
                model.setMode(LOAD_TRAINS);
                break;
            case F5:
                model.setMode(MAKE_TRAINS);
                newTrain = null;
                break;
            case F6:
                model.setMode(LINK_TRAINS);
                break;
            case F7:
                model.setMode(DIVIDE_TRAINS);
                break;

        }

        switch (model.getMode()) {
            case TRACKS:
                maker.onChar(keyEvent);
                break;
            case LOCOMOTIVES:
                this.newTrain = null;
                switch (keyEvent.getKeyType()) {
                    case Character:
                        if (keyEvent.getCharacter() == ' ') {
                            toggleReversed();
                        }
                        break;
                    case ArrowUp:
                        accelerateLocomotive();
                        break;
                    case ArrowDown:
                        decelerateLocomotive();
                        break;
                    case ArrowLeft:
                        selectPrevLocomotive();
                        break;
                    case ArrowRight:
                        selectNextLocomotive();
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
                break;

            case CREATE_LOAD_PLATFORM:
                switch (keyEvent.getKeyType()) {
                    case ArrowUp:
                        createLoadPlatformTrack();
                        break;
                }
                break;
            case FORKS:
                switch (keyEvent.getKeyType()) {
                    case ArrowUp:
                        toggleFork();
                        break;
                    case ArrowDown:
                        toggleFork();
                        break;
                    case ArrowLeft:
                        selectPrevFork();
                        break;
                    case ArrowRight:
                        selectNextFork();
                        break;
                }
                break;
            case LOAD_TRAINS:
                switch (keyEvent.getKeyType()) {
                    case ArrowUp:
                        loadTrain();
                        break;
                    case ArrowDown:
                        // unloadTrain();
                        break;
                    case ArrowLeft:
                        selectPrevLoadPlatform();
                        break;
                    case ArrowRight:
                        selectNextLoadPlatform();
                        break;
                }
                break;
            case MAKE_TRAINS:
                if (model.getRailMap().getTrackAt(model.getCursor().getPosition()) == null) {
                    break;
                }
                String c = keyEvent.getCharacter().toString();
                if (c.isEmpty()) {
                    break;
                }
                if (!c.matches("([A-Za-z])?")) {
                    break;
                }
                RailTrack track = model.getRailMap().getTrackAt(model.getCursor().getPosition());
                if (c.toUpperCase().equals(c)) {
                    createLocomotive(model.getCursor().getDir(), c, track);
                } else {
                    createWagon(model.getCursor().getDir(), c, track);
                }
                model.getCursor().getPosition().move(Dir.E);
                break;
            case LINK_TRAINS:
                switch (keyEvent.getKeyType()) {
                    case ArrowUp:
                        selectVehiclesInFront();
                        break;
                    case ArrowDown:
                        selectVehiclesAtBack();
                        break;
                    case Character:
                        if (keyEvent.getCharacter() == ' ') {
                            linkOkUnlinkSelectedVehicles();
                            break;
                        }
                }
                break;
            case DIVIDE_TRAINS:
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
                        }
                        break;
                    case Delete:
                        destroyLinkers();
                        break;

                }
                break;
        }
    }

    private void destroyLinkers() {
        List<Linker> linkersToDestroy = model.getSelectedLocomotive().getTrain().destroyLinkers();
        for (Linker linker : linkersToDestroy) {
            if (linker instanceof Locomotive) {
                model.removeLocomotive((Locomotive) linker);
            } else {
                model.removeWagon((Wagon) linker);
            }
            linker.getTrack().removeLinker();
        }
    }

    private void toggleReversed() {
        if (model.getSelectedLocomotive().getSpeed() == 0) {
            model.getSelectedLocomotive().toggleReversed();
        }
    }

    private void linkOkUnlinkSelectedVehicles() {
        model.getSelectedLocomotive().getTrain().joinLinkers();
    }

    private void selectVehiclesAtBack() {
        model.getSelectedLocomotive().getTrain().setLinkersToJoin(false);
    }

    private void selectVehiclesInFront() {
        model.getSelectedLocomotive().getTrain().setLinkersToJoin(true);
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
        model.getSelectedLocomotive().getTrain().divideTrain();
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
        Locomotive locomotive = new Locomotive(c);
        Train train = new Train();
        train.pushBack(locomotive);
        train.setDirectorLinker(locomotive);
        model.addLocomotive(locomotive);
        track.enterLinkerFromDir(d, locomotive);
    }

    /***********************************************************
     * FORKS
     **********************************************************/

    private void selectNextFork() {
        model.selectNextFork();
    }

    private void selectPrevFork() {
        model.selectPrevFork();
    }

    private void toggleFork() {
        if (model.getSelectedFork() != null) {
            model.getSelectedFork().flipRoute();
        }
    }

    /***********************************************************
     * TRAINS
     **********************************************************/

    private void selectNextLocomotive() {
        model.selectNextLocomotive();
    }

    private void selectPrevLocomotive() {
        model.selectPrevLocomotive();
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

    /***********************************************************
     * LOAD_PLATFORM
     **********************************************************/

    private void createLoadPlatformTrack() {

    }

    private void selectNextLoadPlatform() {

    }

    private void selectPrevLoadPlatform() {

    }

    private void unloadLocomotive() {

    }

    private void loadTrain() {

    }

}
