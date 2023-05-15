package letrain;

import letrain.mvp.impl.CompactPresenter;
import letrain.mvp.impl.Model;

public class LeTrain {

    private letrain.mvp.Model model = null;
    private CompactPresenter presenter;

    public static void main(String[] args) {
        new LeTrain().start(args);
    }

    public void start(String[] args) {
        try {
            model = new Model();
            model.loadModel("game.ltr");
        } catch (Exception e) {
            model = new Model();
            System.out.println("No game.ltr file found, creating a new one");
        }
        presenter = new CompactPresenter((Model) model);
        presenter.start();
        model.saveModel("game.ltr");
    }

}
