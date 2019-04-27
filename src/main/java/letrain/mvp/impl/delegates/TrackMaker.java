package letrain.mvp.impl.delegates;

import javafx.scene.input.KeyEvent;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.map.Router;
import letrain.map.SimpleRouter;
import letrain.mvp.GameModel;
import letrain.mvp.GamePresenter;
import letrain.mvp.GameView;
import letrain.mvp.impl.LeTrainPresenter;
import letrain.track.rail.ForkRailTrack;
import letrain.track.Track;
import letrain.track.rail.RailTrack;
import letrain.track.rail.StopRailTrack;
import letrain.track.rail.TrainFactoryRailTrack;
import letrain.track.rail.TunnelRailTrack;

public class TrackMaker extends GamePresenterDelegate {
    enum NewTrackType {
        NORMAL_TRACK,
        STOP_TRACK,
        TRAIN_FACTORY_GATE,
        TUNNEL_GATE
    }

    NewTrackType newTrackType;
    Track oldTrack;
    Dir oldDir;
    boolean reversed;
    private int degreesOfRotation = 0;
    private Dir dir;

    public TrackMaker(LeTrainPresenter leTrainPresenter, GameModel model, GameView view) {
        super(leTrainPresenter, model, view);
    }
    @Override
    public void onGameModeSelected(GameMode mode) {
        if(mode.equals(GamePresenter.GameMode.CREATE_NORMAL_TRACKS_COMMAND)){
            Dir cursorInverseDir = model.getCursor().getDir().inverse();
            Point lastPosition = new Point(model.getCursor().getPosition());
            lastPosition.move(cursorInverseDir);
            oldTrack = model.getRailMap().getTrackAt(lastPosition);
            this.newTrackType = NewTrackType.NORMAL_TRACK;
            this.dir = model.getCursor().getDir();
            this.oldDir = dir;
            reversed=false;
        }
    }

    @Override
    public void onUp() {
        degreesOfRotation = 0;
        makeTrack();
        Point position = model.getCursor().getPosition();
        view.setPageOfPos(position.getX(), position.getY());
    }

    @Override
    public void onDown() {
        super.onDown();
    }

    @Override
    public void onLeft() {
        if (degreesOfRotation <= 0) {
            this.dir = this.dir.turnLeft();
            model.getCursor().setDir(this.dir);
            degreesOfRotation += 1;
        }
    }

    @Override
    public void onRight() {
        if (degreesOfRotation >= 0) {
            this.dir = this.dir.turnRight();
            model.getCursor().setDir(this.dir);
            degreesOfRotation -= 1;
        }
    }

    @Override
    public void onChar(KeyEvent c) {
        super.onChar(c);
    }

    public void selectNewTrackType(NewTrackType type) {
        this.newTrackType = type;
    }

    public NewTrackType getNewTrackType() {
        return this.newTrackType;
    }

    public RailTrack createTrackOfSelectedType() {
        switch (newTrackType) {
            case STOP_TRACK:
                return new StopRailTrack();
            case TRAIN_FACTORY_GATE:
                return new TrainFactoryRailTrack();
            case TUNNEL_GATE:
                return new TunnelRailTrack();
            default:
                return new RailTrack();
        }
    }

    private boolean makeTrack() {
        Point cursorPosition = model.getCursor().getPosition();
        Dir dir = model.getCursor().getDir();
        if (oldTrack != null) {
            oldDir = cursorPosition.locate(oldTrack.getPosition());
        } else {
            if (!reversed) {
                oldDir = model.getCursor().getDir().inverse();
            } else {
                oldDir = model.getCursor().getDir();
            }
        }

        RailTrack track = model.getRailMap().getTrackAt(cursorPosition.getX(), cursorPosition.getY());
        if (track == null) {
            track = createTrackOfSelectedType();
        }
        track.addRoute(oldDir, dir);
        if (oldTrack != null) {
            track.connect(oldDir, oldTrack);
        }
        track.setPosition(cursorPosition);
        getModel().getRailMap().addTrack(cursorPosition, track);
        if (canBeAFork(track, oldDir, dir)) {
            final ForkRailTrack myNewTrack = new ForkRailTrack();
            model.addFork(myNewTrack);
            final Router router = track.getRouter();
            router.forEach(t -> myNewTrack.getRouter().addRoute(t.getKey(), t.getValue()));
            getModel().getRailMap().removeTrack(track.getPosition().getX(), track.getPosition().getY());
            getModel().getRailMap().addTrack(model.getCursor().getPosition(), myNewTrack);
        }

        Point newPos = new Point(cursorPosition);
        if (!reversed) {
            newPos.move(model.getCursor().getDir(), 1);
        } else {
            newPos.move(model.getCursor().getDir().inverse());
        }
        model.getCursor().setPosition(newPos);
        oldTrack = track;
        return true;
    }

    public boolean canBeAFork(Track track, Dir from, Dir to) {
        final Router r = new SimpleRouter();
        track.getRouter().forEach(t -> r.addRoute(t.getKey(), t.getValue()));
        r.addRoute(from, to);
        return r.getNumRoutes() == 3;
    }
}
