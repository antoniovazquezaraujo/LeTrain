package letrain.mvp.impl;

import java.util.List;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.screen.Screen;
import letrain.map.Point;
import letrain.mvp.impl.Model.GameModeMenuOption;
import letrain.visitor.Gdx3DRenderer;
import letrain.visitor.Gdx3DRenderer.VehicleLabel;

public class Gdx3DView extends ApplicationAdapter
        implements letrain.mvp.View, letrain.mvp.Presenter, com.badlogic.gdx.InputProcessor {
    private PerspectiveCamera cam;
    private ModelBatch modelBatch;
    private ModelBuilder modelBuilder;
    private com.badlogic.gdx.graphics.g3d.Model boxModel;
    private Environment environment;

    private final letrain.mvp.impl.Model model;
    private final Gdx3DRenderer renderer;
    private com.badlogic.gdx.graphics.g3d.Model groundModel;
    private com.badlogic.gdx.graphics.g3d.Model gridModel;
    private RailTrackMaker trackMaker;

    private SpriteBatch spriteBatch;
    private BitmapFont font;
    private Vector3 labelPos = new Vector3();

    public Gdx3DView(letrain.mvp.impl.Model model) {
        this.model = model;
        this.renderer = new Gdx3DRenderer();
        this.trackMaker = new RailTrackMaker(this);

        // Inicializar el GroundMap con un bloque de terreno para que RailTrackMaker
        // pueda detectar GROUND (0)
        // Usamos el área central o el área total permitida
        model.getGroundMap().renderBlock(0, 0, getCols(), getRows());
    }

    @Override
    public void create() {
        renderer.init();
        modelBatch = new ModelBatch();
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.5f, 0.5f, 0.5f, 1f));
        environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f));

        cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        cam.position.set(20f, 20f, 20f);
        cam.lookAt(0, 0, 0);
        cam.near = 1f;
        cam.far = 1000f;
        cam.update();

        modelBuilder = new ModelBuilder();

        // Suelo de madera o tablero
        groundModel = modelBuilder.createRect(
                -500f, 0, -500f,
                500f, 0, -500f,
                500f, 0, 500f,
                -500f, 0, 500f,
                0, 1, 0,
                new com.badlogic.gdx.graphics.g3d.Material(
                        ColorAttribute.createDiffuse(new Color(0.4f, 0.3f, 0.1f, 1f))),
                Usage.Position | Usage.Normal);

        // Rejilla para orientación (1x1 para coincidir con las celdas)
        modelBuilder.begin();
        com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder mpb = modelBuilder.part("grid", GL20.GL_LINES,
                Usage.Position | Usage.ColorUnpacked, new com.badlogic.gdx.graphics.g3d.Material());
        mpb.setColor(Color.LIGHT_GRAY);
        for (int i = -100; i <= 100; i += 1) {
            mpb.line(i, 0.01f, -100, i, 0.01f, 100);
            mpb.line(-100, 0.01f, i, 100, 0.01f, i);
        }
        gridModel = modelBuilder.end();

        boxModel = modelBuilder.createBox(0.8f, 0.8f, 0.8f,
                new com.badlogic.gdx.graphics.g3d.Material(
                        ColorAttribute.createDiffuse(Color.FOREST)),
                Usage.Position | Usage.Normal);

        spriteBatch = new SpriteBatch();
        font = new BitmapFont();
        font.setColor(Color.WHITE);
        font.getData().setScale(1.5f);

        Gdx.input.setInputProcessor(this);
    }

    @Override
    public boolean keyTyped(char character) {
        if (model.getMode() == letrain.mvp.Model.GameMode.TRAINS) {
            if (Character.isLetter(character)) {
                createVehicle(character);
                return true;
            }
        } else if (model.getMode() == letrain.mvp.Model.GameMode.DRIVE) {
            if (character == ' ') {
                if (model.getSelectedLocomotive() != null && model.getSelectedLocomotive().getSpeed() == 0) {
                    // Validamos que el linker esté en una vía antes de girarlo para evitar NPE
                    if (model.getSelectedLocomotive().getTrack() != null) {
                        model.getSelectedLocomotive().toggleReversed();
                    }
                }
                return true;
            }
        } else if (model.getMode() == letrain.mvp.Model.GameMode.LINK) {
            if (character == ' ') {
                if (model.getSelectedLocomotive() != null && model.getSelectedLocomotive().getTrain() != null) {
                    model.getSelectedLocomotive().getTrain().joinLinkers();
                    model.setMode(letrain.mvp.Model.GameMode.RAILS);
                }
                return true;
            }
        }

        switch (character) {
            case 'r':
                model.setMode(letrain.mvp.Model.GameMode.RAILS);
                return true;
            case 't':
                if (model.getCursorRailTrack() != null) {
                    model.setMode(letrain.mvp.Model.GameMode.TRAINS);
                }
                return true;
            case 'd':
                if (!model.getLocomotives().isEmpty()) {
                    model.setMode(letrain.mvp.Model.GameMode.DRIVE);
                }
                return true;
            case 'l':
                if (!model.getLocomotives().isEmpty()) {
                    model.setMode(letrain.mvp.Model.GameMode.LINK);
                }
                return true;
        }
        return false;
    }

    private void createVehicle(char c) {
        letrain.track.rail.RailTrack track = model.getCursorRailTrack();
        if (track == null || track.getLinker() != null)
            return;

        letrain.map.Dir cursorDir = model.getCursor().getDir();

        if (Character.isUpperCase(c)) {
            int locoId = model.nextLocomotiveId();
            letrain.vehicle.impl.rail.Locomotive locomotive = new letrain.vehicle.impl.rail.Locomotive(
                    locoId, "" + c);
            letrain.vehicle.impl.rail.Train train = new letrain.vehicle.impl.rail.Train(model.nextTrainId());
            train.pushBack(locomotive);
            train.setDirectorLinker(locomotive);
            model.addLocomotive(locomotive);
            // Seleccionamos la locomotora recién creada para que el modelo la reconozca
            model.selectLocomotive(locoId);
            track.enterLinkerFromDir(cursorDir.inverse(), locomotive);
        } else {
            letrain.vehicle.impl.rail.Wagon wagon = new letrain.vehicle.impl.rail.Wagon("" + c);
            model.addWagon(wagon);
            track.enterLinkerFromDir(cursorDir.inverse(), wagon);
        }
    }

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == com.badlogic.gdx.Input.Keys.ESCAPE || keycode == com.badlogic.gdx.Input.Keys.ENTER) {
            model.setMode(letrain.mvp.Model.GameMode.RAILS);
            return true;
        }
        return false;
    }

    @Override
    public boolean keyUp(int keycode) {
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
        return false;
    }

    private com.badlogic.gdx.math.Vector3 camTarget = new com.badlogic.gdx.math.Vector3();

    private float stateTime = 0f;

    @Override
    public void render() {
        handleInput();

        // Bucle de lógica del juego (aprox 20 tps como en el Presenter original)
        stateTime += Gdx.graphics.getDeltaTime();
        if (stateTime > 0.05f) {
            // Aseguramos que el terreno bajo el cursor esté renderizado conforme nos
            // movemos
            letrain.map.Point cp = model.getCursor().getPosition();
            model.getGroundMap().renderBlock(cp.getX() - 5, cp.getY() - 5, 11, 11);

            trackMaker.makeTracks();
            model.moveLocomotives();
            model.loadAndUnloadTrains();
            model.removeDestroyedTrains();
            stateTime = 0;
        }

        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        // Actualizar instancias desde el modelo
        renderer.clear();
        renderer.visitModel(model);
        renderer.getInstances().add(new ModelInstance(groundModel));
        renderer.getInstances().add(new ModelInstance(gridModel));

        // Centrar cámara en el cursor o en la locomotora seleccionada
        float targetX, targetZ;
        if ((model.getMode() == letrain.mvp.Model.GameMode.DRIVE || model.getMode() == letrain.mvp.Model.GameMode.LINK)
                && model.getSelectedLocomotive() != null) {
            letrain.vehicle.impl.rail.Locomotive selected = model.getSelectedLocomotive();
            targetX = selected.getPosition().getX() + 0.5f;
            targetZ = selected.getPosition().getY() + 0.5f;
        } else {
            letrain.map.Point cursorState = model.getCursor().getPosition();
            targetX = cursorState.getX() + 0.5f;
            targetZ = cursorState.getY() + 0.5f;
        }

        // Interpolación de la posición y del punto de enfoque (camTarget) para suavizar
        // saltos
        cam.position.lerp(new com.badlogic.gdx.math.Vector3(targetX, 6f, targetZ + 6f), 0.05f);
        camTarget.lerp(new com.badlogic.gdx.math.Vector3(targetX, 0, targetZ), 0.05f);
        cam.lookAt(camTarget);
        cam.update();

        modelBatch.begin(cam);
        modelBatch.render(renderer.getInstances(), environment);
        modelBatch.end();

        // Renderizado de etiquetas (2D sobre 3D)
        spriteBatch.begin();
        for (VehicleLabel label : renderer.getLabels()) {
            labelPos.set(label.pos);
            cam.project(labelPos);
            // Dibujamos el texto centrado sobre la posición proyectada
            font.draw(spriteBatch, label.text, labelPos.x - 5, labelPos.y + 10);
        }
        spriteBatch.end();
    }

    private float inputDelay = 0f;

    private void handleInput() {
        float deltaTime = Gdx.graphics.getDeltaTime();
        if (inputDelay > 0)
            inputDelay -= deltaTime;

        // Cuantificador por defecto si no hay ninguno al usar Shift (dibujar)
        boolean shiftPressed = Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SHIFT_LEFT)
                || Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SHIFT_RIGHT);
        boolean ctrlPressed = Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.CONTROL_LEFT)
                || Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.CONTROL_RIGHT);

        if (stateTime == 0) {
            if (shiftPressed || ctrlPressed) {
                if (model.getQuantifier() == 0) {
                    model.setQuantifier(1);
                }
            }
        }

        // Modo Conducción / Link vs Otros Modos
        if (model.getMode() == letrain.mvp.Model.GameMode.DRIVE) {
            handleDriveInput();
        } else if (model.getMode() == letrain.mvp.Model.GameMode.LINK) {
            handleLinkInput();
        } else {
            handleStandardInput(ctrlPressed, shiftPressed);
        }
    }

    private void handleDriveInput() {
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.UP)) {
            if (model.getSelectedLocomotive() != null) {
                model.getSelectedLocomotive().incSpeed();
            }
        }
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.DOWN)) {
            if (model.getSelectedLocomotive() != null) {
                model.getSelectedLocomotive().decSpeed();
            }
        }
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.LEFT)) {
            model.selectPrevLocomotive();
        }
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.RIGHT)) {
            model.selectNextLocomotive();
        }
    }

    private void handleLinkInput() {
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.UP)) {
            if (model.getSelectedLocomotive() != null && model.getSelectedLocomotive().getTrain() != null) {
                model.getSelectedLocomotive().getTrain().setLinkersToJoin(true);
            }
        }
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.DOWN)) {
            if (model.getSelectedLocomotive() != null && model.getSelectedLocomotive().getTrain() != null) {
                model.getSelectedLocomotive().getTrain().setLinkersToJoin(false);
            }
        }
    }

    private void handleStandardInput(boolean ctrlPressed, boolean shiftPressed) {
        // Movimiento Longitudinal (Repetible con retardo controlado)
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.UP)) {
            trackMaker.onChar(new com.googlecode.lanterna.input.KeyStroke(com.googlecode.lanterna.input.KeyType.ArrowUp,
                    ctrlPressed, false, shiftPressed));
            inputDelay = 0.5f;
        } else if (Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.UP) && inputDelay <= 0) {
            trackMaker.onChar(new com.googlecode.lanterna.input.KeyStroke(com.googlecode.lanterna.input.KeyType.ArrowUp,
                    ctrlPressed, false, shiftPressed));
            inputDelay = 0.5f;
        }

        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.DOWN)) {
            trackMaker.onChar(new com.googlecode.lanterna.input.KeyStroke(
                    com.googlecode.lanterna.input.KeyType.ArrowDown, false, false, false));
            inputDelay = 0.5f;
        } else if (Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.DOWN) && inputDelay <= 0) {
            trackMaker.onChar(new com.googlecode.lanterna.input.KeyStroke(
                    com.googlecode.lanterna.input.KeyType.ArrowDown, false, false, false));
            inputDelay = 0.5f;
        }

        // Giro (Solo un paso por pulsación para evitar "girar de más")
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.LEFT)) {
            trackMaker.onChar(new com.googlecode.lanterna.input.KeyStroke(
                    com.googlecode.lanterna.input.KeyType.ArrowLeft, false, false, false));
        }
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.RIGHT)) {
            trackMaker.onChar(new com.googlecode.lanterna.input.KeyStroke(
                    com.googlecode.lanterna.input.KeyType.ArrowRight, false, false, false));
        }

        // 'Space' discreto
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.SPACE)) {
            trackMaker.onChar(new com.googlecode.lanterna.input.KeyStroke(' ', false, false));
        }

        // 'R' discreto
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.R)) {
            if (model.getQuantifier() == 0)
                model.setQuantifier(1);
            trackMaker.onChar(new com.googlecode.lanterna.input.KeyStroke(com.googlecode.lanterna.input.KeyType.ArrowUp,
                    true, false));
        }
    }

    @Override
    public void dispose() {
        modelBatch.dispose();
        groundModel.dispose();
        gridModel.dispose();
        boxModel.dispose();
        renderer.dispose();
        spriteBatch.dispose();
        font.dispose();
    }

    // Presenter implementation
    @Override
    public letrain.mvp.View getView() {
        return this;
    }

    @Override
    public letrain.mvp.Model getModel() {
        return model;
    }

    @Override
    public void onMapPageChanged(letrain.map.Point pos, int cols, int rows) {
    }

    @Override
    public void onChar(com.googlecode.lanterna.input.KeyStroke stroke) {
        trackMaker.onChar(stroke);
    }

    @Override
    public void onEditCommands(String program) {
    }

    @Override
    public String getProgram() {
        return "";
    }

    @Override
    public void onLoadGame(java.io.File file) {
    }

    @Override
    public void onSaveGame(java.io.File file) {
    }

    @Override
    public void onGameModeSelected(letrain.mvp.Model.GameMode mode) {
    }

    @Override
    public void onNewGame() {
    }

    @Override
    public void onPlay() {
    }

    @Override
    public void onExitGame() {
        Gdx.app.exit();
    }

    @Override
    public void setProgram(String program) {
    }

    @Override
    public void onSaveCommands(java.io.File file) {
    }

    @Override
    public void onLoadCommands(java.io.File file) {
    }

    // View implementation
    @Override
    public Point getMapScrollPage() {
        return new Point(0, 0);
    }

    @Override
    public void setMapScrollPage(Point pos) {
    }

    @Override
    public void paint() {
    }

    @Override
    public void clear() {
    }

    @Override
    public void set(int x, int y, String c) {
    }

    @Override
    public void setFgColor(TextColor color) {
    }

    @Override
    public void setBgColor(TextColor color) {
    }

    @Override
    public void setPageOfPos(int x, int y) {
    }

    @Override
    public void clear(int x, int y) {
    }

    @Override
    public void fill(int x, int y, int width, int height, String c) {
    }

    @Override
    public void box(int x, int y, int width, int height) {
    }

    @Override
    public void setStatusBarText(String info) {
    }

    @Override
    public void setInfoBarText(String info) {
    }

    @Override
    public void setMenu(List<GameModeMenuOption> options) {
    }

    @Override
    public void setHelpBarText(String info) {
    }

    @Override
    public boolean isEndOfGame(KeyStroke stroke) {
        return false;
    }

    @Override
    public KeyStroke readKey() {
        return null;
    }

    @Override
    public void setScreen(Screen screen) {
    }

    @Override
    public TextColor getFgColor() {
        return null;
    }

    @Override
    public void showSaveDialog() {
    }

    @Override
    public void showLoadDialog() {
    }

    @Override
    public void showEditDialog() {
    }

    @Override
    public void showExitDialog() {
    }

    @Override
    public int getCols() {
        return 80;
    }

    @Override
    public int getRows() {
        return 24;
    }
}
