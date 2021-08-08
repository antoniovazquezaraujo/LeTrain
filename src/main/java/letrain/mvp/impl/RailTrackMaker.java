package letrain.mvp.impl;

import javafx.scene.input.KeyEvent;
import letrain.map.*;
import letrain.track.Track;
import letrain.track.rail.*;
import letrain.vehicle.impl.Cursor;

import static letrain.mvp.impl.CompactPresenter.TrackType.STOP_TRACK;
import static letrain.mvp.impl.CompactPresenter.TrackType.TUNNEL_GATE;

public class RailTrackMaker {
    private final letrain.mvp.View view;
    RailMapFactory railMapFactory;
    public RailTrackMaker(Model model, letrain.mvp.View view) {
        this.view = view;
        this.railMapFactory = new RailMapFactory(model);
    }

    public void onChar(KeyEvent keyEvent) {
        switch (keyEvent.getCode()) {
            case T:
                railMapFactory.selectNewTrackType(TUNNEL_GATE);
                createTrack();
                railMapFactory.selectNewTrackType(CompactPresenter.TrackType.NORMAL_TRACK);
                railMapFactory.setMakingTraks(false);
                break;
            case S:
                railMapFactory.selectNewTrackType(STOP_TRACK);
                createTrack();
                railMapFactory.selectNewTrackType(CompactPresenter.TrackType.NORMAL_TRACK);
                railMapFactory.setMakingTraks(false);
                break;
            case UP:
                if (keyEvent.isShiftDown()) {
                    if (!railMapFactory.isMakingTraks()) {
                        railMapFactory.reset();
                    }
                    railMapFactory.getModel().getCursor().setMode(Cursor.CursorMode.DRAWING);
                    createTrack();
                    railMapFactory.setMakingTraks(true);
                } else if (keyEvent.isControlDown()) {
                    railMapFactory.getModel().getCursor().setMode(Cursor.CursorMode.ERASING);
                    removeTrack();
                    railMapFactory.setMakingTraks(false);
                } else {
                    railMapFactory.getModel().getCursor().setMode(Cursor.CursorMode.MOVING);
                    cursorForward();
                    railMapFactory.setMakingTraks(false);
                }
                break;
            case PAGE_UP:
                if (keyEvent.isControlDown()) {
                    mapPageLeft();
                } else {
                    mapPageUp();
                }
                break;
            case PAGE_DOWN:
                if (keyEvent.isControlDown()) {
                    mapPageRight();
                } else {
                    mapPageDown();
                }
                break;
            case DOWN:
                railMapFactory.getModel().getCursor().setMode(Cursor.CursorMode.MOVING);
                railMapFactory.cursorBackward();
                railMapFactory.setMakingTraks(false);
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
        railMapFactory.removeTrack();
        Point p = railMapFactory.getModel().getCursor().getPosition();
        view.setPageOfPos(p.getX(), p.getY());
    }

    public void createTrack() {
        railMapFactory.createTrack();
        Point position = railMapFactory.getModel().getCursor().getPosition();
        view.setPageOfPos(position.getX(), position.getY());
    }


    public void cursorTurnRight() {
        railMapFactory.cursorTurnRight();
    }

    public void cursorTurnLeft() {
        railMapFactory.cursorTurnLeft();
    }

    private void cursorForward() {
        railMapFactory.cursorForward();
        Point position = railMapFactory.getModel().getCursor().getPosition();
        view.setPageOfPos(position.getX(), position.getY());
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
