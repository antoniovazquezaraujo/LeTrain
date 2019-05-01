package letrain.mvp.impl.delegates;

import letrain.mvp.Model;
import letrain.mvp.View;
import letrain.mvp.impl.Presenter;

public class FreightDockMaker extends PresenterDelegate {
    public FreightDockMaker(Presenter presenter, Model model, View view) {
        super(presenter, model, view);
    }
}
