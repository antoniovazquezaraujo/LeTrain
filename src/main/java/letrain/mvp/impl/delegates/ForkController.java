package letrain.mvp.impl.delegates;

import letrain.mvp.GameModel;
import letrain.mvp.GameView;
import letrain.mvp.impl.LeTrainPresenter;
import letrain.track.rail.ForkRailTrack;

public class ForkController extends GamePresenterDelegate {
    ForkRailTrack selectedFork = null;
    int selectedForkIndex = -1;
    public ForkController(LeTrainPresenter leTrainPresenter, GameModel model, GameView view) {
        super(leTrainPresenter, model, view);
        selectedForkIndex=0;
        if(!model.getForks().isEmpty()) {
            selectedFork = model.getForks().get(selectedForkIndex);
        }
    }

    @Override
    public void onUp() {
        if(selectedFork!= null){
            selectedFork.flipRoute();
        }
    }

    @Override
    public void onDown() {
        if(selectedFork!= null){
            selectedFork.flipRoute();
        }
    }

    @Override
    public void onLeft() {
        selectedForkIndex++;
        if(selectedForkIndex >=model.getTrains().size() ){
            selectedForkIndex = 0;
        }
        selectedFork= model.getForks().get(selectedForkIndex);
    }

    @Override
    public void onRight() {
        selectedForkIndex--;
        if(selectedForkIndex <0 ){
            selectedForkIndex = model.getForks().size()-1;
        }
        selectedFork= model.getForks().get(selectedForkIndex);
    }
}
