package letrain.mvp.impl.delegates;

import letrain.mvp.GameModel;
import letrain.mvp.GameView;

public class ForkController extends GamePresenterDelegate {
    public ForkController(GameModel model, GameView view) {
        super(model, view);
    }
}
