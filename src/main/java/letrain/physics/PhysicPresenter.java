package letrain.physics;

import letrain.mvp.GameViewListener;
import letrain.mvp.View;

public interface PhysicPresenter extends GameViewListener {
    View getView();
    PhysicModel getModel();
}
