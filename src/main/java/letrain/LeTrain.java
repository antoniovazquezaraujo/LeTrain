package letrain;

import letrain.map.RailMapFactory;
import letrain.mvp.impl.CompactPresenter;
import letrain.mvp.impl.Model;
import letrain.vehicle.impl.rail.TrainFactory;

public class LeTrain {

    private letrain.mvp.Model model= null;
    private CompactPresenter presenter;

    public static void main(String[] args) {
        new LeTrain().start(args);
    }

    public void start(String[] args) {
        model = new Model();
        RailMapFactory railMapFactory = new RailMapFactory(model);
        railMapFactory.read("30,20 e30 r1 r1 r1 r35 r1 r1 r1 r5 l5 r5 r5 l3");
        TrainFactory trainFactory = new TrainFactory(model);
        trainFactory.read("50,20 w #Letrain");
        presenter = new CompactPresenter((Model) model);
        presenter.start();
    }

}
