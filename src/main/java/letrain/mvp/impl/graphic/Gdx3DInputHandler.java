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
import letrain.vehicle.impl.Linker;
import letrain.vehicle.impl.rail.Locomotive;
import letrain.vehicle.impl.rail.Train;
import letrain.vehicle.impl.rail.Wagon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

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

    // Itinerary editor state (Phase 5)
    private boolean editingItinerary = false;
    private letrain.itinerary.ItineraryBuilder itineraryBuilder;

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
        if (character == 'i' || character == 'I') {
            toggleItineraryEditor();
            return true;
        }
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
                                model.getSelectedLocomotive().getTrain().resetLinkState();
                            }
                        }
                        return;
                    case 'u':
                        if (!model.getLocomotives().isEmpty()) {
                            model.setMode(Model.GameMode.UNLINK);
                            if (model.getSelectedLocomotive() != null
                                    && model.getSelectedLocomotive().getTrain() != null) {
                                model.getSelectedLocomotive().getTrain().resetUnlinkState();
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
        // If editing itinerary, redirect to itinerary handler
        if (editingItinerary) {
            handleItineraryInput(stroke);
            return;
        }

        if (stroke.getKeyType() == KeyType.ArrowUp) {
            Locomotive loco = model.getSelectedLocomotive();
            if (loco != null && loco.isEngineOn() && !loco.getTrain().isLoading()) {
                loco.incSpeed();
            }
        } else if (stroke.getKeyType() == KeyType.ArrowDown) {
            Locomotive loco = model.getSelectedLocomotive();
            if (loco != null && loco.isEngineOn() && !loco.getTrain().isLoading()) {
                loco.decSpeed();
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
        } else if (stroke.getKeyType() == KeyType.Character
                && (stroke.getCharacter() == 'a' || stroke.getCharacter() == 'A')) {
            // Toggle autonomous mode
            if (model.getSelectedLocomotive() != null) {
                Train t = model.getSelectedLocomotive().getTrain();
                if (t != null) {
                    t.toggleAutoMode();
                }
            }
        } else if (stroke.getKeyType() == KeyType.Character &&
                Character.isDigit(stroke.getCharacter())) {
            locomotiveIdAccumulator = locomotiveIdAccumulator * 10 + Character.getNumericValue(stroke.getCharacter());
            model.selectLocomotive(locomotiveIdAccumulator);
            locomotiveInputTimeout = System.currentTimeMillis() + 1000;
        } else if (stroke.getKeyType() == KeyType.Backspace) {
            locomotiveIdAccumulator = locomotiveIdAccumulator / 10;
            model.selectLocomotive(locomotiveIdAccumulator);
            locomotiveInputTimeout = System.currentTimeMillis() + 1000;
        } else if (stroke.getKeyType() == KeyType.Enter) {
            if (model.getSelectedLocomotive() != null && model.getSelectedLocomotive().getSpeed() == 0) {
                Train selectedTrain = model.getSelectedLocomotive().getTrain();
                if (selectedTrain != null) {
                    Station station = selectedTrain.getStationAtTrain();
                    if (station != null) {
                        if (selectedTrain.isLoading()) {
                            selectedTrain.endLoadUnloadProcess();
                        } else {
                            if (station.getRole() == CargoTypes.StationRole.CONSUMER) {
                                if (!selectedTrain.getCapableWagons(station, true).isEmpty()) {
                                    selectedTrain.startUnloadProcess(station);
                                    selectedTrain.recordStopAtStation();
                                }
                            } else if (station.getRole() == CargoTypes.StationRole.PRODUCER) {
                                if (!selectedTrain.getCapableWagons(station, false).isEmpty()) {
                                    selectedTrain.startLoadProcess(station);
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
                model.getSelectedLocomotive().getTrain().updateLinkersToJoin(true);
            }
        } else if (stroke.getKeyType() == KeyType.ArrowDown) {
            if (model.getSelectedLocomotive() != null && model.getSelectedLocomotive().getTrain() != null) {
                model.getSelectedLocomotive().getTrain().updateLinkersToJoin(false);
            }
        } else if (stroke.getKeyType() == KeyType.ArrowLeft) {
            if (model.getSelectedLocomotive() != null && model.getSelectedLocomotive().getTrain() != null) {
                model.getSelectedLocomotive().getTrain().removeLinkerToJoin();
            }
        } else if (stroke.getKeyType() == KeyType.ArrowRight) {
            if (model.getSelectedLocomotive() != null && model.getSelectedLocomotive().getTrain() != null) {
                model.getSelectedLocomotive().getTrain().addLinkerToJoin();
            }
        } else if (stroke.getKeyType() == KeyType.Character
                && stroke.getCharacter() == ' ') {
            Locomotive loco = model.getSelectedLocomotive();
            if (loco != null && loco.getTrain() != null) {
                Train train = loco.getTrain();
                if (!train.getLinkersToJoin().isEmpty() && train.getNumLinkersToJoin() > 0) {
                    train.joinLinkers();
                }
                model.setMode(Model.GameMode.MENU);
            }
        }
    }

    private void handleUnlinkInput(KeyStroke stroke) {
        if (model.getSelectedLocomotive() != null && model.getSelectedLocomotive().getTrain() != null) {
            Train train = model.getSelectedLocomotive().getTrain();
            if (stroke.getKeyType() == KeyType.ArrowUp) {
                train.setFrontDivisionSense();
            } else if (stroke.getKeyType() == KeyType.ArrowDown) {
                train.setBackDivisionSense();
            } else if (stroke.getKeyType() == KeyType.ArrowLeft) {
                train.selectPrevDivisionLink();
            } else if (stroke.getKeyType() == KeyType.ArrowRight) {
                train.selectNextDivisionLink();
            } else if (stroke.getKeyType() == KeyType.Character
                    && stroke.getCharacter() == ' ') {
                train.divideTrain(() -> model.nextTrainId());
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
            }
        }
    }

    // ── Itinerary editor ──────────────────────────────────────────────

    private void toggleItineraryEditor() {
        editingItinerary = !editingItinerary;
        if (editingItinerary) {
            itineraryBuilder = new letrain.itinerary.ItineraryBuilder(() -> java.util.Optional.ofNullable(model.getRailwayGraph()));
            System.out.println("[ITINERARY] Editor ON — select stations with arrows, SPACE to add, ENTER to save");
        } else {
            itineraryBuilder = null;
            System.out.println("[ITINERARY] Editor OFF");
        }
    }

    private void handleItineraryInput(KeyStroke stroke) {
        if (itineraryBuilder == null) return;

        if (stroke.getKeyType() == KeyType.ArrowLeft) {
            model.selectPrevStation();
            showItineraryStatus();
        } else if (stroke.getKeyType() == KeyType.ArrowRight) {
            model.selectNextStation();
            showItineraryStatus();
        } else if (stroke.getKeyType() == KeyType.Character && stroke.getCharacter() == ' ') {
            letrain.track.Station st = model.getSelectedStation();
            if (st != null) {
                itineraryBuilder.addStation(st);
                System.out.println("[ITINERARY] Added: " + st.getId() + " (" + itineraryBuilder.waypoints().size() + " total)");
            }
        } else if (stroke.getKeyType() == KeyType.Backspace) {
            itineraryBuilder.removeLast();
            System.out.println("[ITINERARY] Removed last (" + itineraryBuilder.waypoints().size() + " total)");
        } else if (stroke.getKeyType() == KeyType.Enter) {
            if (itineraryBuilder.isValid() && model.getSelectedLocomotive() != null) {
                letrain.itinerary.Itinerary it = itineraryBuilder.build();
                letrain.vehicle.impl.rail.Train train = model.getSelectedLocomotive().getTrain();
                if (train != null) {
                    train.getAutopilot().setItinerary(it);
                    System.out.println("[ITINERARY] Saved: " + itineraryBuilder.getName() + " → Train " + train.getId());
                }
                itineraryBuilder.clear();
            } else {
                System.out.println("[ITINERARY] Need at least 2 stations and a selected locomotive");
            }
            showItineraryStatus();
        }
    }

    private void showItineraryStatus() {
        if (itineraryBuilder == null) return;
        String name = itineraryBuilder.getName();
        int count = itineraryBuilder.waypoints().size();
        letrain.track.Station st = model.getSelectedStation();
        String sel = st != null ? " (selected: Station " + st.getId() + ")" : "";
        System.out.println("[ITINERARY] " + name + " [" + count + "]" + sel);
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
                    linker.getTrain().performIndustrialAction(model.getSelectedStation());
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
                        loco.getTrain().setLoading(!loco.getTrain().isLoading());
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
