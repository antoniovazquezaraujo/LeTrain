package letrain.mvp.impl;

import static letrain.mvp.Model.GameMode.DRIVE;
import static letrain.mvp.Model.GameMode.FORKS;
import static letrain.mvp.Model.GameMode.LINK;
import static letrain.mvp.Model.GameMode.MENU;
import static letrain.mvp.Model.GameMode.RAILS;
import static letrain.mvp.Model.GameMode.TRAINS;
import static letrain.mvp.Model.GameMode.UNLINK;

import java.io.IOException;
import java.util.List;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
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
        try {

            KeyStroke stroke = null;
            model.setMode(MENU);
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
                Thread.sleep(50);
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
    // [r:Rails d:Drive f:Forks t:Trains l:Link u:Unlink

    @Override
    public void onChar(KeyStroke keyEvent) {
        if (keyEvent.getKeyType() == KeyType.Character) {
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
            }
        }

        switch (model.getMode()) {
            case RAILS:
                maker.onChar(keyEvent);
                break;
            case DRIVE:
                trainDriverOnChar(keyEvent);
                break;
            case FORKS:
                forkManagerOnChar(keyEvent);
                break;
            case TRAINS:
                trainManagerOnChar(keyEvent);
                break;
            case LINK:
                linkerOnChar(keyEvent);
                break;
            case UNLINK:
                unlinkerOnChar(keyEvent);
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
                }
                break;
            case Delete:
                destroyLinkers();
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
                    linkOkUnlinkSelectedVehicles();
                }
                break;
        }
    }

    private void trainManagerOnChar(KeyStroke keyEvent) {
        if (model.getRailMap().getTrackAt(model.getCursor().getPosition()) == null) {
            return;
        }
        String c = keyEvent.getCharacter().toString();
        if (keyEvent.getKeyType() == KeyType.Tab) {
            model.setMode(MENU);
            return;
        }
        if (c.isEmpty()) {
            return;
        }
        if (!c.matches("([A-Za-z])?")) {
            return;
        }
        RailTrack track = model.getRailMap().getTrackAt(model.getCursor().getPosition());
        if (c.toUpperCase().equals(c)) {
            createLocomotive(model.getCursor().getDir(), c, track);
        } else {
            createWagon(model.getCursor().getDir(), c, track);
        }
        model.getCursor().getPosition().move(Dir.E);
    }

    private void forkManagerOnChar(KeyStroke keyEvent) {
        switch (keyEvent.getKeyType()) {
            case Character:
                if (keyEvent.getCharacter() == ' ') {
                    toggleFork();
                }
                break;
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
    }

    private void trainDriverOnChar(KeyStroke keyEvent) {
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
