package letrain.gui;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import letrain.model.GameModel;
import letrain.model.LeTrainModel;
import letrain.tui.BasicGraphicConverter;
import letrain.tui.GraphicConverter;
import letrain.vehicle.impl.rail.Locomotive;
import letrain.vehicle.impl.rail.Train;
import letrain.vehicle.impl.rail.Wagon;
import letrain.view.UnicodeRenderer;

import static letrain.trackmaker.TrackMaker.NewTrackType.TRAIN_FACTORY_GATE;

public class LeTrainPresenter implements GamePresenter {
    private Timeline loop;
    private final GameModel model;
    private final LeTrainView view;
    GraphicConverter converter = new BasicGraphicConverter();
    private final UnicodeRenderer renderer;

    public LeTrainPresenter() {
        model = new LeTrainModel();
        view = new LeTrainView(this);
        renderer = new UnicodeRenderer(view);
    }

    public void start() {
        loop = new Timeline();
        loop.setCycleCount(Timeline.INDEFINITE);

        KeyFrame kf = new KeyFrame(Duration.seconds(.1), actionEvent -> paintLoop());
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
        view.setPageOfPos(model.getMaker().getPosition().getX(), model.getMaker().getPosition().getY());
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
    public void onMakerCreateFactoryGateTrack() {
        model.getMaker().selectNewTrackType(TRAIN_FACTORY_GATE);
        onMakerAdvance();
        onGameModeSelected(GameModel.Mode.MAP_WALK);
    }

    @Override
    public void onFactoryCreateTrain(String trainName) {
        Train train = new Train();
        trainName.codePoints()
                .mapToObj(c -> {
                    return String.valueOf((char) c);
                }).forEach(t -> {
            Wagon w = new Wagon(t);
            train.pushBack(w);
        });
        train.pushFront(new Locomotive());
        model.addTrain(train);
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
