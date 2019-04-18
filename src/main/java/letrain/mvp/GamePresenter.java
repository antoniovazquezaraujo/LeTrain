package letrain.mvp;

public interface GamePresenter extends GameViewListener {

    GameView getView();

    GameModel getModel();

}
