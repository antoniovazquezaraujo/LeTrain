package letrain.mvp.impl;

import javafx.scene.input.KeyEvent;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.map.Router;
import letrain.map.SimpleRouter;
import letrain.track.Track;
import letrain.track.rail.*;
import letrain.vehicle.impl.Cursor;

import static letrain.mvp.impl.CompactPresenter.TrackType.*;

public class RailTrackMaker {
    private final Model model;
    private final letrain.mvp.View view;
    private CompactPresenter.TrackType newTrackType = CompactPresenter.TrackType.NORMAL_TRACK;

    public RailTrackMaker(Model model, letrain.mvp.View view) {
        this.model = model;
        this.view = view;
    }

    public void onChar(KeyEvent keyEvent) {
        switch(keyEvent.getCode()){
            case T:
                selectNewTrackType(TUNNEL_GATE);
                createTrack();
                selectNewTrackType(CompactPresenter.TrackType.NORMAL_TRACK);
                makingTraks = false;
                break;
            case A:
                selectNewTrackType(TRAIN_FACTORY_GATE);
                createTrack();
                selectNewTrackType(CompactPresenter.TrackType.NORMAL_TRACK);
                makingTraks = false;
                break;
            case S:
                selectNewTrackType(STOP_TRACK);
                createTrack();
                selectNewTrackType(CompactPresenter.TrackType.NORMAL_TRACK);
                makingTraks = false;
                break;
            case UP:
                if (keyEvent.isShiftDown()) {
                    model.getCursor().setMode(Cursor.CursorMode.DRAWING);
                    createTrack();
                    makingTraks = true;
                } else if (keyEvent.isControlDown()) {
                    model.getCursor().setMode(Cursor.CursorMode.ERASING);
                    removeTrack();
                    makingTraks = false;
                } else {
                    model.getCursor().setMode(Cursor.CursorMode.MOVING);
                    cursorForward();
                    makingTraks = false;
                }
                break;
            case PAGE_UP:
                if (keyEvent.isControlDown()) {
                    mapPageRight();
                } else {
                    mapPageUp();
                }
                break;
            case PAGE_DOWN:
                if (keyEvent.isControlDown()) {
                    mapPageLeft();
                } else {
                    mapPageDown();
                }
                break;
            case DOWN:
                cursorBackward();
                break;
            case LEFT:
                cursorTurnLeft();
                break;
            case RIGHT:
                cursorTurnRight();
                break;
        }

    }
    private void removeTrack() {
        Point position = model.getCursor().getPosition();
        RailTrack track = model.getRailMap().getTrackAt(position.getX(), position.getY());
        if (track != null) {
            model.getRailMap().removeTrack(position.getX(), position.getY());
        }
        Point newPos = new Point(model.getCursor().getPosition());
        if (!reversed) {
            newPos.move(model.getCursor().getDir(), 1);
        } else {
            newPos.move(model.getCursor().getDir().inverse());
        }
        model.getCursor().setPosition(newPos);
        Point p = model.getCursor().getPosition();
        view.setPageOfPos(p.getX(), p.getY());

    }

    private void createTrack() {
        degreesOfRotation = 0;
        makeTrack();
        Point position = model.getCursor().getPosition();
        view.setPageOfPos(position.getX(), position.getY());
    }

    private int degreesOfRotation = 0;
    private Dir dir = Dir.N;
    Track oldTrack;
    Dir oldDir;
    boolean reversed = false;
    boolean makingTraks = false;

    private boolean makeTrack() {
        makingTraks = true;
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
        model.getRailMap().addTrack(cursorPosition, track);
        if (canBeAFork(track, oldDir, dir)) {
            final ForkRailTrack myNewTrack = new ForkRailTrack();
            model.addFork(myNewTrack);
            final Router router = track.getRouter();
            router.forEach(t -> myNewTrack.getRouter().addRoute(t.getKey(), t.getValue()));
            model.getRailMap().removeTrack(track.getPosition().getX(), track.getPosition().getY());
            model.getRailMap().addTrack(model.getCursor().getPosition(), myNewTrack);
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

    public void selectNewTrackType(CompactPresenter.TrackType type) {
        this.newTrackType = type;
    }

    public CompactPresenter.TrackType getNewTrackType() {
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

    public boolean canBeAFork(Track track, Dir from, Dir to) {
        final Router r = new SimpleRouter();
        track.getRouter().forEach(t -> r.addRoute(t.getKey(), t.getValue()));
        r.addRoute(from, to);
        return r.getNumRoutes() == 3;
    }
    private void cursorTurnRight() {
        if (makingTraks) {
            if (degreesOfRotation >= 0) {
                this.dir = this.dir.turnRight();
                model.getCursor().setDir(this.dir);
                degreesOfRotation -= 1;
            }
        } else {
            this.dir = this.dir.turnRight();
            model.getCursor().setDir(this.dir);
        }
    }

    private void cursorTurnLeft() {
        if (makingTraks) {
            if (degreesOfRotation <= 0) {
                this.dir = this.dir.turnLeft();
                model.getCursor().setDir(this.dir);
                degreesOfRotation += 1;
            }
        } else {
            this.dir = this.dir.turnLeft();
            model.getCursor().setDir(this.dir);
        }
    }
    private void cursorForward() {
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

    private void cursorBackward() {
        reversed = true;
        cursorForward();
        reversed = false;
    }

    private void mapPageDown() {
        view.clear();
        Point p = view.getMapScrollPage();
        p.setY(p.getY() + 1);
        view.setMapScrollPage(p);
        view.clear();

    }

    private void mapPageLeft() {
        view.clear();
        Point p = view.getMapScrollPage();
        p.setX(p.getX() - 1);
        view.setMapScrollPage(p);
        view.clear();

    }

    private void mapPageUp() {
        view.clear();
        Point p = view.getMapScrollPage();
        p.setY(p.getY() - 1);
        view.setMapScrollPage(p);
        view.clear();

    }

    private void mapPageRight() {
        view.clear();
        Point p = view.getMapScrollPage();
        p.setX(p.getX() + 1);
        view.setMapScrollPage(p);
        view.clear();

    }

}
