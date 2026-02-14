package letrain.mvp.impl;

import java.util.List;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.screen.Screen;
import letrain.map.Point;
import letrain.mvp.impl.Model.GameModeMenuOption;
import letrain.visitor.Gdx3DRenderer;

public class Gdx3DView extends ApplicationAdapter implements letrain.mvp.View, letrain.mvp.Presenter {
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

        // Rejilla para orientación
        modelBuilder.begin();
        com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder mpb = modelBuilder.part("grid", GL20.GL_LINES,
                Usage.Position | Usage.ColorUnpacked, new com.badlogic.gdx.graphics.g3d.Material());
        mpb.setColor(Color.LIGHT_GRAY);
        for (int i = -100; i <= 100; i += 5) {
            mpb.line(i, 0.01f, -100, i, 0.01f, 100);
            mpb.line(-100, 0.01f, i, 100, 0.01f, i);
        }
        gridModel = modelBuilder.end();

        boxModel = modelBuilder.createBox(0.8f, 0.8f, 0.8f,
                new com.badlogic.gdx.graphics.g3d.Material(
                        ColorAttribute.createDiffuse(Color.FOREST)),
                Usage.Position | Usage.Normal);
    }

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
        renderer.getInstances().add(new ModelInstance(groundModel));
        renderer.getInstances().add(new ModelInstance(gridModel));
        renderer.visitModel(model);

        // Centrar cámara en el cursor (más cerca para mejor visibilidad)
        letrain.map.Point cursorState = model.getCursor().getPosition();
        cam.position.lerp(new com.badlogic.gdx.math.Vector3(cursorState.getX(), 5f, cursorState.getY() + 5f), 0.1f);
        cam.lookAt(cursorState.getX(), 0, cursorState.getY());
        cam.update();

        modelBatch.begin(cam);
        for (ModelInstance instance : renderer.getInstances()) {
            modelBatch.render(instance, environment);
        }
        modelBatch.end();
    }

    private void handleInput() {
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

        // Movimiento Longitudinal (Repetible)
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.UP)
                || (Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.UP) && stateTime == 0)) {
            trackMaker.onChar(new com.googlecode.lanterna.input.KeyStroke(com.googlecode.lanterna.input.KeyType.ArrowUp,
                    ctrlPressed, false, shiftPressed));
        }
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.DOWN)
                || (Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.DOWN) && stateTime == 0)) {
            trackMaker.onChar(new com.googlecode.lanterna.input.KeyStroke(
                    com.googlecode.lanterna.input.KeyType.ArrowDown, false, false, false));
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

        // 'T' para crear una locomotora
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.T)) {
            letrain.track.rail.RailTrack track = model.getCursorRailTrack();
            if (track != null && track.getLinker() == null) {
                letrain.vehicle.impl.rail.Locomotive locomotive = new letrain.vehicle.impl.rail.Locomotive(
                        model.nextLocomotiveId(), "L");
                letrain.vehicle.impl.rail.Train train = new letrain.vehicle.impl.rail.Train(model.nextTrainId());
                train.pushBack(locomotive);
                train.setDirectorLinker(locomotive);
                model.addLocomotive(locomotive);
                track.enterLinkerFromDir(model.getCursor().getDir().inverse(), locomotive);
            }
        }
    }

    @Override
    public void dispose() {
        modelBatch.dispose();
        boxModel.dispose();
        groundModel.dispose();
        gridModel.dispose();
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
