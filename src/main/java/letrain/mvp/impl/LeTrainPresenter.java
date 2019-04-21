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
        this(null);
    }

    public LeTrainPresenter(LeTrainModel model) {
        if(model != null){
            this.model = model;
        }else{
            this.model = new LeTrainModel();
        }
        view = new LeTrainView(this);
        renderer = new UnicodeRenderer(view);
        delegates = new HashMap<>();
        delegates.put(GameView.GameMode.NAVIGATE_MAP_COMMAND, new NavigationController(this.model, view));
        delegates.put(GameView.GameMode.CREATE_FACTORY_PLATFORM_COMMAND, new FactoryMaker(this.model, view));
        delegates.put(GameView.GameMode.CREATE_LOAD_PLATFORM_COMMAND, new FreightDockMaker(this.model, view));
        delegates.put(GameView.GameMode.CREATE_NORMAL_TRACKS_COMMAND, new TrackMaker(this.model, view));
        delegates.put(GameView.GameMode.REMOVE_TRACKS_COMMAND, new TrackDestructor(this.model, view));
        delegates.put(GameView.GameMode.USE_FACTORY_PLATFORMS_COMMAND, new FactoryController(this.model, view));
        delegates.put(GameView.GameMode.USE_FORKS_COMMAND, new ForkController(this.model, view));
        delegates.put(GameView.GameMode.USE_TRAINS_COMMAND, new TrainController(this.model, view));
        delegates.put(GameView.GameMode.USE_LOAD_PLATFORMS_COMMAND, new FreightDockController(this.model, view));
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

}
