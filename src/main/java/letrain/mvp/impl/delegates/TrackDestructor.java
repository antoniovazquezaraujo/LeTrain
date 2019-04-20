package letrain.mvp.impl.delegates;

import letrain.mvp.GameModel;
import letrain.mvp.GameView;

public class TrackDestructor extends GamePresenterDelegate {
    public TrackDestructor(GameModel model, GameView view) {
        super(model, view);
    }
}
