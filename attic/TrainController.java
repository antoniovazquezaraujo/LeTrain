package letrain.mvp.impl.delegates;

import javafx.scene.input.KeyEvent;
import letrain.mvp.Model;
import letrain.mvp.View;
import letrain.mvp.impl.Presenter;
import letrain.vehicle.impl.Tractor;
import letrain.vehicle.impl.rail.Train;

public class TrainController extends PresenterDelegate {
    Train train;
    int trainIndex = -1;
    public TrainController(Presenter presenter, Model model, View view) {
        super(presenter, model, view);
    }

    @Override
    public void onGameModeSelected(Model.GameMode mode) {
        if(mode.equals(Model.GameMode.USE_TRAINS)){
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
