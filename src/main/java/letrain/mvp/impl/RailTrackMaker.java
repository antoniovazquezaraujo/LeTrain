package letrain.mvp.impl;

import java.util.Map;

import com.googlecode.lanterna.input.KeyStroke;
import letrain.ground.GroundMap;
import letrain.map.Dir;
import letrain.map.Page;
import letrain.map.Point;
import letrain.map.Router;
import letrain.map.impl.SimpleRouter;
import letrain.mvp.Presenter;
import letrain.mvp.Presenter.TrackType;
import letrain.track.CargoTypes;
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
    private boolean wasRemoving = false;
    private int caterpillarCounter = 0;
    Presenter presenter;
    Point lastCursorPosition = null;
    Integer oldGroundType = null;
    int trackConstructionTimeCounter = 0;
    Map<TrackType, Integer> trackConstructionTime = Map.of(
            TrackType.NORMAL_TRACK, 0,
            TrackType.BRIDGE_TRACK, 0,
            TrackType.BRIDGE_GATE_TRACK, 0,
            TrackType.TUNNEL_TRACK, 0,
            TrackType.TUNNEL_GATE_TRACK, 0,
            TrackType.STATION_TRACK, 0);

    public void startTrackConstruction(TrackType type) {
        if (type == null) {
            this.trackConstructionTimeCounter = 0;
            return;
        }
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

    public void onKeyUp(KeyStroke keyEvent) {
        if (!keyEvent.isShiftDown() && !keyEvent.isCtrlDown()) {
            caterpillarCounter = 0;
            makingTracks = false;
        }
    }

    public void onChar(KeyStroke keyEvent) {
        switch (keyEvent.getKeyType()) {
            case ArrowUp:
                if (keyEvent.isShiftDown()) {
                    if (!makingTracks) {
                        if (wasRemoving) {
                            cursorBackward();
                            wasRemoving = false;
                        }
                        reset();
                        TrackType type = detectTrackType();
                        resetTrackConstructionTime(type);
                    }
                    resetQuantifierSteps();
                    presenter.getModel().getCursor().setMode(Cursor.CursorMode.DRAWING);
                    makingTracks = true;
                    caterpillarCounter = 20;

                } else if (keyEvent.isCtrlDown()) {
                    presenter.getModel().getCursor().setMode(Cursor.CursorMode.ERASING);
                    removeTrack(true);
                    makingTracks = false;
                    wasRemoving = false;
                } else {
                    makingTracks = false;
                    wasRemoving = false;
                    presenter.getModel().getCursor().setMode(Cursor.CursorMode.MOVING);
                    if (presenter.getModel().getQuantifier() > 0) {
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
                    presenter.getModel().setQuantifier(1);
                } else if (keyEvent.getCharacter() == 'w' || keyEvent.getCharacter() == 'W') {
                    manageStationSensor();
                } else if (keyEvent.getCharacter() >= '0' && keyEvent.getCharacter() <= '9') {
                    if (keyEvent.getCharacter() == '0' && presenter.getModel().getQuantifier() == 0) {
                        presenter.getModel().setShowId(true);
                    } else {
                        presenter.getModel().setQuantifier(
                                presenter.getModel().getQuantifier() * 10 + (keyEvent.getCharacter() - '0'));
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
                if (keyEvent.isCtrlDown() || keyEvent.isShiftDown()) {
                    presenter.getModel().getCursor().setMode(Cursor.CursorMode.ERASING);
                    cursorBackward();
                    removeTrack(false);
                    makingTracks = false;
                    wasRemoving = true;
                    caterpillarCounter = 20;
                } else {
                    presenter.getModel().getCursor().setMode(Cursor.CursorMode.MOVING);
                    cursorBackward();
                    makingTracks = false;
                    wasRemoving = false;
                }
                break;
            case ArrowLeft:
                if (keyEvent.isCtrlDown()) {
                    presenter.getModel().getCursor().setMode(Cursor.CursorMode.ERASING);
                }
                cursorTurnLeft();
                wasRemoving = false;
                break;
            case ArrowRight:
                if (keyEvent.isCtrlDown()) {
                    presenter.getModel().getCursor().setMode(Cursor.CursorMode.ERASING);
                }
                cursorTurnRight();
                wasRemoving = false;
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
            default:
                break;
        }

    }

    private void createStation() {
        // Obsolete
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
                sensor.setCreationDir(presenter.getModel().getCursor().getDir());
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
            semaphore.setCreationDir(presenter.getModel().getCursor().getDir());
            presenter.getModel().addSemaphore(semaphore);
        }
    }

    void manageStationSensor() {
        Point position = presenter.getModel().getCursor().getPosition();
        Track track = presenter.getModel().getRailMap().getTrackAt(position.getX(), position.getY());
        if (track != null) {
            Sensor sensor = track.getSensor();
            if (sensor != null && sensor instanceof Station) {
                track.setSensor(null);
                presenter.getModel().removeStation((Station) sensor);
            } else if (sensor == null) {
                // BLOCK if building on industry
                Integer terrainAtPos = presenter.getModel().getGroundMap().getValueAt(position);
                if (terrainAtPos != null && terrainAtPos >= 10 && terrainAtPos <= 29) {
                    return;
                }

                // Direction validation: cursor must be aligned with track
                letrain.map.Dir cursorDir = presenter.getModel().getCursor().getDir();
                if (track.getDir(cursorDir) == null && track.getDir(cursorDir.inverse()) == null) {
                    return;
                }

                Station station = new Station(presenter.getModel().nextStationId());
                station.setTrack(track);
                station.setCreationDir(presenter.getModel().getCursor().getDir());
                station.setSideDir(presenter.getModel().getCursor().getDir().turnRight().turnRight());
                Integer foundTerrain = presenter.getModel().getGroundMap().findClosestIndustry(position, 5);

                // If found, count density for THAT type
                if (foundTerrain != null) {
                    int densityCount = presenter.getModel().getGroundMap().countIndustryDensity(position, 5,
                            foundTerrain);
                    station.setCargoType(letrain.track.CargoTypes.IndustryMapper.getCargoForTerrain(foundTerrain));
                    station.setRole(letrain.track.CargoTypes.IndustryMapper.getRoleForTerrain(foundTerrain));
                    station.setIndustryCount(densityCount);
                    if (station.getRole() == CargoTypes.StationRole.PRODUCER) {
                        station.setStorage(50);
                    }
                } else {
                    station.setCargoType(CargoTypes.NONE);
                    station.setRole(CargoTypes.StationRole.GENERIC);
                }

                track.setSensor(station);
                presenter.getModel().addStation(station);
            }
        }
    }

    private void reset() {
        degreesOfRotation = 0;
        dir = presenter.getModel().getCursor().getDir();
        Point actualCursorPosition = presenter.getModel().getCursor().getPosition();

        // Check if we can resume from oldTrack
        boolean canResume = false;
        if (oldTrack != null && oldTrack.getPosition() != null) {
            double dist = Point.distance(oldTrack.getPosition(), actualCursorPosition);
            if (dist <= 1.5) { // Adjacent (ortho or diag)
                canResume = true;
            }
        }

        if (canResume) {
            oldDir = actualCursorPosition.locate(oldTrack.getPosition());
            if (oldDir == null) {
                // If locate returns null, it means we are on the same tile.
                // We cannot resume drawing a new track piece on the same tile.
                oldTrack = null;
                oldDir = dir;
                canResume = false;
            } else {
                oldGroundType = presenter.getModel().getGroundMap().getValueAt(oldTrack.getPosition());
            }
        }

        if (!canResume) {
            oldTrack = null;
            oldDir = dir;
            oldGroundType = null;
        }

        reversed = false;
    }

    void removeTrack(boolean moveCursor) {
        Point position = presenter.getModel().getCursor().getPosition();
        RailTrack track = presenter.getModel().getRailMap().getTrackAt(position.getX(), position.getY());
        if (track != null) {
            if (track.getSensor() != null) {
                if (track.getSensor() instanceof letrain.track.Station) {
                    presenter.getModel().removeStation((letrain.track.Station) track.getSensor());
                } else {
                    presenter.getModel().removeSensor(track.getSensor());
                }
            }
            presenter.getModel().getRailMap().removeTrack(position);
        }
        if (presenter.getModel().getForks().contains(track)) {
            presenter.getModel().getForks().remove(track);
        }

        if (moveCursor) {
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
    }

    void makeTracks() {
        if (makingTracks) {
            caterpillarCounter = 20;
            if (isQuantifierPending()) {
                if (isTrackConstructionPending()) {
                    showAnimation();
                    decrementTrackConstructionTime();
                } else {
                    TrackType type = detectTrackType();
                    if (type == null) {
                        makingTracks = false;
                        return;
                    }
                    selectNewTrackType(type);
                    createTrack(type);
                    decrementQuantifierSteps();
                    resetTrackConstructionTime(type);
                }
            }
        }

        if (caterpillarCounter > 0) {
            Point p = presenter.getModel().getCursor().getPosition();
            if (presenter.getAudioController() != null) {
                presenter.getAudioController().setJackhammerActive(true, p.getX(), p.getY());
            }
            caterpillarCounter--;
        } else {
            if (presenter.getAudioController() != null) {
                presenter.getAudioController().setJackhammerActive(false, 0, 0);
            }
        }
    }

    private void resetTrackConstructionTime(TrackType type) {
        startTrackConstruction(type);
    }

    private void decrementQuantifierSteps() {
        presenter.getModel().setQuantifierSteps(presenter.getModel().getQuantifierSteps() - 1);
    }

    public void resetQuantifierSteps() {
        presenter.getModel().setQuantifierSteps(presenter.getModel().getQuantifier());
    }

    private void decrementTrackConstructionTime() {
        decreaseTrackConstructionTime();
    }

    private boolean isTrackConstructionPending() {
        return !isTrackConstructionFinished();
    }

    private boolean isQuantifierPending() {
        return presenter.getModel().getQuantifierSteps() > 0;
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

        // BLOCK construction on industrial tiles
        if (actualGroundType != null && actualGroundType >= 10 && actualGroundType <= 29) {
            return null;
        }

        if (actualGroundType == null || oldGroundType == null) {
            return null;
        }

        int effectiveActualType = actualGroundType;
        int effectiveOldType = (oldGroundType >= 10 && oldGroundType <= 29) ? GroundMap.GROUND : oldGroundType;

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
        if (effectiveActualType == effectiveOldType) {
            // seguimos con el mismo tipo de suelo
            switch (effectiveActualType) {
                case GroundMap.GROUND:
                    type = Presenter.TrackType.NORMAL_TRACK;
                    break;
                case GroundMap.WATER:
                    type = Presenter.TrackType.BRIDGE_TRACK;
                    break;
                case GroundMap.ROCK:
                    type = Presenter.TrackType.TUNNEL_TRACK;
                    break;
            }
        } else {
            if (effectiveOldType == GroundMap.GROUND) {
                // pasamos de GROUND a otro tipo de suelo
                if (effectiveActualType == GroundMap.WATER) {
                    // entramos en agua
                    type = Presenter.TrackType.BRIDGE_GATE_TRACK;
                } else if (effectiveActualType == GroundMap.ROCK) {
                    // entramos en roca
                    type = Presenter.TrackType.TUNNEL_GATE_TRACK;
                }
            } else {
                // salimos de otro tipo de suelo
                if (effectiveActualType != GroundMap.GROUND) {
                    // no podemos pasar de un tipo de suelo a otro sin pasar por GROUND
                    return null;
                }
                if (effectiveOldType == GroundMap.WATER) {
                    // salimos de agua
                    type = Presenter.TrackType.BRIDGE_GATE_TRACK;
                } else if (effectiveOldType == GroundMap.ROCK) {
                    // salimos de roca
                    type = Presenter.TrackType.TUNNEL_GATE_TRACK;
                }
            }
        }
        return type;
    }

    boolean makeTrack(TrackType type) {
        makingTracks = true;

        Point actualCursorPosition = presenter.getModel().getCursor().getPosition();
        Integer actualGroundType = presenter.getModel().getGroundMap().getValueAt(actualCursorPosition);

        // STRICT BLOCK construction on industrial tiles
        if (actualGroundType != null && actualGroundType >= 10 && actualGroundType <= 29) {
            return false;
        }

        if (oldGroundType == null) {
            oldGroundType = actualGroundType;
        }

        if (type == null) {
            type = detectTrackType();
        } else {
            selectNewTrackType(type);
        }

        if (type == null) {
            return false;
        }

        // Obtenemos el track bajo el cursor
        RailTrack track = presenter.getModel().getRailMap().getTrackAt(actualCursorPosition);
        if (track == null) {
            // si no había nada creamos un track normal
            track = createTrackOfSelectedType();
        } else {
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
        if (oldDir != null && dir != null) {
            track.addRoute(oldDir, dir);
        }
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
        fork.setCreationDir(presenter.getModel().getCursor().getDir());
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

    public void setCursorPage(Page page) {
        Point oldPos = presenter.getModel().getCursor().getPosition();
        Point newPos = oldPos.setPage(page);
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
