package letrain.mvp.impl.delegates;

import letrain.map.Dir;
import letrain.mvp.GameModel;
import letrain.mvp.GameView;
import letrain.track.rail.RailTrack;
import letrain.vehicle.impl.rail.Locomotive;
import letrain.vehicle.impl.rail.Train;
import letrain.vehicle.impl.rail.Wagon;

public class FactoryController extends GamePresenterDelegate {
    TrainFactory factory;
    Train train;
    public FactoryController(GameModel model, GameView view) {
        super(model, view);
    }

    @Override
    public void onGameModeSelected(GameView.GameMode mode) {
        if(mode.equals(GameView.GameMode.USE_FACTORY_PLATFORMS_COMMAND)){
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
    public void onChar(String c) {
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
