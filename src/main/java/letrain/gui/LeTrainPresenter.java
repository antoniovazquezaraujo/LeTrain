package letrain.gui;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import javafx.util.Duration;
import letrain.sim.GameModel;
import letrain.sim.LeTrainModel;
import letrain.tui.BasicGraphicConverter;
import letrain.tui.GraphicConverter;
import letrain.view.UnicodeRenderer;

public class LeTrainPresenter implements GamePresenter {
    Timeline loop;
    private GameModel model;
    private LeTrainView view;
    GraphicConverter converter = new BasicGraphicConverter();
    UnicodeRenderer renderer ;

    public LeTrainPresenter() {
        model = new LeTrainModel();
        view = new LeTrainView(this);
        renderer = new UnicodeRenderer(view);
    }

    public void start() {
        loop = new Timeline();
        loop.setCycleCount(Timeline.INDEFINITE);

        KeyFrame kf = new KeyFrame(Duration.seconds(.1), actionEvent -> {
            paintLoop();
        });
        loop.getKeyFrames().add(kf);
        loop.play();
    }

    public LeTrainView getView() {
        return view;
    }


    public void paintLoop() {
        view.clear();
        renderer.renderSim(model);
        view.paint();
    }


    /***********************************************************
     * GamePresenter implementation
     **********************************************************/
    @Override
    public void onGameModeSelected(GameModel.Mode mode) {
        model.getMaker().setMode(mode);
    }

    @Override
    public void onMakerAdvance() {
        model.getMaker().advance();
    }

    @Override
    public void onMakerInverse() {
        model.getMaker().reverse();
    }

    @Override
    public void onMakerTurnLeft() {
        model.getMaker().rotateLeft();
    }

    @Override
    public void onMakerTurnRight() {
        model.getMaker().rotateRight();
    }

    @Override
    public void onMakerCreateTunnel() {

    }

    @Override
    public void onMakerCreateFactory() {

    }

    @Override
    public void onFactoryCreateTrain(String trainName) {
//        RailTrack track = model.getMap().getTrackAt(position);
//        Locomotive locomotive = new Locomotive();
//        locomotive.setForce(1000);
//        track.enterLinkerFromDir(model.getMaker().getDirection().inverse(), locomotive);
//        Train train = new Train();
//        train.pushBack(locomotive);
//        train.assignDefaultMainTractor();
//        model.addTrain(train);
    }

    @Override
    public void onFactoryCreateLocomotive() {

    }

    @Override
    public void onFactoryCreateWagon() {

    }

    @Override
    public void onSelectTrain(String trainName) {

    }

    @Override
    public void onIncTrainAcceleration() {
        model.getTrains().forEach(t -> t.getMainTractor().setForce(t.getMainTractor().getForce() + 10));
    }

    @Override
    public void onDecTrainAcceleration() {

    }

    @Override
    public void onSelectFork(String forkName) {

    }

    @Override
    public void onChangeForkDirection() {

    }
}
