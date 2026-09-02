package letrain.mvp.impl.graphic;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import letrain.mvp.input.InputEvent;
import letrain.mvp.input.KeyType;
import letrain.audio.AudioController;
import letrain.map.Dir;
import letrain.map.Point;
import letrain.mvp.Model;
import letrain.mvp.impl.RailTrackMaker;
import letrain.track.CargoTypes;
import letrain.track.RailSemaphore;
import letrain.track.Station;
import letrain.track.rail.RailTrack;
import letrain.vehicle.rail.Linker;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import letrain.vehicle.rail.impl.Wagon;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Encapsula toda la lógica de entrada de la vista 3D. Traduce eventos de LibGDX a InputEvents y
 * gestiona la lógica de negocio asociada a cada modo de juego.
 */
public class Gdx3DInputHandler implements InputProcessor {
    private static final Logger log = LoggerFactory.getLogger(Gdx3DInputHandler.class);

    private final Model model;
    private final GraphicPresenter view;
    private final CameraController cameraController;
    private final RailTrackMaker trackMaker;
    private final AudioController audioController;

    // Multi-digit selection state
    private final com.badlogic.gdx.utils.IntMap<Long> pressedKeys = new com.badlogic.gdx.utils.IntMap<>();
    private static final long INITIAL_REPEAT_DELAY_MS = 400;
    private static final long REPEAT_INTERVAL_MS = 50;

    private int forkIdAccumulator = 0;
    private int semaphoreIdAccumulator = 0;
    private int stationIdAccumulator = 0;
    private int speedSignalId = 0;
    private int locomotiveIdAccumulator = 0;
    private long forkInputTimeout = 0;
    private long semaphoreInputTimeout = 0;
    private long stationInputTimeout = 0;
    private long locomotiveInputTimeout = 0;
    private Locomotive lastCreatedLoco = null;

    public Gdx3DInputHandler(Model model, GraphicPresenter view, CameraController cameraController,
            RailTrackMaker trackMaker, AudioController audioController) {
        this.model = model;
        this.view = view;
        this.cameraController = cameraController;
        this.trackMaker = trackMaker;
        this.audioController = audioController;
    }

    public void update() {
        updateSelectionTimeouts();
        long now = System.currentTimeMillis();
        for (com.badlogic.gdx.utils.IntMap.Entry<Long> entry : pressedKeys.entries()) {
            if (now >= entry.value) {
                entry.value = now + REPEAT_INTERVAL_MS;
                triggerKeyDown(entry.key);
            }
        }
    }

    private void updateSelectionTimeouts() {
        long now = System.currentTimeMillis();
        if (forkInputTimeout > 0 && now > forkInputTimeout) {
            model.selectFork(forkIdAccumulator);
            forkIdAccumulator = 0;
            forkInputTimeout = 0;
        }
        if (semaphoreInputTimeout > 0 && now > semaphoreInputTimeout) {
            model.selectSemaphore(semaphoreIdAccumulator);
            semaphoreIdAccumulator = 0;
            semaphoreInputTimeout = 0;
        }
        if (stationInputTimeout > 0 && now > stationInputTimeout) {
            model.selectStation(stationIdAccumulator);
            stationIdAccumulator = 0;
            stationInputTimeout = 0;
        }
        if (locomotiveInputTimeout > 0 && now > locomotiveInputTimeout) {
            model.selectLocomotive(locomotiveIdAccumulator);
            locomotiveIdAccumulator = 0;
            locomotiveInputTimeout = 0;
        }
    }

