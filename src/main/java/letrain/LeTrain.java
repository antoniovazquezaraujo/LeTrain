package letrain;

import java.io.IOException;

import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;

import letrain.mvp.impl.CompactPresenter;
import letrain.mvp.Model;

public class LeTrain {

    private letrain.mvp.Model model = null;
    private CompactPresenter presenter;

    public static void main(String[] args) {
        new LeTrain().start(args);
    }

    public void start(String[] args) {
        this.model = LeTrainSensorProgramVisitor.loadModel("game.ltr");
        if (this.model == null) {
            this.model = new letrain.mvp.impl.Model();
        }
        presenter = new CompactPresenter((letrain.mvp.Model) this.model);
        presenter.start();
        LeTrainSensorProgramVisitor.saveModel(this.model, "game.ltr");
    }

}
