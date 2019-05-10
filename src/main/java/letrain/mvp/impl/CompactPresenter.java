package letrain.mvp.impl;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.input.KeyEvent;
import javafx.util.Duration;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.mvp.GameViewListener;
import letrain.track.rail.RailTrack;
import letrain.vehicle.impl.Tractor;
import letrain.vehicle.impl.rail.Locomotive;
import letrain.vehicle.impl.rail.Train;
import letrain.vehicle.impl.rail.Wagon;
import letrain.visitor.InfoVisitor;
import letrain.visitor.RenderVisitor;

import static letrain.mvp.Model.GameMode.*;

public class CompactPresenter implements GameViewListener, letrain.mvp.Presenter {
    public enum TrackType {
        NORMAL_TRACK,
        STOP_TRACK,
        TRAIN_FACTORY_GATE,
        TUNNEL_GATE
    }

    private final letrain.mvp.Model model;
    private final letrain.mvp.View view;
    private Timeline loop;
    private final RenderVisitor renderer;
    private final InfoVisitor informer;

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
        loop = new Timeline();
        loop.setCycleCount(Timeline.INDEFINITE);

        KeyFrame kf = new KeyFrame(Duration.seconds(.1), actionEvent -> {
            view.clear();
            renderer.visitModel(model);
            informer.visitModel(model);
            view.paint();
            model.moveTrains();
        });
        loop.getKeyFrames().add(kf);
        loop.play();
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
     * @param mode*/
    @Override
    public void onGameModeSelected(letrain.mvp.Model.GameMode mode) {
        //Avisamos al anterior y al nuevo
    }


    @Override
    public void onChar(KeyEvent keyEvent) {
        switch (keyEvent.getCode()) {
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
                break;
        }

        switch (model.getMode()) {
            case TRACKS:
                maker.onChar(keyEvent);
                break;
            case TRAINS:
                this.newTrain = null;
                switch (keyEvent.getCode()) {
                    case SPACE:
                        if (model.getSelectedTrain() != null) {
                            if (model.getSelectedTrain().getAcceleration() <= 0.001) {
                                model.getSelectedTrain().reverse();
                            }else{
                                model.getSelectedTrain().setForce(0);
                            }
                        }
                        break;
                    case UP:
                        if (keyEvent.isShiftDown()) {
                            decTrainBrakes();
                        }else {
                            accelerateTrain();
                        }
                        break;
                    case DOWN:
                        if (keyEvent.isShiftDown()) {
                            incTrainBrakes();
                        }else {
                            decelerateTrain();
                        }
                        break;
                    case LEFT:
                        selectPrevTrain();
                        break;
                    case RIGHT:
                        selectNextTrain();
                        break;
                    case PAGE_UP:
                        if (keyEvent.isControlDown()) {
                            mapPageRight();
                        } else {
                            mapPageUp();
                        }
                        break;
                    case PAGE_DOWN:
                        if (keyEvent.isControlDown()) {
                            mapPageLeft();
                        } else {
                            mapPageDown();
                        }
                }
                break;

            case CREATE_LOAD_PLATFORM:
                switch (keyEvent.getCode()) {
                    case UP:
                        createLoadPlatformTrack();
                        break;
                }
                break;
            case FORKS:
                switch (keyEvent.getCode()) {
                    case UP:
                        toggleFork();
                        break;
                    case DOWN:
                        toggleFork();
                        break;
                    case LEFT:
                        selectPrevFork();
                        break;
                    case RIGHT:
                        selectNextFork();
                        break;
                }
                break;
            case LOAD_TRAINS:
                switch (keyEvent.getCode()) {
                    case UP:
                        loadTrain();
                        break;
                    case DOWN:
                        unloadTrain();
                        break;
                    case LEFT:
                        selectPrevLoadPlatform();
                        break;
                    case RIGHT:
                        selectNextLoadPlatform();
                        break;
                }
                break;
            case MAKE_TRAINS:
                if (model.getRailMap().getTrackAt(model.getCursor().getPosition()) == null) {
                    break;
                }
                String c = keyEvent.getText();
                if (c.isEmpty()) {
                    break;
                }
                if (!c.matches("([A-Za-z])?")) {
                    break;
                }
                RailTrack track = model.getRailMap().getTrackAt(model.getCursor().getPosition());
                if (c.toUpperCase().equals(c)) {
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

    private void createWagon(String c, RailTrack track) {
        Wagon wagon = new Wagon(c);
        getNewTrain().pushBack(wagon);
        track.enterLinkerFromDir(Dir.E, wagon);
    }

    private void createLocomotive(String c, RailTrack track) {
        Locomotive locomotive = new Locomotive(c);
        getNewTrain().pushBack(locomotive);
        track.enterLinkerFromDir(Dir.E, locomotive);
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
        if (model.getSelectedTrain() == null) return;
        model.getSelectedTrain().decForce(1);
    }

    private void accelerateTrain() {
        if (model.getSelectedTrain() == null) return;
        model.getSelectedTrain().incForce(1);
    }

    private void incTrainBrakes() {
        if (model.getSelectedTrain() == null) return;
        model.getSelectedTrain().incBrakes(1);
    }

    private void decTrainBrakes() {
        if (model.getSelectedTrain() == null) return;
        model.getSelectedTrain().decBrakes(1);
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
