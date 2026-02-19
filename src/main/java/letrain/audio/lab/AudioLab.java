package letrain.audio.lab;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import letrain.audio.core.AudioMixer;
import letrain.audio.sources.AmbientSource;
import letrain.audio.synth.AudioSample;
import letrain.audio.synth.TrainSynthesizer;

public class AudioLab extends ApplicationAdapter {

    private AudioMixer mixer;
    private Stage stage;
    private Skin skin;
    private Table scrollTable;
    private int trainCounter = 0;

    public static void main(String[] arg) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("LeTrain Audio Lab");
        config.setWindowedMode(1200, 800);
        new Lwjgl3Application(new AudioLab(), config);
    }

    @Override
    public void create() {
        mixer = new AudioMixer();
        mixer.start();
        mixer.setListenerPosition(0, 0, 0);

        stage = new Stage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);

        setupSkin();
        setupUI();

        // Add initial train
        addTrain();
    }

    private void setupSkin() {
        skin = new Skin();

        // 1x1 white texture
        com.badlogic.gdx.graphics.Pixmap pixmap = new com.badlogic.gdx.graphics.Pixmap(1, 1,
                com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        skin.add("white", new com.badlogic.gdx.graphics.Texture(pixmap));

        // Font
        com.badlogic.gdx.graphics.g2d.BitmapFont font = new com.badlogic.gdx.graphics.g2d.BitmapFont();
        skin.add("default", font);

        // Label
        Label.LabelStyle labelStyle = new Label.LabelStyle();
        labelStyle.font = font;
        skin.add("default", labelStyle);

        // Button
        TextButton.TextButtonStyle textButtonStyle = new TextButton.TextButtonStyle();
        textButtonStyle.up = skin.newDrawable("white", Color.DARK_GRAY);
        textButtonStyle.down = skin.newDrawable("white", Color.BLACK);
        textButtonStyle.over = skin.newDrawable("white", Color.GRAY);
        textButtonStyle.font = font;
        skin.add("default", textButtonStyle);

        // CheckBox
        CheckBox.CheckBoxStyle checkStyle = new CheckBox.CheckBoxStyle();
        checkStyle.checkboxOn = skin.newDrawable("white", Color.GREEN);
        checkStyle.checkboxOff = skin.newDrawable("white", Color.RED);
        checkStyle.font = font;
        skin.add("default", checkStyle);

        // Slider
        Slider.SliderStyle sliderStyle = new Slider.SliderStyle();
        sliderStyle.background = skin.newDrawable("white", Color.DARK_GRAY);
        sliderStyle.background.setMinHeight(10);
        sliderStyle.knob = skin.newDrawable("white", Color.LIGHT_GRAY);
        sliderStyle.knob.setMinHeight(20);
        sliderStyle.knob.setMinWidth(10);
        skin.add("default-horizontal", sliderStyle);

        // ScrollPane
        ScrollPane.ScrollPaneStyle scrollStyle = new ScrollPane.ScrollPaneStyle();
        scrollStyle.background = skin.newDrawable("white", new Color(0.1f, 0.1f, 0.1f, 1));
        scrollStyle.vScroll = skin.newDrawable("white", Color.DARK_GRAY);
        scrollStyle.vScrollKnob = skin.newDrawable("white", Color.GRAY);
        skin.add("default", scrollStyle);

        // Window
        Window.WindowStyle windowStyle = new Window.WindowStyle();
        windowStyle.background = skin.newDrawable("white", new Color(0.2f, 0.2f, 0.2f, 0.9f));
        windowStyle.titleFont = font;
        skin.add("default", windowStyle);
    }

    private void setupUI() {
        Table root = new Table();
        root.setFillParent(true);
        stage.addActor(root);

        // Header / Toolbar
        Table toolbar = new Table();
        toolbar.setBackground(skin.newDrawable("white", new Color(0.25f, 0.25f, 0.25f, 1)));

        TextButton addTrainBtn = new TextButton("Add Train", skin);
        addTrainBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                addTrain();
            }
        });

        TextButton addAmbienceBtn = new TextButton("Add Ambience (File)", skin);
        addAmbienceBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                new Thread(() -> {
                    javax.swing.JFileChooser chooser = new javax.swing.JFileChooser();
                    chooser.setDialogTitle("Select Audio File");
                    int result = chooser.showOpenDialog(null);
                    if (result == javax.swing.JFileChooser.APPROVE_OPTION) {
                        java.io.File file = chooser.getSelectedFile();
                        Gdx.app.postRunnable(() -> addAmbience(file));
                    }
                }).start();
            }
        });

        toolbar.add(new Label("Virtual Sound Lab", skin)).expandX().left().pad(10);
        toolbar.add(addTrainBtn).pad(5);
        toolbar.add(addAmbienceBtn).pad(5);

        root.add(toolbar).growX().height(50).row();

        // Scrollable List Area
        scrollTable = new Table();
        scrollTable.top(); // Align items to top

        ScrollPane scrollPane = new ScrollPane(scrollTable, skin);
        scrollPane.setScrollingDisabled(true, false); // Horizontal disabled
        scrollPane.setFadeScrollBars(false);

        root.add(scrollPane).grow();
    }

    private void addTrain() {
        trainCounter++;
        TrainSynthesizer synth = new TrainSynthesizer();
        synth.setLocoVolume(1.0f);
        synth.setCoachVolume(1.0f);
        synth.startAudio();
        mixer.addSource(synth);

        // Create UI Panel
        addControlPanel(new TrainControlPanel("Train " + trainCounter, synth));
    }

    private void addAmbience(java.io.File file) {
        try {
            AudioSample sample = new AudioSample(file);
            AmbientSource source = new AmbientSource(sample);
            source.setVolume(0.5f);
            mixer.addSource(source);

            String name = file.getName();

            // Create UI Panel (simplified for ambience)
            addControlPanel(new AmbientControlPanel(name, source));

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void addControlPanel(Table panel) {
        scrollTable.add(panel).growX().pad(5).row();
    }

    // --- Components ---

    private class TrainControlPanel extends Table {
        TrainSynthesizer synth;
        boolean locoMute = false;
        boolean coachMute = false;
        float lastLocoVol = 1.0f;
        float lastCoachVol = 1.0f;

        public TrainControlPanel(String title, TrainSynthesizer s) {
            this.synth = s;
            setBackground(skin.newDrawable("white", new Color(0.3f, 0.3f, 0.4f, 1)));
            pad(10);

            // Row 1: Title & Distance
            add(new Label(title, skin)).left().width(100);

            Slider distanceSlider = new Slider(0, 2000, 10, false, skin);
            distanceSlider.setValue(0);
            final Label distanceLabel = new Label("0 m", skin);

            distanceSlider.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    float val = distanceSlider.getValue();
                    distanceLabel.setText((int) val + " m");
                    // Update X position (Audio uses X for Pan essentially if Listener at 0)
                    // But effectively distance attenuation.
                    // Let's set X = distance. Y=0, Z=0.
                    synth.setPosition(val, 0, 0); // Moving Right
                }
            });

            add(new Label("Dist:", skin)).padRight(5);
            add(distanceSlider).growX().padRight(10);
            add(distanceLabel).width(50).row();

            // Row 1b: Max Distance
            add(new Label("Max Dist:", skin)).left();
            Slider maxDistSlider = new Slider(100, 5000, 100, false, skin);
            maxDistSlider.setValue(1000); // Default
            final Label maxDistLabel = new Label("1000 m", skin);

            maxDistSlider.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    float val = maxDistSlider.getValue();
                    maxDistLabel.setText((int) val + " m");
                    synth.setAudioRange(50f, val);
                }
            });

            add(maxDistSlider).growX().padRight(10);
            add(maxDistLabel).width(50).row();

            // Row 1c: Absorption (Filter Sensitivity)
            add(new Label("Absorb:", skin)).left();
            Slider absorbSlider = new Slider(0.0f, 5.0f, 0.1f, false, skin); // Up to 5x boost
            absorbSlider.setValue(1.0f);
            final Label absorbLabel = new Label("1.0x", skin);

            absorbSlider.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    float val = absorbSlider.getValue();
                    absorbLabel.setText(String.format("%.1fx", val));
                    synth.setFilterSensitivity(val);
                }
            });

            add(absorbSlider).growX().padRight(10);
            add(absorbLabel).width(50).row();

            // Row 2: Throttle / Notch
            add(new Label("Notch:", skin)).left();
            Slider notchSlider = new Slider(0, 8, 1, false, skin); // 0-8 steps
            final Label notchLabel = new Label("0", skin);

            notchSlider.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    int val = (int) notchSlider.getValue();
                    notchLabel.setText(String.valueOf(val));
                    synth.setThrottle(val);
                }
            });

            add(new Label("", skin)); // spacer
            add(notchSlider).growX().padRight(10);
            add(notchLabel).width(50).row();

            // Row 3: Volumes

            // Loco Vol
            add(new Label("Loco Vol:", skin)).left();

            Slider locoSlider = new Slider(0, 1, 0.01f, false, skin);
            locoSlider.setValue(1.0f);
            CheckBox locoMuteBox = new CheckBox("Mute", skin);

            locoSlider.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    lastLocoVol = locoSlider.getValue();
                    if (!locoMute)
                        synth.setLocoVolume(lastLocoVol);
                }
            });

            locoMuteBox.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    locoMute = locoMuteBox.isChecked();
                    if (locoMute)
                        synth.setLocoVolume(0);
                    else
                        synth.setLocoVolume(lastLocoVol);
                }
            });

            Table locoTable = new Table();
            locoTable.add(locoSlider).growX().padRight(5);
            locoTable.add(locoMuteBox);
            add(new Label("", skin));
            add(locoTable).growX().colspan(2).row();

            // Coach Vol
            add(new Label("Coach Vol:", skin)).left();

            Slider coachSlider = new Slider(0, 1, 0.01f, false, skin);
            coachSlider.setValue(1.0f);
            CheckBox coachMuteBox = new CheckBox("Mute", skin);

            coachSlider.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    lastCoachVol = coachSlider.getValue();
                    if (!coachMute)
                        synth.setCoachVolume(lastCoachVol);
                }
            });

            coachMuteBox.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    coachMute = coachMuteBox.isChecked();
                    if (coachMute)
                        synth.setCoachVolume(0);
                    else
                        synth.setCoachVolume(lastCoachVol);
                }
            });

            Table coachTable = new Table();
            coachTable.add(coachSlider).growX().padRight(5);
            coachTable.add(coachMuteBox);
            add(new Label("", skin));
            add(coachTable).growX().colspan(2).row();
        }
    }

    private class AmbientControlPanel extends Table {
        AmbientSource source;
        boolean mute = false;
        float lastVol = 0.5f;

        public AmbientControlPanel(String title, AmbientSource s) {
            this.source = s;
            setBackground(skin.newDrawable("white", new Color(0.3f, 0.4f, 0.3f, 1)));
            pad(10);

            add(new Label(title, skin)).left().width(200);

            // Distance
            Slider distanceSlider = new Slider(0, 2000, 10, false, skin);
            distanceSlider.setValue(0);
            final Label distanceLabel = new Label("0 m", skin);

            distanceSlider.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    float val = distanceSlider.getValue();
                    distanceLabel.setText((int) val + " m");
                    source.setPosition(val, 0, 0);
                }
            });

            add(distanceSlider).growX().pad(5);
            add(distanceLabel).width(50).row();

            // Start Delay / Max Distance
            // Let's add Max Distance slider
            add(new Label("Max Dist:", skin)).left();
            Slider maxDistSlider = new Slider(100, 5000, 100, false, skin);
            maxDistSlider.setValue(2000); // Default for ambience
            final Label maxDistLabel = new Label("2000 m", skin);

            maxDistSlider.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    float val = maxDistSlider.getValue();
                    maxDistLabel.setText((int) val + " m");
                    source.setRange(100f, val);
                }
            });

            add(maxDistSlider).growX().pad(5);
            add(maxDistLabel).width(50).row();

            // Row 1c: Absorption
            add(new Label("Absorb:", skin)).left();
            Slider absorbSlider = new Slider(0.0f, 5.0f, 0.1f, false, skin);
            absorbSlider.setValue(1.0f);
            final Label absorbLabel = new Label("1.0x", skin);

            absorbSlider.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    float val = absorbSlider.getValue();
                    absorbLabel.setText(String.format("%.1fx", val));
                    source.setFilterSensitivity(val);
                }
            });

            add(absorbSlider).growX().pad(5);
            add(absorbLabel).width(50).row();

            // Volume
            Slider volSlider = new Slider(0, 1, 0.01f, false, skin);
            volSlider.setValue(0.5f);
            CheckBox muteBox = new CheckBox("Mute", skin);

            volSlider.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    lastVol = volSlider.getValue();
                    if (!mute)
                        source.setVolume(lastVol);
                }
            });

            muteBox.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    mute = muteBox.isChecked();
                    if (mute)
                        source.setVolume(0);
                    else
                        source.setVolume(lastVol);
                }
            });

            add(volSlider).width(100).pad(5);
            add(muteBox).pad(5);
        }
    }

    @Override
    public void render() {
        Gdx.gl.glClearColor(0.15f, 0.15f, 0.15f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);

        stage.act(Gdx.graphics.getDeltaTime());
        stage.draw();
    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        if (mixer != null)
            mixer.stop();
        if (stage != null)
            stage.dispose();
        if (skin != null)
            skin.dispose();
    }
}
