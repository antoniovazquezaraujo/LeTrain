package letrain.mvp.impl.delegates;

import letrain.map.Dir;
import letrain.map.Point;
import letrain.mvp.GameModel;
import letrain.mvp.GameView;
import letrain.mvp.impl.delegates.GamePresenterDelegate;

public class NavigationDelegate extends GamePresenterDelegate {

    boolean reversed;
    private Dir dir;
    private int degreesOfRotation= 0;
    public NavigationDelegate(GameModel model, GameView view) {
        super(model,view);
        this.dir = Dir.N;
    }

    @Override
    public void onGameModeSelected(GameView.GameMode mode) {

    }

    @Override
    public void onUp() {
        degreesOfRotation=0;
        Point newPos = new Point(getModel().getCursor().getPosition());
        if(!reversed) {
            newPos.move(getModel().getCursor().getDir(), 1);
        }else{
            newPos.move(getModel().getCursor().getDir().inverse());
        }
        getModel().getCursor().setPosition(newPos);
        Point position = model.getCursor().getPosition();
        view.setPageOfPos(position.getX(), position.getY());
    }

    @Override
    public void onLeft() {
        if(degreesOfRotation <= 0 ) {
            this.dir = this.dir.turnLeft();
            getModel().getCursor().setDir(this.dir);
            degreesOfRotation +=1;
        }
    }

    @Override
    public void onRight() {
        if(degreesOfRotation>=0) {
            this.dir = this.dir.turnRight();
            getModel().getCursor().setDir(this.dir);
            degreesOfRotation-=1;
        }
    }



    @Override
    public void onChar(char c) {

    }
}
