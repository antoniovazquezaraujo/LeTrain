package letrain.mvp.impl.delegates;

import javafx.scene.input.KeyEvent;
import letrain.mvp.Model;
import letrain.mvp.Presenter;
import letrain.mvp.View;
import letrain.mvp.GameViewListener;

public  class PresenterDelegate implements Presenter, GameViewListener {
    Model model;
    View view;
    Presenter parent;
    public PresenterDelegate(Presenter presenter, Model model, View view) {
        this.parent = parent;
        this.model = model;
        this.view = view;
    }

    @Override
    public View getView() {
        return view;
    }

    @Override
    public Model getModel() {
        return model;
    }

    @Override
    public  void onGameModeSelected(Model.GameMode mode){

    }

//    @Override
//    public  void onUp(){
//
//    }
//
//    @Override
//    public  void onDown(){
//
//    }
//
//    @Override
//    public  void onLeft(){
//
//    }
//
//    @Override
//    public  void onRight(){
//
//    }

    @Override
    public  void onChar(KeyEvent c){

    }

}
