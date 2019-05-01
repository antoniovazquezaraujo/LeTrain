package letrain.mvp.impl.delegates;

import letrain.mvp.Model;
import letrain.mvp.View;
import letrain.mvp.impl.Presenter;

public class FreightDockController extends PresenterDelegate {
    public FreightDockController(Presenter presenter, Model model, View view) {
        super(presenter, model, view);
    }
}
