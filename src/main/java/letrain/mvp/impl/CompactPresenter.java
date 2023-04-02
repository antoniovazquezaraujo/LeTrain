package letrain.mvp.impl;

import letrain.map.Dir;
import letrain.map.Point;
import letrain.track.rail.RailTrack;
import letrain.vehicle.impl.rail.Locomotive;
import letrain.vehicle.impl.rail.Train;
import letrain.vehicle.impl.rail.Wagon;
import letrain.visitor.InfoVisitor;
import letrain.visitor.RenderVisitor;

import static letrain.mvp.Model.GameMode.*;

import java.io.IOException;

import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import com.googlecode.lanterna.input.KeyType;
public class CompactPresenter implements letrain.mvp.Presenter {
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
    Screen screen;
    RailTrackMaker maker;

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
            this.screen = new TerminalScreen(terminal);
            view.setScreen(this.screen);
            this.screen.startScreen();
            KeyStroke stroke = null;
            while (true) {
                stroke = null;
                stroke = view.readKey();
                if(view.isEndOfGame(stroke)){
                    break;
                }
                if(null != stroke){
                    onChar(stroke);
                }
                renderer.visitModel(model);
                informer.visitModel(model);
                view.paint();
                model.moveTrains();
                Thread.sleep(10);
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
    public void onChar( KeyStroke keystroke) {
        switch (keystroke.getKeyType()) {
            case F1:
                model.setMode(TRACKS);
                break;
            case F2:
                model.setMode(TRAINS);
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
        }

        switch (model.getMode()) {
            case TRACKS:
                maker.onChar(keystroke);
                break;
            case TRAINS:
                this.newTrain = null;
                switch (keystroke.getKeyType()) {
                    case Home:
                        if (model.getSelectedTrain() != null) {
                            if (model.getSelectedTrain().getVelocity() < 0.1f) {
                                model.getSelectedTrain().reverse(false);
                            }
                        }
                        break;
                    case End:
                        if (model.getSelectedTrain() != null) {
                            if (model.getSelectedTrain().getVelocity() < 0.1f) {
                                model.getSelectedTrain().reverse(true);
                            }
                        }
                        break;
                    case Character:
                        if (' ' == keystroke.getCharacter() && model.getSelectedTrain() != null) {
                            if (Math.abs(model.getSelectedTrain().getForce()) != 0) {
                                model.getSelectedTrain().setBrakesActivated(true);
                            } else {
                                model.getSelectedTrain().setBrakesActivated(false);
                            }
                            model.getSelectedTrain().setForce(0);
                            model.getSelectedTrain().setBrakes(0);
                        }
                        break;
                    case ArrowUp:
                        if (model.getSelectedTrain() != null && model.getSelectedTrain().isBrakesActivated()) {
                            incTrainBrakes();
                        } else {
                            accelerateTrain();
                        }
                        break;
                    case ArrowDown:
                        if (model.getSelectedTrain() != null && model.getSelectedTrain().isBrakesActivated()) {
                            decTrainBrakes();
                        } else {
                            decelerateTrain();
                        }
                        break;
                    case ArrowLeft:
                        selectPrevTrain();
                        break;
                    case ArrowRight:
                        selectNextTrain();
                        break;
                    case PageUp:
                        if (keystroke.isCtrlDown()) {
                            mapPageRight();
                        } else {
                            mapPageUp();
                        }
                        break;
                    case PageDown:
                        if (keystroke.isCtrlDown()) {
                            mapPageLeft();
                        } else {
                            mapPageDown();
                        }
                }
                break;

            case CREATE_LOAD_PLATFORM:
                switch (keystroke.getKeyType()) {
                    case ArrowUp:
                        createLoadPlatformTrack();
                        break;
                }
                break;
            case FORKS:
                switch (keystroke.getKeyType()) {
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
                switch (keystroke.getKeyType()) {
                    case ArrowUp:
                        loadTrain();
                        break;
                    case ArrowDown:
                        unloadTrain();
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
                char c = keystroke.getCharacter();
                if (!(""+c).matches("([A-Za-z])?")) {
                    break;
                }
                RailTrack track = model.getRailMap().getTrackAt(model.getCursor().getPosition());
                
                if (Character.toUpperCase(c) == c) {
                    createLocomotive(c, track);
                } else {
                    createWagon(c, track);
                }
                model.getCursor().getPosition().move(Dir.E);
                break;
        }

    }

    /***********************************************************
     * FACTORIES
     **********************************************************/

    Train newTrain;

    private Train getNewTrain() {
        if (newTrain == null) {
            newTrain = new Train();
            model.addTrain(newTrain);
        }
        return newTrain;
    }

    private void createWagon(char c, RailTrack track) {
        Wagon wagon = new Wagon(c);
        wagon.setDir(Dir.E);
        getNewTrain().pushBack(wagon);
        track.enter(wagon);
    }

    private void createLocomotive(char c, RailTrack track) {
        Locomotive locomotive = new Locomotive(c);
        locomotive.setDir(Dir.E);
        getNewTrain().pushBack(locomotive);
        track.enter(locomotive);
        if (getNewTrain().getDirectorLinker() == null) {
            getNewTrain().assignDefaultDirectorLinker();
        }

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

    private void selectNextTrain() {
        model.selectNextTrain();
    }

    private void selectPrevTrain() {
        model.selectPrevTrain();
    }

    private void decelerateTrain() {
        if (model.getSelectedTrain() == null)
            return;
        model.getSelectedTrain().decForce(10);
    }

    private void accelerateTrain() {
        if (model.getSelectedTrain() == null)
            return;
        model.getSelectedTrain().incForce(10);
    }

    private void incTrainBrakes() {
        if (model.getSelectedTrain() == null)
            return;
        model.getSelectedTrain().incBrakes(10);
    }

    private void decTrainBrakes() {
        if (model.getSelectedTrain() == null)
            return;
        model.getSelectedTrain().decBrakes(10);
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

    private void unloadTrain() {

    }

    private void loadTrain() {

    }

}
