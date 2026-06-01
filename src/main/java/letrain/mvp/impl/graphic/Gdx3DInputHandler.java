package letrain.mvp.impl.graphic;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
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
 * Encapsula toda la lógica de entrada de la vista 3D.
 * Traduce eventos de LibGDX a KeyStrokes y gestiona la lógica de negocio
 * asociada a cada modo de juego.
 */
public class Gdx3DInputHandler implements InputProcessor {
    private static final Logger log = LoggerFactory.getLogger(Gdx3DInputHandler.class);

    private final Model model;
    private final GraphicPresenter view;
    private final CameraController cameraController;
    private final RailTrackMaker trackMaker;
    private final AudioController audioController;

    // Multi-digit selection state
    private int forkIdAccumulator = 0;
    private int semaphoreIdAccumulator = 0;
    private int stationIdAccumulator = 0;
    private int locomotiveIdAccumulator = 0;
    private long forkInputTimeout = 0;
    private long semaphoreInputTimeout = 0;
    private long stationInputTimeout = 0;
    private long locomotiveInputTimeout = 0;

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

    @Override
    public boolean keyDown(int keycode) {
        KeyStroke keyStroke = translateKeyCode(keycode);
        if (keyStroke != null) {
            boolean ctrlPressed = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                    || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
            boolean altPressed = Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT)
                    || Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT);
            boolean shiftPressed = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                    || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);

            view.onChar(new KeyStroke(keyStroke.getKeyType(), ctrlPressed, altPressed, shiftPressed));
            return true;
        }
        return false;
    }

    @Override
    public boolean keyUp(int keycode) {
        KeyStroke keyStroke = translateKeyCodeForUp(keycode);
        if (keyStroke != null) {
            boolean ctrlPressed = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                    || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
            boolean shiftPressed = Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                    || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);

            view.onKeyUp(new KeyStroke(keyStroke.getKeyType(), ctrlPressed, false, shiftPressed));
            return true;
        }
        return false;
    }

    @Override
    public boolean keyTyped(char character) {
        // 1. Toggle de cámara
        if (character == 'c' || character == 'C') {
            cameraController.cycleMode(!model.getLocomotives().isEmpty());
            return true;
        }

        // 2. Resto de caracteres -> view.onChar
        boolean ctrlPressed = Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
        boolean altPressed = Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT);

        if (!Character.isISOControl(character)) {
            view.onChar(new KeyStroke(character, ctrlPressed, altPressed));
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

    private KeyStroke translateKeyCode(int keycode) {
        switch (keycode) {
            case Input.Keys.UP: return new KeyStroke(KeyType.ArrowUp);
            case Input.Keys.DOWN: return new KeyStroke(KeyType.ArrowDown);
            case Input.Keys.LEFT: return new KeyStroke(KeyType.ArrowLeft);
            case Input.Keys.RIGHT: return new KeyStroke(KeyType.ArrowRight);
            case Input.Keys.ENTER: return new KeyStroke(KeyType.Enter);
            case Input.Keys.ESCAPE: return new KeyStroke(KeyType.Escape);
            case Input.Keys.BACKSPACE: return new KeyStroke(KeyType.Backspace);
            case Input.Keys.FORWARD_DEL: return new KeyStroke(KeyType.Delete);
            case Input.Keys.HOME: return new KeyStroke(KeyType.Home);
            case Input.Keys.END: return new KeyStroke(KeyType.End);
            case Input.Keys.PAGE_UP: return new KeyStroke(KeyType.PageUp);
            case Input.Keys.PAGE_DOWN: return new KeyStroke(KeyType.PageDown);
            case Input.Keys.INSERT: return new KeyStroke(KeyType.Insert);
            case Input.Keys.TAB: return new KeyStroke(KeyType.Tab);
            case Input.Keys.F1: return new KeyStroke(KeyType.F1);
            case Input.Keys.F12: return new KeyStroke(KeyType.F12);
            default: return null;
        }
    }

    private KeyStroke translateKeyCodeForUp(int keycode) {
        switch (keycode) {
            case Input.Keys.UP: return new KeyStroke(KeyType.ArrowUp);
            case Input.Keys.DOWN: return new KeyStroke(KeyType.ArrowDown);
            case Input.Keys.LEFT: return new KeyStroke(KeyType.ArrowLeft);
            case Input.Keys.RIGHT: return new KeyStroke(KeyType.ArrowRight);
            case Input.Keys.CONTROL_LEFT:
            case Input.Keys.CONTROL_RIGHT:
                return new KeyStroke(KeyType.Unknown, true, false, false);
            case Input.Keys.SHIFT_LEFT:
            case Input.Keys.SHIFT_RIGHT:
                return new KeyStroke(KeyType.Unknown, false, false, true);
            default: return null;
        }
    }

    public void onChar(KeyStroke stroke) {
        if (stroke.getKeyType() == KeyType.F1) {
            log.info("\n" + model.getRailwayGraphReport());
            return;
        }

        if (stroke.getKeyType() == KeyType.F12) {
            view.showReferenceGuide();
            return;
        }

        // Global Camera Zoom/Rotation (Alt + Arrows)
        if (stroke.isAltDown()) {
            if (stroke.getKeyType() == KeyType.ArrowLeft) {
                cameraController.rotateOrbit(-15f);
                return;
            } else if (stroke.getKeyType() == KeyType.ArrowRight) {
                cameraController.rotateOrbit(15f);
                return;
            } else if (stroke.getKeyType() == KeyType.ArrowUp) {
                cameraController.zoomStep(-1f);
                return;
            } else if (stroke.getKeyType() == KeyType.ArrowDown) {
                cameraController.zoomStep(1f);
                return;
            }
        }

        // Global Enter to Menu (matches TerminalPresenter)
        if (stroke.getKeyType() == KeyType.Enter) {
            if (model.getMode() != Model.GameMode.DRIVE) {
                model.setMode(Model.GameMode.MENU);
                return;
            }
        }

        // Mode Switching Shortcuts
        if (stroke.getKeyType() == KeyType.Character && stroke.getCharacter() != ' ') {
            if (model.getMode() != Model.GameMode.TRAINS) {
                switch (stroke.getCharacter()) {
                    case 'r':
                        model.setMode(Model.GameMode.RAILS);
                        return;
                    case 'd':
                        if (!model.getLocomotives().isEmpty())
                            model.setMode(Model.GameMode.DRIVE);
                        return;
                    case 'f':
                        if (!model.getForks().isEmpty())
                            model.setMode(Model.GameMode.FORKS);
                        return;
                    case 's':
                        if (!model.getSemaphores().isEmpty())
                            model.setMode(Model.GameMode.SEMAPHORES);
                        return;
                    case 't':
                        if (model.getCursorRailTrack() != null)
                            model.setMode(Model.GameMode.TRAINS);
                        return;
                    case 'l':
                        if (!model.getLocomotives().isEmpty()) {
                            model.setMode(Model.GameMode.LINK);
                            if (model.getSelectedLocomotive() != null
                                    && model.getSelectedLocomotive().getTrain() != null) {
                                Train train = model.getSelectedLocomotive().getTrain();
                                train.trainCouplingManager.resetLinkState(train);
                            }
                        }
                        return;
                    case 'u':
                        if (!model.getLocomotives().isEmpty()) {
                            model.setMode(Model.GameMode.UNLINK);
                            if (model.getSelectedLocomotive() != null
                                    && model.getSelectedLocomotive().getTrain() != null) {
                                Train train = model.getSelectedLocomotive().getTrain();
                                train.trainCouplingManager.resetUnlinkState(train);
                            }
                        }
                        return;
                    case 'n':
                        if (!model.getStations().isEmpty())
                            model.setMode(Model.GameMode.STATIONS);
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

    public void onKeyUp(KeyStroke stroke) {
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

    private void handleDriveInput(KeyStroke stroke) {
        if (stroke.getKeyType() == KeyType.ArrowUp) {
            Locomotive loco = model.getSelectedLocomotive();
            if (loco != null && loco.isEngineOn() && !loco.getTrain().getLogisticsManager().isLoading()) {
                loco.incSpeed();
                // Manual acceleration disengages autopilot
                if (loco.getTrain() != null && loco.getTrain().isAutoMode()) {
                    loco.getTrain().toggleAutoMode();
                }
            }
        } else if (stroke.getKeyType() == KeyType.ArrowDown) {
            Locomotive loco = model.getSelectedLocomotive();
            if (loco != null && loco.isEngineOn() && !loco.getTrain().getLogisticsManager().isLoading()) {
                loco.decSpeed();
                if (loco.getTrain() != null && loco.getTrain().isAutoMode()) {
                    loco.getTrain().toggleAutoMode();
                }
            }
        } else if (stroke.getKeyType() == KeyType.ArrowLeft) {
            model.selectPrevLocomotive();
        } else if (stroke.getKeyType() == KeyType.ArrowRight) {
            model.selectNextLocomotive();
        } else if (stroke.getKeyType() == KeyType.Character
                && stroke.getCharacter() == ' ') {
            if (locomotiveIdAccumulator > 0) {
                model.selectLocomotive(locomotiveIdAccumulator);
                locomotiveIdAccumulator = 0;
                locomotiveInputTimeout = 0;
            }
            if (model.getSelectedLocomotive() != null && model.getSelectedLocomotive().getSpeed() == 0) {
                if (model.getSelectedLocomotive().getTrack() != null) {
                    model.getSelectedLocomotive().toggleReversed();
                }
            }
        } else if (stroke.getKeyType() == KeyType.Character &&
                Character.isDigit(stroke.getCharacter())) {
            locomotiveIdAccumulator = locomotiveIdAccumulator * 10 + Character.getNumericValue(stroke.getCharacter());
            model.selectLocomotive(locomotiveIdAccumulator);
            locomotiveInputTimeout = System.currentTimeMillis() + 1000;
        } else if (stroke.getKeyType() == KeyType.Character
                && (stroke.getCharacter() == 'a' || stroke.getCharacter() == 'A')) {
            // Toggle autopilot
            if (model.getSelectedLocomotive() != null) {
                Train t = model.getSelectedLocomotive().getTrain();
                if (t != null) t.toggleAutoMode();
            }
        } else if (stroke.getKeyType() == KeyType.Backspace) {
            locomotiveIdAccumulator = locomotiveIdAccumulator / 10;
            model.selectLocomotive(locomotiveIdAccumulator);
            locomotiveInputTimeout = System.currentTimeMillis() + 1000;
        } else if (stroke.getKeyType() == KeyType.Enter) {
            if (model.getSelectedLocomotive() != null && model.getSelectedLocomotive().getSpeed() == 0) {
                Train selectedTrain = model.getSelectedLocomotive().getTrain();
                if (selectedTrain != null) {
                    Station station = selectedTrain.getLogisticsManager().getStationAtTrain();
                    if (station != null) {
                        if (selectedTrain.getLogisticsManager().isLoading()) {
                            selectedTrain.getLogisticsManager().endLoadUnloadProcess();
                        } else {
                            if (station.getRole() == CargoTypes.StationRole.CONSUMER) {
                                if (!selectedTrain.getLogisticsManager().getCapableWagons(station, true).isEmpty()) {
                                    selectedTrain.getLogisticsManager().startUnloadProcess(station);
                                    selectedTrain.recordStopAtStation();
                                }
                            } else if (station.getRole() == CargoTypes.StationRole.PRODUCER) {
                                if (!selectedTrain.getLogisticsManager().getCapableWagons(station, false).isEmpty()) {
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
        } else if (stroke.getKeyType() == KeyType.Character
                && stroke.getCharacter() == 'm') {
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

    private void handleProgramInput(KeyStroke stroke) {
        if (stroke.getKeyType() == KeyType.F12) {
            view.showReferenceGuide();
        }
    }

    private void handleLinkInput(KeyStroke stroke) {
        if (stroke.getKeyType() == KeyType.ArrowUp) {
            if (model.getSelectedLocomotive() != null && model.getSelectedLocomotive().getTrain() != null) {
                Train train = model.getSelectedLocomotive().getTrain();

                train.trainCouplingManager.updateLinkersToJoin(train, true);
            }
        } else if (stroke.getKeyType() == KeyType.ArrowDown) {
            if (model.getSelectedLocomotive() != null && model.getSelectedLocomotive().getTrain() != null) {
                Train train = model.getSelectedLocomotive().getTrain();

                train.trainCouplingManager.updateLinkersToJoin(train, false);
            }
        } else if (stroke.getKeyType() == KeyType.ArrowLeft) {
            if (model.getSelectedLocomotive() != null && model.getSelectedLocomotive().getTrain() != null) {
                Train train = model.getSelectedLocomotive().getTrain();
                if (train.getNumLinkersToJoin() > 0) {
                    train.setNumLinkersToJoin(train.getNumLinkersToJoin() - 1);
                }
            }
        } else if (stroke.getKeyType() == KeyType.ArrowRight) {
            if (model.getSelectedLocomotive() != null && model.getSelectedLocomotive().getTrain() != null) {
                Train train = model.getSelectedLocomotive().getTrain();
                if (train.getNumLinkersToJoin() < train.getLinkersToJoin().size()) {
                    train.setNumLinkersToJoin(train.getNumLinkersToJoin() + 1);
                }
            }
        } else if (stroke.getKeyType() == KeyType.Character
                && stroke.getCharacter() == ' ') {
            Locomotive loco = model.getSelectedLocomotive();
            if (loco != null && loco.getTrain() != null) {
                Train train = loco.getTrain();
                if (!train.getLinkersToJoin().isEmpty() && train.getNumLinkersToJoin() > 0) {
                    train.trainCouplingManager.joinLinkers(train);
                }
                model.setMode(Model.GameMode.MENU);
            }
        }
    }

    private void handleUnlinkInput(KeyStroke stroke) {
        if (model.getSelectedLocomotive() != null && model.getSelectedLocomotive().getTrain() != null) {
            Train train = model.getSelectedLocomotive().getTrain();
            if (stroke.getKeyType() == KeyType.ArrowUp) {
                train.trainCouplingManager.setFrontDivisionSense(train);
            } else if (stroke.getKeyType() == KeyType.ArrowDown) {
                train.trainCouplingManager.setBackDivisionSense(train);
            } else if (stroke.getKeyType() == KeyType.ArrowLeft) {
                train.trainCouplingManager.selectPrevDivisionLink(train);
            } else if (stroke.getKeyType() == KeyType.ArrowRight) {
                train.trainCouplingManager.selectNextDivisionLink(train);
            } else if (stroke.getKeyType() == KeyType.Character
                    && stroke.getCharacter() == ' ') {

                train.trainCouplingManager.divideTrain(train, () -> model.nextTrainId());
                audioController.playOneShot("link",
                        (float) model.getSelectedLocomotive().getPosition().getX(),
                        (float) model.getSelectedLocomotive().getPosition().getY());
                model.setMode(Model.GameMode.MENU);
            }
        }
    }

    private void handleForkInput(KeyStroke stroke) {
        if (stroke.getKeyType() == KeyType.ArrowLeft) {
            model.selectPrevFork();
        } else if (stroke.getKeyType() == KeyType.ArrowRight) {
            model.selectNextFork();
        } else if (stroke.getKeyType() == KeyType.Character
                && stroke.getCharacter() == ' ') {
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
        } else if (stroke.getKeyType() == KeyType.Character &&
                Character.isDigit(stroke.getCharacter())) {
            forkIdAccumulator = forkIdAccumulator * 10 + Character.getNumericValue(stroke.getCharacter());
            model.selectFork(forkIdAccumulator);
            forkInputTimeout = System.currentTimeMillis() + 1000;
        } else if (stroke.getKeyType() == KeyType.Backspace) {
            forkIdAccumulator = forkIdAccumulator / 10;
            model.selectFork(forkIdAccumulator);
            forkInputTimeout = System.currentTimeMillis() + 1000;
        }
    }

    private void handleSemaphoreInput(KeyStroke stroke) {
        if (stroke.getKeyType() == KeyType.ArrowLeft) {
            model.selectPrevSemaphore();
        } else if (stroke.getKeyType() == KeyType.ArrowRight) {
            model.selectNextSemaphore();
        } else if (stroke.getKeyType() == KeyType.Character
                && stroke.getCharacter() == ' ') {
            if (semaphoreIdAccumulator > 0) {
                model.selectSemaphore(semaphoreIdAccumulator);
                semaphoreIdAccumulator = 0;
                semaphoreInputTimeout = 0;
            }
            RailSemaphore s = model.getSelectedSemaphore();
            if (s != null) {
                s.setOpen(!s.isOpen());
                audioController.playOneShot("construction",
                        (float) s.getPosition().getX(),
                        (float) s.getPosition().getY());
            }
        } else if (stroke.getKeyType() == KeyType.Character &&
                Character.isDigit(stroke.getCharacter())) {
            semaphoreIdAccumulator = semaphoreIdAccumulator * 10 + Character.getNumericValue(stroke.getCharacter());
            model.selectSemaphore(semaphoreIdAccumulator);
            semaphoreInputTimeout = System.currentTimeMillis() + 1000;
        } else if (stroke.getKeyType() == KeyType.Backspace) {
            semaphoreIdAccumulator = semaphoreIdAccumulator / 10;
            model.selectSemaphore(semaphoreIdAccumulator);
            semaphoreInputTimeout = System.currentTimeMillis() + 1000;
        }
    }

    private void handleStationInput(KeyStroke stroke) {
        if (stroke.getKeyType() == KeyType.ArrowLeft) {
            model.selectPrevStation();
        } else if (stroke.getKeyType() == KeyType.ArrowRight) {
            model.selectNextStation();
        } else if (stroke.getKeyType() == KeyType.Character
                && stroke.getCharacter() == ' ') {
            if (stationIdAccumulator > 0) {
                model.selectStation(stationIdAccumulator);
                stationIdAccumulator = 0;
                stationInputTimeout = 0;
            }
            if (model.getSelectedStation() != null && model.getSelectedStation().getTrack() != null) {
                Linker linker = model.getSelectedStation().getTrack().getLinker();
                if (linker != null && linker.getTrain() != null) {
                    Train train = linker.getTrain();
                    Station station = model.getSelectedStation();
                    train.getLogisticsManager().performIndustrialAction(station);
                }
            }
        } else if (stroke.getKeyType() == KeyType.Character &&
                Character.isDigit(stroke.getCharacter())) {
            stationIdAccumulator = stationIdAccumulator * 10 + Character.getNumericValue(stroke.getCharacter());
            model.selectStation(stationIdAccumulator);
            stationInputTimeout = System.currentTimeMillis() + 1000;
        } else if (stroke.getKeyType() == KeyType.Backspace) {
            stationIdAccumulator = stationIdAccumulator / 10;
            model.selectStation(stationIdAccumulator);
            stationInputTimeout = System.currentTimeMillis() + 1000;
        } else if (stroke.getKeyType() == KeyType.Character
                && stroke.getCharacter() == '-') {
            Station station = model.getSelectedStation();
            if (station != null) {
                for (Locomotive loco : model.getLocomotives()) {
                    if (loco.getTrain() != null && loco.getTrain().getStationId() == station.getId()) {
                        Train train = loco.getTrain();
                        boolean isLoading = !loco.getTrain().getLogisticsManager().isLoading();
                        train.getLogisticsManager().setLoading(isLoading);
                    }
                }
            }
        }
    }

    private void handleTrainsInput(KeyStroke stroke) {
        if (stroke.getKeyType() == KeyType.Character) {
            char c = stroke.getCharacter();
            if (c == '1') {
                model.setSelectedWagonType(CargoTypes.GOLD);
                view.updateHUD();
            } else if (c == '2') {
                model.setSelectedWagonType(CargoTypes.COAL);
                view.updateHUD();
            } else if (c == '3') {
                model.setSelectedWagonType(CargoTypes.RUBY);
                view.updateHUD();
            } else {
                createVehicle(c);
            }
        } else if (stroke.getKeyType() == KeyType.Backspace) {
            deleteVehicle();
        }
    }

    private void createVehicle(char c) {
        RailTrack track = model.getCursorRailTrack();
        if (track == null || track.getLinker() != null)
            return;

        Dir cursorDir = model.getCursor().getDir();

        if (Character.isUpperCase(c)) {
            int locoId = model.nextLocomotiveId();
            Locomotive locomotive = new Locomotive(locoId, "" + c);
            Train train = new Train(model.nextTrainId());
            train.pushBack(locomotive);
            train.setDirectorLinker(locomotive);
            model.addLocomotive(locomotive);
            model.selectLocomotive(locoId);
            track.enterLinkerFromDir(cursorDir.inverse(), locomotive);
            cursorDir = locomotive.getDir();
        } else {
            Wagon wagon = new Wagon("" + c);
            wagon.setExclusiveCargoType(model.getSelectedWagonType());
            model.addWagon(wagon);
            track.enterLinkerFromDir(cursorDir.inverse(), wagon);
            cursorDir = wagon.getDir();
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
