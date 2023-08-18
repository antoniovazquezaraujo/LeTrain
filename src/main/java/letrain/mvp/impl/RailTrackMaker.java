package letrain.mvp.impl;

import java.util.Map;

import com.googlecode.lanterna.input.KeyStroke;
import letrain.ground.GroundMap;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.map.Router;
import letrain.map.impl.SimpleRouter;
import letrain.mvp.Presenter;
import letrain.mvp.Presenter.TrackType;
import letrain.track.RailSemaphore;
import letrain.track.Sensor;
import letrain.track.Station;
import letrain.track.Track;
import letrain.track.rail.BridgeGateRailTrack;
import letrain.track.rail.BridgeRailTrack;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;
import letrain.track.rail.StationRailTrack;
import letrain.track.rail.TunnelGateRailTrack;
import letrain.track.rail.TunnelRailTrack;
import letrain.vehicle.impl.Cursor;
import letrain.vehicle.impl.Cursor.CursorMode;

public class RailTrackMaker {
    private Presenter.TrackType newTrackType = Presenter.TrackType.NORMAL_TRACK;
    private int degreesOfRotation = 0;
    private Dir dir = Dir.E;
    Track oldTrack;
    Dir oldDir;
    boolean reversed = false;
    boolean makingTracks = false;
    int quantifier = 1;
    int quantifierSteps = 0;
    boolean creatingStation = false;
    Presenter presenter;
    Point lastCursorPosition = null;
    Integer oldGroundType = null;
    private Point stationStart;
    int trackConstructionTimeCounter = 0;
    Map<TrackType, Integer> trackConstructionTime = Map.of(
            TrackType.NORMAL_TRACK, 1,
            TrackType.BRIDGE_TRACK, 10,
            TrackType.BRIDGE_GATE_TRACK, 5,
            TrackType.TUNNEL_TRACK, 20,
            TrackType.TUNNEL_GATE_TRACK, 10,
            TrackType.STATION_TRACK, 30);

    public void startTrackConstruction(TrackType type) {
        this.trackConstructionTimeCounter = trackConstructionTime.get(type);
    }

    public boolean isTrackConstructionFinished() {
        return this.trackConstructionTimeCounter <= 0;
    }

    public void decreaseTrackConstructionTime() {
        this.trackConstructionTimeCounter--;
    }

    public RailTrackMaker(Presenter presenter) {
        this.presenter = presenter;
        if (presenter != null) {
            lastCursorPosition = presenter.getModel().getCursor().getPosition();
            oldGroundType = presenter.getModel().getGroundMap().getValueAt(this.lastCursorPosition);
        }
    }

    public void onChar(KeyStroke keyEvent) {
        switch (keyEvent.getKeyType()) {
            case ArrowUp:
                if (keyEvent.isShiftDown()) {
                    if (!makingTracks) {
                        reset();
                        TrackType type = detectTrackType();
                        resetTrackConstructionTime(type);
                    }
                    resetQuantifierSteps();
                    presenter.getModel().getCursor().setMode(Cursor.CursorMode.DRAWING);
                    makingTracks = true;

                } else if (keyEvent.isCtrlDown()) {
                    presenter.getModel().getCursor().setMode(Cursor.CursorMode.ERASING);
                    removeTrack();
                    makingTracks = false;
                } else {
                    makingTracks = false;
                    if (creatingStation) {
                        createStation();
                        selectNewTrackType(Presenter.TrackType.NORMAL_TRACK);
                        creatingStation = false;
                    }
                    presenter.getModel().getCursor().setMode(Cursor.CursorMode.MOVING);
                    if (quantifier > 0) {
                        resetQuantifierSteps();
                        while (isQuantifierPending()) {
                            cursorForward();
                            decrementQuantifierSteps();
                        }
                    } else {
                        cursorForward();
                    }
                }
                break;
            case Character:
                if (keyEvent.getCharacter() == ' ') {
                    quantifier = 0;
                } else if (keyEvent.getCharacter() == 'w') {
                    creatingStation = !creatingStation;
                    if (creatingStation) {
                        saveStationStart();
                        selectNewTrackType(Presenter.TrackType.STATION_TRACK);
                    } else {
                        createStation();
                        selectNewTrackType(Presenter.TrackType.NORMAL_TRACK);
                    }
                } else if (keyEvent.getCharacter() >= '0' && keyEvent.getCharacter() <= '9') {
                    if (keyEvent.getCharacter() == '0' && quantifier == 0) {
                        presenter.getModel().setShowId(true);
                    } else {
                        quantifier = quantifier * 10 + (keyEvent.getCharacter() - '0');
                    }
                }
                break;
            case PageUp:
                makingTracks = false;
                if (keyEvent.isCtrlDown()) {
                    mapPageLeft();
                } else {
                    mapPageUp();
                }
                break;
            case PageDown:
                makingTracks = false;
                if (keyEvent.isCtrlDown()) {
                    mapPageRight();
                } else {
                    mapPageDown();
                }
                break;
            case ArrowDown:
                presenter.getModel().getCursor().setMode(Cursor.CursorMode.MOVING);
                cursorBackward();
                makingTracks = false;
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
                manageStationSensor();
                break;
        }

    }

