package letrain.mvp.impl;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.input.KeyEvent;
import javafx.util.Duration;
import letrain.map.Point;
import letrain.mvp.GameViewListener;
import letrain.render.RenderingVisitor;

import java.util.HashMap;
import java.util.Map;

public class Presenter implements GameViewListener, letrain.mvp.Presenter {

    private final letrain.mvp.Model model;
    private final letrain.mvp.View view;
    private Timeline loop;
    private final RenderingVisitor renderer;
    PresenterDelegate delegate;
    Map<letrain.mvp.Model.GameMode, PresenterDelegate> delegates;

    public Presenter() {
        this(null);
    }

    public Presenter(Model model) {
        if (model != null) {
            this.model = model;
        } else {
            this.model = new Model();
        }
        view = new View(this);
        renderer = new RenderingVisitor(view);
        delegates = new HashMap<>();
        delegates.put(letrain.mvp.Model.GameMode.NAVIGATE_MAP,             new NavigationController(  this, this.model, view));
        delegates.put(letrain.mvp.Model.GameMode.CREATE_FACTORY_PLATFORM,  new FactoryMaker(          this, this.model, view));
        delegates.put(letrain.mvp.Model.GameMode.CREATE_LOAD_PLATFORM,     new FreightDockMaker(      this, this.model, view));
        delegates.put(letrain.mvp.Model.GameMode.CREATE_TRACKS,     new TrackMaker(            this, this.model, view));
        delegates.put(letrain.mvp.Model.GameMode.REMOVE_TRACKS,            new TrackDestructor(       this, this.model, view));
        delegates.put(letrain.mvp.Model.GameMode.USE_FACTORY_PLATFORMS,    new FactoryController(     this, this.model, view));
        delegates.put(letrain.mvp.Model.GameMode.USE_FORKS,                new ForkController(        this, this.model, view));
        delegates.put(letrain.mvp.Model.GameMode.USE_TRAINS,               new TrainController(       this, this.model, view));
        delegates.put(letrain.mvp.Model.GameMode.USE_LOAD_PLATFORMS,       new FreightDockController( this, this.model, view));
        this.delegate = delegates.get(this.model.getMode());
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
                letrain.mvp.Model.GameMode mode = model.getMode();
                if (keyEvent.isControlDown()) {
                    if(!mode.equals(letrain.mvp.Model.GameMode.REMOVE_TRACKS)) {
                        model.setMode(letrain.mvp.Model.GameMode.REMOVE_TRACKS);
                        onGameModeSelected(letrain.mvp.Model.GameMode.REMOVE_TRACKS);
                    }
                }else if(keyEvent.isShiftDown()){
                    if(!mode.equals(letrain.mvp.Model.GameMode.CREATE_TRACKS)) {
                        model.setMode(letrain.mvp.Model.GameMode.CREATE_TRACKS);
                        onGameModeSelected(letrain.mvp.Model.GameMode.CREATE_TRACKS);
                    }
                }else{
                    model.setMode(letrain.mvp.Model.GameMode.NAVIGATE_MAP);
                    onGameModeSelected(letrain.mvp.Model.GameMode.NAVIGATE_MAP);
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
