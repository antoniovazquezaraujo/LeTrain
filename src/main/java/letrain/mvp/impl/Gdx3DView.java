package letrain.mvp.impl;

import java.io.File;
import java.util.List;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.VertexAttributes.Usage;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
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
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextArea;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.input.KeyStroke;
import letrain.map.Point;
import letrain.mvp.Model.GameModeMenuOption;
import letrain.vehicle.impl.rail.Locomotive;
import letrain.vehicle.impl.rail.Train;
import letrain.visitor.Gdx3DRenderer;

public class Gdx3DView extends ApplicationAdapter
        implements letrain.mvp.View, letrain.mvp.Presenter, com.badlogic.gdx.InputProcessor,
        letrain.vehicle.impl.rail.TrainEventListener {
    private PerspectiveCamera cam;
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

    private final letrain.mvp.impl.Model model;
    private final Gdx3DRenderer renderer;
    private com.badlogic.gdx.graphics.g3d.Model groundModel;
    private com.badlogic.gdx.graphics.g3d.Model gridModel;
    private com.badlogic.gdx.graphics.g3d.Model boxModel;
    private RailTrackMaker trackMaker;

    private SpriteBatch spriteBatch;
    private BitmapFont font;

    private Stage stage;
    private Skin skin;
    private Table menuTable;
    private Label descLabel;
    private Label globalHelpLabel;
    private Label balanceLabel;
    private Label incomeLabel;
    private Label expensesLabel;
    private NotchLever notchLever;
    private ScrollPane logScrollPane;
    private Table logTable;
    private boolean showLog = false;
    private com.badlogic.gdx.graphics.glutils.ShapeRenderer shapeRenderer;

    // Audio
    private letrain.audio.AudioController audioController;

    // Multi-digit selection state (Point 20)
    private int forkIdAccumulator = 0;
    private int semaphoreIdAccumulator = 0;
    private int stationIdAccumulator = 0;
    private int locomotiveIdAccumulator = 0;
    private long forkInputTimeout = 0;
    private long semaphoreInputTimeout = 0;
    private long stationInputTimeout = 0;
    private long locomotiveInputTimeout = 0;

    public Gdx3DView(letrain.mvp.impl.Model model) {
        this.model = model;
        this.renderer = new Gdx3DRenderer();
        this.trackMaker = new RailTrackMaker(this);
        this.audioController = new letrain.audio.AudioController(model);

        // Use the initial cursor position as the center for initial ground loading
        letrain.map.Point startPos = model.getCursor().getPosition();
        model.getGroundMap().renderBlock(startPos.getX() - getCols() / 2, startPos.getY() - getRows() / 2, getCols(),
                getRows());

        // Register as listener for audio events
        model.addTrainEventListener(this);
    }

    @Override
    public void create() {
        renderer.init();
        modelBatch = new ModelBatch();
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.5f, 0.5f, 0.5f, 1f));
        environment.add(new DirectionalLight().set(0.8f, 0.8f, 0.8f, -1f, -0.8f, -0.2f));

        cam = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        letrain.map.Point startPos = model.getCursor().getPosition();
        cam.position.set(startPos.getX() + 20f, 20f, startPos.getY() + 20f);
        cam.lookAt(startPos.getX() + 0.5f, 0, startPos.getY() + 0.5f);
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

        decalBatch = new com.badlogic.gdx.graphics.g3d.decals.DecalBatch(
                new com.badlogic.gdx.graphics.g3d.decals.CameraGroupStrategy(cam));

        boxModel = modelBuilder.createBox(0.8f, 0.8f, 0.8f,
                new com.badlogic.gdx.graphics.g3d.Material(
                        ColorAttribute.createDiffuse(Color.FOREST)),
                Usage.Position | Usage.Normal);

        spriteBatch = new SpriteBatch();
        font = new BitmapFont();
        font.setColor(Color.WHITE);
        font.getData().setScale(1.5f);
        font.getData().markupEnabled = true;

        initUI();
        shapeRenderer = new com.badlogic.gdx.graphics.glutils.ShapeRenderer();

        InputMultiplexer multiplexer = new InputMultiplexer();
        multiplexer.addProcessor(stage);
        multiplexer.addProcessor(this);
        Gdx.input.setInputProcessor(multiplexer);
    }

    private void initUI() {
        stage = new Stage(new ScreenViewport());
        skin = new Skin();

        // Crear una skin procedimental básica
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        skin.add("white", new Texture(pixmap));

        BitmapFont uiFont = new BitmapFont();
        uiFont.getData().markupEnabled = true;
        skin.add("default", uiFont);

        // High-resolution fonts for HUD
        BitmapFont smallFont, mediumFont, largeFont, tinyFont;
        File fontFile = new File("C:/Windows/Fonts/arial.ttf");
        if (fontFile.exists()) {
            FreeTypeFontGenerator generator = new FreeTypeFontGenerator(Gdx.files.absolute(fontFile.getAbsolutePath()));
            FreeTypeFontParameter parameter = new FreeTypeFontParameter();
            parameter.size = 18;
            parameter.magFilter = Texture.TextureFilter.Linear;
            parameter.minFilter = Texture.TextureFilter.Linear;
            smallFont = generator.generateFont(parameter);

            parameter.size = 12; // Even smaller for global help
            tinyFont = generator.generateFont(parameter);

            parameter.size = 26; // For income/expenses
            mediumFont = generator.generateFont(parameter);

            parameter.size = 52; // For balance (approx double)
            largeFont = generator.generateFont(parameter);
            generator.dispose();
        } else {
            // Fallback to default if font not found
            smallFont = new BitmapFont();
            tinyFont = new BitmapFont();
            tinyFont.getData().setScale(0.8f);
            mediumFont = new BitmapFont();
            mediumFont.getData().setScale(1.5f);
            largeFont = new BitmapFont();
            largeFont.getData().setScale(3.0f);
        }

        smallFont.getData().markupEnabled = true;
        tinyFont.getData().markupEnabled = true;
        mediumFont.getData().markupEnabled = true;
        largeFont.getData().markupEnabled = true;

        skin.add("small-font", smallFont);
        skin.add("tiny-font", tinyFont);
        skin.add("medium-font", mediumFont);
        skin.add("large-font", largeFont);

        // TextButton Style (Menu Buttons)
        TextButton.TextButtonStyle textButtonStyle = new TextButton.TextButtonStyle();
        textButtonStyle.up = null; // Transparent by default
        textButtonStyle.down = skin.newDrawable("white", Color.CYAN);
        textButtonStyle.checked = skin.newDrawable("white", Color.BLACK); // Black background for selected
        textButtonStyle.over = skin.newDrawable("white", new Color(0.15f, 0.15f, 0.15f, 1f));
        textButtonStyle.font = skin.getFont("default");
        textButtonStyle.fontColor = Color.WHITE;
        textButtonStyle.downFontColor = Color.WHITE;
        textButtonStyle.overFontColor = Color.WHITE;
        textButtonStyle.checkedFontColor = Color.WHITE;
        skin.add("default", textButtonStyle);

        // Label Style
        Label.LabelStyle labelStyle = new Label.LabelStyle();
        labelStyle.font = uiFont;
        labelStyle.fontColor = Color.WHITE;
        skin.add("default", labelStyle);

        Label.LabelStyle hudLabelStyle = new Label.LabelStyle();
        hudLabelStyle.font = skin.getFont("large-font");
        hudLabelStyle.fontColor = Color.CYAN;
        skin.add("hud", hudLabelStyle);

        Label.LabelStyle largeLabelStyle = new Label.LabelStyle();
        largeLabelStyle.font = skin.getFont("large-font");
        largeLabelStyle.fontColor = Color.WHITE;
        skin.add("large", largeLabelStyle);

        Label.LabelStyle mediumLabelStyle = new Label.LabelStyle();
        mediumLabelStyle.font = skin.getFont("medium-font");
        mediumLabelStyle.fontColor = Color.WHITE;
        skin.add("medium", mediumLabelStyle);

        Label.LabelStyle titleLabelStyle = new Label.LabelStyle();
        titleLabelStyle.font = uiFont;
        titleLabelStyle.fontColor = Color.GOLDENROD;
        skin.add("title", titleLabelStyle);

        Label.LabelStyle smallLabelStyle = new Label.LabelStyle();
        smallLabelStyle.font = skin.getFont("small-font");
        smallLabelStyle.fontColor = Color.WHITE;
        skin.add("small", smallLabelStyle);

        Label.LabelStyle tinyLabelStyle = new Label.LabelStyle();
        tinyLabelStyle.font = skin.getFont("tiny-font");
        tinyLabelStyle.fontColor = Color.LIGHT_GRAY;
        skin.add("tiny", tinyLabelStyle);

        // Window Style
        Window.WindowStyle windowStyle = new Window.WindowStyle();
        windowStyle.titleFont = uiFont;
        windowStyle.background = skin.newDrawable("white", Color.BLACK);
        windowStyle.titleFontColor = Color.WHITE;
        skin.add("default", windowStyle);

        // TextField/TextArea Style
        TextField.TextFieldStyle textFieldStyle = new TextField.TextFieldStyle();
        textFieldStyle.font = uiFont;
        textFieldStyle.fontColor = Color.WHITE;
        textFieldStyle.background = skin.newDrawable("white", new Color(0.2f, 0.2f, 0.2f, 1f));
        textFieldStyle.cursor = skin.newDrawable("white", Color.WHITE);
        textFieldStyle.selection = skin.newDrawable("white", Color.BLUE);
        skin.add("default", textFieldStyle);

        // ScrollPane Style
        ScrollPane.ScrollPaneStyle scrollPaneStyle = new ScrollPane.ScrollPaneStyle();
        scrollPaneStyle.background = skin.newDrawable("white", Color.DARK_GRAY);
        scrollPaneStyle.vScroll = skin.newDrawable("white", Color.GRAY);
        scrollPaneStyle.vScrollKnob = skin.newDrawable("white", Color.LIGHT_GRAY);
        skin.add("default", scrollPaneStyle);

        // ProgressBar Style
        com.badlogic.gdx.scenes.scene2d.ui.ProgressBar.ProgressBarStyle progressBarStyle = new com.badlogic.gdx.scenes.scene2d.ui.ProgressBar.ProgressBarStyle();
        progressBarStyle.background = skin.newDrawable("white", Color.DARK_GRAY);
        progressBarStyle.knobBefore = skin.newDrawable("white", Color.CYAN);
        skin.add("default-horizontal", progressBarStyle);

        // Bottom UI Container
        Table mainBottomTable = new Table();
        mainBottomTable.setFillParent(true);
        mainBottomTable.bottom();
        stage.addActor(mainBottomTable);

        menuTable = new Table();
        // menuTable is populated in updateMenuButtons()

        descLabel = new Label("", skin);
        descLabel.setWrap(true);
        descLabel.setAlignment(com.badlogic.gdx.utils.Align.center);
        globalHelpLabel = new Label(
                "[LIGHT_GRAY][ALT+UP/DOWN / MOUSE WHEEL]: ZOOM | [ALT+LEFT/RIGHT]: ROTATE CAMERA | [C]: CHANGE CAMERA VIEW[]",
                skin,
                "tiny");
        globalHelpLabel.setWrap(true);
        globalHelpLabel.setAlignment(com.badlogic.gdx.utils.Align.center);

        Table bottomContainer = new Table();
        bottomContainer.setBackground(skin.newDrawable("white", new Color(0, 0, 0, 0.6f)));
        bottomContainer.pad(10);

        // Notch Lever (Repositioned to bottom-left of menu area)
        notchLever = new NotchLever();
        bottomContainer.add(notchLever).size(100, 100).padLeft(10).padRight(10).top().bottom();

        // Finances Area (Now between NotchLever and menu)
        Table financeArea = new Table();

        // Create separate styles to avoid sharing and overwriting skin styles
        Label.LabelStyle incomeStyle = new Label.LabelStyle(skin.get("small", Label.LabelStyle.class));
        incomeStyle.fontColor = com.badlogic.gdx.graphics.Color.GREEN;
        incomeLabel = new Label("+ $0", incomeStyle);

        Label.LabelStyle expensesStyle = new Label.LabelStyle(skin.get("small", Label.LabelStyle.class));
        expensesStyle.fontColor = com.badlogic.gdx.graphics.Color.RED;
        expensesLabel = new Label("- $0", expensesStyle);

        Label.LabelStyle balanceStyle = new Label.LabelStyle(skin.get("medium", Label.LabelStyle.class));
        balanceLabel = new Label("$ 0", balanceStyle);

        Table subFinance = new Table();
        subFinance.add(incomeLabel).padRight(15);
        subFinance.add(expensesLabel);

        financeArea.add(subFinance).right().row();
        financeArea.add(balanceLabel).right().padTop(5);

        bottomContainer.add(financeArea).width(200).left().bottom().padLeft(20).padRight(20);

        Table labelArea = new Table();
        labelArea.add(menuTable).padBottom(5).row();
        labelArea.add(descLabel).fillX().expandX().padBottom(2).row();
        labelArea.add(globalHelpLabel).fillX().expandX().padBottom(2).row();

        bottomContainer.add(labelArea).expand().fill().padLeft(20).padRight(10);

        mainBottomTable.add(bottomContainer).expandX().fillX();

        updateMenuButtons();

        // Log Viewer UI
        logTable = new Table();
        logTable.top().left();
        logScrollPane = new ScrollPane(logTable, skin);
        logScrollPane.setFadeScrollBars(false);
        logScrollPane.setVisible(false);
        logScrollPane.setScrollingDisabled(true, false);

        Table logContainer = new Table();
        logContainer.setFillParent(true);
        logContainer.top().left().pad(20);
        logContainer.add(logScrollPane).width(500).height(400).top().left();
        stage.addActor(logContainer);
    }

    private String getMenuButtonText(String rawName, boolean isEnabled) {
        String cleanName = rawName.replace("&", "");
        if (cleanName.isEmpty()) {
            return "";
        }

        // Always capitalize first letter
        String capitalized = cleanName.substring(0, 1).toUpperCase() + cleanName.substring(1);

        if (!rawName.contains("&")) {
            return isEnabled ? "[WHITE]" + capitalized + "[]" : "[LIGHT_GRAY]" + capitalized + "[]";
        }

        int hotkeyIndex = rawName.indexOf("&");
        String prefix = capitalized.substring(0, hotkeyIndex);
        char hotkeyChar = capitalized.charAt(hotkeyIndex);
        String suffix = capitalized.substring(hotkeyIndex + 1);

        if (isEnabled) {
            // Enabled: White text with Cyan hotkey
            return "[WHITE]" + prefix + "[]" + "[CYAN]" + hotkeyChar + "[]" + "[WHITE]" + suffix + "[]";
        } else {
            // When disabled, everything is gray, no blue hotkey
            return "[LIGHT_GRAY]" + capitalized + "[]";
        }
    }

    private void updateMenuButtons() {
        menuTable.clearChildren();
        for (GameModeMenuOption option : model.getMenuModel()) {
            boolean isEnabled = option.enabledIf().get();
            String formattedName = getMenuButtonText(option.gameModeName(), isEnabled);

            TextButton button = new TextButton(formattedName, skin, "default");
            button.setName(option.gameModeName().replace("&", "").toLowerCase());
            button.setDisabled(!isEnabled);

            button.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (!button.isDisabled()) {
                        letrain.mvp.Model.GameMode newMode = option.doWhenSelected().get();
                        model.setMode(newMode);

                        // Inicialización de estados al cambiar de modo mediante botones
                        if (newMode == letrain.mvp.Model.GameMode.LINK) {
                            if (model.getSelectedLocomotive() != null
                                    && model.getSelectedLocomotive().getTrain() != null) {
                                model.getSelectedLocomotive().getTrain().resetLinkState();
                            }
                        } else if (newMode == letrain.mvp.Model.GameMode.UNLINK) {
                            if (model.getSelectedLocomotive() != null
                                    && model.getSelectedLocomotive().getTrain() != null) {
                                model.getSelectedLocomotive().getTrain().resetUnlinkState();
                            }
                        }
                    }
                }
            });

            menuTable.add(button).pad(5).height(30);
        }
    }

    @Override
    public boolean keyTyped(char character) {
        // --- 1. ABSOLUTE GLOBAL CAMERA TOGGLE ---
        if (character == 'c' || character == 'C') {
            if (cameraMode == CameraMode.ORBIT) {
                // Skip CAB mode if there are no locomotives to follow
                if (!model.getLocomotives().isEmpty()) {
                    cameraMode = CameraMode.CAB;
                } else {
                    cameraMode = CameraMode.MAP;
                }
            } else if (cameraMode == CameraMode.CAB) {
                cameraMode = CameraMode.MAP;
            } else {
                cameraMode = CameraMode.ORBIT;
            }
            return true;
        }

        if (model.getMode() == letrain.mvp.Model.GameMode.TRAINS) {
            if (Character.isLetter(character)) {
                createVehicle(character);
                return true;
            }
            // 's' key handler removed (moved to HOME key in keyDown)
        } else if (model.getMode() == letrain.mvp.Model.GameMode.RAILS) {
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

        // All key shortcuts are now centralized in onChar() to allow case-insensitive
        // handling
        // and consistent behavior across inputs.

        // Pass any other character input to the presenter/trackmaker
        boolean ctrlPressed = Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.CONTROL_LEFT)
                || Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.CONTROL_RIGHT);
        boolean altPressed = Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.ALT_LEFT)
                || Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.ALT_RIGHT);

        if (!Character.isISOControl(character)) {
            ((letrain.mvp.GameViewListener) this)
                    .onChar(new com.googlecode.lanterna.input.KeyStroke(character, ctrlPressed, altPressed));
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
        // Avanzar el cursor automáticamente para facilitar la creación de trenes largos
        model.getCursor().getPosition().move(cursorDir);
    }

    @Override
    public boolean keyDown(int keycode) {
        // Interceptar Alt+flechas para controles de cámara (consumir evento)
        // Alt check removed to allow processing in onChar

        // Alt check removed to allow processing in onChar

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
            case com.badlogic.gdx.Input.Keys.L:
                showLog = !showLog;
                logScrollPane.setVisible(showLog);
                return true;
        }

        if (keyStroke != null) {
            boolean ctrlPressed = Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.CONTROL_LEFT)
                    || Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.CONTROL_RIGHT);
            boolean altPressedKey = Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.ALT_LEFT)
                    || Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.ALT_RIGHT);
            boolean shiftPressed = Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SHIFT_LEFT)
                    || Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SHIFT_RIGHT);

            // Re-create keystroke with modifiers if needed
            // Note: Lanterna KeyStroke constructor for KeyType doesn't take modifiers
            // easily
            // without using the other constructor, but for now we dispatch as is.
            // Or we can construct it better if needed.
            // For now, let's dispatch.
            ((letrain.mvp.GameViewListener) this)
                    .onChar(new KeyStroke(keyStroke.getKeyType(), ctrlPressed, altPressedKey, shiftPressed));
            return true;
        }

        return false;

    }

    @Override
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
                // We pass a dummy character or just type for modifiers if we can
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

            ((letrain.mvp.GameViewListener) this)
                    .onKeyUp(new KeyStroke(keyStroke.getKeyType(), ctrlPressed, false, shiftPressed));
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
        if (cameraMode == CameraMode.MAP) {
            mapCameraHeight = com.badlogic.gdx.math.MathUtils.clamp(mapCameraHeight + amountY * 2f, 3f, 100f);
        } else if (cameraMode == CameraMode.ORBIT) {
            targetCameraDistance = com.badlogic.gdx.math.MathUtils.clamp(targetCameraDistance + amountY, 3f, 40f);
        }
        return true;
    }

    private com.badlogic.gdx.math.Vector3 camTarget = new com.badlogic.gdx.math.Vector3();
    private float cameraAngle = 45f; // Ángulo de rotación de la cámara alrededor del punto focal (en grados)
    private float targetCameraAngle = 45f;
    private float cameraDistance = 8.5f; // Distancia horizontal de la cámara al punto focal
    private float targetCameraDistance = 8.5f;
    private com.badlogic.gdx.math.Vector2 currentCabDirection = new com.badlogic.gdx.math.Vector2(0, 1);
    private float mapCameraHeight = 15f; // Altura para la vista MAP

    private enum CameraMode {
        ORBIT, CAB, MAP
    }

    private CameraMode cameraMode = CameraMode.ORBIT;

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

            trackMaker.makeTracks();
            model.moveLocomotives();
            model.loadAndUnloadTrains();
            model.removeDestroyedTrains();
            updateSelectionTimeouts();

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
        updateCamera(alpha);

        // 3. Sincronizar Audio con la posición REAL de la cámara de este frame
        float camAngle = (float) Math.atan2(cam.direction.z, cam.direction.x);
        audioController.setListenerPosition(cam.position.x, cam.position.z, cam.position.y, camAngle);
        audioController.update();

        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        // Actualizar instancias desde el modelo
        renderer.clear();
        renderer.visitModel(model, cam);
        renderer.getInstances().add(new ModelInstance(groundModel));
        // renderer.getInstances().add(new ModelInstance(gridModel)); // Grid oculto

        modelBatch.begin(cam);
        modelBatch.render(renderer.getInstances(), environment);
        modelBatch.end();

        // Renderizado de Etiquetas 3D (Decals)
        if (!renderer.getLabels().isEmpty()) {
            for (letrain.visitor.Gdx3DRenderer.VehicleLabel label : renderer.getLabels()) {
                if (label.text == null || label.text.isEmpty())
                    continue;

                float baseCharSpacing = 0.25f; // Espaciado base
                float charSpacing = baseCharSpacing * label.scale;
                float totalWidth = label.text.length() * charSpacing;
                float startOffset = -totalWidth / 2f + charSpacing / 2f;

                // Vector horizontal paralelo a la cara (perpendicular a la normal y a Y)
                com.badlogic.gdx.math.Vector3 horizontal = new com.badlogic.gdx.math.Vector3(label.normal.z, 0,
                        -label.normal.x).nor();
                // Si la normal es vertical, el producto vectorial anterior es cero.
                // Usamos un vector por defecto en ese caso.
                if (horizontal.len() < 0.1f) {
                    horizontal.set(1, 0, 0);
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
                        com.badlogic.gdx.math.Vector3 up = label.up != null ? label.up
                                : com.badlogic.gdx.math.Vector3.Y;

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
        updateUIData();
        stage.act(Gdx.graphics.getDeltaTime());
        stage.draw();
    }

    private void updateCamera(float alpha) {
        // Centrar cámara en el cursor o en la locomotora seleccionada
        float targetX, targetZ;
        if ((model.getMode() == letrain.mvp.Model.GameMode.DRIVE || model.getMode() == letrain.mvp.Model.GameMode.LINK)
                && model.getSelectedLocomotive() != null) {
            letrain.vehicle.impl.rail.Locomotive selected = model.getSelectedLocomotive();
            com.badlogic.gdx.math.Vector2 interpPos = getInterpolatedPosition(selected, alpha);
            targetX = interpPos.x + 0.5f;
            targetZ = interpPos.y + 0.5f;
        } else if (model.getMode() == letrain.mvp.Model.GameMode.FORKS && model.getSelectedFork() != null) {
            letrain.track.rail.ForkRailTrack selected = model.getSelectedFork();
            targetX = selected.getPosition().getX() + 0.5f;
            targetZ = selected.getPosition().getY() + 0.5f;
        } else if (model.getMode() == letrain.mvp.Model.GameMode.SEMAPHORES && model.getSelectedSemaphore() != null) {
            letrain.track.RailSemaphore selected = model.getSelectedSemaphore();
            targetX = selected.getPosition().getX() + 0.5f;
            targetZ = selected.getPosition().getY() + 0.5f;
        } else if (model.getMode() == letrain.mvp.Model.GameMode.STATIONS && model.getSelectedStation() != null) {
            letrain.track.Station selected = model.getSelectedStation();
            targetX = selected.getPosition().getX() + 0.5f;
            targetZ = selected.getPosition().getY() + 0.5f;
        } else {
            letrain.map.Point cursorState = model.getCursor().getPosition();
            targetX = cursorState.getX() + 0.5f;
            targetZ = cursorState.getY() + 0.5f;
        }

        // Calcular posición de cámara
        if (cameraMode == CameraMode.CAB) {
            letrain.vehicle.impl.rail.Locomotive loco = model.getSelectedLocomotive();
            if (loco == null && !model.getLocomotives().isEmpty()) {
                loco = model.getLocomotives().get(0);
            }

            if (loco != null) {
                // Posición interpolada de la locomotora (visual)
                com.badlogic.gdx.math.Vector2 interpPos = getInterpolatedPosition(loco, alpha);
                float x = interpPos.x + 0.5f;
                float z = interpPos.y + 0.5f;

                // Dirección de la locomotora para mirar hacia adelante
                letrain.map.Dir d = loco.getDir();
                float dx = letrain.visitor.Gdx3DRenderer.getDirX(d);
                float dz = letrain.visitor.Gdx3DRenderer.getDirZ(d);

                com.badlogic.gdx.math.Vector2 targetDir = new com.badlogic.gdx.math.Vector2(dx, dz);

                // Interpolamos currentCabDirection hacia targetDir
                currentCabDirection.lerp(targetDir, 0.05f);
                currentCabDirection.nor();

                float smoothDx = currentCabDirection.x;
                float smoothDz = currentCabDirection.y;

                // Altura de cabina ajustada: "Chase Cam"
                float camX = x - smoothDx * 1.2f;
                float camY = 2.0f;
                float camZ = z - smoothDz * 1.2f;

                cam.position.set(camX, camY, camZ);
                cam.lookAt(x + smoothDx * 5f, 0.5f, z + smoothDz * 5f);
                cam.up.set(0, 1, 0);
            }
        }

        if (cameraMode == CameraMode.ORBIT) {
            // Interpolación del punto de enfoque solo cuando el objetivo cambia
            camTarget.lerp(new com.badlogic.gdx.math.Vector3(targetX, 0, targetZ), 0.05f);

            // Interpolación de ángulo y distancia para suavidad
            cameraAngle = com.badlogic.gdx.math.MathUtils.lerp(cameraAngle, targetCameraAngle, 0.1f);
            cameraDistance = com.badlogic.gdx.math.MathUtils.lerp(cameraDistance, targetCameraDistance, 0.1f);

            // Calcular posición de cámara usando ángulo y distancia horizontal
            float angleRad = cameraAngle * com.badlogic.gdx.math.MathUtils.degreesToRadians;
            float camX = camTarget.x + cameraDistance * com.badlogic.gdx.math.MathUtils.sin(angleRad);
            float camZ = camTarget.z + cameraDistance * com.badlogic.gdx.math.MathUtils.cos(angleRad);
            // ORBIT height now follows distance slightly for a more natural zoom (30%
            // distance)
            float camY = Math.max(2.0f, cameraDistance * 0.7f);

            cam.position.set(camX, camY, camZ);
            cam.lookAt(camTarget);
            cam.up.set(0, 1, 0);
        }

        if (cameraMode == CameraMode.MAP) {
            // Top-down view
            camTarget.lerp(new com.badlogic.gdx.math.Vector3(targetX, 0, targetZ), 0.05f);
            cam.position.set(camTarget.x, mapCameraHeight, camTarget.z);
            cam.lookAt(camTarget.x, 0, camTarget.z);
            cam.up.set(0, 0, -1); // "Up" is north in top-down view
        }
        cam.update();
    }

    private void updateUIData() {
        // Update HUD (Finances)
        if (model.getEconomyManager() != null) {
            long balance = (long) model.getEconomyManager().getBalance();
            long income = (long) model.getEconomyManager().getTotalIncome();
            long expenses = (long) model.getEconomyManager().getTotalExpenses();

            // Income and Expenses use their LabelStyle colors (Green and Red)
            incomeLabel.setText(String.format("+ $ %,d", income));
            expensesLabel.setText(String.format("- $ %,d", expenses));

            // Balance uses markup to switch between Green and Red
            String balanceColorMark = balance >= 0 ? "[#00FF00]" : "[#FF0000]";
            balanceLabel.setText(String.format("%s$ %,d[]", balanceColorMark, balance));
        }

        letrain.vehicle.impl.rail.Locomotive loco = model.getSelectedLocomotive();
        if (loco != null) {
            // Update Notch Lever
            notchLever.setVisible(true);
            notchLever.setNotch(loco.getSpeed());
            notchLever.setTargetNotch(loco.getTargetSpeed());
        } else {
            notchLever.setVisible(false);
        }

        // Marcamos el botón seleccionado según el modo y actualizamos textos
        // dinámicamente
        for (com.badlogic.gdx.scenes.scene2d.Actor actor : menuTable.getChildren()) {
            if (actor instanceof TextButton) {
                TextButton btn = (TextButton) actor;
                String btnName = btn.getName();
                for (GameModeMenuOption option : model.getMenuModel()) {
                    String optionName = option.gameModeName().replace("&", "").toLowerCase();
                    if (optionName.equals(btnName)) {
                        boolean isSelected = option.selectedIf().get();
                        boolean isEnabled = option.enabledIf().get();

                        btn.setChecked(isSelected);
                        btn.setDisabled(!isEnabled);
                        // Update text dynamically to reflect enabled/disabled state (Gray vs
                        // White/Blue)
                        btn.setText(getMenuButtonText(option.gameModeName(), isEnabled));

                        if (isSelected) {
                            descLabel.setText(option.gameModeDescription());
                        }
                    }
                }
            }
        }
        updateLogView();
    }

    private void updateLogView() {
        if (!showLog)
            return;

        List<String> entries = model.getEventLogManager().getEntries();
        if (logTable.getChildren().size == entries.size())
            return;

        logTable.clear();
        for (String entry : entries) {
            Label label = new Label(entry, skin, "small");
            logTable.add(label).left().row();
        }
        logScrollPane.layout();
        logScrollPane.setScrollPercentY(100f);
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
        // Global Camera Zoom/Rotation (Alt + Arrows)
        if (stroke.isAltDown()) {
            if (stroke.getKeyType() == com.googlecode.lanterna.input.KeyType.ArrowLeft) {
                targetCameraAngle -= 15f;
                return;
            } else if (stroke.getKeyType() == com.googlecode.lanterna.input.KeyType.ArrowRight) {
                targetCameraAngle += 15f;
                return;
            } else if (stroke.getKeyType() == com.googlecode.lanterna.input.KeyType.ArrowUp) {
                if (cameraMode == CameraMode.MAP) {
                    mapCameraHeight = Math.max(3f, mapCameraHeight - 1f);
                } else {
                    targetCameraDistance = Math.max(3f, targetCameraDistance - 1f);
                }
                return;
            } else if (stroke.getKeyType() == com.googlecode.lanterna.input.KeyType.ArrowDown) {
                if (cameraMode == CameraMode.MAP) {
                    mapCameraHeight = Math.min(100f, mapCameraHeight + 1f);
                } else {
                    targetCameraDistance = Math.min(40f, targetCameraDistance + 1f);
                }
                return;
            }
        }

        // Global Enter to Menu (matches CompactPresenter)
        if (stroke.getKeyType() == com.googlecode.lanterna.input.KeyType.Enter) {
            // In DRIVE mode, Enter is for loading/unloading, not for switching to MENU.
            // The logic is handled inside handleDriveInput.
            if (model.getMode() != letrain.mvp.Model.GameMode.DRIVE) {
                model.setMode(letrain.mvp.Model.GameMode.MENU);
                return;
            }
        }

        // Mode Switching Shortcuts (from CompactPresenter)
        if (stroke.getKeyType() == com.googlecode.lanterna.input.KeyType.Character && stroke.getCharacter() != ' ') {
            // Exceptions: TRAINS mode uses characters for vehicle types
            if (model.getMode() != letrain.mvp.Model.GameMode.TRAINS) {
                switch (stroke.getCharacter()) {
                    case 'r':
                        model.setMode(letrain.mvp.Model.GameMode.RAILS);
                        return;
                    case 'd':
                        if (!model.getLocomotives().isEmpty())
                            model.setMode(letrain.mvp.Model.GameMode.DRIVE);
                        return;
                    case 'f':
                        if (!model.getForks().isEmpty())
                            model.setMode(letrain.mvp.Model.GameMode.FORKS);
                        return;
                    case 's':
                        if (!model.getSemaphores().isEmpty())
                            model.setMode(letrain.mvp.Model.GameMode.SEMAPHORES);
                        return;
                    case 't':
                        if (model.getCursorRailTrack() != null)
                            model.setMode(letrain.mvp.Model.GameMode.TRAINS);
                        return;
                    case 'l':
                        if (!model.getLocomotives().isEmpty())
                            model.setMode(letrain.mvp.Model.GameMode.LINK);
                        return;
                    case 'u':
                        if (!model.getLocomotives().isEmpty())
                            model.setMode(letrain.mvp.Model.GameMode.UNLINK);
                        return;
                    case 'n':
                        if (!model.getStations().isEmpty())
                            model.setMode(letrain.mvp.Model.GameMode.STATIONS);
                        return;
                    case 'p':
                        model.setMode(letrain.mvp.Model.GameMode.PERSIST);
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
            case PERSIST:
                handlePersistInput(stroke);
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
                if (stroke.getKeyType() == com.googlecode.lanterna.input.KeyType.Character) {
                    createVehicle(stroke.getCharacter());
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void onKeyUp(KeyStroke stroke) {
        if (model.getMode() == letrain.mvp.Model.GameMode.RAILS) {
            trackMaker.onKeyUp(stroke);
        }
    }

    private void handleSnapCursor() {
        letrain.map.Point targetPos = null;
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
        if (stroke.getKeyType() == com.googlecode.lanterna.input.KeyType.ArrowUp) {
            if (model.getSelectedLocomotive() != null && !model.getSelectedLocomotive().getTrain().isLoading()) {
                // Punto 15: Mientras se está cargando o descargando, el tren no podrá moverse.
                model.getSelectedLocomotive().incSpeed();
            }
        } else if (stroke.getKeyType() == com.googlecode.lanterna.input.KeyType.ArrowDown) {
            if (model.getSelectedLocomotive() != null && !model.getSelectedLocomotive().getTrain().isLoading()) {
                // Punto 15: Mientras se está cargando o descargando, el tren no podrá moverse.
                model.getSelectedLocomotive().decSpeed();
            }
        } else if (stroke.getKeyType() == com.googlecode.lanterna.input.KeyType.ArrowLeft) {
            model.selectPrevLocomotive();
        } else if (stroke.getKeyType() == com.googlecode.lanterna.input.KeyType.ArrowRight) {
            model.selectNextLocomotive();
        } else if (stroke.getKeyType() == com.googlecode.lanterna.input.KeyType.Character
                && stroke.getCharacter() == ' ') {
            if (locomotiveIdAccumulator > 0) {
                model.selectLocomotive(locomotiveIdAccumulator);
                locomotiveIdAccumulator = 0;
                locomotiveInputTimeout = 0;
            }
            // Space bar now only toggles reverse when stopped
            if (model.getSelectedLocomotive() != null && model.getSelectedLocomotive().getSpeed() == 0) {
                if (model.getSelectedLocomotive().getTrack() != null) {
                    model.getSelectedLocomotive().toggleReversed();
                }
            }
        } else if (stroke.getKeyType() == com.googlecode.lanterna.input.KeyType.Character &&
                Character.isDigit(stroke.getCharacter())) {
            locomotiveIdAccumulator = locomotiveIdAccumulator * 10 + Character.getNumericValue(stroke.getCharacter());
            model.selectLocomotive(locomotiveIdAccumulator);
            locomotiveInputTimeout = System.currentTimeMillis() + 1000;
        } else if (stroke.getKeyType() == com.googlecode.lanterna.input.KeyType.Backspace) {
            locomotiveIdAccumulator = locomotiveIdAccumulator / 10;
            model.selectLocomotive(locomotiveIdAccumulator);
            locomotiveInputTimeout = System.currentTimeMillis() + 1000;
        } else if (stroke.getKeyType() == com.googlecode.lanterna.input.KeyType.Enter) {
            // Enter key now handles loading/unloading at a station, or switches to menu
            if (model.getSelectedLocomotive() != null && model.getSelectedLocomotive().getSpeed() == 0) {
                letrain.vehicle.impl.rail.Train selectedTrain = model.getSelectedLocomotive().getTrain();
                if (selectedTrain != null) {
                    letrain.track.Station station = selectedTrain.getStationAtTrain();
                    if (station != null) {
                        if (selectedTrain.isLoading()) {
                            selectedTrain.endLoadUnloadProcess();
                        } else {
                            letrain.track.CargoTypes trainCargoType = selectedTrain.getTrainCargoType();
                            if (trainCargoType != null && trainCargoType != letrain.track.CargoTypes.NONE
                                    && station.getRole() == letrain.track.CargoTypes.StationRole.CONSUMER) {
                                selectedTrain.startUnloadProcess(station);
                                selectedTrain.recordStopAtStation();
                            } else if (trainCargoType == letrain.track.CargoTypes.NONE // Handles empty train
                                    && station.getRole() == letrain.track.CargoTypes.StationRole.PRODUCER) {
                                selectedTrain.startLoadProcess(station);
                                selectedTrain.recordStopAtStation();
                            }
                        }
                        return; // Consume the event
                    }
                }
            }
            // If not on a station or not stopped, Enter should switch to menu
            model.setMode(letrain.mvp.Model.GameMode.MENU);
        }
    }

    private void handlePersistInput(KeyStroke stroke) {
        if (stroke.getKeyType() == com.googlecode.lanterna.input.KeyType.ArrowUp) {
            showLoadDialog();
        } else if (stroke.getKeyType() == com.googlecode.lanterna.input.KeyType.ArrowDown) {
            showSaveDialog();
        } else if (stroke.getKeyType() == com.googlecode.lanterna.input.KeyType.Character
                && stroke.getCharacter() == ' ') {
            showEditDialog();
        }
    }

    private void handleLinkInput(KeyStroke stroke) {
        if (stroke.getKeyType() == com.googlecode.lanterna.input.KeyType.ArrowUp) {
            if (model.getSelectedLocomotive() != null && model.getSelectedLocomotive().getTrain() != null) {
                model.getSelectedLocomotive().getTrain().setLinkersToJoin(true);
            }
        } else if (stroke.getKeyType() == com.googlecode.lanterna.input.KeyType.ArrowDown) {
            if (model.getSelectedLocomotive() != null && model.getSelectedLocomotive().getTrain() != null) {
                model.getSelectedLocomotive().getTrain().setLinkersToJoin(false);
            }
        } else if (stroke.getKeyType() == com.googlecode.lanterna.input.KeyType.ArrowLeft) {
            if (model.getSelectedLocomotive() != null && model.getSelectedLocomotive().getTrain() != null) {
                model.getSelectedLocomotive().getTrain().removeLinkerToJoin();
            }
        } else if (stroke.getKeyType() == com.googlecode.lanterna.input.KeyType.ArrowRight) {
            if (model.getSelectedLocomotive() != null && model.getSelectedLocomotive().getTrain() != null) {
                model.getSelectedLocomotive().getTrain().addLinkerToJoin();
            }
        } else if (stroke.getKeyType() == com.googlecode.lanterna.input.KeyType.Character
                && stroke.getCharacter() == ' ') {
            letrain.vehicle.impl.rail.Locomotive loco = model.getSelectedLocomotive();
            if (loco != null && loco.getTrain() != null) {
                letrain.vehicle.impl.rail.Train train = loco.getTrain();
                if (!train.getLinkersToJoin().isEmpty() && train.getNumLinkersToJoin() > 0) {
                    train.joinLinkers();
                }
                model.setMode(letrain.mvp.Model.GameMode.MENU);
            }
        }
    }

    private void handleUnlinkInput(KeyStroke stroke) {
        if (model.getSelectedLocomotive() != null && model.getSelectedLocomotive().getTrain() != null) {
            letrain.vehicle.impl.rail.Train train = model.getSelectedLocomotive().getTrain();
            if (stroke.getKeyType() == com.googlecode.lanterna.input.KeyType.ArrowUp) {
                train.selectNextDivisionLink();
            } else if (stroke.getKeyType() == com.googlecode.lanterna.input.KeyType.ArrowDown) {
                train.selectPrevDivisionLink();
            } else if (stroke.getKeyType() == com.googlecode.lanterna.input.KeyType.ArrowLeft) {
                train.setFrontDivisionSense();
            } else if (stroke.getKeyType() == com.googlecode.lanterna.input.KeyType.ArrowRight) {
                train.setBackDivisionSense();
            } else if (stroke.getKeyType() == com.googlecode.lanterna.input.KeyType.Character
                    && stroke.getCharacter() == ' ') {
                train.divideTrain(() -> model.nextTrainId());
                audioController.playOneShot("link",
                        (float) model.getSelectedLocomotive().getPosition().getX(),
                        (float) model.getSelectedLocomotive().getPosition().getY());
                model.setMode(letrain.mvp.Model.GameMode.MENU);
            }
        }
    }

    private void handleForkInput(KeyStroke stroke) {
        if (stroke.getKeyType() == com.googlecode.lanterna.input.KeyType.ArrowLeft) {
            model.selectPrevFork();
        } else if (stroke.getKeyType() == com.googlecode.lanterna.input.KeyType.ArrowRight) {
            model.selectNextFork();
        } else if (stroke.getKeyType() == com.googlecode.lanterna.input.KeyType.Character
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
        } else if (stroke.getKeyType() == com.googlecode.lanterna.input.KeyType.Character &&
                Character.isDigit(stroke.getCharacter())) {
            forkIdAccumulator = forkIdAccumulator * 10 + Character.getNumericValue(stroke.getCharacter());
            model.selectFork(forkIdAccumulator);
            forkInputTimeout = System.currentTimeMillis() + 1000;
        } else if (stroke.getKeyType() == com.googlecode.lanterna.input.KeyType.Backspace) {
            forkIdAccumulator = forkIdAccumulator / 10;
            model.selectFork(forkIdAccumulator);
            forkInputTimeout = System.currentTimeMillis() + 1000;
        }
    }

    private void handleSemaphoreInput(KeyStroke stroke) {
        if (stroke.getKeyType() == com.googlecode.lanterna.input.KeyType.ArrowLeft) {
            model.selectPrevSemaphore();
        } else if (stroke.getKeyType() == com.googlecode.lanterna.input.KeyType.ArrowRight) {
            model.selectNextSemaphore();
        } else if (stroke.getKeyType() == com.googlecode.lanterna.input.KeyType.Character
                && stroke.getCharacter() == ' ') {
            if (semaphoreIdAccumulator > 0) {
                model.selectSemaphore(semaphoreIdAccumulator);
                semaphoreIdAccumulator = 0;
                semaphoreInputTimeout = 0;
            }
            letrain.track.RailSemaphore s = model.getSelectedSemaphore();
            if (s != null) {
                s.setOpen(!s.isOpen());
                audioController.playOneShot("construction",
                        (float) s.getPosition().getX(),
                        (float) s.getPosition().getY());
            }
        } else if (stroke.getKeyType() == com.googlecode.lanterna.input.KeyType.Character &&
                Character.isDigit(stroke.getCharacter())) {
            semaphoreIdAccumulator = semaphoreIdAccumulator * 10 + Character.getNumericValue(stroke.getCharacter());
            model.selectSemaphore(semaphoreIdAccumulator);
            semaphoreInputTimeout = System.currentTimeMillis() + 1000;
        } else if (stroke.getKeyType() == com.googlecode.lanterna.input.KeyType.Backspace) {
            semaphoreIdAccumulator = semaphoreIdAccumulator / 10;
            model.selectSemaphore(semaphoreIdAccumulator);
            semaphoreInputTimeout = System.currentTimeMillis() + 1000;
        }
    }

    private void handleStationInput(KeyStroke stroke) {
        if (stroke.getKeyType() == com.googlecode.lanterna.input.KeyType.ArrowLeft) {
            model.selectPrevStation();
        } else if (stroke.getKeyType() == com.googlecode.lanterna.input.KeyType.ArrowRight) {
            model.selectNextStation();
        } else if (stroke.getKeyType() == com.googlecode.lanterna.input.KeyType.Character
                && stroke.getCharacter() == ' ') {
            if (stationIdAccumulator > 0) {
                model.selectStation(stationIdAccumulator);
                stationIdAccumulator = 0;
                stationInputTimeout = 0;
            }
            if (model.getSelectedStation() != null && model.getSelectedStation().getTrack() != null) {
                letrain.vehicle.impl.Linker linker = model.getSelectedStation().getTrack().getLinker();
                if (linker != null && linker.getTrain() != null) {
                    linker.getTrain().performIndustrialAction(model.getSelectedStation());
                }
            }
        } else if (stroke.getKeyType() == com.googlecode.lanterna.input.KeyType.Character &&
                Character.isDigit(stroke.getCharacter())) {
            stationIdAccumulator = stationIdAccumulator * 10 + Character.getNumericValue(stroke.getCharacter());
            model.selectStation(stationIdAccumulator);
            stationInputTimeout = System.currentTimeMillis() + 1000;
        } else if (stroke.getKeyType() == com.googlecode.lanterna.input.KeyType.Backspace) {
            stationIdAccumulator = stationIdAccumulator / 10;
            model.selectStation(stationIdAccumulator);
            stationInputTimeout = System.currentTimeMillis() + 1000;
        }
    }

    private void updateSelectionTimeouts() {
        if (forkInputTimeout > 0 && System.currentTimeMillis() > forkInputTimeout) {
            model.selectFork(forkIdAccumulator);
            forkIdAccumulator = 0;
            forkInputTimeout = 0;
        }
        if (semaphoreInputTimeout > 0 && System.currentTimeMillis() > semaphoreInputTimeout) {
            model.selectSemaphore(semaphoreIdAccumulator);
            semaphoreIdAccumulator = 0;
            semaphoreInputTimeout = 0;
        }
        if (stationInputTimeout > 0 && System.currentTimeMillis() > stationInputTimeout) {
            model.selectStation(stationIdAccumulator);
            stationIdAccumulator = 0;
            stationInputTimeout = 0;
        }
        if (locomotiveInputTimeout > 0 && System.currentTimeMillis() > locomotiveInputTimeout) {
            model.selectLocomotive(locomotiveIdAccumulator);
            locomotiveIdAccumulator = 0;
            locomotiveInputTimeout = 0;
        }
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
    }

    @Override
    public void onNewGame() {
    }

    @Override
    public void onPlay() {
    }

    @Override
    public void setProgram(String program) {
    }

    @Override
    public void onSaveCommands(java.io.File file) {
        if (file != null) {
            try (java.io.FileWriter writer = new java.io.FileWriter(file)) {
                writer.write(model.getProgram());
                System.out.println("Gdx3DView: Commands saved successfully to " + file.getAbsolutePath());
            } catch (java.io.IOException e) {
                e.printStackTrace();
                System.err.println("Gdx3DView: Error saving commands: " + e.getMessage());
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
                System.out.println("Gdx3DView: Commands loaded successfully from " + file.getAbsolutePath());
            } catch (java.io.FileNotFoundException e) {
                e.printStackTrace();
                System.err.println("Gdx3DView: Error loading commands: " + e.getMessage());
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
    public void setScreen(com.googlecode.lanterna.screen.Screen screen) {
    }

    @Override
    public TextColor getFgColor() {
        return null;
    }

    @Override
    public void onSaveGame(File file) {
        try (java.io.ObjectOutputStream oos = new java.io.ObjectOutputStream(new java.io.FileOutputStream(file))) {
            oos.writeObject(model);
            System.out.println("Gdx3DView: Game saved successfully to " + file.getAbsolutePath());
        } catch (java.io.IOException e) {
            e.printStackTrace();
            System.err.println("Gdx3DView: Error saving game: " + e.getMessage());
        }
    }

    @Override
    public void onLoadGame(File file) {
        try (java.io.ObjectInputStream ois = new java.io.ObjectInputStream(new java.io.FileInputStream(file))) {
            letrain.mvp.impl.Model loadedModel = (letrain.mvp.impl.Model) ois.readObject();

            // Hack to update final model field using Reflection
            try {
                java.lang.reflect.Field modelField = Gdx3DView.class.getDeclaredField("model");
                modelField.setAccessible(true);
                modelField.set(this, loadedModel);
                System.out.println("Gdx3DView: Game loaded successfully from " + file.getAbsolutePath());

                // Refresh references
                trackMaker = new RailTrackMaker(this);
                audioController = new letrain.audio.AudioController(model);

                // Re-establish script listeners
                if (model.getProgram() != null && !model.getProgram().isEmpty()) {
                    model.setProgram(model.getProgram());
                }

            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("Gdx3DView: Critical error updating model reference: " + e.getMessage());
            }

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Gdx3DView: Error loading game: " + e.getMessage());
        }
    }

    @Override
    public void showSaveDialog() {
        showFileDialog("Save Game", "savegame.dat", (text) -> {
            if (text != null && !text.trim().isEmpty()) {
                File file = new File(text);
                System.out.println("Saving game to: " + file.getAbsolutePath());
                onSaveGame(file);
            }
        });
    }

    @Override
    public void showLoadDialog() {
        showFileDialog("Load Game", "savegame.dat", (text) -> {
            if (text != null && !text.trim().isEmpty()) {
                File file = new File(text);
                System.out.println("Loading game from: " + file.getAbsolutePath());
                if (file.exists()) {
                    onLoadGame(file);
                } else {
                    System.err.println("File not found: " + text);
                }
            }
        });
    }

    private void showFileDialog(String title, String defaultText, java.util.function.Consumer<String> onResult) {
        Gdx.app.postRunnable(() -> {
            Window window = new Window(title, skin);
            window.getTitleTable().pad(10);
            window.pad(20);

            com.badlogic.gdx.scenes.scene2d.ui.Label label = new com.badlogic.gdx.scenes.scene2d.ui.Label("Filename:",
                    skin);
            com.badlogic.gdx.scenes.scene2d.ui.TextField textField = new com.badlogic.gdx.scenes.scene2d.ui.TextField(
                    defaultText, skin);

            TextButton okBtn = new TextButton("OK", skin);
            TextButton cancelBtn = new TextButton("Cancel", skin);

            okBtn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeListener.ChangeEvent event, Actor actor) {
                    onResult.accept(textField.getText());
                    window.remove();
                }
            });

            cancelBtn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeListener.ChangeEvent event, Actor actor) {
                    window.remove();
                }
            });

            // Allow Enter to verify
            textField.setTextFieldListener((textField1, c) -> {
                if (c == '\r' || c == '\n') {
                    onResult.accept(textField1.getText());
                    window.remove();
                }
            });

            window.add(label).padRight(10);
            window.add(textField).width(200).row();
            window.add(okBtn).pad(10);
            window.add(cancelBtn).pad(10);

            window.pack();

            // Center on stage
            window.setPosition(
                    (stage.getWidth() - window.getWidth()) / 2,
                    (stage.getHeight() - window.getHeight()) / 2);

            stage.addActor(window);
            stage.setKeyboardFocus(textField);
        });
    }

    @Override
    public void showEditDialog() {
        Gdx.app.postRunnable(() -> {
            Window window = new Window("Program Editor", skin);
            window.getTitleTable().pad(10);

            TextArea textArea = new TextArea(Gdx3DView.this.getProgram(), skin);
            ScrollPane scrollPane = new ScrollPane(textArea, skin);
            scrollPane.setFadeScrollBars(false);

            TextButton saveBtn = new TextButton("Save & Run", skin);
            TextButton cancelBtn = new TextButton("Cancel", skin);

            saveBtn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeListener.ChangeEvent event, Actor actor) {
                    ((letrain.mvp.GameViewListener) Gdx3DView.this).onEditCommands(textArea.getText());
                    window.remove();
                }
            });

            cancelBtn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeListener.ChangeEvent event, Actor actor) {
                    window.remove();
                }
            });

            window.add(scrollPane).width(600).height(400).colspan(2).pad(10);
            window.row();
            window.add(saveBtn).pad(10);
            window.add(cancelBtn).pad(10);

            window.pack();

            // Center on stage
            window.setPosition(
                    (stage.getWidth() - window.getWidth()) / 2,
                    (stage.getHeight() - window.getHeight()) / 2);

            stage.addActor(window);
            stage.setKeyboardFocus(textArea);
        });
    }

    @Override
    public void showExitDialog() {
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
        cam.viewportWidth = width;
        cam.viewportHeight = height;
        cam.update();
    }

    @Override
    public int getCols() {
        return 80;
    }

    @Override
    public int getRows() {
        return 24;
    }

    private com.badlogic.gdx.math.Vector2 getInterpolatedPosition(letrain.vehicle.impl.rail.Locomotive locomotive,
            float alpha) {
        float x = locomotive.getPosition().getX();
        float y = locomotive.getPosition().getY();

        if (locomotive.getTotalTurns() >= 0) {
            float totalDelay = (float) locomotive.getTotalTurns() + 1.0f;
            float currentDelay = (float) locomotive.getTurns() + 1.0f - alpha;
            float progress = 1.0f - (currentDelay / totalDelay);

            if (progress < 0)
                progress = 0;
            if (progress > 1)
                progress = 1;

            letrain.track.Track currentTrack = locomotive.getTrack();
            if (currentTrack != null) {
                letrain.track.Track nextTrack = currentTrack.getConnected(locomotive.getDir());
                if (nextTrack != null) {
                    float nextX = nextTrack.getPosition().getX();
                    float nextY = nextTrack.getPosition().getY();

                    // Si la distancia es mayor a 1 (teletransporte/wrap), no interpolar
                    if (Math.abs(nextX - x) <= 1 && Math.abs(nextY - y) <= 1) {
                        x = x + (nextX - x) * progress;
                        y = y + (nextY - y) * progress;
                    }
                }
            }
        }
        return new com.badlogic.gdx.math.Vector2(x, y);
    }

    @Override
    public void dispose() {
        if (audioController != null) {
            audioController.stop();
        }
        if (modelBatch != null)
            modelBatch.dispose();
        if (decalBatch != null)
            decalBatch.dispose();
        if (spriteBatch != null)
            spriteBatch.dispose();
        if (font != null)
            font.dispose();
        if (stage != null)
            stage.dispose();
        if (skin != null)
            skin.dispose();
        if (shapeRenderer != null)
            shapeRenderer.dispose();
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
    public void onExitGame() {
        dispose();
        Gdx.app.exit();
        System.exit(0);
    }

    @Override
    public void showMessage(String title, String message) {
        Gdx.app.postRunnable(() -> {
            com.badlogic.gdx.scenes.scene2d.ui.Dialog dialog = new com.badlogic.gdx.scenes.scene2d.ui.Dialog(title,
                    skin) {
                @Override
                protected void result(Object object) {
                    this.remove();
                }
            };
            dialog.text(message);
            dialog.button("OK");
            dialog.pack();
            dialog.setPosition(
                    (stage.getWidth() - dialog.getWidth()) / 2,
                    (stage.getHeight() - dialog.getHeight()) / 2);
            stage.addActor(dialog);
        });
    }

    private class NotchLever extends com.badlogic.gdx.scenes.scene2d.Actor {
        private int notch = 0;
        private int targetNotch = 0;
        private float visualNotch = 0;

        public void setNotch(int notch) {
            this.notch = notch;
        }

        public void setTargetNotch(int targetNotch) {
            this.targetNotch = targetNotch;
        }

        @Override
        public void draw(com.badlogic.gdx.graphics.g2d.Batch batch, float parentAlpha) {
            batch.end();

            // Smooth handle movement
            visualNotch = com.badlogic.gdx.math.MathUtils.lerp(visualNotch, (float) notch, 0.1f);

            shapeRenderer.setProjectionMatrix(batch.getProjectionMatrix());
            shapeRenderer.setTransformMatrix(batch.getTransformMatrix());

            com.badlogic.gdx.graphics.GL20 gl = com.badlogic.gdx.Gdx.gl;
            gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
            gl.glBlendFunc(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA,
                    com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA);

            shapeRenderer.begin(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled);

            float x = getX() + 50; // Centered in the 100px width
            float y = getY();
            float h = getHeight();

            // Background slot
            shapeRenderer.setColor(0.2f, 0.2f, 0.2f, 0.4f * parentAlpha); // Translucent gray
            shapeRenderer.rect(x - 40, y - 20, 65, h + 40); // Even taller to fully enclose labels 0 and 10

            // Tick marks
            shapeRenderer.end();
            shapeRenderer.begin(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Line);
            shapeRenderer.setColor(Color.WHITE);
            for (int i = 0; i <= 10; i++) {
                float ty = y + (i / 10f) * h;
                shapeRenderer.line(x - 10, ty, x + 10, ty);
            }
            shapeRenderer.end();

            // Labels
            batch.begin();
            float oldScaleX = font.getScaleX();
            float oldScaleY = font.getScaleY();
            font.getData().setScale(0.7f);
            for (int i = 0; i <= 10; i++) {
                float ty = y + (i / 10f) * h;
                String txt = String.valueOf(i);
                com.badlogic.gdx.graphics.g2d.GlyphLayout layout = new com.badlogic.gdx.graphics.g2d.GlyphLayout(font,
                        txt);
                font.draw(batch, txt, x - 25 - layout.width, ty + layout.height / 2);
            }
            font.getData().setScale(oldScaleX, oldScaleY);
            batch.end();

            // Target Notch Indicator (Transparent Square)
            shapeRenderer.begin(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(1, 1, 1, 0.4f * parentAlpha); // Semi-transparent white
            float tyNode = y + (targetNotch / 10f) * h;
            shapeRenderer.rect(x - 18, tyNode - 8, 36, 16);

            // Handle (Actual Speed)
            shapeRenderer.setColor(Color.RED);
            float hy = y + (visualNotch / 10f) * h;
            shapeRenderer.rect(x - 15, hy - 5, 30, 10);
            shapeRenderer.end();

            com.badlogic.gdx.Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_BLEND);

            batch.begin();
        }
    }

    @Override
    public void onSpeedChanged(int speed) {
        // Gdx3DView handles speed changes via polling/sync in render loop
    }

    @Override
    public void onSenseChanged(boolean forward) {
        // Gdx3DView handles sense changes via polling/sync in render loop
    }

    @Override
    public void onCrash(Train train, Point pos) {
        audioController.playOneShot("link", pos.getX(), pos.getY());
    }

    @Override
    public void onContact(Point pos) {
        if (audioController != null && pos != null) {
            audioController.playOneShot("link", (float) pos.getX(), (float) pos.getY());
            // Immediately stop audio for all locomotives in the train that hit something
            // This forces them to 'stall' and stop moving sounds instantly.
            for (Locomotive loco : model.getLocomotives()) {
                if (loco.getTrain() != null && (loco.getSpeed() > 0 || loco.getTargetSpeed() > 0)) {
                    // Check if this loco's train is at the collision position
                    // Actually, a simpler way is to check all trains involved.
                    // But usually, the contact event implies the moving train hits something.
                    // For now, let's stop synthesizers for any loco that is "involved" or just all
                    // of them
                    // if they are at speed and we just had a contact nearby?
                    // No, let's be more specific.
                    if (Point.distance(loco.getPosition(), pos) < 2.0) {
                        audioController.stopSynthesizer(loco.getId());
                    }
                }
            }
        }
    }

    @Override
    public void onLink() {
        audioController.playOneShot("link", model.getCursor().getPosition().getX(),
                model.getCursor().getPosition().getY());
    }
}
