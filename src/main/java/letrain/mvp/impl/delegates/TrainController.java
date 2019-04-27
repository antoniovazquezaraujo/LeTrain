package letrain.mvp.impl.delegates;

import javafx.scene.input.KeyEvent;
import letrain.mvp.GameModel;
import letrain.mvp.GamePresenter;
import letrain.mvp.GameView;
import letrain.mvp.impl.LeTrainPresenter;
import letrain.vehicle.impl.Tractor;
import letrain.vehicle.impl.rail.Train;

public class TrainController extends GamePresenterDelegate {
    Train train;
    int trainIndex = -1;
    public TrainController(LeTrainPresenter leTrainPresenter, GameModel model, GameView view) {
        super(leTrainPresenter, model, view);
    }

    @Override
    public void onGameModeSelected(GameMode mode) {
        if(mode.equals(GamePresenter.GameMode.USE_TRAINS_COMMAND)){
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
    public void onChar(KeyEvent c) {
        super.onChar(c);
    }
}
