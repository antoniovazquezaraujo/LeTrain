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
import letrain.vehicle.Cursor;
import letrain.vehicle.Cursor.CursorMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RailTrackMaker {
    private static final Logger log = LoggerFactory.getLogger(RailTrackMaker.class);
    private Presenter.TrackType newTrackType = Presenter.TrackType.NORMAL_TRACK;
    private int degreesOfRotation = 0;
    private Dir dir = Dir.E;
    Track oldTrack;
    Dir oldDir;
    boolean reversed = false;
    boolean makingTracks = false;
    private boolean wasRemoving = false;
    private int caterpillarCounter = 0;
    private boolean quantifierReset = true;
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
                    caterpillarCounter = 5;

                } else if (keyEvent.isCtrlDown()) {
                    presenter.getModel().getCursor().setMode(Cursor.CursorMode.ERASING);
                    removeTrack(true);
                    makingTracks = false;
                    wasRemoving = false;
                    caterpillarCounter = 5;
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
                    quantifierReset = true;
                } else if (keyEvent.getCharacter() == 'w' || keyEvent.getCharacter() == 'W') {
                    manageStationSensor();
                } else if (keyEvent.getCharacter() >= '0' && keyEvent.getCharacter() <= '9') {
                    if (keyEvent.getCharacter() == '0' && presenter.getModel().getQuantifier() == 0) {
                        presenter.getModel().setShowId(true);
                    } else {
                        if (quantifierReset) {
                            presenter.getModel().setQuantifier(keyEvent.getCharacter() - '0');
                            quantifierReset = false;
                        } else {
                            presenter.getModel().setQuantifier(
                                    presenter.getModel().getQuantifier() * 10 + (keyEvent.getCharacter() - '0'));
                        }
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
                    caterpillarCounter = 5;
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
            case Delete:
                manageSpeedSignal();
                break;
            default:
                break;
        }

    }

    private void createStation() {
        // Obsolete
    }

    void manageSpeedSignal() {
        Point position = presenter.getModel().getCursor().getPosition();
        Track track = presenter.getModel().getRailMap().getTrackAt(position.getX(), position.getY());
        if (track != null) {
            Sensor sensor = track.getSensor();
            if (sensor != null && sensor instanceof letrain.track.SpeedSignal) {
                presenter.getModel().removeSensor(sensor);
            } else if (sensor == null) {
                letrain.track.SpeedSignal speedSignal = new letrain.track.SpeedSignal(presenter.getModel().nextSensorId(), presenter.getModel().getCursor().getDir(), 3, true);
                speedSignal.setTrack(track);
                presenter.getModel().addSensor(speedSignal);
            }
        }
    }

    void manageSensor() {
        Point position = presenter.getModel().getCursor().getPosition();
        Track track = presenter.getModel().getRailMap().getTrackAt(position.getX(), position.getY());
        if (track != null) {
            Sensor sensor = track.getSensor();
            if (sensor != null) {
                presenter.getModel().removeSensor(sensor);
            } else {
                sensor = new Sensor(presenter.getModel().nextSensorId());
                sensor.setTrack(track);
                sensor.setCreationDir(presenter.getModel().getCursor().getDir());
                presenter.getModel().addSensor(sensor);
                track.setSensor(sensor);
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

                presenter.getModel().addStation(station);
                track.setSensor(station);
            }
        }
    }

    private void reset() {
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
                degreesOfRotation = 0;
            } else {
                oldGroundType = presenter.getModel().getGroundMap().getValueAt(oldTrack.getPosition());
                // ADR-005 / Infrastructure rule: Initialize rotation degrees based on entry angle
                degreesOfRotation = oldDir.inverse().angularDistance(dir);
            }
        }

        if (!canResume) {
            oldTrack = null;
            oldDir = dir;
            oldGroundType = null;
            degreesOfRotation = 0;
        }

        reversed = false;
    }

    void removeTrack(boolean moveCursor) {
        Point position = presenter.getModel().getCursor().getPosition();
        RailTrack track = presenter.getModel().getRailMap().getTrackAt(position.getX(), position.getY());
        if (track != null) {
            // ADR-005: Prohibido modificar o eliminar raíles con vehículos encima
            if (track.getLinker() != null) {
                log.warn("Cannot remove occupied track at {}", position);
                return;
            }
            presenter.getModel().removeTrack(position);
        }

        if (moveCursor) {
            cursorForward();
        }
    }

    void makeTracks() {
        if (makingTracks) {
            if (isQuantifierPending()) {
                caterpillarCounter = 5;
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
            Point point = presenter.getModel().getCursor().getPosition();
            if (presenter.getAudioController() != null) {
                presenter.getAudioController().setJackhammerActive(true, point.getX(), point.getY());
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
            presenter.getView().ensureVisible(position.getX(), position.getY(), presenter.getView().getCameraDeadzone(), presenter.getView().isCameraPagination());
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
                    // si saltamos de agua a roca sin pasar por GROUND no dejamos
                    return null;
                }
                // pasamos de otro tipo de suelo a GROUND
                type = Presenter.TrackType.NORMAL_TRACK;
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
            if (oldTrack != null && type == Presenter.TrackType.NORMAL_TRACK) {
                if (oldTrack.getClass().equals(letrain.track.rail.TunnelRailTrack.class)) {
                    convertOldTrackToGate(Presenter.TrackType.TUNNEL_GATE_TRACK);
                } else if (oldTrack.getClass().equals(letrain.track.rail.BridgeRailTrack.class)) {
                    convertOldTrackToGate(Presenter.TrackType.BRIDGE_GATE_TRACK);
                }
            }
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
        // la vieja dir y la nueva.
        // REGLA DE LOS 45 GRADOS: Prohibido curvas de más de 1 paso angular.
        if (oldDir != null && dir != null) {
            if (Math.abs(oldDir.inverse().angularDistance(dir)) > 1) {
                log.warn("Illegal rail2 curvature attempted: > 45 degrees. Aborting placement.");
                return false;
            }
            track.addRoute(oldDir, dir);
        }
        track.setPosition(actualCursorPosition);
        presenter.getModel().addTrack(actualCursorPosition, track);
        presenter.getModel().getEconomyManager().onRailTrackConstructed(newTrackType);
        if (canBeAFork(track, oldDir, dir)) {
            RailTrack trackToSubstitute = track;
            final ForkRailTrack fork = createForkRailTrack(actualCursorPosition, trackToSubstitute);
            addRoutesToFork(trackToSubstitute, fork);
            fork.setNormalRoute();
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
        presenter.getModel().removeTrack(track1.getPosition());
        presenter.getModel().addTrack(track1.getPosition(), fork);
    }

    ForkRailTrack createForkRailTrack(Point cursorPosition, RailTrack track) {
        final ForkRailTrack fork = new ForkRailTrack(presenter.getModel().nextForkId());
        fork.setPosition(cursorPosition);
        fork.setCreationDir(presenter.getModel().getCursor().getDir());
        return fork;
    }

    private void convertOldTrackToGate(Presenter.TrackType gateType) {
        if (oldTrack == null) return;
        RailTrack newGate;
        if (gateType == Presenter.TrackType.TUNNEL_GATE_TRACK) {
            newGate = new letrain.track.rail.TunnelGateRailTrack();
        } else {
            newGate = new letrain.track.rail.BridgeGateRailTrack();
        }
        newGate.setPosition(oldTrack.getPosition());
        
        final letrain.map.Router router = oldTrack.getRouter();
        router.forEach(t -> {
            newGate.addRoute(t.getKey(), t.getValue());
        });
        
        presenter.getModel().removeTrack(oldTrack.getPosition());

        for (Dir d : Dir.values()) {
            Track connected = oldTrack.getConnected(d);
            if (connected != null) {
                newGate.connect(d, connected);
                connected.connect(d.inverse(), newGate);
            }
        }
        
        presenter.getModel().addTrack(oldTrack.getPosition(), newGate);
        oldTrack = newGate;
    }

    private void addRoutesToFork(RailTrack track, final ForkRailTrack fork) {
        final Router router = track.getRouter();
        router.forEach(t -> {
            fork.addRoute(t.getKey(), t.getValue());
        });
    }

    void addTrackConnectionsToFork(RailTrack track, final ForkRailTrack fork) {
        for (Dir dir : Dir.values()) {
            if (track.getConnected(dir) != null) {
                Track connectedTrack = track.getConnected(dir);
                connectedTrack.disconnect(dir.inverse());
                connectedTrack.connect(dir.inverse(), fork);
                fork.connect(dir, connectedTrack);
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
        Dir d = presenter.getModel().getCursor().getDir();
        if (!reversed) {
            newPos.move(d, 1);
        } else {
            newPos.move(d.inverse());
        }
        updateCursorPosition(newPos);
        
        CursorMode mode = presenter.getModel().getCursor().getMode();
        if (!makingTracks && (mode == CursorMode.MOVING || mode == CursorMode.ERASING)) {
            RailTrack nextTrack = presenter.getModel().getRailMap().getTrackAt(newPos);
            if (nextTrack != null) {
                Dir entryDir = (!reversed) ? d.inverse() : d;
                Dir exitDir = nextTrack.getDir(entryDir);
                if (exitDir != null) {
                    Dir newDir = (!reversed) ? exitDir : exitDir.inverse();
                    presenter.getModel().getCursor().setDir(newDir);
                    this.dir = newDir;
                }
            }
        }
        
        Point position = presenter.getModel().getCursor().getPosition();
        presenter.getView().ensureVisible(position.getX(), position.getY(), presenter.getView().getCameraDeadzone(), presenter.getView().isCameraPagination());
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
        Point offset = presenter.getView().getScrollOffset();
        presenter.getView().setScrollOffset(new Point(offset.getX(), offset.getY() + presenter.getView().getRows()));
        varyCursorPosition(new Point(0, 1 * presenter.getView().getRows()));
        presenter.getView().clear();
    }

    void mapPageLeft() {
        presenter.getView().clear();
        Point offset = presenter.getView().getScrollOffset();
        presenter.getView().setScrollOffset(new Point(offset.getX() - presenter.getView().getCols(), offset.getY()));
        varyCursorPosition(new Point((-1 * presenter.getView().getCols()), 0));
        presenter.getView().clear();
    }

    void mapPageUp() {
        presenter.getView().clear();
        Point offset = presenter.getView().getScrollOffset();
        presenter.getView().setScrollOffset(new Point(offset.getX(), offset.getY() - presenter.getView().getRows()));
        varyCursorPosition(new Point(0, -1 * presenter.getView().getRows()));
        presenter.getView().clear();
    }

    void mapPageRight() {
        presenter.getView().clear();
        Point offset = presenter.getView().getScrollOffset();
        presenter.getView().setScrollOffset(new Point(offset.getX() + presenter.getView().getCols(), offset.getY()));
        varyCursorPosition(new Point((1 * presenter.getView().getCols()), 0));
        presenter.getView().clear();
    }

}
