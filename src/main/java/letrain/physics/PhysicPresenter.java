package letrain.physics;

import letrain.mvp.GameViewListener;
import letrain.mvp.View;
import letrain.physics.PhysicModel;

public interface PhysicPresenter extends GameViewListener {


    View getView();

    PhysicModel getModel();
}
