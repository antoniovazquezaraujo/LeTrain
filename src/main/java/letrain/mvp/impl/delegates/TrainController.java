package letrain.mvp.impl.delegates;

import letrain.mvp.GameModel;
import letrain.mvp.GameView;
import letrain.vehicle.impl.Tractor;
import letrain.vehicle.impl.rail.Train;

public class TrainController extends GamePresenterDelegate {
    Train train;
    int trainIndex = -1;
    public TrainController(GameModel model, GameView view) {
        super(model, view);
    }

    @Override
    public void onGameModeSelected(GameView.GameMode mode) {
        if(mode.equals(GameView.GameMode.USE_TRAINS_COMMAND)){
            trainIndex=0;
            train = model.getTrains().get(trainIndex);
        }
    }

    @Override
    public void onUp() {
        ((Tractor)train.getDirectorLinker()).incForce(1000);
    }

    @Override
    public void onDown() {
        ((Tractor)train.getDirectorLinker()).decForce(1000);
    }

    @Override
    public void onLeft() {
        trainIndex--;
        if(trainIndex <0 ){
            trainIndex = model.getTrains().size()-1;
        }
        train = model.getTrains().get(trainIndex);
    }

    @Override
    public void onRight() {
        trainIndex++;
        if(trainIndex >=model.getTrains().size() ){
            trainIndex = 0;
        }
        train = model.getTrains().get(trainIndex);
    }

    @Override
    public void onChar(String c) {
        super.onChar(c);
    }
}
