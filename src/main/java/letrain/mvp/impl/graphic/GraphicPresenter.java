package letrain.mvp.impl.graphic;

import java.io.File;
import java.util.List;
import java.util.Optional;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.SplitPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.googlecode.lanterna.input.KeyStroke;
import letrain.map.Point;
import letrain.mvp.Model.GameModeMenuOption;
import letrain.mvp.impl.GameSaveService;
import letrain.mvp.impl.RailTrackMaker;
import letrain.mvp.impl.SimulationController;
import letrain.utils.FontManager;
import letrain.utils.ValidationUtils;
import letrain.visitor.gdx3d.Gdx3DRenderer;
import letrain.vehicle.impl.rail.Locomotive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GraphicPresenter extends ApplicationAdapter
        implements letrain.mvp.View, letrain.mvp.Presenter,
        letrain.vehicle.impl.rail.TrainEventListener {
    private static final Logger log = LoggerFactory.getLogger(GraphicPresenter.class);
    private static final String DEFAULT_SAVEGAME_FILENAME = "savegame.dat";
    private com.badlogic.gdx.graphics.PerspectiveCamera cam;
    private ModelBatch modelBatch;
    private ModelBuilder modelBuilder;
    private com.badlogic.gdx.graphics.g3d.decals.DecalBatch decalBatch;
    private java.util.Map<Character, com.badlogic.gdx.graphics.g2d.TextureRegion> glyphRegions = new java.util.HashMap<>();

    private com.badlogic.gdx.graphics.g3d.decals.Decal getGlyphDecal(char c) {
        if (!glyphRegions.containsKey(c)) {
            com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph glyph = font.getData().getGlyph(c);
            if (glyph == null)
                return null;

            com.badlogic.gdx.graphics.g2d.TextureRegion region = new com.badlogic.gdx.graphics.g2d.TextureRegion(
                    font.getRegion().getTexture(),
                    glyph.u, glyph.v, glyph.u2, glyph.v2);
            region.flip(false, true); // Corregir inversión vertical
            glyphRegions.put(c, region);
        }

        com.badlogic.gdx.graphics.g2d.TextureRegion region = glyphRegions.get(c);
        // Force size to 0.5x0.5 world units for readability
        return com.badlogic.gdx.graphics.g3d.decals.Decal.newDecal(0.5f, 0.5f, region, true);
    }

    private Environment environment;

    private letrain.mvp.Model model;
    private Gdx3DRenderer renderer;
    private com.badlogic.gdx.graphics.g3d.Model groundModel;
    private com.badlogic.gdx.graphics.g3d.Model gridModel;
    private com.badlogic.gdx.graphics.g3d.Model boxModel;
    private RailTrackMaker trackMaker;

    private letrain.visitor.gdx3d.Gdx3DResourceContext resourceContext;

    private SpriteBatch spriteBatch;
    private BitmapFont font;

    private Gdx3DHud hud;

    // Audio
    private letrain.audio.AudioController audioController;

    private SimulationController simulationController;

    // Persistence
    private final GameSaveService gameSaveService;

    private CameraController cameraController;
    private Gdx3DInputHandler inputHandler;

    public GraphicPresenter(letrain.mvp.Model model) {
        this.model = ValidationUtils.requireNonNull(model, "model");
        this.resourceContext = new letrain.visitor.gdx3d.Gdx3DResourceContext();
        this.renderer = new Gdx3DRenderer(resourceContext);
        this.trackMaker = new RailTrackMaker(this);
        this.audioController = new letrain.audio.AudioController(model);
        this.gameSaveService = new GameSaveService();
        this.cameraController = new CameraController(model);
        this.inputHandler = new Gdx3DInputHandler(model, this, cameraController, trackMaker, audioController);
        this.simulationController = new SimulationController(model, audioController, trackMaker);

        // Use the initial cursor position as the center for initial ground loading
        letrain.map.Point startPos = model.getCursor().getPosition();
        model.getGroundMap().renderBlock(startPos.getX() - getCols() / 2, startPos.getY() - getRows() / 2, getCols(),
                getRows());

        // Register as listener for audio events
        model.addTrainEventListener(this);
    }

    public Stage getStage() {
        return hud != null ? hud.getStage() : null;
    }

    @Override
    public int getCols() {
        return 80;
    }

    @Override
    public int getRows() {
        return 24;
    }

    @Override
    public void showMessage(String title, String message) {
        if (hud != null) {
            hud.showMessage(title, message);
        }
    }

    @Override
    public void showReferenceGuide() {
        String guide = "LeTrain Automation Reference:\n\n" +
                "Triggers:\n" +
                "  sensor [ID] on train (enter|exit) { actions }\n" +
                "  station [ID] on train (enter|exit) { actions }\n" +
                "  train [ID] on (enter|exit|link|unlink|crash|contact) { actions }\n\n" +
                "Actions:\n" +
                "  train set speed [0-100]\n" +
                "  train invert\n" +
                "  train load / train unload\n" +
                "  train unlink (front|back) [count]\n" +
                "  fork [ID] set (straight|curved|flip)\n\n" +
                "Examples:\n" +
                "  station 1 on train enter { train load; train unlink back 1; }\n" +
                "  train 1 on crash { train set speed 0; }";
        showMessage("Automation Cheat Sheet", guide);
    }

    @Override
    public void create() {
        resourceContext.init();
        renderer.init();
        modelBatch = new ModelBatch();
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.5f, 0.5f, 0.5f, 1f));
        environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f));

        cam = cameraController.init(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

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

        decalBatch = new com.badlogic.gdx.graphics.g3d.decals.DecalBatch(
                new com.badlogic.gdx.graphics.g3d.decals.CameraGroupStrategy(cam));

        boxModel = modelBuilder.createBox(0.8f, 0.8f, 0.8f,
                new com.badlogic.gdx.graphics.g3d.Material(
                        ColorAttribute.createDiffuse(Color.FOREST)),
                Usage.Position | Usage.Normal);

        spriteBatch = new SpriteBatch();
        font = FontManager.loadMonospaceFont(24);
        font.setColor(Color.WHITE);
        font.getData().markupEnabled = true;

        hud = new Gdx3DHud(model, this);

        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(hud.getStage());
        multiplexer.addProcessor(inputHandler);
        Gdx.input.setInputProcessor(multiplexer);
    }

    private float stateTime = 0f;

    @Override
    public letrain.audio.AudioController getAudioController() {
        return audioController;
    }

    @Override
    public void render() {
        // Bucle de lógica del juego (aprox 20 tps como en el Presenter original)
        stateTime += Gdx.graphics.getDeltaTime();
        if (stateTime > 0.05f) {
            // Aseguramos que el terreno bajo el cursor esté renderizado conforme nos
            // movemos
            letrain.map.Point cp = model.getCursor().getPosition();
            int radius = model.getEconomyManager().getViewRadius();
            model.getGroundMap().renderBlock(cp.getX() - radius, cp.getY() - radius, radius * 2 + 1, radius * 2 + 1);

            simulationController.tick();
            inputHandler.update();
            hud.updateIDE();

            stateTime -= 0.05f;
            if (stateTime > 0.05f)
                stateTime = 0.05f; // Evitar espiral de la muerte
        }

        // 1. Calculate factor de interpolación
        float alpha = stateTime / 0.05f;
        if (alpha > 1f)
            alpha = 1f;
        renderer.setAnimationAlpha(alpha);

        // 2. ACTUALIZAR CÁMARA ANTES QUE EL AUDIO
        cameraController.update(alpha);

        // 3. Sincronizar Audio con la posición REAL de la cámara de este frame
        float camAngle = cameraController.getListenerAngle();
        audioController.setListenerPosition(cam.position.x, cam.position.z, cam.position.y, camAngle);
        audioController.update();

        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        // Actualizar instancias desde el modelo
        renderer.clear();
        renderer.visitModel(model, cam);
        modelBatch.begin(cam);
        modelBatch.render(renderer.getInstances(), environment);
        // Render the background table slightly below ground level
        ModelInstance tableInstance = new ModelInstance(groundModel);
        tableInstance.transform.setToTranslation(0, -0.02f, 0);
        modelBatch.render(tableInstance, environment);
        modelBatch.end();

        // Renderizado de Etiquetas 3D (Decals)
        if (!renderer.getLabels().isEmpty()) {
            for (letrain.visitor.gdx3d.Gdx3DRenderer.VehicleLabel label : renderer.getLabels()) {
                if (label.text == null || label.text.isEmpty())
                    continue;

                float baseCharSpacing = 0.25f; // Espaciado base
                float charSpacing = baseCharSpacing * label.scale;
                float totalWidth = label.text.length() * charSpacing;
                float startOffset = -totalWidth / 2f + charSpacing / 2f;

                // Comportamiento de orientación:
                // El 'up' vector define la dirección "hacia arriba" del carácter.
                // El 'horizontal' vector define la dirección en la que se disponen los
                // caracteres (el "avance" del texto).

                com.badlogic.gdx.math.Vector3 up = label.up != null ? label.up : com.badlogic.gdx.math.Vector3.Y;

                // Vector horizontal paralelo a la cara
                com.badlogic.gdx.math.Vector3 horizontal = new com.badlogic.gdx.math.Vector3();

                // Si la normal es vertical (p.ej. número ID en techo), el horizontal debe ser
                // perpendicular al 'up' vector (que suele ser el forward).
                // Horizontal = Up x Normal
                if (Math.abs(label.normal.y) > 0.9f) {
                    horizontal.set(up).crs(label.normal).nor();
                } else {
                    // Para caras laterales, mantenemos la lógica de ser perpendicular a la normal
                    // en el plano XZ
                    horizontal.set(label.normal.z, 0, -label.normal.x).nor();
                    if (horizontal.len() < 0.1f) {
                        horizontal.set(1, 0, 0);
                    }
                }

                for (int i = 0; i < label.text.length(); i++) {
                    char c = label.text.charAt(i);
                    com.badlogic.gdx.graphics.g3d.decals.Decal d = getGlyphDecal(c);
                    if (d != null) {
                        d.setColor(label.color != null ? label.color : com.badlogic.gdx.graphics.Color.WHITE);
                        d.setScale(label.scale);

                        // Posición con desplazamiento horizontal para centrar el texto
                        float offset = startOffset + i * charSpacing;
                        com.badlogic.gdx.math.Vector3 charPos = label.pos.cpy()
                                .add(horizontal.x * offset, horizontal.y * offset, horizontal.z * offset);
                        d.setPosition(charPos);

                        // Orientar el decal para que mire hacia afuera de la superficie
                        // (up ya ha sido definido arriba del bucle de glifos)

                        // Si la normal es vertical (0,1,0) y el up también es Y o paralelo, lookAt
                        // fallará.
                        // En ese caso, necesitamos un 'up' que esté en el plano horizontal si estamos
                        // mirando arriba.
                        d.lookAt(charPos.cpy().add(label.normal), up);
                        decalBatch.add(d);
                    }
                }
            }
            Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
            Gdx.gl.glDepthFunc(GL20.GL_LEQUAL);
            Gdx.gl.glDepthMask(false);
            decalBatch.flush();
            Gdx.gl.glDepthMask(true); // Restore depth writing
        }

        // 2-PASS SHELL RENDERING for Transparent World (Mountains/Tunnels)
        if (!renderer.getTransparentInstances().isEmpty()) {
            // PASS 1: Depth only (Fill Z-buffer with the closest surface)
            Gdx.gl.glColorMask(false, false, false, false);
            Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);
            Gdx.gl.glDepthFunc(GL20.GL_LESS);
            Gdx.gl.glDepthMask(true);

            modelBatch.begin(cam);
            modelBatch.render(renderer.getTransparentInstances(), environment);
            modelBatch.end();

            // PASS 2: Color only (Draw the closest surface only)
            Gdx.gl.glColorMask(true, true, true, true);
            Gdx.gl.glDepthMask(false); // No more depth writing
            Gdx.gl.glDepthFunc(GL20.GL_LEQUAL); // Only draw what matches the Z-buffer

            modelBatch.begin(cam);
            modelBatch.render(renderer.getTransparentInstances(), environment);
            modelBatch.end();

            // Reset states
            Gdx.gl.glDepthMask(true);
            Gdx.gl.glDepthFunc(GL20.GL_LESS);
        }

        spriteBatch.begin();
        // (Labels 2D removidos)
        spriteBatch.end();

        // Renderizado de UI (Menú)
        hud.render(Gdx.graphics.getDeltaTime());
    }

    public CameraController getCameraController() {
        return cameraController;
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
        inputHandler.onChar(stroke);
    }

    @Override
    public void onKeyUp(KeyStroke stroke) {
        inputHandler.onKeyUp(stroke);
    }

    @Override
    public void onEditCommands(String program) {
        List<String> errors = model.setProgram(program);
        handleScriptErrors(errors);
    }

    private void handleScriptErrors(List<String> errors) {
        if (errors != null && !errors.isEmpty()) {
            String combinedErrors = String.join("\n", errors);
            showMessage("Script Errors", combinedErrors);
        }
    }

    @Override
    public String getProgram() {
        return model.getProgram();
    }

    @Override
    public void onGameModeSelected(letrain.mvp.Model.GameMode mode) {
        if (mode == letrain.mvp.Model.GameMode.PROGRAM) {
            hud.showIDE();
        }
    }

    @Override
    public void onNewGame() {
        // Not used in 3D view (handled by presenter/model directly).
    }

    @Override
    public void onPlay() {
        // Not used in 3D view (handled by presenter/model directly).
    }

    @Override
    public void setProgram(String program) {
        // Not used in 3D view (program is set via onEditCommands/onLoadCommands).
    }

    @Override
    public void onSaveCommands(java.io.File file) {
        if (file != null) {
            try (java.io.FileWriter writer = new java.io.FileWriter(file)) {
                writer.write(model.getProgram());
                log.info("Commands saved successfully to {}", file.getAbsolutePath());
            } catch (java.io.IOException e) {
                log.error("Error saving commands to {}", file.getAbsolutePath(), e);
            }
        }
    }

    @Override
    public void onLoadCommands(java.io.File file) {
        if (file != null && file.exists()) {
            try (java.util.Scanner scanner = new java.util.Scanner(file)) {
                StringBuilder sb = new StringBuilder();
                while (scanner.hasNextLine()) {
                    sb.append(scanner.nextLine()).append("\n");
                }
                List<String> errors = model.setProgram(sb.toString());
                handleScriptErrors(errors);
                log.info("Commands loaded successfully from {}", file.getAbsolutePath());
            } catch (java.io.FileNotFoundException e) {
                log.error("Error loading commands from {}", file.getAbsolutePath(), e);
            }
        }
    }

    // View implementation
    @Override
    public Point getMapScrollPage() {
        return new Point(0, 0);
    }

    @Override
    public void setMapScrollPage(Point pos) {
        // Not used in 3D view.
    }

    @Override
    public void paint() {
        // Not used in 3D view (LibGDX render loop drives drawing).
    }

    @Override
    public void clear() {
        // Not used in 3D view.
    }

    @Override
    public void set(int x, int y, String c) {
        // Not used in 3D view.
    }

    public void setFgColor(com.googlecode.lanterna.TextColor color) {
        // Not used in 3D view.
    }

    public void setBgColor(com.googlecode.lanterna.TextColor color) {
        // Not used in 3D view.
    }

    @Override
    public void setPageOfPos(int x, int y) {
        // Not used in 3D view.
    }

    @Override
    public void clear(int x, int y) {
        // Not used in 3D view.
    }

    @Override
    public void fill(int x, int y, int width, int height, String c) {
        // Not used in 3D view.
    }

    @Override
    public void box(int x, int y, int width, int height) {
        // Not used in 3D view.
    }

    @Override
    public void setStatusBarText(String info) {
        // Not used in 3D view.
    }

    @Override
    public void setInfoBarText(String info) {
        // Not used in 3D view.
    }

    @Override
    public void setMenu(List<GameModeMenuOption> options) {
        // Not used in 3D view (menu is rendered via Scene2D).
        if (hud != null) {
            hud.updateMenuButtons();
        }
    }

    @Override
    public void setHelpBarText(String info) {
        // Not used in 3D view.
    }

    public boolean isEndOfGame(com.googlecode.lanterna.input.KeyStroke stroke) {
        return false;
    }

    public com.googlecode.lanterna.input.KeyStroke readKey() {
        // Not used in 3D view (input handled by LibGDX).
        return null;
    }

    public void setScreen(com.googlecode.lanterna.screen.Screen screen) {
        // Not used in 3D view.
    }

    public com.googlecode.lanterna.TextColor getFgColor() {
        // Not used in 3D view.
        return null;
    }

    @Override
    public void onSaveGame(File file) {
        if (file == null) {
            log.warn("Ignoring save request with null file");
            return;
        }
        boolean ok = gameSaveService.save(model, file);
        if (!ok) {
            showMessage("Save Error", "Could not save game to\n" + file.getAbsolutePath());
        }
    }

    private void applyLoadedModel(letrain.mvp.impl.Model loadedModel, File file) {
        this.model = ValidationUtils.requireNonNull(loadedModel, "loadedModel");

        log.info("Game loaded successfully from {}", file.getAbsolutePath());

        // Stop previous sounds
        if (this.audioController != null) {
            this.audioController.stop();
        }

        // Refresh all references that depend on the model
        this.trackMaker = new RailTrackMaker(this);
        this.audioController = new letrain.audio.AudioController(model);
        this.cameraController = new CameraController(model);
        this.cam = cameraController.init(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        this.inputHandler = new Gdx3DInputHandler(model, this, cameraController, trackMaker, audioController);
        this.simulationController = new SimulationController(model, audioController, trackMaker);
        this.renderer = new letrain.visitor.gdx3d.Gdx3DRenderer(resourceContext);
        this.decalBatch = new com.badlogic.gdx.graphics.g3d.decals.DecalBatch(
                new com.badlogic.gdx.graphics.g3d.decals.CameraGroupStrategy(cam));

        // Re-initialize HUD with new model
        this.hud = new Gdx3DHud(model, this);
        InputMultiplexer multiplexer = (InputMultiplexer) Gdx.input.getInputProcessor();
        multiplexer.getProcessors().clear();
        multiplexer.addProcessor(hud.getStage());
        multiplexer.addProcessor(inputHandler);

        // Register View as listener
        model.addTrainEventListener(this);

        // Render initial ground around cursor
        letrain.map.Point startPos = model.getCursor().getPosition();
        model.getGroundMap().renderBlock(startPos.getX() - getCols() / 2, startPos.getY() - getRows() / 2, getCols(),
                getRows());


        // Re-attach stations as listeners to trains they are hosting
        for (letrain.vehicle.impl.rail.Locomotive loco : model.getLocomotives()) {
            letrain.vehicle.impl.rail.Train train = loco.getTrain();
            if (train != null && train.getStationId() != 0) {
                for (letrain.track.Station station : model.getStations()) {
                    if (station.getId() == train.getStationId()) {
                        train.addTrainEventListener(station);
                        break;
                    }
                }
            }
        }
    }

    @Override
    public void onLoadGame(File file) {
        Optional<letrain.mvp.impl.Model> maybeModel = gameSaveService.load(file);
        if (maybeModel.isEmpty()) {
            if (file != null) {
                showMessage("Load Error", "Could not load game from\n" + file.getAbsolutePath());
            } else {
                showMessage("Load Error", "Could not load game: invalid file");
            }
            return;
        }

        letrain.mvp.impl.Model loadedModel = maybeModel.get();
        try {
            applyLoadedModel(loadedModel, file);
        } catch (Exception e) {
            log.error("Critical error updating model reference after loading game from {}",
                    file != null ? file.getAbsolutePath() : "<null>",
                    e);
            showMessage("Load Error", "A critical error occurred while applying loaded game state.");
        }
    }

    @Override
    public void showSaveDialog() {
        hud.showFileDialog("Save Game", DEFAULT_SAVEGAME_FILENAME, (text) -> {
            if (text != null && !text.trim().isEmpty()) {
                File file = new File(text);
                log.info("Saving game to {}", file.getAbsolutePath());
                onSaveGame(file);
            }
        });
    }

    @Override
    public void showLoadDialog() {
        hud.showFileDialog("Load Game", DEFAULT_SAVEGAME_FILENAME, (text) -> {
            if (text != null && !text.trim().isEmpty()) {
                File file = new File(text);
                log.info("Loading game from {}", file.getAbsolutePath());
                if (file.exists()) {
                    onLoadGame(file);
                } else {
                    log.warn("Savegame file not found: {}", text);
                }
            }
        });
    }

    @Override
    public String getGameObjectsReport() {
        return model.getGameObjectsReport();
    }

    @Override
    public java.util.List<String> getEventLogEntries() {
        return model.getEventLogManager().getEntries();
    }

    @Override
    public void showIDE() {
        hud.showIDE();
    }

    @Override
    public void showExitDialog() {
        showMessage("Exit", "Use ALT+F4 to exit the application.");
    }

    @Override
    public void resize(int width, int height) {
        hud.resize(width, height);
        cameraController.resize(width, height);
    }

    @Override
    public void dispose() {
        if (audioController != null) {
            audioController.stop();
        }
        if (resourceContext != null) {
            resourceContext.dispose();
        }
        if (modelBatch != null)
            modelBatch.dispose();
        if (decalBatch != null)
            decalBatch.dispose();
        if (spriteBatch != null)
            spriteBatch.dispose();
        if (font != null)
            font.dispose();
        if (hud != null)
            hud.dispose();
        if (groundModel != null)
            groundModel.dispose();
        if (gridModel != null)
            gridModel.dispose();
        if (boxModel != null)
            boxModel.dispose();

        // Force exit to ensure no lingering threads (e.g. console input) keep JVM alive
        System.exit(0);
    }

    @Override
    public void onSpeedChanged(int speed) {
        // GraphicPresenter handles speed changes via polling/sync in render loop
    }

    @Override
    public void onSenseChanged(boolean forward) {
        // GraphicPresenter handles sense changes via polling/sync in render loop
    }

    @Override
    public void onCrash(letrain.vehicle.impl.rail.Train train, Point pos, int speed) {
        audioController.playOneShot("link", pos.getX(), pos.getY());
    }

    @Override
    public void onContact(letrain.vehicle.impl.rail.Train train, Point pos, int speed) {
        if (audioController != null && pos != null) {
            audioController.playOneShot("link", (float) pos.getX(), (float) pos.getY());
            // Immediately stop audio for all locomotives in the train that hit something
            // This forces them to 'stall' and stop moving sounds instantly.
            for (letrain.vehicle.impl.rail.Locomotive loco : model.getLocomotives()) {
                if (loco.getTrain() != null && (loco.getSpeed() > 0 || loco.getTargetSpeed() > 0)) {
                    if (Point.distance(loco.getPosition(), pos) < 2.0) {
                        audioController.stopSynthesizer(loco.getId());
                    }
                }
            }
        }
    }

    @Override
    public void onLink(letrain.vehicle.impl.rail.Train train) {
        audioController.playOneShot("link", model.getCursor().getPosition().getX(),
                model.getCursor().getPosition().getY());
    }

    @Override
    public void onUnlink(letrain.vehicle.impl.rail.Train train) {
        audioController.playOneShot("link", model.getCursor().getPosition().getX(),
                model.getCursor().getPosition().getY());
    }

    public void updateHUD() {
        if (hud != null) {
            hud.updateHUD();
        }
    }

    @Override
    public void onExitGame() {
        dispose();
        Gdx.app.exit();
        System.exit(0);
    }
}
