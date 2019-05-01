package letrain.mvp.impl.delegates;

import javafx.scene.input.KeyEvent;
import letrain.map.Dir;
import letrain.mvp.Model;
import letrain.mvp.View;
import letrain.mvp.impl.Presenter;
import letrain.track.rail.RailTrack;
import letrain.vehicle.impl.rail.Locomotive;
import letrain.vehicle.impl.rail.Train;
import letrain.vehicle.impl.rail.Wagon;

public class FactoryController extends PresenterDelegate {
    TrainFactory factory;
    Train train;
    public FactoryController(Presenter presenter, Model model, View view) {
        super(presenter, model, view);
    }

    @Override
    public void onGameModeSelected(Model.GameMode mode) {
        if(mode.equals(Model.GameMode.USE_FACTORY_PLATFORMS)){
            factory = model.getTrainFactories().get(0);
            model.getCursor().setPosition(factory.getPosition());
            model.getCursor().setDir(Dir.N);
            train = new Train();
            model.addTrain(train);
        }
    }

    @Override
    public void onUp() {
        super.onUp();
    }

    @Override
    public void onDown() {
        super.onDown();
    }

    @Override
    public void onLeft() {
        super.onLeft();
    }

    @Override
    public void onRight() {
        super.onRight();
    }

    @Override
    public void onChar(KeyEvent keyEvent) {
        String c = keyEvent.getCharacter();
        if(!c.matches("([A-Za-z])*")){
            return;
        }
        RailTrack track = model.getRailMap().getTrackAt(model.getCursor().getPosition());
        if(c.toUpperCase().equals(c)){
            Locomotive locomotive = new Locomotive(c);
            train.pushBack(locomotive);
            track.enterLinkerFromDir(Dir.E, locomotive);
            if(train.getDirectorLinker()== null){
                train.assignDefaultDirectorLinker();
            }
        } else {
            Wagon wagon = new Wagon(c);
            train.pushBack(wagon);
            track.enterLinkerFromDir(Dir.E, wagon);
        }
        model.getCursor().getPosition().move(Dir.E);
    }
}
