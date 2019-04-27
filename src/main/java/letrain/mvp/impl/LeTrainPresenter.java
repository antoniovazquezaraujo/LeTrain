package letrain.mvp.impl;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.input.KeyEvent;
import javafx.util.Duration;
import letrain.map.Point;
import letrain.mvp.GameModel;
import letrain.mvp.GamePresenter;
import letrain.mvp.GameView;
import letrain.mvp.GameViewListener;
import letrain.mvp.impl.delegates.*;
import letrain.render.RenderingVisitor;

import java.util.HashMap;
import java.util.Map;

public class LeTrainPresenter implements GameViewListener, GamePresenter {

    private GameMode mode = GamePresenter.GameMode.NAVIGATE_MAP_COMMAND;
    private final GameModel model;
    private final GameView view;
    private Timeline loop;
    private final RenderingVisitor renderer;
    GamePresenterDelegate delegate;
    Map<GameMode, GamePresenterDelegate> delegates;

    public LeTrainPresenter() {
        this(null);
    }

    public LeTrainPresenter(LeTrainModel model) {
        if (model != null) {
            this.model = model;
        } else {
            this.model = new LeTrainModel();
        }
        view = new LeTrainView(this);
        renderer = new RenderingVisitor(view);
        delegates = new HashMap<>();
        delegates.put(GamePresenter.GameMode.NAVIGATE_MAP_COMMAND,             new NavigationController(  this, this.model, view));
        delegates.put(GamePresenter.GameMode.CREATE_FACTORY_PLATFORM_COMMAND,  new FactoryMaker(          this, this.model, view));
        delegates.put(GamePresenter.GameMode.CREATE_LOAD_PLATFORM_COMMAND,     new FreightDockMaker(      this, this.model, view));
        delegates.put(GamePresenter.GameMode.CREATE_NORMAL_TRACKS_COMMAND,     new TrackMaker(            this, this.model, view));
        delegates.put(GamePresenter.GameMode.REMOVE_TRACKS_COMMAND,            new TrackDestructor(       this, this.model, view));
        delegates.put(GamePresenter.GameMode.USE_FACTORY_PLATFORMS_COMMAND,    new FactoryController(     this, this.model, view));
        delegates.put(GamePresenter.GameMode.USE_FORKS_COMMAND,                new ForkController(        this, this.model, view));
        delegates.put(GamePresenter.GameMode.USE_TRAINS_COMMAND,               new TrainController(       this, this.model, view));
        delegates.put(GamePresenter.GameMode.USE_LOAD_PLATFORMS_COMMAND,       new FreightDockController( this, this.model, view));
        this.delegate = delegates.get(mode);
    }

    public void start() {
        loop = new Timeline();
        loop.setCycleCount(Timeline.INDEFINITE);

        KeyFrame kf = new KeyFrame(Duration.seconds(.1), actionEvent -> {
            view.clear();
            renderer.visitModel(model);
            view.paint();
            model.moveTrains();
        });
        loop.getKeyFrames().add(kf);
        loop.play();
    }


    @Override
    public GameMode getMode() {
        return mode;
    }

    @Override
    public void setMode(GameMode mode) {
        this.mode = mode;
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
    public void onGameModeSelected(GameMode mode) {
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
    public void onChar(KeyEvent keyEvent) {
        switch (keyEvent.getCode()) {
            case PAGE_UP:
                if (keyEvent.isControlDown()) {
                    view.clear();
                    Point p = view.getMapScrollPage();
                    p.setX(p.getX() - 1);
                    view.setMapScrollPage(p);
                    view.clear();
                }else{
                    view.clear();
                    Point p = view.getMapScrollPage();
                    p.setY(p.getY() - 1);
                    view.setMapScrollPage(p);
                    view.clear();
                }
                break;
            case PAGE_DOWN:
                if (keyEvent.isControlDown()) {
                    view.clear();
                    Point p = view.getMapScrollPage();
                    p.setX(p.getX() + 1);
                    view.setMapScrollPage(p);
                    view.clear();
                }else{
                    view.clear();
                    Point p = view.getMapScrollPage();
                    p.setY(p.getY() + 1);
                    view.setMapScrollPage(p);
                    view.clear();
                }
                break;
            case UP:
                GamePresenter.GameMode mode = getMode();
                if (keyEvent.isControlDown()) {
                    if(!mode.equals(GameMode.REMOVE_TRACKS_COMMAND)) {
                        setMode(GameMode.REMOVE_TRACKS_COMMAND);
                        onGameModeSelected(GameMode.REMOVE_TRACKS_COMMAND);
                    }
                }else if(keyEvent.isShiftDown()){
                    if(!mode.equals(GameMode.CREATE_NORMAL_TRACKS_COMMAND)) {
                        setMode(GameMode.CREATE_NORMAL_TRACKS_COMMAND);
                        onGameModeSelected(GameMode.CREATE_NORMAL_TRACKS_COMMAND);
                    }
                }else{
                    setMode(GameMode.NAVIGATE_MAP_COMMAND);
                    onGameModeSelected(GameMode.NAVIGATE_MAP_COMMAND);
                }
                delegate.onUp();
                break;
            case DOWN:
                    delegate.onDown();
                break;
            case LEFT:
                    delegate.onLeft();
                break;
            case RIGHT:
                    delegate.onRight();
                break;
            case Q:
                System.exit(0);
                break;
            default:
                delegate.onChar(keyEvent);
                break;
        }
    }

}
