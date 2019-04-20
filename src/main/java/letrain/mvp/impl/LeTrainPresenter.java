package letrain.mvp.impl;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;
import letrain.mvp.GameModel;
import letrain.mvp.GamePresenter;
import letrain.mvp.GameView;
import letrain.mvp.GameViewListener;
import letrain.mvp.impl.delegates.*;
import letrain.render.UnicodeRenderer;

import java.util.HashMap;
import java.util.Map;

public class LeTrainPresenter implements GameViewListener, GamePresenter {

    private final GameModel model;
    private final GameView view;
    private Timeline loop;
    private final UnicodeRenderer renderer;
    GamePresenterDelegate delegate;
    Map<GameView.GameMode, GamePresenterDelegate> delegates;
    public LeTrainPresenter() {
        model = new LeTrainModel();
        view = new LeTrainView(this);
        renderer = new UnicodeRenderer(view);
        delegates = new HashMap<>();
        delegates.put(GameView.GameMode.NAVIGATE_MAP_COMMAND, new NavigationController(model, view));
        delegates.put(GameView.GameMode.CREATE_FACTORY_PLATFORM_COMMAND, new FactoryMaker(model, view));
        delegates.put(GameView.GameMode.CREATE_LOAD_PLATFORM_COMMAND, new FreightDockMaker(model, view));
        delegates.put(GameView.GameMode.CREATE_NORMAL_TRACKS_COMMAND, new TrackMaker(model, view));
        delegates.put(GameView.GameMode.REMOVE_TRACKS_COMMAND, new TrackDestructor(model, view));
        delegates.put(GameView.GameMode.USE_FACTORY_PLATFORMS_COMMAND, new FactoryController(model, view));
        delegates.put(GameView.GameMode.USE_FORKS_COMMAND, new ForkController(model, view));
        delegates.put(GameView.GameMode.USE_TRAINS_COMMAND, new TrainController(model, view));
        delegates.put(GameView.GameMode.USE_LOAD_PLATFORMS_COMMAND, new FreightDockController(model, view));
        this.delegate= delegates.get(GameView.GameMode.NAVIGATE_MAP_COMMAND);
    }

    public void start() {
        loop = new Timeline();
        loop.setCycleCount(Timeline.INDEFINITE);

        KeyFrame kf = new KeyFrame(Duration.seconds(.1), actionEvent -> {
            view.clear();
            renderer.renderModel(model);
            view.paint();
            model.moveTrains();
        });
        loop.getKeyFrames().add(kf);
        loop.play();
    }


    /***********************************************************
     * GamePresenter implementation
     **********************************************************/
    @Override
    public GameView getView() {
        return view;
    }

    @Override
    public GameModel getModel() {
        return model;
    }


    /***********************************************************
     * GameViewListener implementation
     *********************************************************
     * @param mode*/
    @Override
    public void onGameModeSelected(GameView.GameMode mode) {
        //Avisamos al anterior y al nuevo
        delegate.onGameModeSelected(mode);
        delegate = delegates.get(mode);
        delegate.onGameModeSelected(mode);
    }

    @Override
    public void onUp() {
        delegate.onUp();
    }

    @Override
    public void onDown() {
        delegate.onDown();
    }

    @Override
    public void onLeft() {
        delegate.onLeft();
    }

    @Override
    public void onRight() {
        delegate.onRight();
    }

    @Override
    public void onChar(String c) {
        delegate.onChar(c);
    }


//    @Override
//    public void onMakerCreateTunnelTrack() {
//        if (model.getMaker().getMode().equals(GameModel.Mode.MAKE_TRACKS)) {
//            model.getMaker().selectNewTrackType(TUNNEL_GATE);
//            model.getMaker().advanceCursor();
//            model.getMaker().setMode(GameModel.Mode.MAP_WALK);
//            model.getMaker().selectNewTrackType(NORMAL_TRACK);
//        }
//    }
//
//    @Override
//    public void onMakerCreateStopTrack() {
//        model.getMaker().selectNewTrackType(STOP_TRACK);
//        onUp();
//        model.getMaker().selectNewTrackType(NORMAL_TRACK);
//        onGameModeSelected(GameModel.Mode.MAP_WALK);
//    }
//
//    @Override
//    public void onMakerCreateFactoryGateTrack() {
//        model.getMaker().selectNewTrackType(TRAIN_FACTORY_GATE);
//        onUp();
//        model.getMaker().selectNewTrackType(NORMAL_TRACK);
//        onGameModeSelected(GameModel.Mode.MAP_WALK);
//    }
//
//    @Override
//    public void onFactoryCreateTrain(String trainName) {
//        Train train = new Train();
//        trainName.codePoints()
//                .mapToObj(c -> {
//                    return String.valueOf((char) c);
//                }).forEach(t -> {
//            Wagon w = new Wagon(t);
//            train.pushBack(w);
//        });
//        train.pushFront(new Locomotive());
//        model.addTrain(train);
//    }
//
//    @Override
//    public void onFactoryCreateLocomotive() {
//
//    }
//
//    @Override
//    public void onFactoryCreateWagon() {
//
//    }
//
//    @Override
//    public void onSelectTrain(String trainName) {
//
//    }
//
//    @Override
//    public void onIncTrainAcceleration() {
//        model.getTrains().forEach(t -> t.getDirectorLinker().setForce(t.getDirectorLinker().getForce() + 10));
//    }
//
//    @Override
//    public void onDecTrainAcceleration() {
//
//    }
//
//    @Override
//    public void onSelectFork(String forkName) {
//
//    }
//
//    @Override
//    public void onChangeForkDirection() {
//
//    }
}
