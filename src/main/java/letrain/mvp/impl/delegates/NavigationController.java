package letrain.mvp.impl.delegates;

import letrain.map.Dir;
import letrain.map.Point;
import letrain.mvp.GameModel;
import letrain.mvp.GameView;

public class NavigationController extends GamePresenterDelegate {

    boolean reversed;
    private Dir dir;

    public NavigationController(GameModel model, GameView view) {
        super(model, view);
        this.dir = model.getCursor().getDir();
    }

    @Override
    public void onGameModeSelected(GameView.GameMode mode) {
        if (mode.equals(GameView.GameMode.NAVIGATE_MAP_COMMAND)) {
            this.dir = model.getCursor().getDir();
            reversed = false;
        }
    }

    @Override
    public void onUp() {
        Point newPos = new Point(model.getCursor().getPosition());
        if (!reversed) {
            newPos.move(model.getCursor().getDir(), 1);
        } else {
            newPos.move(model.getCursor().getDir().inverse());
        }
        model.getCursor().setPosition(newPos);
        Point position = model.getCursor().getPosition();
        view.setPageOfPos(position.getX(), position.getY());
    }

    @Override
    public void onLeft() {
        this.dir = this.dir.turnLeft();
        model.getCursor().setDir(this.dir);
    }

    @Override
    public void onRight() {
        this.dir = this.dir.turnRight();
        model.getCursor().setDir(this.dir);
    }

    @Override
    public void onChar(String c) {

    }
}
