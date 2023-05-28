package letrain.mvp.impl;

import com.googlecode.lanterna.input.KeyStroke;

import letrain.map.Dir;
import letrain.map.Point;
import letrain.map.Router;
import letrain.map.SimpleRouter;
import letrain.mvp.Presenter;
import letrain.mvp.impl.CompactPresenter.TrackType;
import letrain.track.PlatformSensor;
import letrain.track.RailSemaphore;
import letrain.track.Sensor;
import letrain.track.Track;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.PlatformTrack;
import letrain.track.rail.RailTrack;
import letrain.track.rail.StopRailTrack;
import letrain.track.rail.TrainFactoryRailTrack;
import letrain.track.rail.TunnelRailTrack;
import letrain.vehicle.impl.Cursor;

public class RailTrackMaker {
    private CompactPresenter.TrackType newTrackType = CompactPresenter.TrackType.NORMAL_TRACK;
    private int degreesOfRotation = 0;
    private Dir dir = Dir.N;
    Track oldTrack;
    Dir oldDir;
    boolean reversed = false;
    boolean makingTraks = false;
    int numSteps = 0;
    boolean creatingPlatform = false;
    Presenter presenter;

    public RailTrackMaker(Presenter presenter) {
        this.presenter = presenter;
    }

    public void onChar(KeyStroke keyEvent) {
        switch (keyEvent.getKeyType()) {
            case ArrowUp:
                if (keyEvent.isShiftDown()) {
                    if (!makingTraks) {
                        reset();
                    }
                    presenter.getModel().getCursor().setMode(Cursor.CursorMode.DRAWING);
                    if (numSteps > 0) {
                        for (int n = 0; n < numSteps; n++) {
                            if (!createTrack()) {
                                break;
                            }
                        }
                    } else {
                        createTrack();
                    }
                    makingTraks = true;
                } else if (keyEvent.isCtrlDown()) {
                    presenter.getModel().getCursor().setMode(Cursor.CursorMode.ERASING);
                    removeTrack();
                    makingTraks = false;
                } else {
                    presenter.getModel().getCursor().setMode(Cursor.CursorMode.MOVING);
                    if (numSteps > 0) {
                        for (int n = 0; n < numSteps; n++) {
                            cursorForward();
                        }
                    } else {
                        cursorForward();
                    }
                    makingTraks = false;
                }
                break;
            case Character:
                if (keyEvent.getCharacter() == ' ') {
                    numSteps = 0;
                } else if (keyEvent.getCharacter() == 'w') {
                    creatingPlatform = !creatingPlatform;
                    if (creatingPlatform) {
                        selectNewTrackType(TrackType.PLATFORM_TRACK);
                    } else {
                        selectNewTrackType(TrackType.NORMAL_TRACK);
                    }
                } else if (keyEvent.getCharacter() >= '0' && keyEvent.getCharacter() <= '9') {
                    if (keyEvent.getCharacter() == '0' && numSteps == 0) {
                        presenter.getModel().setShowId(true);
                    } else {
                        numSteps = numSteps * 10 + (keyEvent.getCharacter() - '0');
                    }
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
                presenter.getModel().getCursor().setMode(Cursor.CursorMode.MOVING);
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
                manageSensor();
                break;
            case Home:
                manageSemaphore();
                break;
            case End:
                managePlatformSensor();
                break;
        }

    }

    void manageSensor() {
        Point position = presenter.getModel().getCursor().getPosition();
        Track track = presenter.getModel().getRailMap().getTrackAt(position.getX(), position.getY());
        if (track != null) {
            Sensor sensor = track.getSensor();
            if (sensor != null) {
                track.setSensor(null);
                presenter.getModel().removeSensor(sensor);
            } else {
                sensor = new Sensor(presenter.getModel().nextSensorId());
                sensor.setTrack(track);
                track.setSensor(sensor);
                presenter.getModel().addSensor(sensor);
            }
        }
    }

    void manageSemaphore() {
        Point position = presenter.getModel().getCursor().getPosition();
        RailSemaphore semaphore = presenter.getModel().getSemaphoreAt(position);
        if (semaphore != null) {
            presenter.getModel().removeSemaphore(semaphore);
        } else {
            semaphore = new RailSemaphore(presenter.getModel().nextSemaphoreId(), position);
            presenter.getModel().addSemaphore(semaphore);
        }
    }

    void managePlatformSensor() {
        Point position = presenter.getModel().getCursor().getPosition();
        Track track = presenter.getModel().getRailMap().getTrackAt(position.getX(), position.getY());
        if (track != null && track instanceof PlatformTrack) {
            Sensor sensor = track.getSensor();
            if (sensor != null) {
                if (sensor instanceof PlatformSensor) {
                    track.setSensor(null);
                }
                presenter.getModel().removeSensor(sensor);
            } else {
                sensor = new PlatformSensor(presenter.getModel().nextSensorId());
                sensor.setTrack(track);
                track.setSensor(sensor);
                presenter.getModel().addSensor(sensor);
            }
        }

    }

    private void reset() {
        degreesOfRotation = 0;
        dir = presenter.getModel().getCursor().getDir();
        oldTrack = null;
        oldDir = dir;
        reversed = false;
    }

    void removeTrack() {
        Point position = presenter.getModel().getCursor().getPosition();
        RailTrack track = presenter.getModel().getRailMap().getTrackAt(position.getX(), position.getY());
        if (track != null) {
            presenter.getModel().getRailMap().removeTrack(position);
        }
        if (presenter.getModel().getForks().contains(track)) {
            presenter.getModel().getForks().remove(track);
        }
        Point newPos = new Point(presenter.getModel().getCursor().getPosition());
        if (!reversed) {
            newPos.move(presenter.getModel().getCursor().getDir(), 1);
        } else {
            newPos.move(presenter.getModel().getCursor().getDir().inverse());
        }
        presenter.getModel().getCursor().setPosition(newPos);
        Point p = presenter.getModel().getCursor().getPosition();
        presenter.getView().setPageOfPos(p.getX(), p.getY());

    }

    boolean createTrack() {
        degreesOfRotation = 0;
        if (makeTrack()) {
            Point position = presenter.getModel().getCursor().getPosition();
            presenter.getView().setPageOfPos(position.getX(), position.getY());
            return true;
        }
        return false;
    }

    boolean makeTrack() {
        makingTraks = true;
        Point cursorPosition = presenter.getModel().getCursor().getPosition();
        Dir dir = presenter.getModel().getCursor().getDir();

        // Si venimos de algún track, obtenemos la dirección de salida
        if (oldTrack != null) {
            oldDir = cursorPosition.locate(oldTrack.getPosition());
        } else {
            // Si no venimos de ningún track, la oldDir será la nueva o su inversa
            if (!reversed) {
                oldDir = presenter.getModel().getCursor().getDir().inverse();
            } else {
                oldDir = presenter.getModel().getCursor().getDir();
            }
        }

        // Obtenemos el track bajo el cursor
        RailTrack track = presenter.getModel().getRailMap().getTrackAt(cursorPosition);
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
        presenter.getModel().getRailMap().addTrack(cursorPosition, track);
        if (canBeAFork(track, oldDir, dir)) {
            RailTrack trackToSubstitute = track;
            final ForkRailTrack fork = createForkRailTrack(cursorPosition, trackToSubstitute);
            addRoutesToFork(trackToSubstitute, fork);
            fork.setNormalRoute();
            presenter.getModel().addFork(fork);
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
            newPos.move(presenter.getModel().getCursor().getDir(), 1);
        } else {
            newPos.move(presenter.getModel().getCursor().getDir().inverse());
        }
        presenter.getModel().getCursor().setPosition(newPos);
        oldTrack = track;
        return true;
    }

    private void substituteInMapTrackWithFork(RailTrack track1, final ForkRailTrack fork) {
        presenter.getModel().getRailMap().removeTrack(track1.getPosition());
        presenter.getModel().getRailMap().addTrack(presenter.getModel().getCursor().getPosition(), fork);
    }

    ForkRailTrack createForkRailTrack(Point cursorPosition, RailTrack track) {
        final ForkRailTrack fork = new ForkRailTrack(presenter.getModel().nextForkId());
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
            case PLATFORM_TRACK:
                return new PlatformTrack();
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
                presenter.getModel().getCursor().setDir(this.dir);
                degreesOfRotation -= 1;
            }
        } else {
            this.dir = this.dir.turnRight();
            presenter.getModel().getCursor().setDir(this.dir);
        }
    }

    private void cursorTurnLeft() {
        if (makingTraks) {
            if (degreesOfRotation <= 0) {
                this.dir = this.dir.turnLeft();
                presenter.getModel().getCursor().setDir(this.dir);
                degreesOfRotation += 1;
            }
        } else {
            this.dir = this.dir.turnLeft();
            presenter.getModel().getCursor().setDir(this.dir);
        }
    }

    void cursorForward() {
        Point newPos = new Point(presenter.getModel().getCursor().getPosition());
        if (!reversed) {
            newPos.move(presenter.getModel().getCursor().getDir(), 1);
        } else {
            newPos.move(presenter.getModel().getCursor().getDir().inverse());
        }
        presenter.getModel().getCursor().setPosition(newPos);
        Point position = presenter.getModel().getCursor().getPosition();
        presenter.getView().setPageOfPos(position.getX(), position.getY());
    }

    void cursorBackward() {
        reversed = true;
        cursorForward();
        reversed = false;
    }

    void mapPageDown() {
        presenter.getView().clear();
        Point p = presenter.getView().getMapScrollPage();
        p.setY(p.getY() + 1);
        presenter.getView().setMapScrollPage(p);
        presenter.getView().clear();

    }

    void mapPageLeft() {
        presenter.getView().clear();
        Point p = presenter.getView().getMapScrollPage();
        p.setX(p.getX() - 1);
        presenter.getView().setMapScrollPage(p);
        presenter.getView().clear();

    }

    void mapPageUp() {
        presenter.getView().clear();
        Point p = presenter.getView().getMapScrollPage();
        p.setY(p.getY() - 1);
        presenter.getView().setMapScrollPage(p);
        presenter.getView().clear();

    }

    void mapPageRight() {
        presenter.getView().clear();
        Point p = presenter.getView().getMapScrollPage();
        p.setX(p.getX() + 1);
        presenter.getView().setMapScrollPage(p);
        presenter.getView().clear();

    }

}
