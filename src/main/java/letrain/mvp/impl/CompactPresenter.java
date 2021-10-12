package letrain.mvp.impl;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.input.KeyEvent;
import javafx.util.Duration;
import letrain.map.Dir;
import letrain.mvp.GameViewListener;
import letrain.physics.Vector2D;
import letrain.track.rail.RailTrack;
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
                newTrain=null;
                break;
        }

        switch (model.getMode()) {
            case TRACKS:
                maker.onChar(keyEvent);
                break;
            case TRAINS:
                this.newTrain = null;
                switch (keyEvent.getCode()) {
                    case HOME:
                        if (model.getSelectedTrain() != null) {
                            if(model.getSelectedTrain().getVelocity()<0.1f) {
                                model.getSelectedTrain().reverse(false);
                            }
                        }
                        break;
                    case END:
                        if (model.getSelectedTrain() != null) {
                            if(model.getSelectedTrain().getVelocity()<0.1f) {
                                model.getSelectedTrain().reverse(true);
                            }
                        }
                        break;
                    case SPACE:
                        if (model.getSelectedTrain() != null) {
                            if(Math.abs(model.getSelectedTrain().getMotorForce())!=0) {
                                model.getSelectedTrain().activateBrakes(true);
                            }else{
                                model.getSelectedTrain().activateBrakes(false);
                            }
                            model.getSelectedTrain().setMotorForce(0);
                            model.getSelectedTrain().setBrakes(0);
                        }
                        break;
                    case UP:
                        if (model.getSelectedTrain()!=null && model.getSelectedTrain().isBrakesActivated()) {
                            incTrainBrakes();
                        }else {
                            accelerateTrain();
                        }
                        break;
                    case DOWN:
                        if (model.getSelectedTrain()!=null && model.getSelectedTrain().isBrakesActivated()) {
                            decTrainBrakes();
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
                if (model.getRailMap().getTrackAt(model.getCursor().getPosition2D()) == null) {
                    break;
                }
                String c = keyEvent.getText();
                if (c.isEmpty()) {
                    break;
                }
                if (!c.matches("([A-Za-z])?")) {
                    break;
                }
                RailTrack track = model.getRailMap().getTrackAt(model.getCursor().getPosition2D());
                if (c.toUpperCase().equals(c)) {
                    createLocomotive(c, track);
                } else {
                    createWagon(c, track);
                }
                model.getCursor().setDir(Dir.E);
                model.getCursor().move();
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
        wagon.setDir(Dir.W);
        getNewTrain().pushBack(wagon);
        track.enter(wagon);
    }

    private void createLocomotive(String c, RailTrack track) {
        Locomotive locomotive = new Locomotive(c);
        locomotive.setDir(Dir.W);
        getNewTrain().pushBack(locomotive);
        track.enter(locomotive);
        if (getNewTrain().getMainTractor() == null) {
            getNewTrain().assignDefaultMainTractor();
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
        model.getSelectedTrain().decMotorForce(10);
    }

    private void accelerateTrain() {
        if (model.getSelectedTrain() == null) return;
        model.getSelectedTrain().incMotorForce(10);
    }

    private void incTrainBrakes() {
        if (model.getSelectedTrain() == null) return;
        model.getSelectedTrain().incBrakes(10);
    }

    private void decTrainBrakes() {
        if (model.getSelectedTrain() == null) return;
        model.getSelectedTrain().decBrakes(10);
    }

    private void mapPageDown() {
        view.clear();
        Vector2D p = view.getMapScrollPage();
        p.setY(p.getY() + 1);
        view.setMapScrollPage(p);
        view.clear();
    }

    private void mapPageLeft() {
        view.clear();
        Vector2D p = view.getMapScrollPage();
        p.setX(p.getX() - 1);
        view.setMapScrollPage(p);
        view.clear();

    }

    private void mapPageUp() {
        view.clear();
        Vector2D p = view.getMapScrollPage();
        p.setY(p.getY() - 1);
        view.setMapScrollPage(p);
        view.clear();

    }

    private void mapPageRight() {
        view.clear();
        Vector2D p = view.getMapScrollPage();
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
