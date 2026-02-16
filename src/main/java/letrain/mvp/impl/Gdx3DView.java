package letrain.mvp.impl;

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
import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.graphics.g3d.ModelInstance;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
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
    private com.badlogic.gdx.graphics.g3d.decals.DecalBatch decalBatch;
    private java.util.Map<Character, com.badlogic.gdx.graphics.g2d.TextureRegion> glyphRegions = new java.util.HashMap<>();
    
    private com.badlogic.gdx.graphics.g3d.decals.Decal getGlyphDecal(char c) {
        if (!glyphRegions.containsKey(c)) {
            com.badlogic.gdx.graphics.g2d.BitmapFont.Glyph glyph = font.getData().getGlyph(c);
            if (glyph == null) return null;
            
            com.badlogic.gdx.graphics.g2d.TextureRegion region = new com.badlogic.gdx.graphics.g2d.TextureRegion(
                    font.getRegion().getTexture(),
                    glyph.u, glyph.v, glyph.u2, glyph.v2);
            region.flip(false, true); // Corregir inversión vertical
            glyphRegions.put(c, region);
        }
        
        com.badlogic.gdx.graphics.g2d.TextureRegion region = glyphRegions.get(c);
        // Force size to 0.5x0.5 world units
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
    private Vector3 labelPos = new Vector3();

    private Stage stage;
    private Skin skin;
    private Table menuTable;
    private Label descLabel;

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
        

        decalBatch = new com.badlogic.gdx.graphics.g3d.decals.DecalBatch(new com.badlogic.gdx.graphics.g3d.decals.CameraGroupStrategy(cam));

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

        TextButton.TextButtonStyle textButtonStyle = new TextButton.TextButtonStyle();
        textButtonStyle.up = skin.newDrawable("white", Color.DARK_GRAY);
        textButtonStyle.down = skin.newDrawable("white", Color.BLACK);
        textButtonStyle.checked = skin.newDrawable("white", Color.BLUE);
        textButtonStyle.over = skin.newDrawable("white", Color.LIGHT_GRAY);
        textButtonStyle.font = skin.getFont("default");
        skin.add("default", textButtonStyle);

        Label.LabelStyle labelStyle = new Label.LabelStyle();
        labelStyle.font = uiFont;
        labelStyle.fontColor = Color.YELLOW;
        skin.add("default", labelStyle);

        menuTable = new Table();
        menuTable.setFillParent(true);
        menuTable.bottom();
        stage.addActor(menuTable);

        descLabel = new Label("", skin);
        Table descTable = new Table();
        descTable.setFillParent(true);
        descTable.bottom();
        descTable.add(descLabel).padBottom(50);
        stage.addActor(descTable);

        updateMenuButtons();
    }

    private void updateMenuButtons() {
        menuTable.clearChildren();
        for (GameModeMenuOption option : model.getMenuModel()) {
            String rawName = option.gameModeName();
            String formattedName = rawName;
            if (rawName.contains("&")) {
                int index = rawName.indexOf("&");
                if (index + 1 < rawName.length()) {
                    char mnemonic = rawName.charAt(index + 1);
                    formattedName = rawName.substring(0, index) + "[YELLOW]" + mnemonic + "[]"
                            + rawName.substring(index + 2);
                }
            }

            TextButton button = new TextButton(formattedName, skin);
            button.setChecked(option.selectedIf().get());
            button.setDisabled(!option.enabledIf().get());

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
        if (model.getMode() == letrain.mvp.Model.GameMode.TRAINS) {
            if (Character.isLetter(character)) {
                createVehicle(character);
                return true;
            }
        // 's' key handler removed (moved to HOME key in keyDown)
        } else if (model.getMode() == letrain.mvp.Model.GameMode.RAILS) {
             // No specific char input for RAILS mode anymore
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
        } else if (model.getMode() == letrain.mvp.Model.GameMode.FORKS) {
            if (character == ' ') {
                if (model.getSelectedFork() != null) {
                    model.getSelectedFork().flipRoute();
                }
                return true;
            }
        } else if (model.getMode() == letrain.mvp.Model.GameMode.UNLINK) {
            if (character == ' ') {
                if (model.getSelectedLocomotive() != null && model.getSelectedLocomotive().getTrain() != null) {
                    model.getSelectedLocomotive().getTrain().divideTrain(model::nextTrainId);
                    model.setMode(letrain.mvp.Model.GameMode.RAILS);
                }
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

        switch (character) {
            case 'r':
                model.setMode(letrain.mvp.Model.GameMode.RAILS);
                return true;
            case 'n':
                if (!model.getStations().isEmpty()) {
                    model.setMode(letrain.mvp.Model.GameMode.STATIONS);
                }
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
                    if (model.getSelectedLocomotive() != null && model.getSelectedLocomotive().getTrain() != null) {
                        model.getSelectedLocomotive().getTrain().resetLinkState();
                    }
                }
                return true;
            case 'u':
                if (!model.getLocomotives().isEmpty()) {
                    model.setMode(letrain.mvp.Model.GameMode.UNLINK);
                    if (model.getSelectedLocomotive() != null && model.getSelectedLocomotive().getTrain() != null) {
                        model.getSelectedLocomotive().getTrain().resetUnlinkState();
                    }
                }
                return true;
            case 'f':
                if (!model.getForks().isEmpty()) {
                    model.setMode(letrain.mvp.Model.GameMode.FORKS);
                }
                return true;
            case 's':
                if (!model.getSemaphores().isEmpty()) {
                    model.setMode(letrain.mvp.Model.GameMode.SEMAPHORES);
                }
                return true;
            case 'c':
                cameraMode = (cameraMode == CameraMode.ORBIT) ? CameraMode.CAB : CameraMode.ORBIT;
                return true;
        }

        
        // Pass any other character input to the presenter/trackmaker
        boolean ctrlPressed = Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.CONTROL_LEFT)
                || Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.CONTROL_RIGHT);
        boolean altPressed = Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.ALT_LEFT)
                || Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.ALT_RIGHT);
        
        onChar(new com.googlecode.lanterna.input.KeyStroke(character, ctrlPressed, altPressed));
        return true;
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
        boolean altPressed = Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.ALT_LEFT)
                || Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.ALT_RIGHT);

        if (altPressed && (keycode == com.badlogic.gdx.Input.Keys.LEFT
                || keycode == com.badlogic.gdx.Input.Keys.RIGHT
                || keycode == com.badlogic.gdx.Input.Keys.UP
                || keycode == com.badlogic.gdx.Input.Keys.DOWN)) {
            // Consumir el evento para que no se procese en otros lugares
            return true;
        }

        if (keycode == com.badlogic.gdx.Input.Keys.ESCAPE) {
            model.setMode(letrain.mvp.Model.GameMode.RAILS);
            return true;
        }

        if (keycode == com.badlogic.gdx.Input.Keys.ENTER) {
            if (model.getMode() != letrain.mvp.Model.GameMode.LINK
                    && model.getMode() != letrain.mvp.Model.GameMode.UNLINK) {
                model.setMode(letrain.mvp.Model.GameMode.RAILS);
                return true;
            }
        }

        if (model.getMode() == letrain.mvp.Model.GameMode.RAILS) {
            if (keycode == com.badlogic.gdx.Input.Keys.HOME) {
                trackMaker.manageSemaphore();
                return true;
            } else if (keycode == com.badlogic.gdx.Input.Keys.INSERT) {
                trackMaker.manageSensor();
                return true;
            }
        } else if (model.getMode() == letrain.mvp.Model.GameMode.SEMAPHORES) {
             if (keycode == com.badlogic.gdx.Input.Keys.LEFT) {
                model.selectPrevSemaphore();
                return true;
            } else if (keycode == com.badlogic.gdx.Input.Keys.RIGHT) {
                model.selectNextSemaphore();
                return true;
            } else if (keycode == com.badlogic.gdx.Input.Keys.SPACE) {
                letrain.track.RailSemaphore s = model.getSelectedSemaphore();
                if (s != null) {
                    s.setOpen(!s.isOpen());
                }
                return true;
            }
        } else if (model.getMode() == letrain.mvp.Model.GameMode.STATIONS) {
            if (keycode == com.badlogic.gdx.Input.Keys.LEFT) {
                model.selectPrevStation();
                return true;
            } else if (keycode == com.badlogic.gdx.Input.Keys.RIGHT) {
                model.selectNextStation();
                return true;
            }
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
    private float cameraAngle = 45f; // Ángulo de rotación de la cámara alrededor del punto focal (en grados)
    private float cameraDistance = 8.5f; // Distancia horizontal de la cámara al punto focal
    private float cameraHeight = 6f; // Altura fija de la cámara sobre el suelo
    
    private enum CameraMode { ORBIT, CAB }
    private CameraMode cameraMode = CameraMode.ORBIT;

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
            stateTime -= 0.05f;
            if (stateTime > 0.05f)
                stateTime = 0.05f; // Evitar espiral de la muerte si hay mucho lag
        }

        // Calcular factor de interpolación
        float alpha = stateTime / 0.05f;
        if (alpha > 1f)
            alpha = 1f;
        renderer.setAnimationAlpha(alpha);

        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        // Actualizar instancias desde el modelo
        renderer.clear();
        renderer.visitModel(model);
        renderer.getInstances().add(new ModelInstance(groundModel));
        // renderer.getInstances().add(new ModelInstance(gridModel)); // Grid oculto

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
                // Usamos la dirección interpolada si es posible, o la lógica básica.
                // Para simplificar, usamos la dirección del modelo.
                letrain.map.Dir d = loco.getDir();
                float dx = letrain.visitor.Gdx3DRenderer.getDirX(d);
                float dz = letrain.visitor.Gdx3DRenderer.getDirZ(d);
                
                // Altura de cabina ajustada: "Chase Cam"
                // Posición: (x, y, z) - dir * 1.2 + (0, 2.0, 0)
                // Esto coloca la cámara detrás y arriba, viendo un trozo de la locomotora.
                float camX = x - dx * 1.2f;
                float camY = 2.0f;
                float camZ = z - dz * 1.2f;
                
                cam.position.set(camX, camY, camZ);
                // Mirar hacia adelante (x + dx*5, 0.5, z + dz*5)
                // Bajamos un poco el punto de mira para ver la vía y la locomotora
                cam.lookAt(x + dx * 5f, 0.5f, z + dz * 5f);
                cam.up.set(0, 1, 0);
            } else {
               // Fallback a Orbita
               cameraMode = CameraMode.ORBIT;
            }
        } 
        
        if (cameraMode == CameraMode.ORBIT) {
            // Interpolación del punto de enfoque solo cuando el objetivo cambia
            camTarget.lerp(new com.badlogic.gdx.math.Vector3(targetX, 0, targetZ), 0.05f);

            // Calcular posición de cámara usando ángulo y distancia horizontal
            float angleRad = cameraAngle * com.badlogic.gdx.math.MathUtils.degreesToRadians;
            float camX = camTarget.x + cameraDistance * com.badlogic.gdx.math.MathUtils.sin(angleRad);
            float camZ = camTarget.z + cameraDistance * com.badlogic.gdx.math.MathUtils.cos(angleRad);
            // Altura fija para mantener inclinación constante
            float camY = cameraHeight;

            // Actualizar posición de cámara sin interpolación para respuesta inmediata
            cam.position.set(camX, camY, camZ);
            cam.lookAt(camTarget);
            cam.up.set(0, 1, 0); // Mantener vector up fijo para evitar volteo
        }
        cam.update();

        modelBatch.begin(cam);
        modelBatch.render(renderer.getInstances(), environment);
        modelBatch.end();

        
        // Renderizado de Etiquetas 3D (Decals)
        if (!renderer.getLabels().isEmpty()) {
            for (letrain.visitor.Gdx3DRenderer.VehicleLabel label : renderer.getLabels()) {
                if (label.text == null || label.text.isEmpty()) continue;
                
                char c = label.text.charAt(0);
                com.badlogic.gdx.graphics.g3d.decals.Decal d = getGlyphDecal(c);
                if (d != null) {
                    d.setPosition(label.pos);
                    // Orientar el decal para que mire hacia afuera de la superficie
                    // (hacia donde apunta la normal)
                    // lookAt hace que el frente del decal mire al target.
                    // Queremos que el frente mire a pos + normal.
                    d.lookAt(label.pos.cpy().add(label.normal), com.badlogic.gdx.math.Vector3.Y);
                    
                    decalBatch.add(d);
                }
            }
            decalBatch.flush();
        }
        
        spriteBatch.begin();
        // (Labels 2D removidos)
        spriteBatch.end();

        // Renderizado de UI (Menú)
        updateUIData();
        stage.act(Gdx.graphics.getDeltaTime());
        stage.draw();
    }

    private void updateUIData() {
        // Marcamos el botón seleccionado según el modo
        for (com.badlogic.gdx.scenes.scene2d.Actor actor : menuTable.getChildren()) {
            if (actor instanceof TextButton) {
                TextButton btn = (TextButton) actor;
                String btnText = btn.getText().toString().toLowerCase()
                        .replace("[yellow]", "").replace("[]", "");
                for (GameModeMenuOption option : model.getMenuModel()) {
                    String optionName = option.gameModeName().replace("&", "").toLowerCase();
                    if (optionName.equals(btnText)) {
                        btn.setChecked(option.selectedIf().get());
                        btn.setDisabled(!option.enabledIf().get());
                        if (btn.isChecked()) {
                            descLabel.setText(option.gameModeDescription());
                        }
                    }
                }
            }
        }
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
        boolean altPressed = Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.ALT_LEFT)
                || Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.ALT_RIGHT);

        // Controles de cámara con Alt+flechas (funcionan en TODOS los modos)
        if (altPressed) {
            // Alt+izquierda/derecha: panear cámara alrededor del punto focal
            if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.LEFT)) {
                cameraAngle -= 15f; // Rotar 15° a la izquierda
                return; // No procesar más input para evitar mover el cursor
            }
            if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.RIGHT)) {
                cameraAngle += 15f; // Rotar 15° a la derecha
                return; // No procesar más input para evitar mover el cursor
            }

            // Alt+arriba/abajo: ajustar zoom
            if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.UP)) {
                cameraDistance = Math.max(3f, cameraDistance - 1f); // Acercar (mínimo 3)
                return; // No procesar más input para evitar mover el cursor
            }
            if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.DOWN)) {
                cameraDistance = Math.min(20f, cameraDistance + 1f); // Alejar (máximo 20)
                return; // No procesar más input para evitar mover el cursor
            }
        }

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
        } else if (model.getMode() == letrain.mvp.Model.GameMode.FORKS) {
            handleForkInput();
        } else if (model.getMode() == letrain.mvp.Model.GameMode.UNLINK) {
            handleUnlinkInput();
        } else if (model.getMode() == letrain.mvp.Model.GameMode.SEMAPHORES) {
            // Controlado por keyDown/keyUp
        } else {
            handleStandardInput(ctrlPressed, shiftPressed, altPressed);
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
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.LEFT)) {
            if (model.getSelectedLocomotive() != null && model.getSelectedLocomotive().getTrain() != null) {
                model.getSelectedLocomotive().getTrain().removeLinkerToJoin();
            }
        }
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.RIGHT)) {
            if (model.getSelectedLocomotive() != null && model.getSelectedLocomotive().getTrain() != null) {
                model.getSelectedLocomotive().getTrain().addLinkerToJoin();
            }
        }
    }

    private void handleForkInput() {
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.LEFT)) {
            model.selectPrevFork();
        }
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.RIGHT)) {
            model.selectNextFork();
        }
    }

    private void handleUnlinkInput() {
        if (model.getSelectedLocomotive() != null && model.getSelectedLocomotive().getTrain() != null) {
            letrain.vehicle.impl.rail.Train train = model.getSelectedLocomotive().getTrain();
            if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.UP)) {
                train.selectNextDivisionLink();
            }
            if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.DOWN)) {
                train.selectPrevDivisionLink();
            }
            if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.LEFT)) {
                train.setFrontDivisionSense();
            }
            if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.RIGHT)) {
                train.setBackDivisionSense();
            }
        }
    }

    private void handleStandardInput(boolean ctrlPressed, boolean shiftPressed, boolean altPressed) {
        // Movimiento Longitudinal (Repetible con retardo controlado)
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.UP) && !altPressed) {
            trackMaker.onChar(new com.googlecode.lanterna.input.KeyStroke(com.googlecode.lanterna.input.KeyType.ArrowUp,
                    ctrlPressed, false, shiftPressed));
            inputDelay = 0.5f;
        } else if (Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.UP) && inputDelay <= 0 && !altPressed) {
            trackMaker.onChar(new com.googlecode.lanterna.input.KeyStroke(com.googlecode.lanterna.input.KeyType.ArrowUp,
                    ctrlPressed, false, shiftPressed));
            inputDelay = 0.5f;
        }

        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.DOWN) && !altPressed) {
            trackMaker.onChar(new com.googlecode.lanterna.input.KeyStroke(
                    com.googlecode.lanterna.input.KeyType.ArrowDown, false, false, false));
            inputDelay = 0.5f;
        } else if (Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.DOWN) && inputDelay <= 0 && !altPressed) {
            trackMaker.onChar(new com.googlecode.lanterna.input.KeyStroke(
                    com.googlecode.lanterna.input.KeyType.ArrowDown, false, false, false));
            inputDelay = 0.5f;
        }

        // Giro (Solo un paso por pulsación para evitar "girar de más")
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.LEFT) && !altPressed) {
            trackMaker.onChar(new com.googlecode.lanterna.input.KeyStroke(
                    com.googlecode.lanterna.input.KeyType.ArrowLeft, false, false, false));
        }
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.RIGHT) && !altPressed) {
            trackMaker.onChar(new com.googlecode.lanterna.input.KeyStroke(
                    com.googlecode.lanterna.input.KeyType.ArrowRight, false, false, false));
        }

        // 'Space' discreto
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.SPACE)) {
            trackMaker.onChar(new com.googlecode.lanterna.input.KeyStroke(' ', false, false));
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
        stage.dispose();
        skin.dispose();
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
}