    private void saveStationStart() {
        stationStart = new Point(presenter.getModel().getCursor().getPosition());
    }

    private void createStation() {
        Point position = presenter.getModel().getCursor().getPosition();
        int x = (position.getX() == this.stationStart.getX())
                ? position.getX()
                : (position.getX() - (position.getX() + 1 - this.stationStart.getX()) / 2);
        int y = (position.getY() == this.stationStart
                .getY())
                        ? position.getY()
                        : position.getY() - (position.getY() + 1 - this.stationStart.getY()) / 2;
        Point sensorPosition = new Point(x, y);
        Track track = presenter.getModel().getRailMap().getTrackAt(sensorPosition.getX(), sensorPosition.getY());
        if (track != null && track instanceof StationRailTrack) {
            Station station = new Station(presenter.getModel().nextStationId());
            station.setTrack(track);
            track.setSensor(station);
            presenter.getModel().addStation(station);
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

    void manageStationSensor() {
        Point position = presenter.getModel().getCursor().getPosition();
        Track track = presenter.getModel().getRailMap().getTrackAt(position.getX(), position.getY());
        if (track != null && track instanceof StationRailTrack) {
            Sensor sensor = track.getSensor();
            if (sensor != null) {
                if (sensor instanceof Station) {
                    track.setSensor(null);
                }
                presenter.getModel().removeSensor(sensor);
            } else {
                sensor = new Station(presenter.getModel().nextStationId());
                sensor.setTrack(track);
                track.setSensor(sensor);
                presenter.getModel().addStation((Station) sensor);
            }
        }

    }

    private void reset() {
        degreesOfRotation = 0;
        dir = presenter.getModel().getCursor().getDir();
        oldTrack = null;
        oldDir = dir;
        reversed = false;
        oldGroundType = null;
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
        updateCursorPosition(newPos);
        Point p = presenter.getModel().getCursor().getPosition();
        presenter.getView().setPageOfPos(p.getX(), p.getY());

    }

    void makeTracks() {
        if (makingTracks) {
            if (isQuantifierPending()) {
                if (isTrackConstructionPending()) {
                    showAnimation();
                    decrementTrackConstructionTime();
                } else {
                    TrackType type = detectTrackType();
                    selectNewTrackType(type);
                    createTrack(type);
                    decrementQuantifierSteps();
                    resetTrackConstructionTime(type);
                }
            }
        }

    }

    private void resetTrackConstructionTime(TrackType type) {
        startTrackConstruction(type);
    }

    private void decrementQuantifierSteps() {
        this.quantifierSteps--;
    }

    public void resetQuantifierSteps() {
        this.quantifierSteps = quantifier;
    }

    private void decrementTrackConstructionTime() {
        decreaseTrackConstructionTime();
    }

    private boolean isTrackConstructionPending() {
        return !isTrackConstructionFinished();
    }

    private boolean isQuantifierPending() {
        return this.quantifierSteps >= 0;
    }

    void showAnimation() {
        presenter.getModel().getCursor().setMode(CursorMode.MAKING_TRACKS);
    }

    boolean createTrack(TrackType type) {
        degreesOfRotation = 0;
        if (makeTrack(type)) {
            Point position = presenter.getModel().getCursor().getPosition();
            presenter.getView().setPageOfPos(position.getX(), position.getY());
            return true;
        }
        return false;
    }

    public TrackType detectTrackType() {
        Point actualCursorPosition = presenter.getModel().getCursor().getPosition();
        Integer actualGroundType = presenter.getModel().getGroundMap().getValueAt(actualCursorPosition);
        if (oldGroundType == null) {
            oldGroundType = actualGroundType;
        }
        // Si venimos de algún track, obtenemos la dirección de salida
        if (oldTrack != null) {
            oldDir = actualCursorPosition.locate(oldTrack.getPosition());
        } else {
            // Si no venimos de ningún track, la oldDir será la nueva o su inversa
            if (!reversed) {
                oldDir = presenter.getModel().getCursor().getDir().inverse();
            } else {
                oldDir = presenter.getModel().getCursor().getDir();
            }
        }
        TrackType type = null;
        if (actualGroundType == oldGroundType) {
            // seguimos con el mismo tipo de suelo
            switch (actualGroundType) {
                case GroundMap.GROUND:
                    type = Presenter.TrackType.NORMAL_TRACK;
                    if (creatingStation) {
                        type = Presenter.TrackType.STATION_TRACK;
                    }
                    break;
                case GroundMap.WATER:
                    type = Presenter.TrackType.BRIDGE_TRACK;
                    break;
                case GroundMap.ROCK:
                    type = Presenter.TrackType.TUNNEL_TRACK;
                    break;
            }
        } else {
            if (creatingStation) {
                return null;
            }
            // cambio de suelo
            if (oldGroundType == GroundMap.GROUND) {
                // pasamos de GROUND a otro tipo de suelo
                if (actualGroundType == GroundMap.WATER) {
                    // entramos en agua
                    type = Presenter.TrackType.BRIDGE_GATE_TRACK;
                } else if (actualGroundType == GroundMap.ROCK) {
                    // entramos en roca
                    type = Presenter.TrackType.TUNNEL_GATE_TRACK;
                }
            } else {
                // salimos de otro tipo de suelo
                if (actualGroundType != GroundMap.GROUND) {
                    // no podemos pasar de un tipo de suelo a otro sin pasar por GROUND
                    return null;
                }
                if (oldGroundType == GroundMap.WATER) {
                    // salimos de agua
                    type = Presenter.TrackType.BRIDGE_GATE_TRACK;
                } else if (this.oldGroundType == GroundMap.ROCK) {
                    // salimos de roca
                    type = Presenter.TrackType.TUNNEL_GATE_TRACK;
                }
            }
        }
        return type;
    }

    boolean makeTrack(TrackType type) {
        makingTracks = true;

        // TrackType type = detectTrackType();
        Dir dir = presenter.getModel().getCursor().getDir();
        Point actualCursorPosition = presenter.getModel().getCursor().getPosition();
        Integer actualGroundType = presenter.getModel().getGroundMap().getValueAt(actualCursorPosition);
        if (oldGroundType == null) {
            oldGroundType = actualGroundType;
        }
        if (type == null) {
            return false;
        } else {
            selectNewTrackType(type);
        }

        // Obtenemos el track bajo el cursor
        RailTrack track = presenter.getModel().getRailMap().getTrackAt(actualCursorPosition);
        if (track == null) {
            // si no había nada creamos un track normal
            track = createTrackOfSelectedType();
        } else {
            if (creatingStation) {
                return false;
            }
            if (StationRailTrack.class.isAssignableFrom(track.getClass())) {
                return false;
            }
            if (actualGroundType != GroundMap.GROUND) {
                // si la dirección del cursor es distinta de la del track actual retornamos
                if (track != null && !track.canExit(presenter.getModel().getCursor().getDir())) {
                    return false;
                }
            }
            // si había un fork no seguimos
            if (ForkRailTrack.class.isAssignableFrom(track.getClass())) {
                return false;
            }
        }
        // al track que había (o al que hemos creado normal) le agregamos la ruta entre
        // la vieja dir y la nueva
        track.addRoute(oldDir, dir);
        track.setPosition(actualCursorPosition);
        presenter.getModel().getRailMap().addTrack(actualCursorPosition, track);
        presenter.getModel().getEconomyManager().onRailTrackConstructed(newTrackType);
        if (canBeAFork(track, oldDir, dir)) {
            RailTrack trackToSubstitute = track;
            final ForkRailTrack fork = createForkRailTrack(actualCursorPosition, trackToSubstitute);
            addRoutesToFork(trackToSubstitute, fork);
            fork.setNormalRoute();
            presenter.getModel().addFork(fork);
            presenter.getModel().getEconomyManager().onForkConstructed(fork);
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

        Point newPos = new Point(actualCursorPosition);
        if (!reversed) {
            newPos.move(presenter.getModel().getCursor().getDir(), 1);
        } else {
            newPos.move(presenter.getModel().getCursor().getDir().inverse());
        }
        updateCursorPosition(newPos);
        oldTrack = track;
        oldGroundType = actualGroundType;
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

    public void selectNewTrackType(Presenter.TrackType type) {
        this.newTrackType = type;
    }

    public Presenter.TrackType getNewTrackType() {
        return this.newTrackType;
    }

    public RailTrack createTrackOfSelectedType() {
        switch (newTrackType) {
            case STATION_TRACK:
                return new StationRailTrack();
            case TUNNEL_GATE_TRACK:
                return new TunnelGateRailTrack();
            case TUNNEL_TRACK:
                return new TunnelRailTrack();
            case BRIDGE_GATE_TRACK:
                return new BridgeGateRailTrack();
            case BRIDGE_TRACK:
                return new BridgeRailTrack();
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
        if (creatingStation) {
            return;
        }
        if (makingTracks) {
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
        if (creatingStation) {
            return;
        }
        if (makingTracks) {
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
        updateCursorPosition(newPos);
        Point position = presenter.getModel().getCursor().getPosition();
        presenter.getView().setPageOfPos(position.getX(), position.getY());
    }

    private void updateCursorPosition(Point newPos) {
        presenter.getModel().getCursor().setPosition(newPos);
        lastCursorPosition = newPos;
    }

    private void varyCursorPosition(Point newPos) {
        Point oldPos = presenter.getModel().getCursor().getPosition();
        newPos.setX(oldPos.getX() + newPos.getX());
        newPos.setY(oldPos.getY() + newPos.getY());
        presenter.getModel().getCursor().setPosition(newPos);
        lastCursorPosition = newPos;
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
        varyCursorPosition(new Point(0, 1 * presenter.getView().getRows()));
        presenter.getView().setMapScrollPage(p);
        presenter.getView().clear();

    }

    void mapPageLeft() {
        presenter.getView().clear();
        Point p = presenter.getView().getMapScrollPage();
        p.setX(p.getX() - 1);
        varyCursorPosition(new Point((-1 * presenter.getView().getCols()), 0));
        presenter.getView().setMapScrollPage(p);
        presenter.getView().clear();

    }

    void mapPageUp() {
        presenter.getView().clear();
        Point p = presenter.getView().getMapScrollPage();
        p.setY(p.getY() - 1);
        varyCursorPosition(new Point(0, -1 * presenter.getView().getRows()));
        presenter.getView().setMapScrollPage(p);
        presenter.getView().clear();

    }

    void mapPageRight() {
        presenter.getView().clear();
        Point p = presenter.getView().getMapScrollPage();
        p.setX(p.getX() + 1);
        varyCursorPosition(new Point((1 * presenter.getView().getCols()), 0));
        presenter.getView().setMapScrollPage(p);
        presenter.getView().clear();

    }

}
