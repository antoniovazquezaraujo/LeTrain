package letrain.mvp.impl;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.googlecode.lanterna.input.KeyStroke;

/**
 * Encapsula la lógica de entrada de la vista 3D:
 * - Traducción LibGDX -> KeyStroke (Lanterna)
 * - Atajos de cámara (C, Alt+flechas, rueda)
 * - Creación / borrado de vehículos y reenvío al GameViewListener.
 */
public class InputController {

    private final Model model;
    private final letrain.mvp.GameViewListener listener;
    private final CameraController cameraController;

    public InputController(Model model, letrain.mvp.GameViewListener listener, CameraController cameraController) {
        this.model = model;
        this.listener = listener;
        this.cameraController = cameraController;
    }

    public boolean keyTyped(char character) {
        // 1. Toggle de cámara
        if (character == 'c' || character == 'C') {
            cameraController.cycleMode(!model.getLocomotives().isEmpty());
            return true;
        }

        // 2. Creación de vehículos en modo TRAINS
        if (model.getMode() == letrain.mvp.Model.GameMode.TRAINS) {
            if (Character.isLetter(character)) {
                createVehicle(character);
                return true;
            }
        } else if (model.getMode() == letrain.mvp.Model.GameMode.STATIONS) {
            if (character == '-') {
                letrain.track.Station station = model.getSelectedStation();
                if (station != null) {
                    for (letrain.vehicle.impl.rail.Locomotive loco : model.getLocomotives()) {
                        if (loco.getTrain() != null && loco.getTrain().getStationId() == station.getId()) {
                            loco.getTrain().isLoading = !loco.getTrain().isLoading;
                        }
                    }
                }
                return true;
            }
        }

        // 3. Resto de caracteres -> GameViewListener.onChar
        boolean ctrlPressed = Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.CONTROL_LEFT)
                || Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.CONTROL_RIGHT);
        boolean altPressed = Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.ALT_LEFT)
                || Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.ALT_RIGHT);

        if (!Character.isISOControl(character)) {
            listener.onChar(new KeyStroke(character, ctrlPressed, altPressed));
            return true;
        }
        return false;
    }

    public boolean keyDown(int keycode) {
        KeyStroke keyStroke = null;
        switch (keycode) {
            case com.badlogic.gdx.Input.Keys.UP:
                keyStroke = new KeyStroke(com.googlecode.lanterna.input.KeyType.ArrowUp);
                break;
            case com.badlogic.gdx.Input.Keys.DOWN:
                keyStroke = new KeyStroke(com.googlecode.lanterna.input.KeyType.ArrowDown);
                break;
            case com.badlogic.gdx.Input.Keys.LEFT:
                keyStroke = new KeyStroke(com.googlecode.lanterna.input.KeyType.ArrowLeft);
                break;
            case com.badlogic.gdx.Input.Keys.RIGHT:
                keyStroke = new KeyStroke(com.googlecode.lanterna.input.KeyType.ArrowRight);
                break;
            case com.badlogic.gdx.Input.Keys.ENTER:
                keyStroke = new KeyStroke(com.googlecode.lanterna.input.KeyType.Enter);
                break;
            case com.badlogic.gdx.Input.Keys.ESCAPE:
                keyStroke = new KeyStroke(com.googlecode.lanterna.input.KeyType.Escape);
                break;
            case com.badlogic.gdx.Input.Keys.BACKSPACE:
                keyStroke = new KeyStroke(com.googlecode.lanterna.input.KeyType.Backspace);
                break;
            case com.badlogic.gdx.Input.Keys.FORWARD_DEL:
                keyStroke = new KeyStroke(com.googlecode.lanterna.input.KeyType.Delete);
                break;
            case com.badlogic.gdx.Input.Keys.HOME:
                keyStroke = new KeyStroke(com.googlecode.lanterna.input.KeyType.Home);
                break;
            case com.badlogic.gdx.Input.Keys.END:
                keyStroke = new KeyStroke(com.googlecode.lanterna.input.KeyType.End);
                break;
            case com.badlogic.gdx.Input.Keys.PAGE_UP:
                keyStroke = new KeyStroke(com.googlecode.lanterna.input.KeyType.PageUp);
                break;
            case com.badlogic.gdx.Input.Keys.PAGE_DOWN:
                keyStroke = new KeyStroke(com.googlecode.lanterna.input.KeyType.PageDown);
                break;
            case com.badlogic.gdx.Input.Keys.INSERT:
                keyStroke = new KeyStroke(com.googlecode.lanterna.input.KeyType.Insert);
                break;
            case com.badlogic.gdx.Input.Keys.TAB:
                keyStroke = new KeyStroke(com.googlecode.lanterna.input.KeyType.Tab);
                break;
            case com.badlogic.gdx.Input.Keys.F12:
                keyStroke = new KeyStroke(com.googlecode.lanterna.input.KeyType.F12);
                break;
        }

        if (keyStroke != null) {
            boolean ctrlPressed = Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.CONTROL_LEFT)
                    || Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.CONTROL_RIGHT);
            boolean altPressedKey = Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.ALT_LEFT)
                    || Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.ALT_RIGHT);
            boolean shiftPressed = Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SHIFT_LEFT)
                    || Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SHIFT_RIGHT);

            listener.onChar(new KeyStroke(keyStroke.getKeyType(), ctrlPressed, altPressedKey, shiftPressed));
            return true;
        }

        return false;
    }

    public boolean keyUp(int keycode) {
        KeyStroke keyStroke = null;
        switch (keycode) {
            case com.badlogic.gdx.Input.Keys.UP:
                keyStroke = new KeyStroke(com.googlecode.lanterna.input.KeyType.ArrowUp);
                break;
            case com.badlogic.gdx.Input.Keys.DOWN:
                keyStroke = new KeyStroke(com.googlecode.lanterna.input.KeyType.ArrowDown);
                break;
            case com.badlogic.gdx.Input.Keys.LEFT:
                keyStroke = new KeyStroke(com.googlecode.lanterna.input.KeyType.ArrowLeft);
                break;
            case com.badlogic.gdx.Input.Keys.RIGHT:
                keyStroke = new KeyStroke(com.googlecode.lanterna.input.KeyType.ArrowRight);
                break;
            case com.badlogic.gdx.Input.Keys.CONTROL_LEFT:
            case com.badlogic.gdx.Input.Keys.CONTROL_RIGHT:
                keyStroke = new KeyStroke(com.googlecode.lanterna.input.KeyType.Unknown, true, false, false);
                break;
            case com.badlogic.gdx.Input.Keys.SHIFT_LEFT:
            case com.badlogic.gdx.Input.Keys.SHIFT_RIGHT:
                keyStroke = new KeyStroke(com.googlecode.lanterna.input.KeyType.Unknown, false, false, true);
                break;
        }

        if (keyStroke != null) {
            boolean ctrlPressed = Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.CONTROL_LEFT)
                    || Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.CONTROL_RIGHT);
            boolean shiftPressed = Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SHIFT_LEFT)
                    || Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SHIFT_RIGHT);

            listener.onKeyUp(new KeyStroke(keyStroke.getKeyType(), ctrlPressed, false, shiftPressed));
            return true;
        }
        return false;
    }

    public boolean scrolled(float amountX, float amountY, Stage stage) {
        com.badlogic.gdx.math.Vector2 stageCoords = stage.screenToStageCoordinates(
                new com.badlogic.gdx.math.Vector2(Gdx.input.getX(), Gdx.input.getY()));
        if (stage.hit(stageCoords.x, stageCoords.y, true) != null) {
            return true; // Bloquear zoom si el ratón está sobre la UI
        }

        cameraController.zoom(amountY);
        return true;
    }

    private void createVehicle(char c) {
        letrain.track.rail.RailTrack track = model.getCursorRailTrack();
        if (track == null || track.getLinker() != null)
            return;

        letrain.map.Dir cursorDir = model.getCursor().getDir();

        if (Character.isUpperCase(c)) {
            int locoId = model.nextLocomotiveId();
            letrain.vehicle.impl.rail.Locomotive locomotive = new letrain.vehicle.impl.rail.Locomotive(locoId, "" + c);
            letrain.vehicle.impl.rail.Train train = new letrain.vehicle.impl.rail.Train(model.nextTrainId());
            train.pushBack(locomotive);
            train.setDirectorLinker(locomotive);
            model.addLocomotive(locomotive);
            model.selectLocomotive(locoId);
            track.enterLinkerFromDir(cursorDir.inverse(), locomotive);
            cursorDir = locomotive.getDir();
        } else {
            letrain.vehicle.impl.rail.Wagon wagon = new letrain.vehicle.impl.rail.Wagon("" + c);
            model.addWagon(wagon);
            track.enterLinkerFromDir(cursorDir.inverse(), wagon);
            cursorDir = wagon.getDir();
        }
        model.getCursor().setDir(cursorDir);
        model.getCursor().getPosition().move(cursorDir);
    }
}

