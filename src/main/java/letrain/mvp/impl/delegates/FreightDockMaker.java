package letrain.mvp.impl.delegates;

import letrain.mvp.GameModel;
import letrain.mvp.GameView;
import letrain.mvp.impl.LeTrainPresenter;

public class FreightDockMaker extends GamePresenterDelegate {
    public FreightDockMaker(LeTrainPresenter leTrainPresenter, GameModel model, GameView view) {
        super(leTrainPresenter, model, view);
    }
}
