package letrain.mvp.impl;

import javafx.scene.input.KeyEvent;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.map.Router;
import letrain.map.SimpleRouter;
import letrain.track.Track;
import letrain.track.rail.*;
import letrain.vehicle.impl.Cursor;

import static letrain.mvp.impl.CompactPresenter.TrackType.STOP_TRACK;
import static letrain.mvp.impl.CompactPresenter.TrackType.TUNNEL_GATE;

public class RailTrackMaker {
    private final Model model;
    private final letrain.mvp.View view;
    private CompactPresenter.TrackType newTrackType = CompactPresenter.TrackType.NORMAL_TRACK;
    private int degreesOfRotation = 0;
    private Dir dir = Dir.N;
    Track oldTrack;
    Dir oldDir;
    boolean reversed = false;
    boolean makingTraks = false;


    public RailTrackMaker(Model model, letrain.mvp.View view) {
        this.model = model;
        this.view = view;
    }

    public void onChar(KeyEvent keyEvent) {
        switch (keyEvent.getCode()) {
            case T:
                selectNewTrackType(TUNNEL_GATE);
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
                    if (!makingTraks) {
                        reset();
                    }
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
                model.getCursor().setMode(Cursor.CursorMode.MOVING);
                cursorBackward();
                makingTraks = false;
                break;
            case LEFT:
                cursorTurnLeft();
                break;
            case RIGHT:
                cursorTurnRight();
                break;
        }

    }

    private void reset() {
        degreesOfRotation = 0;
        dir = model.getCursor().getDir();
        oldTrack = null;
        oldDir = dir;
        reversed = false;
    }

    private void removeTrack() {
        Point position = model.getCursor().getPosition();
        RailTrack track = model.getRailMap().getTrackAt(position.getX(), position.getY());
        if (track != null) {
            model.getRailMap().removeTrack(position.getX(), position.getY());
        }
        if (model.getForks().contains(track)) {
            model.getForks().remove(track);
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

        //Obtenemos el track bajo el cursor
        RailTrack track = model.getRailMap().getTrackAt(cursorPosition.getX(), cursorPosition.getY());
        if (track == null) {
            //si no había nada creamos un track normal
            track = createTrackOfSelectedType();
        } else {
            // si había un fork no seguimos
            if (ForkRailTrack.class.isAssignableFrom(track.getClass())) {
                return false;
            }
        }
        // al track que había (o al que hemos creado normal) le agregamos la ruta entre la vieja dir y la nueva
        track.addRoute(oldDir, dir);
        track.setPosition(cursorPosition);
        model.getRailMap().addTrack(cursorPosition, track);
        if (canBeAFork(track, oldDir, dir)) {
            final ForkRailTrack myNewTrack = new ForkRailTrack();
            myNewTrack.setPosition(cursorPosition);
            model.addFork(myNewTrack);
            final Router router = track.getRouter();
            router.forEach(t -> {
                myNewTrack.addRoute(t.getKey(), t.getValue());
            });
            myNewTrack.setNormalRoute();
            System.out.println("Fork agregado:"+ myNewTrack.toString());
            model.getRailMap().removeTrack(track.getPosition().getX(), track.getPosition().getY());
            model.getRailMap().addTrack(model.getCursor().getPosition(), myNewTrack);
            for (Dir d : Dir.values()) {
                if (track.getConnected(d) != null) {
                    Track connected = track.getConnected(d);
                    connected.disconnect(d.inverse());
                    connected.connect(d.inverse(), myNewTrack);
                    myNewTrack.connect(d, connected);
                }
            }
            track = myNewTrack;
//            myNewTrack.setAlternativeRoute();
        }
        if (oldTrack != null) {
            //conectamos el track con oldTrack en oldDir, bien.
            track.connect(oldDir, oldTrack);
            //conectamos a oldTrack con track, en la inversa
            oldTrack.connect(track.getDir(dir).inverse(), track);
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
