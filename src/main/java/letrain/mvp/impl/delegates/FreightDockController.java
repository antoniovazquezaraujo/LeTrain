package letrain.mvp.impl.delegates;

import letrain.mvp.GameModel;
import letrain.mvp.GameView;
import letrain.mvp.impl.LeTrainPresenter;

public class FreightDockController extends GamePresenterDelegate {
    public FreightDockController(LeTrainPresenter leTrainPresenter, GameModel model, GameView view) {
        super(leTrainPresenter, model, view);
    }
}
