package letrain.mvp.impl.delegates;

import javafx.scene.input.KeyEvent;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.mvp.Model;
import letrain.mvp.View;
import letrain.mvp.impl.Presenter;

public class NavigationController extends PresenterDelegate {

    boolean reversed;
    private Dir dir;

    public NavigationController(Presenter presenter, Model model, View view) {
        super(presenter, model, view);
        this.dir = model.getCursor().getDir();
    }

    @Override
    public void onGameModeSelected(Model.GameMode mode) {
        if (mode.equals(Model.GameMode.NAVIGATE_MAP)) {
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
    public void onDown() {
        reversed= true;
        onUp();
        reversed = false;
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
    public void onChar(KeyEvent c) {

    }
}
