package letrain.mvp.impl.delegates;

import letrain.map.Dir;
import letrain.map.Point;
import letrain.mvp.GameModel;
import letrain.mvp.GameView;
import letrain.mvp.impl.LeTrainPresenter;
import letrain.track.rail.RailTrack;

public class TrackDestructor extends GamePresenterDelegate {
    boolean reversed;
    private Dir dir;
    @Override
    public void onGameModeSelected(GameMode mode) {
        super.onGameModeSelected(mode);
        this.dir = model.getCursor().getDir();
        this.reversed = false;
    }

    @Override
    public void onUp() {
        Point position = model.getCursor().getPosition();
        RailTrack track = model.getRailMap().getTrackAt(position.getX(), position.getY());
        if(track != null){
            model.getRailMap().removeTrack(position.getX(), position.getY());
        }
        Point newPos = new Point(model.getCursor().getPosition());
        if (!reversed) {
            newPos.move(model.getCursor().getDir(), 1);
        } else {
            newPos.move(model.getCursor().getDir().inverse());
        }
        model.getCursor().setPosition(newPos);
        Point p= model.getCursor().getPosition();
        view.setPageOfPos(p.getX(), p.getY());
    }

    @Override
    public void onDown() {
        super.onDown();
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

    public TrackDestructor(LeTrainPresenter leTrainPresenter, GameModel model, GameView view) {
        super(leTrainPresenter, model, view);
    }
}
