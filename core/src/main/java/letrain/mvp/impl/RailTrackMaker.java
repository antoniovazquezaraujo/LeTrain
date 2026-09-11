package letrain.mvp.impl;

import letrain.mvp.input.InputEvent;
import letrain.mvp.input.KeyType;
import java.util.Map;
import letrain.ground.GroundMap;
import letrain.map.Dir;
import letrain.map.Page;
import letrain.map.Point;
import letrain.map.Router;
import letrain.map.impl.SimpleRouter;
import letrain.mvp.Presenter;
import letrain.mvp.Presenter.TrackType;
import letrain.track.RailSemaphore;
import letrain.track.Sensor;
import letrain.track.Station;
import letrain.track.Track;
import letrain.track.rail.ForkRailTrack;
import letrain.track.rail.RailTrack;
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
    public boolean makingTracks = false;
    private boolean wasRemoving = false;
    private int caterpillarCounter = 0;
    private boolean quantifierReset = true;
    Presenter presenter;
    Point lastCursorPosition = null;
    Integer oldGroundType = null;
    int trackConstructionTimeCounter = 0;

    /**
     * True while a console turtle sequence (write/move/del via {@code TurtleBuilder}) is running.
     * Console lines are already recorded whole by the presenter funnel, so per-tile keyboard
     * recording must be suppressed while the console drives this maker.
     */
    private boolean journalSuppressed = false;

    /** Toggled by the console turtle builder around its sequences. */
    public void setJournalSuppressed(boolean suppressed) {
        this.journalSuppressed = suppressed;
    }

    /**
     * Records a keyboard edit as a canonical, self-positioned DSL command (ADR-020 item 3). Only
     * fires while pause-editing is on (undo history active) and the edit is keyboard-driven, not a
     * console turtle sequence. The action is prefixed with the absolute cursor position and facing
     * captured at the moment of the mutation, so a replay is deterministic.
     */
    private void journalKeyboardEdit(Point position, Dir dir, String action) {
        journalKeyboardEdit(position, dir, action, null);
    }

    /**
     * Like {@link #journalKeyboardEdit(Point, Dir, String)}, but also records the {@code resumeFrom}
     * origin when the placed piece continued the previous rail (chaining). Without it, a slice
     * replayed after a checkpoint restore would start the fresh maker with no {@code oldTrack} and
     * lay that piece disconnected/wrong.
     */
    private void journalKeyboardEdit(Point position, Dir dir, String action, Point resumeFrom) {
        if (journalSuppressed || presenter == null) {
            return;
        }
        letrain.mvp.Model model = presenter.getModel();
        if (model == null) {
            return;
        }
        String text = "go " + position.getX() + "," + position.getY() + "; face "
                + dir.name().toLowerCase() + "; " + action + ";";
        // Only while the world is frozen in edit mode: the command journal (ADR-020 item 2) and the
        // undo history (item 3) must capture the same edits, in lockstep.
        if (!model.isSimulationPaused()) {
            return;
        }
        letrain.command.CommandJournal journal = model.getCommandJournal();
        if (journal != null && journal.isRecording()) {
            journal.record(text);
        }
        letrain.command.UndoRedoHistory history = presenter.getUndoRedoHistory();
        if (history == null) {
            return;
        }
        history.record(text, resumeFrom);
    }

    /**
     * The position of the rail piece this maker's last successful build left {@code oldTrack}
     * pointing at, when it is adjacent to {@code cell} (i.e. the next piece would chain from it).
     * Null when the build started a disconnected piece.
     */
    private Point resumeOriginAdjacentTo(Point cell) {
        if (oldTrack == null || oldTrack.getPosition() == null) {
            return null;
        }
        return Point.distance(oldTrack.getPosition(), cell) <= 1.5
                ? new Point(oldTrack.getPosition())
                : null;
    }

    /**
     * Re-seeds this maker so the next build continues the rail {@code predecessor} instead of
     * starting a disconnected piece. Used by the undo/redo slice replay right after a checkpoint
     * restore, when the maker was recreated fresh and its transient chaining state was lost. {@code
     * reset()} (run by the turtle builder before each sequence) recomputes the actual resume
     * direction/ground from this track and the cursor.
     */
    public void resumeChainFrom(Track predecessor) {
        this.oldTrack = predecessor;
    }

    public void startTrackConstruction(TrackType type) {
        if (type == null) {
            this.trackConstructionTimeCounter = 0;
            return;
        }
        if (presenter == null) {
            this.trackConstructionTimeCounter = 0;
            return;
        }
        // Paused editing (ADR-020): construction is instantaneous, so bridges/tunnels skip their
        // construction delay.
        boolean paused = presenter.getModel().isSimulationPaused();
        this.trackConstructionTimeCounter = paused ? 0
                : presenter.getModel().getEconomyManager().getConstructionDelay(type);
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

    public void onKeyUp(InputEvent keyEvent) {
        if (!keyEvent.isShiftDown() && !keyEvent.isCtrlDown()) {
            caterpillarCounter = 0;
            makingTracks = false;
        }
    }

    public void onChar(InputEvent keyEvent) {
        if (keyEvent.getKeyType() == KeyType.Character) {
            Character c = keyEvent.getCharacter();
            if (c != null) {
                switch (c) {
                    case 'k':
                    case 'K':
                        keyEvent = new InputEvent(KeyType.ArrowUp, null, keyEvent.isCtrlDown(), keyEvent.isAltDown(), c == 'K' || keyEvent.isShiftDown());
                        break;
                    case 'j':
                    case 'J':
                        keyEvent = new InputEvent(KeyType.ArrowDown, null, keyEvent.isCtrlDown(), keyEvent.isAltDown(), c == 'J' || keyEvent.isShiftDown());
                        break;
                    case 'h':
                    case 'H':
                        keyEvent = new InputEvent(KeyType.ArrowLeft, null, keyEvent.isCtrlDown(), keyEvent.isAltDown(), c == 'H' || keyEvent.isShiftDown());
                        break;
                    case 'l':
                    case 'L':
                        keyEvent = new InputEvent(KeyType.ArrowRight, null, keyEvent.isCtrlDown(), keyEvent.isAltDown(), c == 'L' || keyEvent.isShiftDown());
                        break;
                }
            }
        }
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
                    if (presenter.getModel().getCursor().getMode() != Cursor.CursorMode.MAKING_TRACKS) {
                        presenter.getModel().getCursor().setMode(Cursor.CursorMode.DRAWING);
                    }
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
                    if (keyEvent.getCharacter() == '0'
                            && presenter.getModel().getQuantifier() == 0) {
                        presenter.getModel().setShowId(true);
                    } else {
                        if (quantifierReset) {
                            presenter.getModel().setQuantifier(keyEvent.getCharacter() - '0');
                            quantifierReset = false;
                        } else {
                            presenter.getModel()
                                    .setQuantifier(presenter.getModel().getQuantifier() * 10
                                            + (keyEvent.getCharacter() - '0'));
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


    void manageSpeedSignal() {
        Point position = presenter.getModel().getCursor().getPosition();
        Track track =
                presenter.getModel().getRailMap().getTrackAt(position.getX(), position.getY());
        if (track != null) {
            letrain.track.TrackComponent component = track.getComponent();
            if (component instanceof letrain.track.SpeedSignal) {
                presenter.getModel().removeSensor((letrain.track.SpeedSignal) component);
                journalKeyboardEdit(position, presenter.getModel().getCursor().getDir(), "del sg");
            } else if (component == null) {
                letrain.track.SpeedSignal speedSignal =
                        new letrain.track.SpeedSignal(presenter.getModel().nextSpeedSignalId(),
                                presenter.getModel().getCursor().getDir(), 3, true);
                speedSignal.setTrack(track);
                presenter.getModel().addSensor(speedSignal);
                journalKeyboardEdit(position, presenter.getModel().getCursor().getDir(), "new sg");
            }
        }
    }

    void manageSensor() {
        Point position = presenter.getModel().getCursor().getPosition();
        Track track =
                presenter.getModel().getRailMap().getTrackAt(position.getX(), position.getY());
        if (track != null) {
            letrain.track.TrackComponent component = track.getComponent();
            if (component instanceof letrain.track.Sensor
                    && !(component instanceof Station)
                    && !(component instanceof letrain.track.SpeedSignal)
                    && !(component instanceof RailSemaphore)) {
                presenter.getModel().removeSensor((letrain.track.Sensor) component);
                journalKeyboardEdit(position, presenter.getModel().getCursor().getDir(), "del sn");
            } else if (component == null) {
                Sensor sensor = new Sensor(presenter.getModel().nextSensorId());
                sensor.setTrack(track);
                sensor.setCreationDir(presenter.getModel().getCursor().getDir());
                presenter.getModel().addSensor(sensor);
                track.setComponent(sensor);
                journalKeyboardEdit(position, presenter.getModel().getCursor().getDir(), "new sn");
            }
        }
    }

    void manageSemaphore() {
        Point position = presenter.getModel().getCursor().getPosition();
        Track track =
                presenter.getModel().getRailMap().getTrackAt(position.getX(), position.getY());
        if (track != null) {
            letrain.track.TrackComponent component = track.getComponent();
            if (component instanceof RailSemaphore) {
                presenter.getModel().removeSemaphore((RailSemaphore) component);
                journalKeyboardEdit(position, presenter.getModel().getCursor().getDir(), "del sm");
            } else if (component == null) {
                RailSemaphore semaphore =
                        new RailSemaphore(presenter.getModel().nextSemaphoreId());
                semaphore.setCreationDir(presenter.getModel().getCursor().getDir());
                semaphore.setTrack(track);
                presenter.getModel().addSemaphore(semaphore);
                journalKeyboardEdit(position, presenter.getModel().getCursor().getDir(), "new sm");
            }
        }
    }

    void manageStationSensor() {
        Point position = presenter.getModel().getCursor().getPosition();
        Track track =
                presenter.getModel().getRailMap().getTrackAt(position.getX(), position.getY());
        if (track != null) {
            letrain.track.TrackComponent component = track.getComponent();
            if (component instanceof Station) {
                presenter.getModel().removeStation((Station) component);
                journalKeyboardEdit(position, presenter.getModel().getCursor().getDir(), "del st");
            } else if (component == null) {
                // Allow building on industry (removed the block)
                Integer terrainAtPos = presenter.getModel().getGroundMap().getValueAt(position);

                // Direction validation: cursor must be aligned with track
                letrain.map.Dir cursorDir = presenter.getModel().getCursor().getDir();
                if (track.getDir(cursorDir) == null && track.getDir(cursorDir.inverse()) == null) {
                    return;
                }

                Station station = new Station(presenter.getModel().nextStationId());
                station.setTrack(track);
                station.setCreationDir(presenter.getModel().getCursor().getDir());
                station.setSideDir(
                        presenter.getModel().getCursor().getDir().turnRight().turnRight());
                presenter.getModel().applyStationRoleByIndustry(station, position);

                presenter.getModel().addStation(station);
                track.setComponent(station);
                journalKeyboardEdit(position, presenter.getModel().getCursor().getDir(), "new st");
            }
        }
    }

    public void reset() {
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
                oldGroundType =
                        presenter.getModel().getGroundMap().getValueAt(oldTrack.getPosition());
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

    public void removeTrack(boolean moveCursor) {
        Point position = presenter.getModel().getCursor().getPosition();
        RailTrack track =
                presenter.getModel().getRailMap().getTrackAt(position.getX(), position.getY());
        if (track != null) {
            // ADR-005: Prohibido modificar o eliminar raíles con vehículos encima
            if (track.getLinker() != null) {
                log.warn("Cannot remove occupied track at {}", position);
                return;
            }
            presenter.getModel().removeTrack(position);
            // Keyboard erase of one tile, journaled as a canonical self-positioned delete.
            journalKeyboardEdit(new Point(position), presenter.getModel().getCursor().getDir(),
                    "del 1");
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
                    TrackType type = detectTrackType();
                    boolean needsDelay = type == Presenter.TrackType.TUNNEL_TRACK
                            || type == Presenter.TrackType.BRIDGE_TRACK;
                    if (needsDelay) {
                        showAnimation();
                        float delay = presenter.getModel().getEconomyManager().getConstructionDelay(type);
                        float progress = delay > 0 ? (1.0f - ((float) trackConstructionTimeCounter / delay)) : 1.0f;
                        presenter.getModel().getCursor().setProgress(progress);
                        decrementTrackConstructionTime();
                    } else {
                        // Gate / normal track: skip remaining timer immediately
                        trackConstructionTimeCounter = 0;
                    }
                } else {
                    TrackType type = detectTrackType();
                    if (type == null) {
                        makingTracks = false;
                        presenter.getModel().getCursor().setMode(letrain.vehicle.Cursor.CursorMode.DRAWING);
                        presenter.getModel().getCursor().setProgress(0f);
                        return;
                    }
                    selectNewTrackType(type);
                    createTrack(type);
                    decrementQuantifierSteps();
                    resetTrackConstructionTime(type);
                    if (!isQuantifierPending()) {
                        presenter.getModel().getCursor().setMode(letrain.vehicle.Cursor.CursorMode.DRAWING);
                        presenter.getModel().getCursor().setProgress(0f);
                    }
                }
            }
        }

        if (caterpillarCounter > 0) {
            Point point = presenter.getModel().getCursor().getPosition();
            if (presenter.getAudioController() != null) {
                presenter.getAudioController().setJackhammerActive(true, point.getX(),
                        point.getY());
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
        // The tile being worked on is one step BEHIND the cursor
        Point pos = new Point(presenter.getModel().getCursor().getPosition());
        letrain.map.Dir dir = presenter.getModel().getCursor().getDir();
        pos.move(dir.inverse(), 1);
        presenter.getModel().getCursor().setConstructionPosition(pos);
    }

    public boolean createTrack(TrackType type) {
        degreesOfRotation = 0;
        if (makeTrack(type)) {
            Point position = presenter.getModel().getCursor().getPosition();
            presenter.getView().ensureVisible(position.getX(), position.getY(),
                    presenter.getView().getCameraDeadzone(),
                    presenter.getView().isCameraPagination());
            return true;
        }
        return false;
    }

    public TrackType detectTrackType() {
        Point actualCursorPosition = presenter.getModel().getCursor().getPosition();
        Integer actualGroundType =
                presenter.getModel().getGroundMap().getValueAt(actualCursorPosition);

        if (oldGroundType == null) {
            oldGroundType = actualGroundType;
        }

        if (actualGroundType == null || oldGroundType == null) {
            return null;
        }

        int effectiveActualType =
                (actualGroundType >= 10 && actualGroundType <= 29) ? GroundMap.GROUND : actualGroundType;
        int effectiveOldType =
                (oldGroundType >= 10 && oldGroundType <= 29) ? GroundMap.GROUND : oldGroundType;

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

    public boolean makeTrack(TrackType type) {
        makingTracks = true;

        Point actualCursorPosition = presenter.getModel().getCursor().getPosition();
        Integer actualGroundType =
                presenter.getModel().getGroundMap().getValueAt(actualCursorPosition);

        if (oldGroundType == null) {
            oldGroundType = actualGroundType;
        }

        if (type == null) {
            type = detectTrackType();
        }

        if (type == null) {
            return false;
        }

        // Keep the *new* selected type in sync with the type actually being placed. The UI tick
        // path already calls selectNewTrackType(type) before createTrack(type), but the turtle
        // console/headless path calls createTrack(null) so makeTrack detects the terrain-based
        // type itself (NORMAL / BRIDGE* / TUNNEL*). Selecting here makes both paths produce the
        // same rail kind (bridges over water, tunnels in rock) instead of silently laying NORMAL
        // track, and keeps economy counters consistent.
        selectNewTrackType(type);

        // Obtenemos el track bajo el cursor
        RailTrack track = presenter.getModel().getRailMap().getTrackAt(actualCursorPosition);
        if (track == null) {
            // si no había nada creamos un track normal
            track = createTrackOfSelectedType();
            if (oldTrack != null && type == Presenter.TrackType.NORMAL_TRACK) {
                if (oldTrack instanceof letrain.track.rail.RailTrack && ((letrain.track.rail.RailTrack)oldTrack).getVisualType() == letrain.track.rail.RailTrack.VisualType.TUNNEL) {
                    convertOldTrackToGate(Presenter.TrackType.TUNNEL_GATE_TRACK);
                } else if (oldTrack instanceof letrain.track.rail.RailTrack && ((letrain.track.rail.RailTrack)oldTrack).getVisualType() == letrain.track.rail.RailTrack.VisualType.BRIDGE) {
                    convertOldTrackToGate(Presenter.TrackType.BRIDGE_GATE_TRACK);
                }
            }
        } else {
            int mappedGroundType = (actualGroundType != null && actualGroundType >= 10 && actualGroundType <= 29) ? GroundMap.GROUND : actualGroundType;
            if (mappedGroundType != GroundMap.GROUND) {
                // si la dirección del cursor es distinta de la del track actual retornamos
                if (track != null && !track.canExit(presenter.getModel().getCursor().getDir())) {
                    return false;
                }
            }
            // si había un fork, validamos que la ruta que intentamos trazar ya existe en el fork
            if (ForkRailTrack.class.isAssignableFrom(track.getClass())) {
                boolean routeExists = false;
                if (oldDir != null && dir != null) {
                    letrain.map.Router r = track.getRouter();
                    if (r instanceof letrain.map.impl.ForkRouter) {
                        letrain.map.impl.ForkRouter fr = (letrain.map.impl.ForkRouter) r;
                        letrain.utils.Pair<letrain.map.Dir, letrain.map.Dir> orig = fr.getOriginalRoute();
                        letrain.utils.Pair<letrain.map.Dir, letrain.map.Dir> alt = fr.getAlternativeRoute();
                        if (orig != null && ((orig.getKey() == oldDir && orig.getValue() == dir) || (orig.getKey() == dir && orig.getValue() == oldDir))) routeExists = true;
                        if (alt != null && ((alt.getKey() == oldDir && alt.getValue() == dir) || (alt.getKey() == dir && alt.getValue() == oldDir))) routeExists = true;
                    }
                }
                if (!routeExists) {
                    return false;
                }
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
            if (!ForkRailTrack.class.isAssignableFrom(track.getClass())) {
                track.addRoute(oldDir, dir);
            }
        }
        track.setPosition(actualCursorPosition);
        presenter.getModel().addTrack(actualCursorPosition, track);
        presenter.getModel().getEconomyManager().onRailTrackConstructed(newTrackType);
        if (!ForkRailTrack.class.isAssignableFrom(track.getClass()) && canBeAFork(track, oldDir, dir)) {
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

        // Keyboard build of one tile, journaled as a canonical self-positioned write. Reverse
        // building is not expressible as a plain "write 1" so it is skipped (rare in the UI).
        if (!reversed) {
            journalKeyboardEdit(new Point(actualCursorPosition),
                    presenter.getModel().getCursor().getDir(), "write 1",
                    resumeOriginAdjacentTo(actualCursorPosition));
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
        if (oldTrack == null) {
            return;
        }
        RailTrack newGate = new RailTrack();
        if (gateType == Presenter.TrackType.TUNNEL_GATE_TRACK) {
            newGate.setVisualType(letrain.track.rail.RailTrack.VisualType.TUNNEL_GATE);
        } else {
            newGate.setVisualType(letrain.track.rail.RailTrack.VisualType.BRIDGE_GATE);
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
        RailTrack track = new RailTrack();
        switch (newTrackType) {
            case STATION_TRACK:
                track.setVisualType(letrain.track.rail.RailTrack.VisualType.STATION);
                break;
            case TUNNEL_GATE_TRACK:
                track.setVisualType(letrain.track.rail.RailTrack.VisualType.TUNNEL_GATE);
                break;
            case TUNNEL_TRACK:
                track.setVisualType(letrain.track.rail.RailTrack.VisualType.TUNNEL);
                break;
            case BRIDGE_GATE_TRACK:
                track.setVisualType(letrain.track.rail.RailTrack.VisualType.BRIDGE_GATE);
                break;
            case BRIDGE_TRACK:
                track.setVisualType(letrain.track.rail.RailTrack.VisualType.BRIDGE);
                break;
            default:
                break;
        }
        return track;
    }

    public boolean canBeAFork(Track track, Dir from, Dir to) {
        final Router r = new SimpleRouter();
        track.getRouter().forEach(t -> r.addRoute(t.getKey(), t.getValue()));
        r.addRoute(from, to);
        return r.getNumRoutes() == 3;
    }

    public void cursorTurnRight() {
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

    public void cursorTurnLeft() {
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

    public void cursorForward() {
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
        presenter.getView().ensureVisible(position.getX(), position.getY(),
                presenter.getView().getCameraDeadzone(), presenter.getView().isCameraPagination());
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

    public void cursorBackward() {
        reversed = true;
        cursorForward();
        reversed = false;
    }

    void mapPageDown() {
        presenter.getView().clear();
        Point offset = presenter.getView().getScrollOffset();
        presenter.getView().setScrollOffset(
                new Point(offset.getX(), offset.getY() + presenter.getView().getRows()));
        varyCursorPosition(new Point(0, 1 * presenter.getView().getRows()));
        presenter.getView().clear();
    }

    void mapPageLeft() {
        presenter.getView().clear();
        Point offset = presenter.getView().getScrollOffset();
        presenter.getView().setScrollOffset(
                new Point(offset.getX() - presenter.getView().getCols(), offset.getY()));
        varyCursorPosition(new Point((-1 * presenter.getView().getCols()), 0));
        presenter.getView().clear();
    }

    void mapPageUp() {
        presenter.getView().clear();
        Point offset = presenter.getView().getScrollOffset();
        presenter.getView().setScrollOffset(
                new Point(offset.getX(), offset.getY() - presenter.getView().getRows()));
        varyCursorPosition(new Point(0, -1 * presenter.getView().getRows()));
        presenter.getView().clear();
    }

    void mapPageRight() {
        presenter.getView().clear();
        Point offset = presenter.getView().getScrollOffset();
        presenter.getView().setScrollOffset(
                new Point(offset.getX() + presenter.getView().getCols(), offset.getY()));
        varyCursorPosition(new Point((1 * presenter.getView().getCols()), 0));
        presenter.getView().clear();
    }
}
