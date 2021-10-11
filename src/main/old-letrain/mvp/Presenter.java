package letrain.mvp;

public interface Presenter extends GameViewListener {


    View getView();

    Model getModel();
}
