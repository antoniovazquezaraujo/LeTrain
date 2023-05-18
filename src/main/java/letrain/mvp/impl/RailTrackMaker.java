package letrain.mvp.impl;

import com.googlecode.lanterna.input.KeyStroke;

import letrain.map.Dir;
import letrain.map.Point;
import letrain.map.Router;
import letrain.map.SimpleRouter;
import letrain.track.RailSemaphore;
import letrain.track.Sensor;
import letrain.track.Track;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;
import letrain.track.rail.StopRailTrack;
import letrain.track.rail.TrainFactoryRailTrack;
import letrain.track.rail.TunnelRailTrack;
import letrain.vehicle.impl.Cursor;

public class RailTrackMaker {
    private final letrain.mvp.Model model;
    private final letrain.mvp.View view;
    private CompactPresenter.TrackType newTrackType = CompactPresenter.TrackType.NORMAL_TRACK;
    private int degreesOfRotation = 0;
    private Dir dir = Dir.N;
    Track oldTrack;
    Dir oldDir;
    boolean reversed = false;
    boolean makingTraks = false;

    public RailTrackMaker(letrain.mvp.Model model, letrain.mvp.View view) {
        this.model = model;
        this.view = view;
    }

    public void onChar(KeyStroke keyEvent) {
        switch (keyEvent.getKeyType()) {
            case ArrowUp:
                if (keyEvent.isShiftDown()) {
                    if (!makingTraks) {
                        reset();
                    }
                    model.getCursor().setMode(Cursor.CursorMode.DRAWING);
                    createTrack();
                    makingTraks = true;
                } else if (keyEvent.isCtrlDown()) {
                    model.getCursor().setMode(Cursor.CursorMode.ERASING);
                    removeTrack();
                    makingTraks = false;
                } else {
                    model.getCursor().setMode(Cursor.CursorMode.MOVING);
                    cursorForward();
                    makingTraks = false;
                }
                break;
            case PageUp:
                if (keyEvent.isCtrlDown()) {
                    mapPageLeft();
                } else {
                    mapPageUp();
                }
                break;
            case PageDown:
                if (keyEvent.isCtrlDown()) {
                    mapPageRight();
                } else {
                    mapPageDown();
                }
                break;
            case ArrowDown:
                model.getCursor().setMode(Cursor.CursorMode.MOVING);
                cursorBackward();
                makingTraks = false;
                break;
            case ArrowLeft:
                cursorTurnLeft();
                break;
            case ArrowRight:
                cursorTurnRight();
                break;
            case Insert:
                addSensor();
                break;
            case Delete:
                removeSensor();
                break;
            case Home:
                addSemaphore();
                break;
            case End:
                removeSemaphore();
                break;
        }

    }

    void addSensor() {
        Point position = model.getCursor().getPosition();
        Track track = model.getRailMap().getTrackAt(position.getX(), position.getY());
        if (track != null) {
            Sensor sensor = new Sensor();
            sensor.setTrack(track);
            track.setSensor(sensor);
            model.addSensor(sensor);
        }
    }

    void addSemaphore() {
        Point position = model.getCursor().getPosition();
        RailSemaphore semaphore = new RailSemaphore(position);
        model.addSemaphore(semaphore);
    }

    void removeSensor() {
        Point position = model.getCursor().getPosition();
        Track track = model.getRailMap().getTrackAt(position.getX(), position.getY());
        if (track != null) {
            Sensor sensor = track.getSensor();
            if (sensor != null) {
                track.setSensor(null);
                model.removeSensor(sensor);
            }
        }
    }

    void removeSemaphore() {
        Point position = model.getCursor().getPosition();
        RailSemaphore semaphore = model.getSemaphoreAt(position);
        if (semaphore != null) {
            model.removeSemaphore(semaphore);
        }
    }

    private void reset() {
        degreesOfRotation = 0;
        dir = model.getCursor().getDir();
        oldTrack = null;
        oldDir = dir;
        reversed = false;
    }