    private boolean triggerKeyDown(int keycode) {
        InputEvent keyStroke = translateKeyCode(keycode);
        if (keyStroke != null) {
            boolean ctrlPressed = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                    || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
            boolean altPressed = Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT)
                    || Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT);
            boolean shiftPressed = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                    || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);

            view.onChar(
                    new InputEvent(keyStroke.getKeyType(), keyStroke.getCharacter(), ctrlPressed, altPressed, shiftPressed));
            return true;
        }
        return false;
    }

    @Override
    public boolean keyDown(int keycode) {
        pressedKeys.put(keycode, System.currentTimeMillis() + INITIAL_REPEAT_DELAY_MS);
        return triggerKeyDown(keycode);
    }

    @Override
    public boolean keyUp(int keycode) {
        pressedKeys.remove(keycode);
        InputEvent keyStroke = translateKeyCodeForUp(keycode);
        if (keyStroke != null) {
            boolean ctrlPressed = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                    || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
            boolean shiftPressed = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                    || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);

            view.onKeyUp(new InputEvent(keyStroke.getKeyType(), keyStroke.getCharacter(), ctrlPressed, false, shiftPressed));
            return true;
        }
        return false;
    }

    @Override
    public boolean keyTyped(char character) {
        // 1. Toggle de cámara
        if (character == 'z' || character == 'Z') {
            cameraController.cycleMode(!model.getLocomotives().isEmpty());
            return true;
        }

        // 2. Resto de caracteres -> view.onChar
        boolean ctrlPressed = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
        boolean altPressed = Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT);

        if (!Character.isISOControl(character)) {
            char lower = Character.toLowerCase(character);
            if (lower == 'h' || lower == 'j' || lower == 'k' || lower == 'l') {
                return false; // Already handled in keyDown
            }
            view.onChar(new InputEvent(character, ctrlPressed, altPressed));
            return true;
        }
        return false;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        return false;
    }

    @Override
    public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        return false;
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        return false;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        Stage stage = view.getStage();
        if (stage != null) {
            com.badlogic.gdx.math.Vector2 stageCoords = stage.screenToStageCoordinates(
                    new com.badlogic.gdx.math.Vector2(Gdx.input.getX(), Gdx.input.getY()));
            if (stage.hit(stageCoords.x, stageCoords.y, true) != null) {
                return true; // Bloquear zoom si el ratón está sobre la UI
            }
        }

        cameraController.zoom(amountY);
        return true;
    }

    private InputEvent translateKeyCode(int keycode) {
        switch (keycode) {
                        case Input.Keys.H:
                return new InputEvent(KeyType.Character, 'h', false, false, false);
            case Input.Keys.J:
                return new InputEvent(KeyType.Character, 'j', false, false, false);
            case Input.Keys.K:
                return new InputEvent(KeyType.Character, 'k', false, false, false);
            case Input.Keys.L:
                return new InputEvent(KeyType.Character, 'l', false, false, false);
            case Input.Keys.UP:
                return new InputEvent(KeyType.ArrowUp);
            case Input.Keys.DOWN:
                return new InputEvent(KeyType.ArrowDown);
            case Input.Keys.LEFT:
                return new InputEvent(KeyType.ArrowLeft);
            case Input.Keys.RIGHT:
                return new InputEvent(KeyType.ArrowRight);
            case Input.Keys.ENTER:
                return new InputEvent(KeyType.Enter);
            case Input.Keys.ESCAPE:
                return new InputEvent(KeyType.Escape);
            case Input.Keys.BACKSPACE:
                return new InputEvent(KeyType.Backspace);
            case Input.Keys.FORWARD_DEL:
                return new InputEvent(KeyType.Delete);
            case Input.Keys.HOME:
                return new InputEvent(KeyType.Home);
            case Input.Keys.END:
                return new InputEvent(KeyType.End);
            case Input.Keys.PAGE_UP:
                return new InputEvent(KeyType.PageUp);
            case Input.Keys.PAGE_DOWN:
                return new InputEvent(KeyType.PageDown);
            case Input.Keys.INSERT:
                return new InputEvent(KeyType.Insert);
            case Input.Keys.TAB:
                return new InputEvent(KeyType.Tab);
            case Input.Keys.F1:
                return new InputEvent(KeyType.F1);
            case Input.Keys.F12:
                return new InputEvent(KeyType.F12);
            default:
                return null;
        }
    }

    private InputEvent translateKeyCodeForUp(int keycode) {
        switch (keycode) {
                        case Input.Keys.H:
                return new InputEvent(KeyType.Character, 'h', false, false, false);
            case Input.Keys.J:
                return new InputEvent(KeyType.Character, 'j', false, false, false);
            case Input.Keys.K:
                return new InputEvent(KeyType.Character, 'k', false, false, false);
            case Input.Keys.L:
                return new InputEvent(KeyType.Character, 'l', false, false, false);
            case Input.Keys.UP:
                return new InputEvent(KeyType.ArrowUp);
            case Input.Keys.DOWN:
                return new InputEvent(KeyType.ArrowDown);
            case Input.Keys.LEFT:
                return new InputEvent(KeyType.ArrowLeft);
            case Input.Keys.RIGHT:
                return new InputEvent(KeyType.ArrowRight);
            case Input.Keys.CONTROL_LEFT:
            case Input.Keys.CONTROL_RIGHT:
                return new InputEvent(KeyType.Unknown, true, false, false);
            case Input.Keys.SHIFT_LEFT:
            case Input.Keys.SHIFT_RIGHT:
                return new InputEvent(KeyType.Unknown, false, false, true);
            default:
                return null;
        }
    }

    public void onChar(InputEvent stroke) {
        if (model.getMode() == Model.GameMode.COMMAND) {
            if (stroke.getKeyType() == KeyType.Escape) {
                model.setMode(Model.GameMode.RAILS);
                model.setCommandText("");
                model.setCommandError("");
                return;
            } else if (stroke.getKeyType() == KeyType.Enter) {
                log.info("Execute command: " + model.getCommandText());
                String error = letrain.command.PlayerCommandExecutor.execute(model.getCommandText(), model, file -> view.onSaveGame(file), file -> view.onLoadGame(file), new letrain.command.TurtleDelegate() {
                    @Override public void startSequence() { trackMaker.reset(); trackMaker.makingTracks = false; }
                    @Override public void moveForward() { trackMaker.cursorForward(); }
                    @Override public void buildForward() { trackMaker.createTrack(null); }
                    @Override public void eraseForward() { trackMaker.removeTrack(true); }
                    @Override public void turnLeft() { trackMaker.cursorTurnLeft(); }
                    @Override public void turnRight() { trackMaker.cursorTurnRight(); }
                    @Override public void endSequence() { trackMaker.makingTracks = false; }
                }, (title, msg) -> view.showMessage(title, msg), () -> view.onExitGame());

                if (error != null) {
                    model.setCommandError(error);
                    return;
                }
                model.setMode(Model.GameMode.RAILS);
                model.setCommandText("");
                model.setCommandError("");
                if (cameraController != null) cameraController.forceSnap();
                return;
            } else if (stroke.getKeyType() == KeyType.Backspace) {
                String t = model.getCommandText();
                if (t.length() > 0) {
                    model.setCommandText(t.substring(0, t.length() - 1));
                    model.setCommandError("");
                }
                return;
            } else if (stroke.getKeyType() == KeyType.Character) {
                Character c = stroke.getCharacter();
                if (c != null) {
                    model.setCommandText(model.getCommandText() + c);
                    model.setCommandError("");
                }
                return;
            }
            return; // Ignore other keys in COMMAND mode
        }

        if (getEffectiveKeyType(stroke) == KeyType.Character && stroke.getCharacter() != null && stroke.getCharacter() == ':') {
            if (model.getMode() != Model.GameMode.PROGRAM) {
                model.setMode(Model.GameMode.COMMAND);
                model.setCommandText("");
                model.setCommandError("");
                return;
            }
        }

        if (stroke.getKeyType() == KeyType.F1) {
            log.info("\n" + model.getRailwayGraphReport());
            return;
        }

        // Global Camera Zoom/Rotation (Alt + Arrows)
        if (stroke.isAltDown()) {
            if (getEffectiveKeyType(stroke) == KeyType.ArrowLeft) {
                cameraController.rotateOrbit(-15f);
                return;
            } else if (getEffectiveKeyType(stroke) == KeyType.ArrowRight) {
                cameraController.rotateOrbit(15f);
                return;
            } else if (getEffectiveKeyType(stroke) == KeyType.ArrowUp) {
                cameraController.zoomStep(-1f);
                return;
            } else if (getEffectiveKeyType(stroke) == KeyType.ArrowDown) {
                cameraController.zoomStep(1f);
                return;
            }
        }

        // Global Enter to Menu (matches TerminalPresenter)
        if (getEffectiveKeyType(stroke) == KeyType.Enter) {
            if (model.getMode() == Model.GameMode.LINK
                    || model.getMode() == Model.GameMode.UNLINK) {
                model.setMode(model.getPreviousMode());
                return;
            } else if (model.getMode() != Model.GameMode.DRIVE) {
                lastCreatedLoco = null;
                model.setMode(Model.GameMode.MENU);
                return;
            }
        }

        // Mode Switching Shortcuts
        if (getEffectiveKeyType(stroke) == KeyType.Character && stroke.getCharacter() != ' ') {
            if (model.getMode() != Model.GameMode.TRAINS) {
                switch (stroke.getCharacter()) {
                    case 'r':
                        model.setMode(Model.GameMode.RAILS);
                        return;
                    case 'd':
                        if (!model.getLocomotives().isEmpty()) {
                            model.setMode(Model.GameMode.DRIVE);
                        }
                        return;
                    case 'f':
                        if (!model.getForks().isEmpty()) {
                            model.setMode(Model.GameMode.FORKS);
                        }
                        return;
                    case 'g':
                        if (!model.getSpeedSignals().isEmpty())
                            model.setMode(Model.GameMode.SPEED_SIGNALS);
                        return;
                    case 's':
                        if (!model.getSemaphores().isEmpty())
                            model.setMode(Model.GameMode.SEMAPHORES);
                        return;
                    case 't':
                        if (model.getCursorRailTrack() != null) {
                            lastCreatedLoco = null;
                            model.setMode(Model.GameMode.TRAINS);
                        }
                        return;
                    case 'c':
                        if (model.canEnterLinkMode()) {
                            model.setMode(Model.GameMode.LINK);
                            if (model.getSelectedLocomotive() != null
                                    && model.getSelectedLocomotive().getTrain() != null) {
                                Train train = model.getSelectedLocomotive().getTrain();
                                train.getTrainCouplingManager().resetLinkState(train);
                            }
                        }
                        return;
                    case 'u':
                        if (model.canEnterUnlinkMode()) {
                            model.setMode(Model.GameMode.UNLINK);
                            if (model.getSelectedLocomotive() != null
                                    && model.getSelectedLocomotive().getTrain() != null) {
                                Train train = model.getSelectedLocomotive().getTrain();
                                train.getTrainCouplingManager().resetUnlinkState(train);
                            }
                        }
                        return;
                    case 'e':
                        if (!model.getSensors().isEmpty()) {
                            model.setMode(Model.GameMode.SENSORS);
                        }
                        return;
                    case 'n':
                        if (!model.getStations().isEmpty()) {
                            model.setMode(Model.GameMode.STATIONS);
                        }
                        return;
                    case 'p':
                        model.setMode(Model.GameMode.PROGRAM);
                        view.onGameModeSelected(Model.GameMode.PROGRAM);
                        return;
                    case 'o':
                        handleSnapCursor();
                        return;
                }
            }
        }

        switch (model.getMode()) {
                        case MENU:
                if (stroke.getKeyType() == KeyType.ArrowUp ||
                    stroke.getKeyType() == KeyType.ArrowDown ||
                    stroke.getKeyType() == KeyType.ArrowLeft ||
                    stroke.getKeyType() == KeyType.ArrowRight) {
                    trackMaker.onChar(stroke);
                } else if (stroke.getKeyType() == KeyType.Character) {
                    Character c = stroke.getCharacter();
                    if (c != null && (c == 'h' || c == 'j' || c == 'k' || c == 'l' || c == 'H' || c == 'J' || c == 'K' || c == 'L')) {
                        trackMaker.onChar(stroke);
                    }
                }
                break;
            case RAILS:
                trackMaker.onChar(stroke);
                break;
            case DRIVE:
                handleDriveInput(stroke);
                break;
            case PROGRAM:
                handleProgramInput(stroke);
                break;
            case LINK:
                handleLinkInput(stroke);
                break;
            case UNLINK:
                handleUnlinkInput(stroke);
                break;
            case FORKS:
                handleForkInput(stroke);
                break;
            case SEMAPHORES:
                handleSemaphoreInput(stroke);
                break;
            case SENSORS:
                handleSensorsInput(stroke);
                break;
            case SPEED_SIGNALS:
                handleSpeedSignalsInput(stroke);
                break;
            case STATIONS:
                handleStationInput(stroke);
                break;
            case TRAINS:
                handleTrainsInput(stroke);
                break;
            default:
                break;
        }
    }

    public void onKeyUp(InputEvent stroke) {
        if (model.getMode() == Model.GameMode.RAILS) {
            trackMaker.onKeyUp(stroke);
        }
    }

    private void handleSnapCursor() {
        Point targetPos = null;
        switch (model.getMode()) {
            case DRIVE:
            case LINK:
            case UNLINK:
                if (model.getSelectedLocomotive() != null) {
                    targetPos = model.getSelectedLocomotive().getPosition();
                }
                break;
            case FORKS:
                if (model.getSelectedFork() != null) {
                    targetPos = model.getSelectedFork().getPosition();
                }
                break;
            case SEMAPHORES:
                if (model.getSelectedSemaphore() != null) {
                    targetPos = model.getSelectedSemaphore().getPosition();
                }
                break;
            case SENSORS:
                if (model.getSelectedSensor() != null) {
                    targetPos = model.getSelectedSensor().getPosition();
                }
                break;
            case SPEED_SIGNALS:
                if (model.getSelectedSpeedSignal() != null) {
                    targetPos = model.getSelectedSpeedSignal().getPosition();
                }
                break;
            case STATIONS:
                if (model.getSelectedStation() != null) {
                    targetPos = model.getSelectedStation().getPosition();
                }
                break;
            default:
                break;
        }

        if (targetPos != null) {
            model.getCursor().setPosition(targetPos);
        }
    }


    private KeyType getEffectiveKeyType(InputEvent event) {
        if (event.getKeyType() == KeyType.Character && event.getCharacter() != null) {
            switch (Character.toLowerCase(event.getCharacter())) {
                case 'k': return KeyType.ArrowUp;
                case 'j': return KeyType.ArrowDown;
                case 'h': return KeyType.ArrowLeft;
                case 'l': return KeyType.ArrowRight;
            }
        }
        return event.getKeyType();
    }

    private void handleDriveInput(InputEvent stroke) {
        if (getEffectiveKeyType(stroke) == KeyType.ArrowUp) {
            Locomotive loco = model.getSelectedLocomotive();
            if (loco != null && loco.isEngineOn()
                    && !loco.getTrain().getLogisticsManager().isLoading()) {
                loco.incSpeed();
                // Manual acceleration disengages autopilot
                if (loco.getTrain() != null && loco.getTrain().isAutoMode()) {
                    loco.getTrain().toggleAutoMode();
                }
            }
        } else if (getEffectiveKeyType(stroke) == KeyType.ArrowDown) {
            Locomotive loco = model.getSelectedLocomotive();
            if (loco != null && loco.isEngineOn()
                    && !loco.getTrain().getLogisticsManager().isLoading()) {
                loco.decSpeed();
                if (loco.getTrain() != null && loco.getTrain().isAutoMode()) {
                    loco.getTrain().toggleAutoMode();
                }
            }
        } else if (getEffectiveKeyType(stroke) == KeyType.ArrowLeft) {
            model.selectPrevLocomotive();
        } else if (getEffectiveKeyType(stroke) == KeyType.ArrowRight) {
            model.selectNextLocomotive();
        } else if (getEffectiveKeyType(stroke) == KeyType.Character && stroke.getCharacter() == ' ') {
            if (locomotiveIdAccumulator > 0) {
                model.selectLocomotive(locomotiveIdAccumulator);
                locomotiveIdAccumulator = 0;
                locomotiveInputTimeout = 0;
            }
            if (model.getSelectedLocomotive() != null
                    && model.getSelectedLocomotive().getSpeed() == 0) {
                if (model.getSelectedLocomotive().getTrack() != null) {
                    model.getSelectedLocomotive().toggleReversed();
                }
            }
        } else if (getEffectiveKeyType(stroke) == KeyType.Character
                && Character.isDigit(stroke.getCharacter())) {
            locomotiveIdAccumulator =
                    locomotiveIdAccumulator * 10 + Character.getNumericValue(stroke.getCharacter());
            model.selectLocomotive(locomotiveIdAccumulator);
            locomotiveInputTimeout = System.currentTimeMillis() + 1000;
        } else if (getEffectiveKeyType(stroke) == KeyType.Character
                && (stroke.getCharacter() == 'a' || stroke.getCharacter() == 'A')) {
            // Toggle autopilot
            if (model.getSelectedLocomotive() != null) {
                Train t = model.getSelectedLocomotive().getTrain();
                if (t != null) {
                    t.toggleAutoMode();
                }
            }
        } else if (getEffectiveKeyType(stroke) == KeyType.Backspace) {
            locomotiveIdAccumulator = locomotiveIdAccumulator / 10;
            model.selectLocomotive(locomotiveIdAccumulator);
            locomotiveInputTimeout = System.currentTimeMillis() + 1000;
        } else if (getEffectiveKeyType(stroke) == KeyType.Enter) {
            if (model.getSelectedLocomotive() != null
                    && model.getSelectedLocomotive().getSpeed() == 0) {
                Train selectedTrain = model.getSelectedLocomotive().getTrain();
                if (selectedTrain != null) {
                    Station station = selectedTrain.getLogisticsManager().getStationAtTrain();
                    if (station != null) {
                        if (selectedTrain.getLogisticsManager().isLoading()) {
                            selectedTrain.getLogisticsManager().endLoadUnloadProcess();
                        } else {
                            if (station.getRole() == CargoTypes.StationRole.CONSUMER) {
                                if (!selectedTrain.getLogisticsManager()
                                        .getCapableWagons(station, true).isEmpty()) {
                                    selectedTrain.getLogisticsManager().startUnloadProcess(station);
                                    selectedTrain.recordStopAtStation();
                                }
                            } else if (station.getRole() == CargoTypes.StationRole.PRODUCER) {
                                if (!selectedTrain.getLogisticsManager()
                                        .getCapableWagons(station, false).isEmpty()) {
                                    selectedTrain.getLogisticsManager().startLoadProcess(station);
                                    selectedTrain.recordStopAtStation();
                                }
                            }
                        }
                        return;
                    }
                }
            }
            model.setMode(Model.GameMode.MENU);
        } else if (getEffectiveKeyType(stroke) == KeyType.Character && stroke.getCharacter() == 'm') {
            Locomotive loco = model.getSelectedLocomotive();
            if (loco != null) {
                if (!loco.isEngineOn()) {
                    audioController.startEngine(loco);
                } else if (loco.getSpeed() == 0 && loco.getTargetSpeed() == 0) {
                    audioController.stopEngineWithSound(loco.getId(), loco);
                }
            }
        }
    }

    private void handleProgramInput(InputEvent stroke) {}

    private void handleLinkInput(InputEvent stroke) {
        if (getEffectiveKeyType(stroke) == KeyType.ArrowUp) {
            if (model.getSelectedLocomotive() != null
                    && model.getSelectedLocomotive().getTrain() != null) {
                Train train = model.getSelectedLocomotive().getTrain();

                train.getTrainCouplingManager().updateLinkersToJoin(train, true);
            }
        } else if (getEffectiveKeyType(stroke) == KeyType.ArrowDown) {
            if (model.getSelectedLocomotive() != null
                    && model.getSelectedLocomotive().getTrain() != null) {
                Train train = model.getSelectedLocomotive().getTrain();

                train.getTrainCouplingManager().updateLinkersToJoin(train, false);
            }
        } else if (getEffectiveKeyType(stroke) == KeyType.ArrowLeft) {
            if (model.getSelectedLocomotive() != null
                    && model.getSelectedLocomotive().getTrain() != null) {
                Train train = model.getSelectedLocomotive().getTrain();
                if (train.getNumLinkersToJoin() > 0) {
                    train.setNumLinkersToJoin(train.getNumLinkersToJoin() - 1);
                }
            }
        } else if (getEffectiveKeyType(stroke) == KeyType.ArrowRight) {
            if (model.getSelectedLocomotive() != null
                    && model.getSelectedLocomotive().getTrain() != null) {
                Train train = model.getSelectedLocomotive().getTrain();
                if (train.getNumLinkersToJoin() < train.getLinkersToJoin().size()) {
                    train.setNumLinkersToJoin(train.getNumLinkersToJoin() + 1);
                }
            }
        } else if (getEffectiveKeyType(stroke) == KeyType.Character && stroke.getCharacter() == ' ') {
            Locomotive loco = model.getSelectedLocomotive();
            if (loco != null && loco.getTrain() != null) {
                Train train = loco.getTrain();
                if (!train.getLinkersToJoin().isEmpty() && train.getNumLinkersToJoin() > 0) {
                    train.getTrainCouplingManager().joinLinkers(train);
                }
                model.setMode(model.getPreviousMode());
            }
        }
    }

    private void handleUnlinkInput(InputEvent stroke) {
        if (model.getSelectedLocomotive() != null
                && model.getSelectedLocomotive().getTrain() != null) {
            Train train = model.getSelectedLocomotive().getTrain();
            if (getEffectiveKeyType(stroke) == KeyType.ArrowUp) {
                train.getTrainCouplingManager().setFrontDivisionSense(train);
            } else if (getEffectiveKeyType(stroke) == KeyType.ArrowDown) {
                train.getTrainCouplingManager().setBackDivisionSense(train);
            } else if (getEffectiveKeyType(stroke) == KeyType.ArrowLeft) {
                train.getTrainCouplingManager().selectPrevDivisionLink(train);
            } else if (getEffectiveKeyType(stroke) == KeyType.ArrowRight) {
                train.getTrainCouplingManager().selectNextDivisionLink(train);
            } else if (getEffectiveKeyType(stroke) == KeyType.Character && stroke.getCharacter() == ' ') {

                train.getTrainCouplingManager().divideTrain(train, () -> model.nextTrainId());
                audioController.playOneShot("link",
                        (float) model.getSelectedLocomotive().getPosition().getX(),
                        (float) model.getSelectedLocomotive().getPosition().getY());
                model.setMode(model.getPreviousMode());
            }
        }
    }

    private void handleForkInput(InputEvent stroke) {
        if (getEffectiveKeyType(stroke) == KeyType.ArrowLeft) {
            model.selectPrevFork();
        } else if (getEffectiveKeyType(stroke) == KeyType.ArrowRight) {
            model.selectNextFork();
        } else if (getEffectiveKeyType(stroke) == KeyType.Character && stroke.getCharacter() == ' ') {
            if (forkIdAccumulator > 0) {
                model.selectFork(forkIdAccumulator);
                forkIdAccumulator = 0;
                forkInputTimeout = 0;
            }
            if (model.getSelectedFork() != null) {
                model.getSelectedFork().flipRoute();
                audioController.playOneShot("fork",
                        (float) model.getSelectedFork().getPosition().getX(),
                        (float) model.getSelectedFork().getPosition().getY());
            }
        } else if (getEffectiveKeyType(stroke) == KeyType.Character
                && Character.isDigit(stroke.getCharacter())) {
            forkIdAccumulator =
                    forkIdAccumulator * 10 + Character.getNumericValue(stroke.getCharacter());
            model.selectFork(forkIdAccumulator);
            forkInputTimeout = System.currentTimeMillis() + 1000;
        } else if (getEffectiveKeyType(stroke) == KeyType.Backspace) {
            forkIdAccumulator = forkIdAccumulator / 10;
            model.selectFork(forkIdAccumulator);
            forkInputTimeout = System.currentTimeMillis() + 1000;
        }
    }

    private void handleSpeedSignalsInput(InputEvent stroke) {
        switch (getEffectiveKeyType(stroke)) {
            case Backspace:
                speedSignalId = speedSignalId / 10;
                model.selectSpeedSignal(speedSignalId);
                break;
            case Character:
                if (stroke.getCharacter() == 'm' || stroke.getCharacter() == 'M') {
                    if (model.getSelectedSpeedSignal() != null) {
                        model.getSelectedSpeedSignal()
                                .setMax(!model.getSelectedSpeedSignal().isMax());
                    }
                } else if (stroke.getCharacter() == ' ') {
                    if (speedSignalId > 0) {
                        model.selectSpeedSignal(speedSignalId);
                        speedSignalId = 0;
                    }
                    if (model.getSelectedSpeedSignal() != null) {
                        letrain.track.SpeedSignal sig = model.getSelectedSpeedSignal();
                        sig.setCreationDir(sig.getCreationDir().inverse());
                        cameraController.forceSnap();
                    }
                } else if (stroke.getCharacter() >= '0' && stroke.getCharacter() <= '9') {
                    if (model.getSelectedSpeedSignal() != null) {
                        int val = stroke.getCharacter() - '0';
                        if (val == 0) {
                            val = 10;
                        }
                        model.getSelectedSpeedSignal().setLimit(val);
                    } else {
                        speedSignalId = speedSignalId * 10 + (stroke.getCharacter() - '0');
                    }
                }
                break;
            case ArrowLeft:
                model.selectPrevSpeedSignal();
                break;
            case ArrowRight:
                model.selectNextSpeedSignal();
                break;
            case ArrowUp:
                if (model.getSelectedSpeedSignal() != null) {
                    int l = model.getSelectedSpeedSignal().getLimit();
                    if (l < 10) {
                        model.getSelectedSpeedSignal().setLimit(l + 1);
                    }
                }
                break;
            case ArrowDown:
                if (model.getSelectedSpeedSignal() != null) {
                    int l = model.getSelectedSpeedSignal().getLimit();
                    if (l > 1) {
                        model.getSelectedSpeedSignal().setLimit(l - 1);
                    }
                }
                break;
            default:
                break;
        }
    }


    private long sensorInputTimeout = 0;
    private int sensorIdAccumulator = 0;

    private void handleSensorsInput(InputEvent stroke) {
        if (getEffectiveKeyType(stroke) == KeyType.ArrowLeft) {
            model.selectPrevSensor();
        } else if (getEffectiveKeyType(stroke) == KeyType.ArrowRight) {
            model.selectNextSensor();
        } else if (getEffectiveKeyType(stroke) == KeyType.Character
                && Character.isDigit(stroke.getCharacter())) {
            sensorIdAccumulator =
                    sensorIdAccumulator * 10 + Character.getNumericValue(stroke.getCharacter());
            model.selectSensor(sensorIdAccumulator);
            sensorInputTimeout = System.currentTimeMillis() + 1000;
        } else if (getEffectiveKeyType(stroke) == KeyType.Character && stroke.getCharacter() == ' ') {
            if (sensorIdAccumulator > 0) {
                model.selectSensor(sensorIdAccumulator);
                sensorIdAccumulator = 0;
                sensorInputTimeout = 0;
            }
        }
    }

    private void handleSemaphoreInput(InputEvent stroke) {
        if (getEffectiveKeyType(stroke) == KeyType.ArrowLeft) {
            model.selectPrevSemaphore();
        } else if (getEffectiveKeyType(stroke) == KeyType.ArrowRight) {
            model.selectNextSemaphore();
        } else if (getEffectiveKeyType(stroke) == KeyType.Character
                && (stroke.getCharacter() == 'm' || stroke.getCharacter() == 'M')) {
            RailSemaphore s = model.getSelectedSemaphore();
            if (s != null) {
                s.setOpen(!s.isOpen());
                audioController.playOneShot("construction", (float) s.getPosition().getX(),
                        (float) s.getPosition().getY());
            }
        } else if (getEffectiveKeyType(stroke) == KeyType.Character && stroke.getCharacter() == ' ') {
            if (semaphoreIdAccumulator > 0) {
                model.selectSemaphore(semaphoreIdAccumulator);
                semaphoreIdAccumulator = 0;
                semaphoreInputTimeout = 0;
            }
            RailSemaphore s = model.getSelectedSemaphore();
            if (s != null && s.getCreationDir() != null) {
                s.setCreationDir(s.getCreationDir().inverse());
                if (cameraController != null) {
                    cameraController.forceSnap();
                }
            }

        } else if (getEffectiveKeyType(stroke) == KeyType.Character
                && Character.isDigit(stroke.getCharacter())) {
            semaphoreIdAccumulator =
                    semaphoreIdAccumulator * 10 + Character.getNumericValue(stroke.getCharacter());
            model.selectSemaphore(semaphoreIdAccumulator);
            semaphoreInputTimeout = System.currentTimeMillis() + 1000;
        } else if (getEffectiveKeyType(stroke) == KeyType.Backspace) {
            semaphoreIdAccumulator = semaphoreIdAccumulator / 10;
            model.selectSemaphore(semaphoreIdAccumulator);
            semaphoreInputTimeout = System.currentTimeMillis() + 1000;
        }
    }

    private void handleStationInput(InputEvent stroke) {
        if (getEffectiveKeyType(stroke) == KeyType.ArrowLeft) {
            model.selectPrevStation();
        } else if (getEffectiveKeyType(stroke) == KeyType.ArrowRight) {
            model.selectNextStation();
        } else if (getEffectiveKeyType(stroke) == KeyType.Character && stroke.getCharacter() == ' ') {
            if (stationIdAccumulator > 0) {
                model.selectStation(stationIdAccumulator);
                stationIdAccumulator = 0;
                stationInputTimeout = 0;
            }
            if (model.getSelectedStation() != null
                    && model.getSelectedStation().getTrack() != null) {
                Linker linker = model.getSelectedStation().getTrack().getLinker();
                if (linker != null && linker.getTrain() != null) {
                    Train train = linker.getTrain();
                    Station station = model.getSelectedStation();
                    train.getLogisticsManager().performIndustrialAction(station);
                }
            }
        } else if (getEffectiveKeyType(stroke) == KeyType.Character
                && Character.isDigit(stroke.getCharacter())) {
            stationIdAccumulator =
                    stationIdAccumulator * 10 + Character.getNumericValue(stroke.getCharacter());
            model.selectStation(stationIdAccumulator);
            stationInputTimeout = System.currentTimeMillis() + 1000;
        } else if (getEffectiveKeyType(stroke) == KeyType.Backspace) {
            stationIdAccumulator = stationIdAccumulator / 10;
            model.selectStation(stationIdAccumulator);
            stationInputTimeout = System.currentTimeMillis() + 1000;
        } else if (getEffectiveKeyType(stroke) == KeyType.Character && stroke.getCharacter() == '-') {
            Station station = model.getSelectedStation();
            if (station != null) {
                for (Locomotive loco : model.getLocomotives()) {
                    if (loco.getTrain() != null
                            && loco.getTrain().getStationId() == station.getId()) {
                        Train train = loco.getTrain();
                        boolean isLoading = !loco.getTrain().getLogisticsManager().isLoading();
                        train.getLogisticsManager().setLoading(isLoading);
                    }
                }
            }
        }
    }

    private void handleTrainsInput(InputEvent stroke) {
        if (stroke.getKeyType() == KeyType.Character) {
            char c = stroke.getCharacter();
            if (Character.isDigit(c)) {
                if (lastCreatedLoco != null) {
                    int colorIdx = c - '0';
                    lastCreatedLoco.setColor(
                            Locomotive.COLOR_PALETTE[colorIdx % Locomotive.COLOR_PALETTE.length]);
                    return;
                }
                if (c == '1') {
                    model.setSelectedWagonType(CargoTypes.GOLD);
                    view.updateHUD();
                } else if (c == '2') {
                    model.setSelectedWagonType(CargoTypes.COAL);
                    view.updateHUD();
                } else if (c == '3') {
                    model.setSelectedWagonType(CargoTypes.RUBY);
                    view.updateHUD();
                }
            } else {
                createVehicle(c);
            }
        } else if (stroke.getKeyType() == KeyType.Backspace) {
            deleteVehicle();
        }
    }

    private void createVehicle(char c) {
        RailTrack track = model.getCursorRailTrack();
        if (track == null || track.getLinker() != null) {
            return;
        }

        Dir cursorDir = model.getCursor().getDir();

        if (Character.isUpperCase(c)) {
            int locoId = model.peekNextLocomotiveId();
            Locomotive locomotive = new Locomotive(locoId, "" + c);
            int trainId = model.peekNextTrainId();
            Train train = new Train(trainId);
            train.pushBack(locomotive);
            train.setDirectorLinker(locomotive);

            track.enterLinkerFromDir(cursorDir.inverse(), locomotive);

            if (locomotive.getDir() == null) {
                track.removeLinker();
                return;
            }

            model.nextLocomotiveId();
            model.nextTrainId();
            model.addLocomotive(locomotive);
            model.selectLocomotive(locoId);

            train.getSafetyManager().claimOccupiedSegments();
            cursorDir = locomotive.getDir();
            lastCreatedLoco = locomotive;
        } else {
            Wagon wagon = new Wagon("" + c);
            wagon.setExclusiveCargoType(model.getSelectedWagonType());

            track.enterLinkerFromDir(cursorDir.inverse(), wagon);

            if (wagon.getDir() == null) {
                track.removeLinker();
                return;
            }

            model.addWagon(wagon);

            if (wagon.getTrain() != null) {
                wagon.getTrain().getSafetyManager().claimOccupiedSegments();
            }
            cursorDir = wagon.getDir();
            lastCreatedLoco = null;
        }
        model.getCursor().setDir(cursorDir);
        model.getCursor().getPosition().move(cursorDir);
    }

    private void deleteVehicle() {
        Dir cursorDir = model.getCursor().getDir();
        model.getCursor().getPosition().move(cursorDir.inverse());

        RailTrack track = model.getCursorRailTrack();
        if (track != null && track.getLinker() != null) {
            Linker linker = track.getLinker();
            if (linker instanceof Locomotive) {
                model.removeLocomotive((Locomotive) linker);
                if (lastCreatedLoco == linker) {
                    lastCreatedLoco = null;
                }
            } else if (linker instanceof Wagon) {
                model.removeWagon((Wagon) linker);
            }
            track.removeLinker();

            Dir entryDir = track.getRouter().getDir(cursorDir);
            if (entryDir != null) {
                model.getCursor().setDir(entryDir.inverse());
            }
        } else {
            model.getCursor().getPosition().move(cursorDir);
        }
    }
}
