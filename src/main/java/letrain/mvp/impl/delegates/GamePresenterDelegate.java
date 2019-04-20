package letrain.mvp.impl.delegates;

import letrain.mvp.GameModel;
import letrain.mvp.GameView;
import letrain.mvp.GameViewListener;

public  class GamePresenterDelegate implements letrain.mvp.GamePresenter, GameViewListener {
    GameModel model;
    GameView view;

    public GamePresenterDelegate(GameModel model, GameView view) {
        this.model = model;
        this.view = view;
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
    public  void onGameModeSelected(GameView.GameMode mode){

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
    public  void onChar(String c){

    }
}
