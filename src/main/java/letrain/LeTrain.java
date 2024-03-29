package letrain;

import letrain.mvp.impl.CompactPresenter;

public class LeTrain {

    private letrain.mvp.Model model = null;
    private CompactPresenter presenter;

    public static void main(String[] args) {
        new LeTrain().start(args);
    }

    public void start(String[] args) {
        if (this.model == null) {
            this.model = new letrain.mvp.impl.Model();
        }
        presenter = new CompactPresenter((letrain.mvp.impl.Model) this.model);
        presenter.start();
        presenter.stop();
    }
}
