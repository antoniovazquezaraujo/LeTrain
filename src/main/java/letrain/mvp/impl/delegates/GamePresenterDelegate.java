package letrain.mvp.impl.delegates;

import javafx.scene.input.KeyEvent;
import letrain.mvp.GameModel;
import letrain.mvp.GamePresenter;
import letrain.mvp.GameView;
import letrain.mvp.GameViewListener;

public  class GamePresenterDelegate implements letrain.mvp.GamePresenter, GameViewListener {
    GameModel model;
    GameView view;
    GamePresenter parent;
    public GamePresenterDelegate(GamePresenter presenter, GameModel model, GameView view) {
        this.parent = parent;
        this.model = model;
        this.view = view;
    }

    @Override
    public GameMode getMode() {
        return parent.getMode();
    }

    @Override
    public void setMode(GameMode mode) {
        parent.setMode(mode);
    }

    @Override
    public GameView getView() {
        return view;
    }

    @Override
    public GameModel getModel() {
        return model;
    }

    @Override
    public  void onGameModeSelected(GameMode mode){

    }

    @Override
    public  void onUp(){

    }

    @Override
    public  void onDown(){

    }

    @Override
    public  void onLeft(){

    }

    @Override
    public  void onRight(){

    }

    @Override
    public  void onChar(KeyEvent c){

    }

}
