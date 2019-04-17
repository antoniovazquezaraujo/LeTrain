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

public class LeTrainPresenter extends Application implements GamePresenter {
    Timeline loop;
    private GameModel model;
    private LeTrainView view;
    GraphicConverter converter = new BasicGraphicConverter();

    public static void main(String[] args) {
        launch(args);
    }

    public LeTrainPresenter() {
        model = new LeTrainModel();
        view = new LeTrainView(this);
    }

    @Override
    public void start(Stage stage) throws Exception {
        GridPane pane = new GridPane();
        pane.getChildren().add(view);
        final Scene scene = new Scene(pane);
        stage.setScene(scene);
        stage.show();

        loop = new Timeline();
        loop.setCycleCount(Timeline.INDEFINITE);

        KeyFrame kf = new KeyFrame(Duration.seconds(.1), actionEvent -> {
            paintLoop();
        });
        loop.getKeyFrames().add(kf);
        loop.play();
    }

    public void paintLoop() {
        view.clear();
        model.getMap().forEach(t -> {
            view.set(
                    t.getPosition().getX(),
                    t.getPosition().getY(),
                    converter.getTrackAspect(t));
        });
        model.getTrains().forEach(train -> {
            train.getLinkers().forEach(linker -> {
                view.set(
                        linker.getPosition().getX(),
                        linker.getPosition().getY(), converter.getLinkerAspect(linker));
            });
        });
        view.set(model.getMaker().getPosition().getX(), model.getMaker().getPosition().getY(), converter.getRailTrackMakerAspect(model.getMaker()));
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
