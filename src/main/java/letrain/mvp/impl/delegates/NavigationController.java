package letrain.mvp.impl.delegates;

import letrain.map.Dir;
import letrain.map.Point;
import letrain.mvp.GameModel;
import letrain.mvp.GameView;

public class NavigationController extends GamePresenterDelegate {

    boolean reversed;
    private Dir dir;
    private int degreesOfRotation= 0;
    public NavigationController(GameModel model, GameView view) {
        super(model,view);
        this.dir = Dir.N;
    }

    @Override
    public void onGameModeSelected(GameView.GameMode mode) {

    }

    @Override
    public void onUp() {
        degreesOfRotation=0;
        Point newPos = new Point(model.getCursor().getPosition());
        if(!reversed) {
            newPos.move(model.getCursor().getDir(), 1);
        }else{
            newPos.move(model.getCursor().getDir().inverse());
        }
        model.getCursor().setPosition(newPos);
        Point position = model.getCursor().getPosition();
        view.setPageOfPos(position.getX(), position.getY());
    }

    @Override
    public void onLeft() {
        if(degreesOfRotation <= 0 ) {
            this.dir = this.dir.turnLeft();
            model.getCursor().setDir(this.dir);
            degreesOfRotation +=1;
        }
    }

    @Override
    public void onRight() {
        if(degreesOfRotation>=0) {
            this.dir = this.dir.turnRight();
            model.getCursor().setDir(this.dir);
            degreesOfRotation-=1;
        }
    }



    @Override
    public void onChar(String c) {

    }
}