    void removeTrack() {
        Point position = model.getCursor().getPosition();
        RailTrack track = model.getRailMap().getTrackAt(position.getX(), position.getY());
        if (track != null) {
            model.getRailMap().removeTrack(position);
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

    void createTrack() {
        degreesOfRotation = 0;
        makeTrack();
        Point position = model.getCursor().getPosition();
        view.setPageOfPos(position.getX(), position.getY());
    }

    boolean makeTrack() {
        makingTraks = true;
        Point cursorPosition = model.getCursor().getPosition();
        Dir dir = model.getCursor().getDir();

        // Si venimos de algún track, obtenemos la dirección de salida
        if (oldTrack != null) {
            oldDir = cursorPosition.locate(oldTrack.getPosition());
        } else {
            // Si no venimos de ningún track, la oldDir será la nueva o su inversa
            if (!reversed) {
                oldDir = model.getCursor().getDir().inverse();
            } else {
                oldDir = model.getCursor().getDir();
            }
        }

        // Obtenemos el track bajo el cursor
        RailTrack track = model.getRailMap().getTrackAt(cursorPosition);
        if (track == null) {
            // si no había nada creamos un track normal
            track = createTrackOfSelectedType();
        } else {
            // si había un fork no seguimos
            if (ForkRailTrack.class.isAssignableFrom(track.getClass())) {
                return false;
            }
        }
        // al track que había (o al que hemos creado normal) le agregamos la ruta entre
        // la vieja dir y la nueva
        track.addRoute(oldDir, dir);
        track.setPosition(cursorPosition);
        model.getRailMap().addTrack(cursorPosition, track);
        if (canBeAFork(track, oldDir, dir)) {
            RailTrack trackToSubstitute = track;
            final ForkRailTrack fork = createForkRailTrack(cursorPosition, trackToSubstitute);
            addRoutesToFork(trackToSubstitute, fork);
            fork.setNormalRoute();
            model.addFork(fork);
            substituteInMapTrackWithFork(trackToSubstitute, fork);
            addTrackConnectionsToFork(trackToSubstitute, fork);
            track = fork;
        }
        if (oldTrack != null) {
            // conectamos el track con oldTrack en oldDir, bien.
            track.connect(oldDir, oldTrack);
            // conectamos a oldTrack con track, en la inversa
            oldTrack.connect(oldDir.inverse(), track);
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

    private void substituteInMapTrackWithFork(RailTrack track1, final ForkRailTrack fork) {
        model.getRailMap().removeTrack(track1.getPosition());
        model.getRailMap().addTrack(model.getCursor().getPosition(), fork);
    }

    ForkRailTrack createForkRailTrack(Point cursorPosition, RailTrack track) {
        final ForkRailTrack fork = new ForkRailTrack();
        fork.setPosition(cursorPosition);
        return fork;
    }

    private void addRoutesToFork(RailTrack track, final ForkRailTrack fork) {
        final Router router = track.getRouter();
        router.forEach(t -> {
            fork.addRoute(t.getKey(), t.getValue());
        });
    }

    void addTrackConnectionsToFork(RailTrack track, final ForkRailTrack fork) {
        for (Dir d : Dir.values()) {
            if (track.getConnected(d) != null) {
                Track connectedTrack = track.getConnected(d);
                connectedTrack.disconnect(d.inverse());
                connectedTrack.connect(d.inverse(), fork);
                fork.connect(d, connectedTrack);
            }
        }
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

    void cursorForward() {
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

    void cursorBackward() {
        reversed = true;
        cursorForward();
        reversed = false;
    }

    void mapPageDown() {
        view.clear();
        Point p = view.getMapScrollPage();
        p.setY(p.getY() + 1);
        view.setMapScrollPage(p);
        view.clear();

    }

    void mapPageLeft() {
        view.clear();
        Point p = view.getMapScrollPage();
        p.setX(p.getX() - 1);
        view.setMapScrollPage(p);
        view.clear();

    }

    void mapPageUp() {
        view.clear();
        Point p = view.getMapScrollPage();
        p.setY(p.getY() - 1);
        view.setMapScrollPage(p);
        view.clear();

    }

    void mapPageRight() {
        view.clear();
        Point p = view.getMapScrollPage();
        p.setX(p.getX() + 1);
        view.setMapScrollPage(p);
        view.clear();

    }

}
