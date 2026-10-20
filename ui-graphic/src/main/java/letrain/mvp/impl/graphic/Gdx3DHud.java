package letrain.mvp.impl.graphic;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Colors;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Cell;
import com.badlogic.gdx.scenes.scene2d.ui.CheckBox;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.SplitPane;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.TextTooltip;
import com.badlogic.gdx.scenes.scene2d.ui.Tree;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import letrain.ground.TerrainPreview;
import letrain.itinerary.Punctuality;
import letrain.mvp.EditorText;
import letrain.mvp.FileMenuText;
import letrain.mvp.MenuText;
import letrain.mvp.CoverArt;
import letrain.mvp.Model;
import letrain.mvp.Model.GameModeMenuOption;
import letrain.mvp.NewGameText;
import letrain.mvp.SettingsText;
import letrain.palette.VisualPalette;
import letrain.settings.NewGameForm;
import letrain.settings.SettingCategory;
import letrain.settings.SettingDefinition;
import letrain.settings.SettingFieldState;
import letrain.settings.SettingValueType;
import letrain.settings.SettingsApplyResult;
import letrain.settings.SettingsCatalog;
import letrain.utils.FontManager;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;

public class Gdx3DHud {

    /**
     * Menu palette, mirroring the 2D terminal's {@code TerminalView} constants (issue #710): white
     * labels and shortcuts in green. A disabled entry is painted grey ({@code Color.GRAY}, libGDX's
     * {@code #808080}) and loses the green shortcut. Markup colours are the libGDX counterparts of
     * the terminal's {@code TextColor}s.
     */
    static final Color SELECTED_BG = new Color(0f, 0f, 0.67f, 1f);

    /**
     * Tint that greys out the read-only Terrain controls (seed field and water/mountain sliders) on
     * the Settings dialog, so they read as non-editable like the 2D block (#767).
     */
    private static final Color DISABLED_TINT = new Color(0.6f, 0.6f, 0.6f, 1f);
    private static final String ENABLED_MARKUP = "[WHITE]";
    private static final String DISABLED_MARKUP = "[GRAY]";
    private static final String HOTKEY_MARKUP = "[GREEN]";
    private static final String INFO_MARKUP = "[GRAY]";
    private static final String MARKUP_RESET = "[]";

    /**
     * Rows of the bottom menu block, in the same order as the 2D menu box (issue #710 phase 2): the
     * menu strip, the selected-train status, the selected-mode hint and the key help. Kept as data
     * so the deterministic order is testable without a GL context.
     */
    enum MenuRow {
        MENU, TRAIN, HINT, KEYS
    }

    static final List<MenuRow> MENU_BLOCK_ROWS =
            List.of(MenuRow.MENU, MenuRow.TRAIN, MenuRow.HINT, MenuRow.KEYS);

    /**
     * Columns of the bottom strip (issue #710 phases 2-3): the menu block on the left and the
     * status lines (Pos/Step over finances) right-aligned at the screen edge. Kept as data so the
     * deterministic order is testable without a GL context.
     */
    enum StripColumn {
        MENU_BLOCK, STATUS_LINES
    }

    static final List<StripColumn> STRIP_COLUMNS =
            List.of(StripColumn.MENU_BLOCK, StripColumn.STATUS_LINES);

    /** Both status lines are right-aligned at the screen edge (owner feedback on #719). */
    static final int STATUS_LINE_ALIGN = com.badlogic.gdx.utils.Align.right;

    /**
     * The menu block hugs the left edge: without an explicit alignment, Scene2D centers the table
     * content when it is narrower than its cell, which left a gap where the old notch lever used to
     * be in TRAINS/PROGRAM/COMMAND (short or absent hint).
     */
    static final int MENU_BLOCK_ALIGN = com.badlogic.gdx.utils.Align.left;

    private static final String KEYS_TEXT = "[Alt+▲▼ / Mouse Wheel]:Zoom | [Alt+◀▶]:Rotate"
            + " | [z/Z]:Camera | [a/r/d/f/s/t/c/u/p/n]:Modes | [Tab]:Toggle Panel"
            + " | [Esc]:Menu";


    private final Model model;
    private final GraphicPresenter view;
    private final Stage stage;
    private Skin skin;
    private Table menuTable;
    private Table bottomContainer;
    private Label trainLabel;
    private Label descLabel;
    private Label keysLabel;
    private Cell<Label> descCell;
    private Cell<Label> keysCell;
    private Label recDot;
    private Label clockLabel;
    private Label systemLabel;
    private Label financeLabel;

    // #818: per-frame update caches. Values are compared before formatting/setText so an idle frame
    // does no String.format and never invalidates the label layout.
    private List<GameModeMenuOption> menuOptionsCache;
    private final List<TextButton> menuButtons = new ArrayList<>();
    private boolean[] lastMenuEnabled = new boolean[0];
    private boolean[] lastMenuSelected = new boolean[0];
    private String lastDescription;
    private int lastSystemX = Integer.MIN_VALUE;
    private int lastSystemY;
    private int lastSystemSteps;
    private int lastSystemQuantifier;
    private Object lastSystemSaveTime;
    private boolean lastFinancePresent;
    private double lastIncome;
    private double lastExpenses;
    private double lastBalance;
    private Locomotive lastTrainLoco;
    private int lastTrainId;
    private int lastTrainSpeed;
    private int lastTrainTarget;
    private int lastTrainWagons;
    private boolean lastTrainReversed;
    private String lastTrainPunctuality;

    /** 2D parity: 2 full, 1 compact, 0 hidden (see {@link HudHelp}). */
    private int helpLevel = HudHelp.FULL;

    private Window ideWindow;
    private boolean fileMenuOpen;
    private boolean confirmDialogOpen;
    private boolean newGameDialogOpen;
    private boolean settingsDialogOpen;
    private Table coverTable;

    /** Cover capture bundled with the game (copying-core resources onto the classpath). */
    static final String COVER_IMAGE_PATH = "letrain/cover/cover_3d.png";

    /**
     * Texture behind the 3D title cover; null when the cover is hidden or the capture is missing.
     */
    private Texture coverTexture;

    /** Full-screen image of the title cover; null when there is no capture to paint. */
    private Image coverImage;
    /** "The Letter Train Simulator" overlay on the 3D cover (#801). */
    private Label coverTagline;

    private Window messageWindow;
    private ScrollPane messageScroll;
    private Label messageLabel;

    public Gdx3DHud(Model model, GraphicPresenter view) {
        this.model = model;
        this.view = view;
        this.stage = new Stage(new ScreenViewport());
        initUI();
    }

    public Stage getStage() {
        return stage;
    }

    /** True while the PROGRAM editor window is open (game shortcuts must be ignored then). */
    public boolean isIDEOpen() {
        return ideWindow != null;
    }

    private void initUI() {
        skin = new Skin();

        // Crear una skin procedimental básica
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        skin.add("white", new Texture(pixmap));

        BitmapFont uiFont = FontManager.loadFont("JuliaMono-Regular", 18);
        uiFont.getData().markupEnabled = true;
        skin.add("default", uiFont);

        // High-resolution fonts for HUD
        BitmapFont smallFont = FontManager.loadFont("JuliaMono-Regular", 18);
        BitmapFont tinyFont = FontManager.loadFont("JuliaMono-Regular", 12);
        BitmapFont mediumFont = FontManager.loadFont("JuliaMono-Regular", 26);
        BitmapFont largeFont = FontManager.loadFont("JuliaMono-Regular", 52);

        smallFont.getData().markupEnabled = true;
        tinyFont.getData().markupEnabled = true;
        mediumFont.getData().markupEnabled = true;
        largeFont.getData().markupEnabled = true;

        skin.add("small-font", smallFont);
        skin.add("tiny-font", tinyFont);
        skin.add("medium-font", mediumFont);
        skin.add("large-font", largeFont);

        // Monospace font for IDE
        BitmapFont monospaceFont = FontManager.loadMonospaceFont(18);
        monospaceFont.getData().markupEnabled = true;
        skin.add("monospace-font", monospaceFont);

        Label.LabelStyle monoLabelStyle = new Label.LabelStyle();
        monoLabelStyle.font = monospaceFont;
        monoLabelStyle.fontColor = Color.WHITE;
        skin.add("monospace", monoLabelStyle);

        TextField.TextFieldStyle textAreaStyle = new TextField.TextFieldStyle();
        textAreaStyle.font = monospaceFont;
        textAreaStyle.fontColor = Color.WHITE;
        textAreaStyle.selection = skin.newDrawable("white", new Color(0.2f, 0.2f, 0.8f, 0.5f));
        textAreaStyle.cursor = skin.newDrawable("white", Color.WHITE);
        textAreaStyle.background = skin.newDrawable("white", new Color(0.05f, 0.05f, 0.05f, 0.8f));
        skin.add("monospace-textarea", textAreaStyle);

        // TextButton Style (Menu Buttons)
        TextButton.TextButtonStyle textButtonStyle = new TextButton.TextButtonStyle();
        textButtonStyle.up = skin.newDrawable("white", new Color(0.2f, 0.2f, 0.2f, 1f));
        textButtonStyle.down = skin.newDrawable("white", Color.CYAN);
        textButtonStyle.checked = skin.newDrawable("white", SELECTED_BG);
        textButtonStyle.over = skin.newDrawable("white", new Color(0.15f, 0.15f, 0.15f, 1f));
        textButtonStyle.font = skin.getFont("default");
        textButtonStyle.fontColor = Color.WHITE;
        textButtonStyle.downFontColor = Color.WHITE;
        textButtonStyle.overFontColor = Color.WHITE;
        textButtonStyle.checkedFontColor = Color.WHITE;
        skin.add("default", textButtonStyle);

        // Focused variant (S4b feedback, #780): the focused button swaps to this style so the
        // highlight is a background block (SELECTED_BG) with white text, never a text colour swap.
        // Shared by the File menu, the confirmation dialog, the New Game dialog and the Settings
        // footer.
        TextButton.TextButtonStyle focusedButtonStyle =
                new TextButton.TextButtonStyle(textButtonStyle);
        focusedButtonStyle.up = skin.newDrawable("white", SELECTED_BG);
        focusedButtonStyle.over = skin.newDrawable("white", new Color(0.15f, 0.28f, 0.85f, 1f));
        focusedButtonStyle.fontColor = Color.WHITE;
        focusedButtonStyle.overFontColor = Color.WHITE;
        skin.add("focused", focusedButtonStyle);

        // Toggle Button Style (IDE Panels)
        TextButton.TextButtonStyle toggleStyle = new TextButton.TextButtonStyle();
        toggleStyle.up = skin.newDrawable("white", new Color(0.2f, 0.2f, 0.2f, 1f));
        toggleStyle.down = skin.newDrawable("white", Color.CYAN);
        toggleStyle.checked = skin.newDrawable("white", new Color(0.4f, 0.4f, 0.4f, 1f));
        toggleStyle.over = skin.newDrawable("white", new Color(0.3f, 0.3f, 0.3f, 1f));
        toggleStyle.font = skin.getFont("default");
        toggleStyle.fontColor = Color.WHITE;
        toggleStyle.downFontColor = Color.WHITE;
        toggleStyle.overFontColor = Color.WHITE;
        toggleStyle.checkedFontColor = Color.CYAN;
        skin.add("toggle", toggleStyle);

        TextButton.TextButtonStyle monoToggleStyle = new TextButton.TextButtonStyle(toggleStyle);
        monoToggleStyle.font = skin.getFont("monospace-font");
        skin.add("monospace-toggle", monoToggleStyle);

        TextButton.TextButtonStyle monoButtonStyle =
                new TextButton.TextButtonStyle(textButtonStyle);
        monoButtonStyle.font = skin.getFont("monospace-font");
        monoButtonStyle.up = skin.newDrawable("white", new Color(0.2f, 0.2f, 0.2f, 1f));
        skin.add("monospace-button", monoButtonStyle);

        // Flat menu strip (issue #710 phase 2): no background or padding so the entries read as
        // the 2D text row; only the selected mode keeps the blue block. Clickability is kept.
        TextButton.TextButtonStyle menuButtonStyle = new TextButton.TextButtonStyle();
        menuButtonStyle.up = null;
        menuButtonStyle.over = skin.newDrawable("white", new Color(1f, 1f, 1f, 0.12f));
        menuButtonStyle.down = skin.newDrawable("white", new Color(1f, 1f, 1f, 0.25f));
        menuButtonStyle.checked = skin.newDrawable("white", SELECTED_BG);
        menuButtonStyle.font = skin.getFont("default");
        menuButtonStyle.fontColor = Color.WHITE;
        menuButtonStyle.overFontColor = Color.WHITE;
        menuButtonStyle.downFontColor = Color.WHITE;
        menuButtonStyle.checkedFontColor = Color.WHITE;
        skin.add("menu-button", menuButtonStyle);

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

        // Info rows of the bottom menu block: the same grey #808080 the 2D menu box uses for its
        // help lines (issue #710 phase 2). The key row keeps the original tiny size so the full row
        // fits next to the notch lever and the finances.
        Label.LabelStyle hudInfoStyle = new Label.LabelStyle();
        hudInfoStyle.font = skin.getFont("small-font");
        hudInfoStyle.fontColor = Color.GRAY;
        skin.add("hud-info", hudInfoStyle);

        Label.LabelStyle hudKeysStyle = new Label.LabelStyle();
        hudKeysStyle.font = skin.getFont("tiny-font");
        hudKeysStyle.fontColor = Color.GRAY;
        skin.add("hud-keys", hudKeysStyle);

        Label.LabelStyle tinyLabelStyle = new Label.LabelStyle();
        tinyLabelStyle.font = skin.getFont("tiny-font");
        tinyLabelStyle.fontColor = Color.LIGHT_GRAY;
        skin.add("tiny", tinyLabelStyle);

        // Window Style
        Window.WindowStyle windowStyle = new Window.WindowStyle();
        windowStyle.titleFont = uiFont;

        // Window Background - White Border (Default)
        Pixmap pixWhite = new Pixmap(20, 20, Pixmap.Format.RGBA8888);
        pixWhite.setColor(Color.WHITE);
        pixWhite.fill();
        pixWhite.setColor(Color.BLACK);
        pixWhite.fillRectangle(6, 6, 8, 8);
        com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable windowWhite =
                new com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable(
                        new com.badlogic.gdx.graphics.g2d.NinePatch(new Texture(pixWhite), 6, 6, 6,
                                6));
        skin.add("window-white", windowWhite);

        // Window Background - Cyan Border (Hover)
        Pixmap pixCyan = new Pixmap(20, 20, Pixmap.Format.RGBA8888);
        pixCyan.setColor(Color.CYAN);
        pixCyan.fill();
        pixCyan.setColor(Color.BLACK);
        pixCyan.fillRectangle(6, 6, 8, 8);
        com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable windowCyan =
                new com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable(
                        new com.badlogic.gdx.graphics.g2d.NinePatch(new Texture(pixCyan), 6, 6, 6,
                                6));
        skin.add("window-cyan", windowCyan);

        // Window Background - Blue Border (Resize Hover)
        Pixmap pixBlue = new Pixmap(20, 20, Pixmap.Format.RGBA8888);
        pixBlue.setColor(new Color(0.2f, 0.55f, 1.0f, 1f));
        pixBlue.fill();
        pixBlue.setColor(Color.BLACK);
        pixBlue.fillRectangle(6, 6, 8, 8);
        com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable windowBlue =
                new com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable(
                        new com.badlogic.gdx.graphics.g2d.NinePatch(new Texture(pixBlue), 6, 6, 6,
                                6));
        skin.add("window-blue", windowBlue);

        windowStyle.background = windowWhite;
        windowStyle.titleFontColor = Color.WHITE;
        skin.add("default", windowStyle);

        // Dialog Style - dark panel with a light border and roomier title (the plain window
        // style makes dialogs look cramped and hides the title behind its white frame).
        Window.WindowStyle dialogStyle = new Window.WindowStyle();
        dialogStyle.titleFont = skin.getFont("medium-font");
        dialogStyle.titleFontColor = new Color(1f, 0.85f, 0.4f, 1f);
        Pixmap pixDialog = new Pixmap(32, 32, Pixmap.Format.RGBA8888);
        pixDialog.setColor(new Color(0.05f, 0.06f, 0.08f, 0.98f));
        pixDialog.fill();
        pixDialog.setColor(new Color(0.29f, 0.65f, 1f, 1f));
        pixDialog.drawRectangle(0, 0, 32, 32);
        pixDialog.drawRectangle(1, 1, 30, 30);
        dialogStyle.background = new com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable(
                new com.badlogic.gdx.graphics.g2d.NinePatch(new Texture(pixDialog), 10, 10, 10,
                        10));
        skin.add("dialog", dialogStyle);

        // TextField/TextArea Style
        TextField.TextFieldStyle textFieldStyle = new TextField.TextFieldStyle();
        textFieldStyle.font = uiFont;
        textFieldStyle.fontColor = Color.WHITE;
        textFieldStyle.background = skin.newDrawable("white", new Color(0.2f, 0.2f, 0.2f, 1f));
        textFieldStyle.cursor = skin.newDrawable("white", Color.WHITE);
        textFieldStyle.selection = skin.newDrawable("white", Color.BLUE);
        skin.add("default", textFieldStyle);

        // Slider style shared by the New Game terrain proportions (S4, #777) and the read-only
        // Terrain page of the in-game Settings (S4b, #778). The skin's "white" is a 1x1 pixel with
        // no minimum size, so the bare drawables collapsed to "needles"; the explicit minimums and
        // the round knob give the track real thickness. The disabled variants paint the read-only
        // Terrain sliders grey without the setColor(DISABLED_TINT) hack.
        Slider.SliderStyle sliderStyle = new Slider.SliderStyle();
        sliderStyle.background = sliderTrack(skin, new Color(0.2f, 0.2f, 0.2f, 1f));
        sliderStyle.knobBefore = sliderTrack(skin, new Color(0.29f, 0.65f, 1f, 0.45f));
        sliderStyle.knob = sliderKnob(new Color(0.29f, 0.65f, 1f, 1f));
        sliderStyle.disabledBackground = sliderTrack(skin, new Color(0.35f, 0.35f, 0.35f, 1f));
        sliderStyle.disabledKnobBefore = sliderTrack(skin, new Color(0.5f, 0.5f, 0.5f, 0.45f));
        sliderStyle.disabledKnob = sliderKnob(new Color(0.5f, 0.5f, 0.5f, 1f));
        skin.add("default-horizontal", sliderStyle);

        // Focused variant of the slider (S4b feedback, #780): same track, cyan knob, so the
        // focused slider reads as selected without changing the geometry.
        Slider.SliderStyle focusedSliderStyle = new Slider.SliderStyle(sliderStyle);
        focusedSliderStyle.knob = sliderKnob(Color.CYAN);
        skin.add("focused-horizontal", focusedSliderStyle);

        // CheckBox style for the boolean settings (S4b, #778): a 26x26 square, hand-drawn off/on
        // (dark square with a light border, on adds the accent check) plus grey disabled variants.
        CheckBox.CheckBoxStyle checkBoxStyle = new CheckBox.CheckBoxStyle();
        checkBoxStyle.font = skin.getFont("default");
        checkBoxStyle.fontColor = Color.WHITE;
        checkBoxStyle.disabledFontColor = Color.GRAY;
        checkBoxStyle.checkboxOff = checkboxDrawable(false, false);
        checkBoxStyle.checkboxOn = checkboxDrawable(true, false);
        checkBoxStyle.checkboxOffDisabled = checkboxDrawable(false, true);
        checkBoxStyle.checkboxOnDisabled = checkboxDrawable(true, true);
        skin.add("default", checkBoxStyle);

        // ScrollPane Style
        ScrollPane.ScrollPaneStyle scrollPaneStyle = new ScrollPane.ScrollPaneStyle();
        scrollPaneStyle.background = skin.newDrawable("white", Color.DARK_GRAY);
        scrollPaneStyle.vScroll = skin.newDrawable("white", Color.GRAY);
        scrollPaneStyle.vScrollKnob = skin.newDrawable("white", Color.LIGHT_GRAY);
        // SplitPane Style
        SplitPane.SplitPaneStyle splitPaneStyle = new SplitPane.SplitPaneStyle();
        com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable handle =
                (com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable) skin.newDrawable("white",
                        Color.WHITE);
        handle.setMinWidth(6f);
        handle.setMinHeight(6f);
        splitPaneStyle.handle = handle;
        skin.add("default-horizontal", splitPaneStyle);
        skin.add("default-vertical", splitPaneStyle);

        SplitPane.SplitPaneStyle splitPaneStyleHover = new SplitPane.SplitPaneStyle(splitPaneStyle);
        com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable handleHover =
                (com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable) skin.newDrawable("white",
                        Color.CYAN);
        handleHover.setMinWidth(6f);
        handleHover.setMinHeight(6f);
        splitPaneStyleHover.handle = handleHover;
        skin.add("default-horizontal-hover", splitPaneStyleHover);
        skin.add("default-vertical-hover", splitPaneStyleHover);

        skin.add("default", scrollPaneStyle);

        // ProgressBar Style
        com.badlogic.gdx.scenes.scene2d.ui.ProgressBar.ProgressBarStyle progressBarStyle =
                new com.badlogic.gdx.scenes.scene2d.ui.ProgressBar.ProgressBarStyle();
        progressBarStyle.background = skin.newDrawable("white", Color.DARK_GRAY);
        progressBarStyle.knobBefore = skin.newDrawable("white", Color.CYAN);
        skin.add("default-horizontal", progressBarStyle);

        // Triangles for Insertion Buttons
        Pixmap pixmapTriangleG = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
        pixmapTriangleG.setColor(Color.GREEN);
        pixmapTriangleG.fillTriangle(0, 8, 16, 0, 16, 16);
        skin.add("green-triangle", new Texture(pixmapTriangleG));

        Pixmap pixmapTriangleW = new Pixmap(16, 16, Pixmap.Format.RGBA8888);
        pixmapTriangleW.setColor(Color.WHITE);
        pixmapTriangleW.fillTriangle(0, 8, 16, 0, 16, 16);
        skin.add("white-triangle", new Texture(pixmapTriangleW));

        // Top-left REC indicator (blinking red dot while the command journal records)
        Table mainTopTable = new Table();
        mainTopTable.setFillParent(true);
        mainTopTable.top().left();
        recDot = new Label("REC", skin, "small");
        recDot.setColor(Color.RED);
        recDot.setVisible(false);
        mainTopTable.add(recDot).pad(6);

        // Game clock (ADR-022): ASCII on purpose, the HUD bitmap fonts may lack accents.
        clockLabel = new Label("", skin, "small");
        clockLabel.setColor(Color.WHITE);
        mainTopTable.add(clockLabel).pad(6);
        stage.addActor(mainTopTable);

        // Bottom UI Container (issue #710 phases 2-3): the menu block on the left and the two
        // status lines right-aligned at the screen edge -- `|Pos:x,y|Step:a/b|` on top (no Page,
        // the 3D HUD has no pagination) and the compact `|In:...|Out:...|$:...|` finances just
        // below. The old graphical notch lever is gone: the 2D-style speed bar lives in the menu
        // block's Train row.
        Table mainBottomTable = new Table();
        mainBottomTable.setFillParent(true);
        mainBottomTable.bottom();
        stage.addActor(mainBottomTable);

        menuTable = new Table();
        // menuTable is populated in updateMenuButtons()

        trainLabel = new Label("", skin, "hud-info");
        trainLabel.setAlignment(com.badlogic.gdx.utils.Align.left);
        descLabel = new Label("", skin, "small");
        descLabel.setAlignment(com.badlogic.gdx.utils.Align.left);
        // Long hints truncate with an ellipsis instead of drawing over the status column.
        descLabel.setEllipsis(true);
        keysLabel = new Label(KEYS_TEXT, skin, "hud-keys");
        keysLabel.setAlignment(com.badlogic.gdx.utils.Align.left);

        systemLabel = new Label("", skin, "hud-info");
        systemLabel.setAlignment(STATUS_LINE_ALIGN);
        financeLabel = new Label("", skin, "hud-info");
        financeLabel.setAlignment(STATUS_LINE_ALIGN);

        Table statusArea = new Table();
        statusArea.add(systemLabel).right().row();
        statusArea.add(financeLabel).right().padTop(2);

        Table labelArea = new Table();
        labelArea.align(MENU_BLOCK_ALIGN);
        addMenuBlockRows(labelArea);

        bottomContainer = new Table();
        bottomContainer.setBackground(skin.newDrawable("white", new Color(0, 0, 0, 0.6f)));
        bottomContainer.pad(10);
        addStripColumns(bottomContainer, labelArea, statusArea);

        mainBottomTable.add(bottomContainer).expandX().fillX();

        updateMenuButtons();
        applyHelpLevel();
    }

    /**
     * Track/base segment of the sliders: the shared "white" pixel with an explicit minimum height
     * so the 1x1 drawable does not collapse to a needle (#778).
     */
    private static Drawable sliderTrack(Skin skin, Color color) {
        BaseDrawable track = (BaseDrawable) skin.newDrawable("white", color);
        track.setMinHeight(8f);
        return track;
    }

    /**
     * Round knob of the sliders: a 24x24 filled disc in {@code color}, kept square via equal
     * minimums so Scene2D never deforms it into an ellipse (#778).
     */
    private static Drawable sliderKnob(Color color) {
        Pixmap pixmap = new Pixmap(24, 24, Pixmap.Format.RGBA8888);
        pixmap.setColor(color);
        pixmap.fillCircle(12, 12, 11);
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        TextureRegionDrawable knob = new TextureRegionDrawable(new TextureRegion(texture));
        knob.setMinWidth(24f);
        knob.setMinHeight(24f);
        return knob;
    }

    /**
     * Checkbox square for the boolean settings: a dark square with a light border; the checked
     * variant adds the accent tick, the disabled ones grey everything out (#778).
     */
    private static Drawable checkboxDrawable(boolean checked, boolean disabled) {
        int size = 26;
        Color fill =
                disabled ? new Color(0.18f, 0.18f, 0.18f, 1f) : new Color(0.15f, 0.15f, 0.15f, 1f);
        Color border =
                disabled ? new Color(0.45f, 0.45f, 0.45f, 1f) : new Color(0.7f, 0.7f, 0.7f, 1f);
        Color accent = disabled ? new Color(0.5f, 0.5f, 0.5f, 1f) : new Color(0.29f, 0.65f, 1f, 1f);
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pixmap.setColor(fill);
        pixmap.fill();
        pixmap.setColor(border);
        pixmap.drawRectangle(0, 0, size, size);
        pixmap.drawRectangle(1, 1, size - 2, size - 2);
        if (checked) {
            // Two-stroke tick: short down-right leg into a long up-right leg, doubled for weight.
            pixmap.setColor(accent);
            pixmap.drawLine(6, 14, 11, 19);
            pixmap.drawLine(7, 14, 12, 19);
            pixmap.drawLine(11, 19, 20, 8);
            pixmap.drawLine(11, 18, 20, 7);
        }
        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        TextureRegionDrawable square = new TextureRegionDrawable(new TextureRegion(texture));
        square.setMinWidth(size);
        square.setMinHeight(size);
        return square;
    }

    /** Adds the bottom strip columns in {@link #STRIP_COLUMNS} order. */
    private static void addStripColumns(Table strip, Table labelArea, Table statusArea) {
        for (StripColumn column : STRIP_COLUMNS) {
            switch (column) {
                case MENU_BLOCK -> strip.add(labelArea).expandX().fillX().padRight(20);
                case STATUS_LINES -> strip.add(statusArea).right().top().padRight(10);
            }
        }
    }

    /**
     * Adds the bottom menu-block rows in {@link #MENU_BLOCK_ROWS} order. The hint and key rows keep
     * their cells so {@link #setRowVisible} can release their height when hidden: a hidden actor
     * still reserves its cell height in Scene2D (#652).
     */
    private void addMenuBlockRows(Table panel) {
        for (MenuRow row : MENU_BLOCK_ROWS) {
            switch (row) {
                case MENU -> {
                    panel.add(menuTable).left();
                    panel.row();
                }
                case TRAIN -> {
                    panel.add(trainLabel).left().padTop(2);
                    panel.row();
                }
                case HINT -> {
                    // minWidth 0 so the column can shrink under a long hint and the label
                    // ellipsizes instead of drawing over the right-aligned status column.
                    descCell = panel.add(descLabel).left().padTop(2).minWidth(0);
                    panel.row();
                }
                case KEYS -> {
                    keysCell = panel.add(keysLabel).left().padTop(2);
                    panel.row();
                }
            }
        }
    }

    /** Applies a help level (2 full, 1 compact, 0 hidden); the presenter cycles it via Tab. */
    public void setHelpLevel(int level) {
        this.helpLevel = Math.max(HudHelp.HIDDEN, Math.min(HudHelp.FULL, level));
        applyHelpLevel();
    }

    private void applyHelpLevel() {
        bottomContainer.setVisible(HudHelp.showBottomPanel(helpLevel));
        setRowVisible(keysCell, keysLabel, HudHelp.showKeyHelp(helpLevel));
    }

    /**
     * Shows or hides a row by releasing its cell: {@code Actor.setVisible(false)} alone would keep
     * the row height reserved (#652).
     */
    private static void setRowVisible(Cell<?> cell, Actor actor, boolean visible) {
        if (visible) {
            if (cell.getActor() == null) {
                cell.setActor(actor);
            }
        } else if (cell.getActor() != null) {
            cell.setActor(null);
        }
    }

    /**
     * Swaps a button between the normal and the focused (SELECTED_BG background) styles. Used by
     * every 3D dialog so the focused control is highlighted with a background block, never a text
     * colour change (owner feedback S4b, #780).
     */
    private void paintButtonFocus(TextButton button, boolean focused) {
        button.setStyle(
                skin.get(focused ? "focused" : "default", TextButton.TextButtonStyle.class));
    }

    /**
     * Swaps a slider between the normal and the focused (cyan knob) styles (S4b feedback, #780).
     */
    private void paintSliderFocus(Slider slider, boolean focused) {
        slider.setStyle(skin.get(focused ? "focused-horizontal" : "default-horizontal",
                Slider.SliderStyle.class));
    }

    /**
     * Label for a menu button with libGDX markup: white text, the shortcut in green; a disabled
     * mode is painted grey and loses the shortcut colour. The label is split with the shared
     * {@link MenuText} parser, the same one the 2D terminal uses, so wording and shortcuts cannot
     * drift between clients. Static and package-visible so a test can check it without a GL
     * context.
     */
    static String getMenuButtonText(String rawName, boolean isEnabled) {
        MenuText.Label label = MenuText.parse(rawName);
        String plainText = label.plainText();
        if (plainText.isEmpty()) {
            return "";
        }
        if (!isEnabled) {
            return DISABLED_MARKUP + plainText + MARKUP_RESET;
        }
        StringBuilder text = new StringBuilder();
        if (!label.prefix().isEmpty()) {
            text.append(ENABLED_MARKUP).append(label.prefix()).append(MARKUP_RESET);
        }
        if (label.hasHotkey()) {
            text.append(HOTKEY_MARKUP).append(label.hotkey()).append(MARKUP_RESET);
        }
        if (!label.suffix().isEmpty()) {
            text.append(ENABLED_MARKUP).append(label.suffix()).append(MARKUP_RESET);
        }
        return text.toString();
    }

    /**
     * Tab text of the editor window: the shared {@link EditorText} tab names in the HUD's padded
     * button style, so the 2D and 3D tabs read the same.
     */
    static String editorTabText(String label) {
        return " " + label + " ";
    }

    /** Tab text with the mnemonic tinted (Alt hint), mirroring the 2D tab highlight. */
    static String editorTabAltText(String label, char mnemonic) {
        return mnemonicMarkup(label, mnemonic, false);
    }

    /**
     * Footer button text of the editor window: the shared {@link EditorText} words in the HUD's
     * uppercase button style (the 2D footer shows the same words in title case).
     */
    static String editorButtonText(String label) {
        return " " + label.toUpperCase(Locale.ROOT) + " ";
    }

    /**
     * Footer button text with the mnemonic tinted (Alt hint), mirroring the 2D footer highlight.
     */
    static String editorButtonAltText(String label, char mnemonic) {
        return mnemonicMarkup(label, mnemonic, true);
    }

    /**
     * Pads {@code label} and tints the first occurrence of {@code mnemonic} with the shortcut
     * colour; the 2D renderers highlight the same letter, so both Alt hints match.
     */
    private static String mnemonicMarkup(String label, char mnemonic, boolean upperCase) {
        String text = upperCase ? label.toUpperCase(Locale.ROOT) : label;
        int at = indexOfIgnoreCase(text, mnemonic);
        if (at < 0) {
            return " " + text + " ";
        }
        return " " + text.substring(0, at) + "[#42A5F5]" + text.charAt(at) + "[]"
                + text.substring(at + 1) + " ";
    }

    /** First position of {@code letter} in {@code text}, ignoring case; -1 when absent. */
    private static int indexOfIgnoreCase(String text, char letter) {
        char upper = Character.toUpperCase(letter);
        for (int i = 0; i < text.length(); i++) {
            if (Character.toUpperCase(text.charAt(i)) == upper) {
                return i;
            }
        }
        return -1;
    }

    public void updateMenuButtons() {
        menuTable.clearChildren();
        menuButtons.clear();
        List<GameModeMenuOption> options = menuOptions();
        lastMenuEnabled = new boolean[options.size()];
        lastMenuSelected = new boolean[options.size()];
        for (int i = 0; i < options.size(); i++) {
            GameModeMenuOption option = options.get(i);
            boolean isEnabled = option.enabledIf().get();
            lastMenuEnabled[i] = isEnabled;
            String formattedName = getMenuButtonText(option.gameModeName(), isEnabled);

            TextButton button = new TextButton(formattedName, skin, "menu-button");
            button.setName(menuButtonName(option.gameModeName()));
            button.setDisabled(!isEnabled);

            button.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (!button.isDisabled()) {
                        letrain.mvp.Model.GameMode newMode = option.doWhenSelected().get();
                        model.setMode(newMode);
                        view.onGameModeSelected(newMode);

                        // Inicialización de estados al cambiar de modo mediante botones
                        if (newMode == letrain.mvp.Model.GameMode.LINK) {
                            if (model.getSelectedLocomotive() != null
                                    && model.getSelectedLocomotive().getTrain() != null) {
                                Train train = model.getSelectedLocomotive().getTrain();
                                train.getTrainCouplingManager().resetLinkState(train);
                            }
                        } else if (newMode == letrain.mvp.Model.GameMode.UNLINK) {
                            if (model.getSelectedLocomotive() != null
                                    && model.getSelectedLocomotive().getTrain() != null) {
                                Train train = model.getSelectedLocomotive().getTrain();
                                train.getTrainCouplingManager().resetUnlinkState(train);
                            }
                        }
                    }
                }
            });

            menuTable.add(button).padRight(10);
            menuButtons.add(button);
        }
    }

    /**
     * Shared menu definition: the wording and the {@code enabledIf}/{@code selectedIf} suppliers
     * are constant for a model, so the HUD builds the list once instead of allocating twelve
     * options per frame (#818). A new HUD is created when the model is swapped.
     */
    private List<GameModeMenuOption> menuOptions() {
        if (menuOptionsCache == null) {
            menuOptionsCache = model.getMenuModel();
        }
        return menuOptionsCache;
    }

    public void updateHUD() {
        // Force UI update
        updateUIData();
    }

    /** Refreshes the game clock label; only rewrites the text when it actually changed. */
    public void updateClock() {
        if (clockLabel == null || model.getGameClock() == null) {
            return;
        }
        letrain.time.GameTime now = model.getGameClock().now();
        String text = String.format("D%d %02d:%02d", now.day(), now.hour(), now.minute());
        if (!text.equals(clockLabel.getText().toString())) {
            clockLabel.setText(text);
        }
    }

    public void updateUIData() {
        // Blinking REC indicator while the command journal records.
        if (recDot != null) {
            boolean recording = model.getCommandJournal() != null
                    && model.getCommandJournal().isRecording() && model.isSimulationPaused();
            recDot.setVisible(recording && (System.currentTimeMillis() / 500) % 2 == 0);
        }
        updateClock();
        updateSystemLine();
        updateFinanceLine();
        updateTrainLine();

        // Marcamos el botón seleccionado según el modo y actualizamos textos
        // dinámicamente
        setRowVisible(descCell, descLabel, showMenuDescription(helpLevel, model.getMode()));
        updateMenuState();

        if (model.getMode() == letrain.mvp.Model.GameMode.COMMAND) {
            setTextIfChanged(descLabel, commandLineText(model));
        }
    }

    /** Notch row (2D format, no Page): only reformats when cursor/step/save-time changed. */
    private void updateSystemLine() {
        int x = model.getCursor().getPosition().getX();
        int y = model.getCursor().getPosition().getY();
        int steps = model.getQuantifierSteps();
        int quantifier = model.getQuantifier();
        Object saveTime = model.getLastSaveTime();
        if (x == lastSystemX && y == lastSystemY && steps == lastSystemSteps
                && quantifier == lastSystemQuantifier
                && java.util.Objects.equals(saveTime, lastSystemSaveTime)) {
            return;
        }
        lastSystemX = x;
        lastSystemY = y;
        lastSystemSteps = steps;
        lastSystemQuantifier = quantifier;
        lastSystemSaveTime = saveTime;
        setTextIfChanged(systemLabel, systemInfoText(model));
    }

    /** Compact finances line: only reformats when one of the three amounts changed. */
    private void updateFinanceLine() {
        if (model.getEconomyManager() == null) {
            if (lastFinancePresent) {
                lastFinancePresent = false;
                setTextIfChanged(financeLabel, "");
            }
            return;
        }
        double income = model.getEconomyManager().getTotalIncome();
        double expenses = model.getEconomyManager().getTotalExpenses();
        double balance = model.getEconomyManager().getBalance();
        if (lastFinancePresent && Double.compare(income, lastIncome) == 0
                && Double.compare(expenses, lastExpenses) == 0
                && Double.compare(balance, lastBalance) == 0) {
            return;
        }
        lastFinancePresent = true;
        lastIncome = income;
        lastExpenses = expenses;
        lastBalance = balance;
        setTextIfChanged(financeLabel, financeText(model));
    }

    /**
     * Train row: the 2D-style speed bar is the only notch indicator (the graphical lever was
     * removed). Only reformats when the selected train state changed; a null selection clears it.
     */
    private void updateTrainLine() {
        Locomotive loco = model.getSelectedLocomotive();
        if (loco == null) {
            lastTrainLoco = null;
            setTextIfChanged(trainLabel, "");
            return;
        }
        Train train = loco.getTrain();
        int trainId = train != null ? train.getId() : loco.getId();
        int speed = loco.getSpeed();
        int target = loco.getTargetSpeed();
        int wagons = (train != null && train.getLinkers() != null)
                ? Math.max(0, train.getLinkers().size() - 1)
                : 0;
        boolean reversed = loco.isReversed();
        String punctuality = punctualitySuffix(train);
        if (loco == lastTrainLoco && trainId == lastTrainId && speed == lastTrainSpeed
                && target == lastTrainTarget && wagons == lastTrainWagons
                && reversed == lastTrainReversed
                && java.util.Objects.equals(punctuality, lastTrainPunctuality)) {
            return;
        }
        lastTrainLoco = loco;
        lastTrainId = trainId;
        lastTrainSpeed = speed;
        lastTrainTarget = target;
        lastTrainWagons = wagons;
        lastTrainReversed = reversed;
        lastTrainPunctuality = punctuality;
        setTextIfChanged(trainLabel, trainStatusText(loco));
    }

    /**
     * Menu rows: one pass over the shared options and the buttons created by
     * {@link #updateMenuButtons()} (same order). Only changed rows touch their widget, so the
     * buttons×options double loop and the per-frame label rebuilds are gone (#818).
     */
    private void updateMenuState() {
        List<GameModeMenuOption> options = menuOptions();
        String selectedDescription = null;
        int count = Math.min(menuButtons.size(), options.size());
        for (int i = 0; i < count; i++) {
            GameModeMenuOption option = options.get(i);
            TextButton button = menuButtons.get(i);
            boolean enabled = option.enabledIf().get();
            if (i < lastMenuEnabled.length && lastMenuEnabled[i] != enabled) {
                lastMenuEnabled[i] = enabled;
                button.setDisabled(!enabled);
                // Only rebuild the label markup when the grey/white state actually changed.
                button.setText(getMenuButtonText(option.gameModeName(), enabled));
            }
            boolean selected = option.selectedIf().get();
            if (i < lastMenuSelected.length && lastMenuSelected[i] != selected) {
                lastMenuSelected[i] = selected;
                button.setChecked(selected);
            }
            if (selected) {
                selectedDescription =
                        INFO_MARKUP + MenuText.selectedHint(option.gameModeDescription(),
                                commandJournalRecording()) + MARKUP_RESET;
            }
        }
        if (selectedDescription != null) {
            setTextIfChanged(descLabel, selectedDescription);
        }
    }

    /** Sets a label only when the text really differs, avoiding a layout invalidation per frame. */
    private static void setTextIfChanged(Label label, String text) {
        if (label != null && !text.contentEquals(label.getText())) {
            label.setText(text);
        }
    }

    /** Stable name used to match a rendered button with its model option. */
    private static String menuButtonName(String rawName) {
        return MenuText.parse(rawName).plainText().toLowerCase();
    }

    /** Command-journal recording state, read exactly as the 2D help bar does. */
    private boolean commandJournalRecording() {
        return model.getCommandJournal() != null && model.getCommandJournal().isRecording()
                && model.isSimulationPaused();
    }

    /**
     * Whether the selected-mode description is visible: at the full help level, like the 2D help
     * bar, and whenever the console is active because the command line is rendered there.
     * Package-visible so a test can check the 2D parity of the visibility rule.
     */
    static boolean showMenuDescription(int helpLevel, Model.GameMode mode) {
        return HudHelp.showSelectedHint(helpLevel) || mode == Model.GameMode.COMMAND;
    }

    /**
     * Key-help row of the top-left block, worded in the 2D style (bracketed keys, sentence-case
     * actions, {@code [key]:Action}). Only bindings that exist in {@code Gdx3DInputHandler} are
     * listed: Alt+arrows and the mouse wheel zoom, Alt+arrows rotate, z/Z cycle the camera, the
     * mode letters, Tab and Esc. The 2D {@code [PgUp/Dn]:Scroll} row has no 3D binding, so it is
     * omitted. Static and package-visible so a test can check it without a GL context.
     */
    static String keysText() {
        return KEYS_TEXT;
    }

    /**
     * System info line of the notch row, replicating the 2D {@code InfoVisitor} wording except the
     * page part: position, quantifier step and, when the game was saved, the last save time
     * ({@code |Pos:x,y|Step:a/b|Saved:HH:MM|}). The 3D HUD has no pagination. Static and
     * package-visible so a test can check it without a GL context.
     */
    static String systemInfoText(Model model) {
        String pos = model.getCursor().getPosition().getX() + ","
                + model.getCursor().getPosition().getY();
        StringBuilder info = new StringBuilder();
        info.append("|Pos:").append(pos).append("|Step:").append(model.getQuantifierSteps())
                .append("/").append(model.getQuantifier()).append("|");
        if (model.getLastSaveTime() != null) {
            info.append("Saved:").append(model.getLastSaveTime().toString().substring(11, 16))
                    .append("|");
        }
        return info.toString();
    }

    /**
     * Compact finances line under the notch row, same text and format as the 2D info bar:
     * {@code |In:...|Out:...|$:...|}. Static and package-visible so a test can check it without a
     * GL context.
     */
    static String financeText(Model model) {
        if (model.getEconomyManager() == null) {
            return "";
        }
        return String.format(java.util.Locale.US, "|In:%,.2f|Out:%,.2f|$:%,.2f|",
                model.getEconomyManager().getTotalIncome(),
                model.getEconomyManager().getTotalExpenses(),
                model.getEconomyManager().getBalance());
    }

    /**
     * Selected-train status row, same format as the 2D info bar (train id, 10-cell notch bar, speed
     * progression and wagon count) with libGDX colour markup. Static and package-visible so a test
     * can check the parity without a GL context.
     */
    static String trainStatusText(Locomotive loco) {
        if (loco == null) {
            return "";
        }
        Train train = loco.getTrain();
        int trainId = train != null ? train.getId() : loco.getId();
        int speed = loco.getSpeed();
        int target = loco.getTargetSpeed();
        String speedStr;
        if (speed < target) {
            speedStr = speed + "->" + target;
        } else if (speed > target) {
            speedStr = target + "<-" + speed;
        } else {
            speedStr = String.valueOf(speed);
        }
        int wagonsCount = (train != null && train.getLinkers() != null)
                ? Math.max(0, train.getLinkers().size() - 1)
                : 0;
        return String.format("Train: %d | Speed: %s %s | Wagons: %d%s%s", trainId,
                notchBar(speed, target, 10), speedStr, wagonsCount,
                loco.isReversed() ? " (Rev)" : "", punctualitySuffix(train));
    }

    /**
     * Compact punctuality suffix for the Train row (ADR-022 phase 2c): {@code " | Punct: +2"}.
     * Empty when the train has no autopilot or no measured stop, so a train without times shows
     * nothing. The value comes from {@link Punctuality#compact()}, the same formatting the
     * {@code info train N} block uses.
     */
    private static String punctualitySuffix(Train train) {
        if (train == null || train.getAutopilot() == null) {
            return "";
        }
        return train.getAutopilot().punctuality().map(Punctuality::compact)
                .map(value -> " | Punct: " + value).orElse("");
    }

    /**
     * 10-cell notch bar with the 2D semantics: current speed in green, the target in red when it
     * differs, and empty cells in the label's grey.
     */
    static String notchBar(int current, int target, int max) {
        StringBuilder bar = new StringBuilder();
        for (int i = 1; i <= max; i++) {
            if (i == target && current != target) {
                bar.append("[RED]■[]");
            } else if (i <= current) {
                bar.append("[GREEN]■[]");
            } else {
                bar.append("□");
            }
        }
        return bar.toString();
    }

    /**
     * Command bar text plus its short feedback. Errors are red; notices (D1 contextual channel)
     * yellow. Static and package-visible so a test can check it without a GL context.
     */
    static String commandLineText(letrain.mvp.Model model) {
        String desc = ":" + model.getCommandText() + "_";
        if (model.getCommandError() != null && !model.getCommandError().isEmpty()) {
            desc += " [RED][ERROR: " + model.getCommandError() + "][]";
        }
        if (model.getCommandNotice() != null && !model.getCommandNotice().isEmpty()) {
            desc += " [YELLOW][NOTICE: " + model.getCommandNotice() + "][]";
        }
        return desc;
    }

    /**
     * Full-screen title cover: shown at startup, any key continues (issue #743). Since #775 it
     * paints the capture of the letter-train on a black backdrop, edge to edge in {@code cover}
     * mode (no text, no letterboxing: the overflow of the longer axis is cropped off screen). If
     * the capture is missing the cover degrades to the plain black backdrop so startup never
     * breaks.
     */
    public void showCover() {
        if (coverTable != null) {
            return;
        }
        coverTable = new Table();
        coverTable.setFillParent(true);
        coverTable.setBackground(skin.newDrawable("white", new Color(0f, 0f, 0f, 1f)));

        coverTexture = openCoverTexture();
        if (coverTexture != null) {
            coverImage = new Image(new TextureRegionDrawable(new TextureRegion(coverTexture)));
            // A plain WidgetGroup does not clip its children, so the image can spill past the
            // screen edges; the Table only stretches the layer to the stage size.
            WidgetGroup coverLayer = new WidgetGroup();
            coverLayer.setFillParent(true);
            coverLayer.addActor(coverImage);
            // 2D parity (#801): the same subtitle the braille art carries, as a monospace overlay
            // so the capture stays untouched. Positioned and scaled in layoutCoverImage.
            coverTagline = new Label(CoverArt.TAGLINE, skin, "monospace");
            coverTagline.setColor(Color.WHITE);
            coverLayer.addActor(coverTagline);
            coverTable.add(coverLayer).grow();
            layoutCoverImage(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        } else {
            Gdx.app.error("LeTrain", "3D cover capture not found: " + COVER_IMAGE_PATH);
        }
        stage.addActor(coverTable);
    }

    /**
     * Loads the bundled cover capture, or {@code null} if it is missing in this build. Kept apart
     * so the many headless tests of the HUD do not need a real texture.
     */
    private static Texture openCoverTexture() {
        FileHandle file = Gdx.files.internal(COVER_IMAGE_PATH);
        if (!file.exists()) {
            return null;
        }
        Texture texture = new Texture(file);
        texture.setFilter(TextureFilter.Linear, TextureFilter.Linear);
        return texture;
    }

    /**
     * Sizes and centres the cover image in {@code cover} mode: it fills the screen on both axes and
     * the overflow of the longer axis is simply off screen. Re-run from {@link #resize(int, int)}
     * so the cover follows a window resize.
     */
    private void layoutCoverImage(int screenWidth, int screenHeight) {
        if (coverImage == null || coverTexture == null) {
            return;
        }
        int imageWidth = coverTexture.getWidth();
        int imageHeight = coverTexture.getHeight();
        float scale = coverScale(imageWidth, imageHeight, screenWidth, screenHeight);
        float width = imageWidth * scale;
        float height = imageHeight * scale;
        coverImage.setSize(width, height);
        coverImage.setPosition(coverOffset(width, screenWidth), coverOffset(height, screenHeight));
        if (coverTagline != null) {
            // Legible at any window size: the monospace label scales with the height and stays
            // centred near the bottom, just under the capture.
            float fontScale = Math.max(0.8f, Math.min(2.4f, screenHeight / 720f));
            coverTagline.setFontScale(fontScale);
            coverTagline.pack();
            coverTagline.setPosition((screenWidth - coverTagline.getWidth()) / 2f,
                    Math.max(12f, screenHeight * 0.055f));
        }
    }

    /**
     * Scale that makes an {@code imageWidth x imageHeight} picture cover a {@code screenWidth x
     * screenHeight} screen without deforming it: the larger of the two axis ratios, so the other
     * axis overflows. Returns {@code 1f} for degenerate sizes (zero/negative) to avoid division by
     * zero or a {@code NaN}; package-visible and static so a test can pin it without a GL context.
     */
    static float coverScale(int imageWidth, int imageHeight, int screenWidth, int screenHeight) {
        if (imageWidth <= 0 || imageHeight <= 0 || screenWidth <= 0 || screenHeight <= 0) {
            return 1f;
        }
        return Math.max((float) screenWidth / imageWidth, (float) screenHeight / imageHeight);
    }

    /**
     * Centring offset for one axis in {@code cover} mode: half of what is left after scaling. It is
     * always {@code <= 0} (the scaled picture is at least as big as the screen); package-visible
     * and static so a test can pin it without a GL context.
     */
    static float coverOffset(float scaledSize, int screenSize) {
        return (screenSize - scaledSize) / 2f;
    }

    /** Removes the title cover; the game behind becomes visible and playable. */
    public void hideCover() {
        if (coverTable == null) {
            return;
        }
        coverTable.remove();
        coverTable = null;
        coverImage = null;
        coverTagline = null;
        if (coverTexture != null) {
            coverTexture.dispose();
            coverTexture = null;
        }
    }

    /** True while the title cover is on screen. */
    public boolean isCoverVisible() {
        return coverTable != null;
    }

    /**
     * File menu ({@code Esc} in both clients), built from the shared {@link FileMenuText} rows:
     * New/Load/Save/Settings/Quit. Locked rows stay visible but disabled with their reason next to
     * the label; Esc closes the menu again (owner decision D11).
     *
     * @param hasGame whether a game is running ({@link GraphicPresenter#hasActiveGame()}): the boot
     *        menu (false) disables {@code Save} and, on Esc, returns to the title cover
     * @param onDismissed run when the menu is closed with Esc (not when a row acts): the boot flow
     *        uses it to show the cover again (D8, #777)
     */
    public void showFileMenu(boolean hasGame, Runnable onDismissed) {
        if (fileMenuOpen) {
            return;
        }
        fileMenuOpen = true;
        Gdx.app.postRunnable(() -> {
            final Actor blocker = new Actor();
            blocker.setBounds(0, 0, stage.getWidth(), stage.getHeight());
            blocker.addListener(new InputListener() {
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer,
                        int button) {
                    return true;
                }
            });

            // No window title (#802): the menu reads better compact, and the rows already say it
            // all.
            final com.badlogic.gdx.scenes.scene2d.ui.Dialog dialog =
                    new com.badlogic.gdx.scenes.scene2d.ui.Dialog("",
                            skin.get("dialog", Window.WindowStyle.class));

            // close just hides the menu (a row acted); dismiss also notifies the boot flow when the
            // player leaves the menu with Esc and still has no game (D8, #777).
            final Runnable close = () -> {
                fileMenuOpen = false;
                blocker.remove();
                dialog.hide(null);
            };
            final Runnable dismiss = () -> {
                close.run();
                if (onDismissed != null) {
                    onDismissed.run();
                }
            };

            // One row per shared item, in the shared order. Locked rows are non-focusable and
            // keep their label greyed out, with the lock reason coming from the shared wording.
            // Since S4 (#777) New is enabled and opens the 3D New Game dialog; Settings stays
            // disabled with the parity reason until S4b.
            final List<TextButton> focusable = new ArrayList<>();
            final java.util.Map<TextButton, Runnable> actions = new java.util.HashMap<>();
            final java.util.Map<Character, Runnable> hotkeyActions = new java.util.HashMap<>();
            for (FileMenuText.Item item : FileMenuText
                    .items(FileMenuText.MenuState.graphic(hasGame))) {
                // Alt access key in the label, tinted like every other dialog (#803).
                final TextButton button =
                        new TextButton(settingsHotkeyLabel(fileMenuButtonText(item),
                                FileMenuText.mnemonic(item.command())), skin);
                final Runnable action = fileMenuAction(item, close);
                if (item.enabled()) {
                    focusable.add(button);
                    hotkeyActions.put(Character.toLowerCase(FileMenuText.mnemonic(item.command())),
                            action);
                } else {
                    button.setDisabled(true);
                    button.getLabel().setColor(Color.GRAY);
                    // The reason no longer rides in the label (#802); the terminal just greys the
                    // row out, the 3D client can explain it on hover.
                    if (!item.disabledReason().isBlank()) {
                        button.addListener(newTooltip(item.disabledReason()));
                    }
                }
                button.addListener(new ChangeListener() {
                    @Override
                    public void changed(ChangeEvent event, Actor actor) {
                        if (item.enabled()) {
                            action.run();
                        }
                    }
                });
                actions.put(button, action);
                // Text-sized buttons (#802): the single column takes the longest label, which keeps
                // the stack tidy without the old fixed 560 px.
                dialog.getContentTable().add(button).pad(6).row();
            }

            // Keyboard navigation: Tab/arrows move the focus between the enabled rows, Enter/Space
            // activate the focused one and Esc closes the menu. The dialog is modal, so it swallows
            // the rest of the keys while open.
            final Runnable paintFocus = () -> {
                Actor focused = stage.getKeyboardFocus();
                for (TextButton button : focusable) {
                    paintButtonFocus(button, focused == button);
                }
            };
            InputListener navigation = new InputListener() {
                @Override
                public boolean keyDown(InputEvent event, int keycode) {
                    if (keycode == Input.Keys.TAB || keycode == Input.Keys.UP
                            || keycode == Input.Keys.DOWN || keycode == Input.Keys.LEFT
                            || keycode == Input.Keys.RIGHT) {
                        Actor focused = stage.getKeyboardFocus();
                        int index = focusable.indexOf(focused);
                        // Shift+Tab walks the ring backwards, like Shift+Up/Shift+Left.
                        int step = (keycode == Input.Keys.UP || keycode == Input.Keys.LEFT
                                || (keycode == Input.Keys.TAB && isShiftPressed())) ? -1 : 1;
                        int next = Math.floorMod(index + step, focusable.size());
                        stage.setKeyboardFocus(focusable.get(next));
                        paintFocus.run();
                        return true;
                    }
                    // Alt+letter jumps to a row (#803), tinted in the label like the rest.
                    if (isAltPressed() && keycode >= Input.Keys.A && keycode <= Input.Keys.Z) {
                        char letter = (char) ('A' + keycode - Input.Keys.A);
                        Runnable action = hotkeyActions.get(Character.toLowerCase(letter));
                        if (action != null) {
                            action.run();
                            return true;
                        }
                    }
                    if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER
                            || keycode == Input.Keys.SPACE) {
                        Runnable action = actions.get(stage.getKeyboardFocus());
                        if (action != null) {
                            action.run();
                        }
                        return true;
                    }
                    if (keycode == Input.Keys.ESCAPE) {
                        // #803 (owner decision): at the boot menu Esc quits like the Quit row; with
                        // a
                        // game running it just closes the menu (D11).
                        if (hasGame) {
                            dismiss.run();
                        } else {
                            Gdx.app.exit();
                        }
                        return true;
                    }
                    return true;
                }

                @Override
                public boolean keyTyped(InputEvent event, char character) {
                    return true;
                }
            };
            for (TextButton button : focusable) {
                button.addListener(navigation);
            }

            // Compact, title-less menu (#802): only a small top pad and no forced wide/tall box.
            dialog.padTop(16f);
            dialog.pack();
            dialog.setWidth(Math.max(dialog.getWidth(), 320f));
            dialog.setHeight(Math.max(dialog.getHeight(), 260f));
            dialog.setPosition((stage.getWidth() - dialog.getWidth()) / 2,
                    (stage.getHeight() - dialog.getHeight()) / 2);

            stage.addActor(blocker);
            stage.addActor(dialog);
            stage.setKeyboardFocus(focusable.get(0));
            paintFocus.run();
        });
    }

    /**
     * Row text of the File menu: the shared bare label. The lock reason stays on the item and is
     * shown as a hover tooltip while the row is disabled (#802); the 2D terminal shows the exact
     * same words (issue #743).
     */
    static String fileMenuButtonText(FileMenuText.Item item) {
        return FileMenuText.displayLabel(item);
    }

    /** Action of one File menu row; locked rows stay disabled and never run it. */
    private Runnable fileMenuAction(FileMenuText.Item item, Runnable close) {
        switch (item.command()) {
            case NEW:
                return () -> {
                    close.run();
                    view.onNewGame();
                };
            case LOAD:
                return () -> {
                    close.run();
                    view.showLoadDialog();
                };
            case SAVE:
                return () -> {
                    close.run();
                    view.showSaveDialog();
                };
            case SETTINGS:
                return () -> {
                    close.run();
                    view.onSettings();
                };
            case QUIT:
                return () -> Gdx.app.exit();
            default:
                return () -> {
                };
        }
    }

    /**
     * Confirmation before {@code Rebuild world}: the replay replaces the current world, so the
     * player must agree first (issue #740).
     */
    private void confirmRebuildWorld(Runnable onConfirm) {
        showConfirmDialog(EditorText.REBUILD_CONFIRM_TITLE, EditorText.REBUILD_CONFIRM_MESSAGE,
                EditorText.REBUILD_WORLD, onConfirm);
    }

    /**
     * Modal yes/no confirmation, reused by {@code Rebuild world} (#740) and the New Game discard
     * confirmation (S4, #777). Same keyboard navigation as the File menu (Tab/arrows move the
     * focus, Enter/Space activate the focused button, Esc cancels); the dialog swallows the rest of
     * the keys while open. The confirm action only runs when the player agrees.
     */
    public void showConfirmDialog(String title, String messageText, String confirmLabel,
            Runnable onConfirm) {
        showConfirmDialog(title, messageText, confirmLabel, onConfirm, null);
    }

    /**
     * Modal yes/no confirmation with an optional cancel callback (S4b, #778): the Settings dialog
     * uses it to keep the keyboard focus on its own form when the player dismisses the
     * confirmation. Same keyboard navigation as the File menu (Tab/arrows move the focus,
     * Enter/Space activate the focused button, Esc cancels); the dialog swallows the rest of the
     * keys while open. Only one of the two callbacks runs.
     */
    public void showConfirmDialog(String title, String messageText, String confirmLabel,
            Runnable onConfirm, Runnable onCancel) {
        if (confirmDialogOpen) {
            return;
        }
        confirmDialogOpen = true;
        Gdx.app.postRunnable(() -> {
            final Actor blocker = new Actor();
            blocker.setBounds(0, 0, stage.getWidth(), stage.getHeight());
            blocker.addListener(new InputListener() {
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer,
                        int button) {
                    return true;
                }
            });

            final com.badlogic.gdx.scenes.scene2d.ui.Dialog dialog =
                    new com.badlogic.gdx.scenes.scene2d.ui.Dialog(title,
                            skin.get("dialog", Window.WindowStyle.class)) {
                        @Override
                        protected void result(Object object) {
                            confirmDialogOpen = false;
                            blocker.remove();
                            if (Boolean.TRUE.equals(object)) {
                                onConfirm.run();
                            }
                        }
                    };

            Label message = new Label(messageText, skin, "medium");
            message.setWrap(true);
            dialog.getContentTable().pad(10, 48, 24, 48).add(message).width(560f);

            // Every dialog button gets a visible Alt hotkey (#803): the confirm action takes its
            // first letter (avoiding Cancel's C) and Cancel keeps C, tinted in the label.
            final char confirmKey = confirmMnemonic(confirmLabel);
            TextButton confirmButton =
                    new TextButton(settingsHotkeyLabel(confirmLabel, confirmKey), skin);
            TextButton cancelButton = new TextButton(settingsHotkeyLabel("Cancel", 'C'), skin);
            dialog.getButtonTable().defaults().space(24).minWidth(140).minHeight(50);
            dialog.button(confirmButton, Boolean.TRUE);
            dialog.button(cancelButton, Boolean.FALSE);

            // Keyboard navigation: Tab/arrows move the focus between the buttons, Enter/Space
            // activate the focused one and Esc cancels. The dialog is modal, so it swallows the
            // rest of the keys while open.
            final Runnable paintFocus = () -> {
                Actor focused = stage.getKeyboardFocus();
                paintButtonFocus(confirmButton, focused == confirmButton);
                paintButtonFocus(cancelButton, focused == cancelButton);
            };
            InputListener navigation = new InputListener() {
                @Override
                public boolean keyDown(InputEvent event, int keycode) {
                    if (keycode == Input.Keys.TAB || keycode == Input.Keys.RIGHT
                            || keycode == Input.Keys.LEFT) {
                        Actor focused = stage.getKeyboardFocus();
                        stage.setKeyboardFocus(
                                focused == confirmButton ? cancelButton : confirmButton);
                        paintFocus.run();
                        return true;
                    }
                    // Alt+letter reaches either button (#803): the confirm key or C for Cancel.
                    if (isAltPressed() && keycode >= Input.Keys.A && keycode <= Input.Keys.Z) {
                        char letter = (char) ('A' + keycode - Input.Keys.A);
                        if (Character.toLowerCase(letter) == Character.toLowerCase(confirmKey)) {
                            activate(true);
                            return true;
                        }
                        if (Character.toLowerCase(letter) == 'c') {
                            activate(false);
                            return true;
                        }
                    }
                    if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER
                            || keycode == Input.Keys.SPACE) {
                        activate(stage.getKeyboardFocus() == confirmButton);
                        return true;
                    }
                    if (keycode == Input.Keys.ESCAPE) {
                        activate(false);
                        return true;
                    }
                    return true;
                }

                @Override
                public boolean keyTyped(InputEvent event, char character) {
                    return true;
                }

                private void activate(boolean confirmed) {
                    confirmDialogOpen = false;
                    blocker.remove();
                    dialog.hide(null);
                    if (confirmed) {
                        if (onConfirm != null) {
                            onConfirm.run();
                        }
                    } else if (onCancel != null) {
                        onCancel.run();
                    }
                }
            };
            confirmButton.addListener(navigation);
            cancelButton.addListener(navigation);

            // Reserve enough top padding so the title fits inside the title bar (Window sizes
            // the title table to `padTop`).
            dialog.padTop(46);
            dialog.pack();
            dialog.setWidth(Math.max(dialog.getWidth(), 560f));
            dialog.setHeight(Math.max(dialog.getHeight(), 240f));
            dialog.setPosition((stage.getWidth() - dialog.getWidth()) / 2,
                    (stage.getHeight() - dialog.getHeight()) / 2);

            stage.addActor(blocker);
            stage.addActor(dialog);
            stage.setKeyboardFocus(confirmButton);
            paintFocus.run();
        });
    }

    /**
     * New Game dialog (S4, #777; five pages #808): the 3D counterpart of the 2D dialog. It shows
     * the same five catalogue pages as the 2D {@code NewGameDialog} — Terrain, Game, Economy, Build
     * and UI — in the shared order, read from {@link SettingCategory} and the catalogue, nothing
     * hardcoded. The Terrain page keeps its live block (seed, water/mountain sliders, 100 % bar and
     * map preview); the other pages are one scrolling column of catalogue fields bound to the
     * {@link NewGameForm}, exactly the rows the Settings dialog reads.
     *
     * <p>
     * Keyboard (2D parity, S4b #778/#780): <b>Alt+T/G/E/B/U</b> jump to a page (mnemonic tinted
     * like the Settings tabs), <b>Ctrl+Tab</b>/<b>Ctrl+Shift+Tab</b> cycle them, <b>Tab</b>/arrows
     * walk the active page's fields plus Start/Cancel (Shift+Tab reverses),
     * <b>Alt+S</b>/<b>Alt+C</b> start/cancel, Left/Right move a focused slider, Enter/Space
     * activate a focused button and Esc cancels. The form owns the catalogue rules; {@code Start}
     * validates through {@link NewGameForm#toSettings()} and, on failure, shows the catalogue
     * message inline without closing.
     *
     * @param form the prefilled form (seed + catalogue values) owned by the presenter
     * @param onStart receives the validated settings when the player starts
     * @param onCancel run when the dialog is cancelled
     */
    public void showNewGameDialog(NewGameForm form, Consumer<Map<String, String>> onStart,
            Runnable onCancel) {
        if (newGameDialogOpen) {
            return;
        }
        newGameDialogOpen = true;
        Gdx.app.postRunnable(() -> {
            final Actor blocker = new Actor();
            blocker.setBounds(0, 0, stage.getWidth(), stage.getHeight());
            blocker.addListener(new InputListener() {
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer,
                        int button) {
                    return true;
                }
            });

            final com.badlogic.gdx.scenes.scene2d.ui.Dialog dialog =
                    new com.badlogic.gdx.scenes.scene2d.ui.Dialog(NewGameText.TITLE,
                            skin.get("dialog", Window.WindowStyle.class));

            // One scrolling column shared by every page (the Settings dialog pattern): the Terrain
            // block or the catalogue field rows are swapped in place (#808). The terrain previews
            // are disposed when the page is left or the dialog closes.
            final Table pageTable = new Table();
            pageTable.top().left();
            pageTable.padLeft(SETTINGS_LEFT_GUTTER);
            final ScrollPane pageScroll = new ScrollPane(pageTable, skin);
            pageScroll.setFadeScrollBars(false);

            final List<Actor> pageFields = new ArrayList<>();
            final List<Actor> focusable = new ArrayList<>();
            final List<TerrainPreviewView> terrainPreviews = new ArrayList<>();
            final SettingCategory[] active = {SettingCategory.TERRAIN};
            final Map<SettingCategory, TextButton> categoryButtons = new LinkedHashMap<>();
            final Table categoryBar = new Table();

            // Start/Cancel carry their Alt mnemonic in the label (2D parity: Alt+S / Alt+C).
            final TextButton startButton =
                    new TextButton(settingsHotkeyLabel(NewGameText.START, 'S'), skin);
            final TextButton cancelButton =
                    new TextButton(settingsHotkeyLabel(NewGameText.CANCEL, 'C'), skin);
            final List<TextButton> footerButtons = List.of(startButton, cancelButton);

            // Focus highlight (S4b, #780): sliders swap to the cyan-knob style, buttons to the
            // SELECTED_BG background and checkboxes are tinted cyan. Refreshed after navigation.
            final Runnable paintFocus = () -> {
                Actor focused = stage.getKeyboardFocus();
                for (Actor widget : focusable) {
                    if (widget instanceof Slider) {
                        paintSliderFocus((Slider) widget, focused == widget);
                    } else if (widget instanceof CheckBox) {
                        // libGDX 1.14.2: CheckBox extends TextButton, so this branch
                        // must run before the TextButton one, or paintButtonFocus
                        // would hand it a TextButtonStyle, which CheckBox.setStyle
                        // rejects (#808).
                        ((CheckBox) widget).getImage()
                                .setColor(focused == widget ? Color.CYAN : Color.WHITE);
                    } else if (widget instanceof TextButton) {
                        paintButtonFocus((TextButton) widget, focused == widget);
                    }
                }
            };

            final Runnable finish = () -> {
                newGameDialogOpen = false;
                blocker.remove();
                dialog.hide(null);
                disposeTerrainPreviews(terrainPreviews);
            };
            final Runnable start = () -> {
                try {
                    Map<String, String> settings = form.toSettings();
                    finish.run();
                    if (onStart != null) {
                        onStart.accept(settings);
                    }
                } catch (IllegalArgumentException e) {
                    // 2D parity: the catalogue message, titled like the 2D dialog, and the New Game
                    // dialog stays open so the player can fix the field (S4b #780 error channel).
                    showMessage(NewGameText.INVALID_TITLE, e.getMessage());
                }
            };
            final Runnable cancel = () -> {
                finish.run();
                if (onCancel != null) {
                    onCancel.run();
                }
            };

            // Swaps the visible page and refreshes the Tab cycle: the active page's fields plus
            // Start/Cancel (the tab selector stays out of it, like 2D #757). The Terrain page is
            // the
            // live block, the rest are catalogue field rows bound to the form (#808).
            final Consumer<SettingCategory> showCategory = category -> {
                active[0] = category;
                disposeTerrainPreviews(terrainPreviews);
                pageTable.clear();
                pageFields.clear();
                if (category == SettingCategory.TERRAIN) {
                    fillNewGameTerrain(pageTable, pageFields, form, terrainPreviews);
                } else {
                    fillNewGameCategory(category, pageTable, pageFields, form);
                }
                for (Map.Entry<SettingCategory, TextButton> entry : categoryButtons.entrySet()) {
                    entry.getValue().setChecked(entry.getKey() == category);
                }
                focusable.clear();
                focusable.addAll(pageFields);
                focusable.addAll(footerButtons);
                pageScroll.setScrollY(0);
                // Always pin the focus: with a null focus Stage sends keyDown to the root and this
                // dialog never hears it, so Tab/Enter/Esc would be dead (#778).
                focusAndSelect(pageFields.isEmpty() ? focusable.get(0) : pageFields.get(0));
                paintFocus.run();
            };
            for (SettingCategory category : SETTINGS_CATEGORIES) {
                final TextButton tab = new TextButton(
                        settingsHotkeyLabel(category.displayName(), settingsTabLetter(category)),
                        skin, "toggle");
                // ClickListener (not ChangeListener): showCategory re-syncs every tab's checked
                // state, so reacting to ChangeEvent would ping-pong the programmatic setChecked
                // back into this listener and recurse until StackOverflowError (#778).
                tab.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        if (active[0] == category) {
                            tab.setChecked(true);
                            return;
                        }
                        showCategory.accept(category);
                    }
                });
                categoryButtons.put(category, tab);
                categoryBar.add(tab).pad(4);
            }

            startButton.addListener(fireAction(start));
            cancelButton.addListener(fireAction(cancel));

            // Slider key auto-repeat (#804): holding Left/Right keeps the focused slider moving
            // after an initial delay, reusing the game input's timings for a consistent feel.
            // libGDX sends discrete keyDown events, so the repeat is driven by the frame clock;
            // the 2D terminal relies on the OS/terminal key repeat instead.
            final int[] repeatDirection = {0};
            final float[] repeatElapsed = {0f};
            final boolean[] repeatBurst = {false};
            dialog.addAction(new com.badlogic.gdx.scenes.scene2d.Action() {
                @Override
                public boolean act(float delta) {
                    if (repeatDirection[0] == 0) {
                        return false;
                    }
                    Actor focused = stage.getKeyboardFocus();
                    if (!(focused instanceof Slider)) {
                        repeatDirection[0] = 0;
                        return false;
                    }
                    float delay = Gdx3DInputHandler.INITIAL_REPEAT_DELAY_MS / 1000f;
                    float interval = Gdx3DInputHandler.REPEAT_INTERVAL_MS / 1000f;
                    repeatElapsed[0] += delta;
                    if (!repeatBurst[0]) {
                        if (repeatElapsed[0] < delay) {
                            return false;
                        }
                        repeatBurst[0] = true;
                        repeatElapsed[0] -= delay;
                    }
                    while (repeatElapsed[0] >= interval) {
                        repeatElapsed[0] -= interval;
                        stepSlider((Slider) focused, repeatDirection[0]);
                    }
                    return false;
                }
            });

            // Keyboard: Ctrl+Tab cycles, Alt+letter reaches a page or Start/Cancel, Tab/arrows walk
            // the active page and the two buttons, Enter/Space activate a focused button, Left/
            // Right move a focused slider (or the caret in a text field) and Esc cancels. Text
            // fields keep the arrows for the caret and the space for typing.
            dialog.addListener(new InputListener() {
                @Override
                public boolean keyDown(InputEvent event, int keycode) {
                    Actor focused = stage.getKeyboardFocus();
                    if (keycode == Input.Keys.TAB && isCtrlPressed()) {
                        // Ctrl+Tab / Ctrl+Shift+Tab cycles the pages and never triggers the normal
                        // Tab navigation.
                        showCategory.accept(settingsCycleTab(active[0], isShiftPressed()));
                        return true;
                    }
                    if (isAltPressed() && keycode >= Input.Keys.A && keycode <= Input.Keys.Z) {
                        char letter = (char) ('A' + keycode - Input.Keys.A);
                        Optional<SettingCategory> tab = settingsTabHotkey(letter);
                        if (tab.isPresent()) {
                            showCategory.accept(tab.get());
                            return true;
                        }
                        char lower = Character.toLowerCase(letter);
                        if (lower == 's') {
                            start.run();
                            return true;
                        }
                        if (lower == 'c') {
                            cancel.run();
                            return true;
                        }
                    }
                    if ((keycode == Input.Keys.LEFT || keycode == Input.Keys.RIGHT)
                            && focused instanceof Slider) {
                        int direction = keycode == Input.Keys.LEFT ? -1 : 1;
                        stepSlider((Slider) focused, direction);
                        // Arm the auto-repeat: one step now, then delay + burst while held.
                        repeatDirection[0] = direction;
                        repeatElapsed[0] = 0f;
                        repeatBurst[0] = false;
                        return true;
                    }
                    if (keycode == Input.Keys.TAB || keycode == Input.Keys.UP
                            || keycode == Input.Keys.DOWN
                            || ((keycode == Input.Keys.LEFT || keycode == Input.Keys.RIGHT)
                                    && !(focused instanceof TextField))) {
                        // Shift+Tab walks the ring backwards, like Shift+Up/Shift+Left.
                        int step = (keycode == Input.Keys.UP || keycode == Input.Keys.LEFT
                                || (keycode == Input.Keys.TAB && isShiftPressed())) ? -1 : 1;
                        navigateSettingsFocus(step, focusable, pageScroll, pageTable);
                        paintFocus.run();
                        return true;
                    }
                    if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER
                            || keycode == Input.Keys.SPACE) {
                        // libGDX 1.14.2: CheckBox extends TextButton, so this branch runs first.
                        if (focused instanceof CheckBox) {
                            CheckBox checkBox = (CheckBox) focused;
                            checkBox.setChecked(!checkBox.isChecked());
                            return true;
                        }
                        if (focused == startButton) {
                            start.run();
                            return true;
                        }
                        if (focused == cancelButton) {
                            cancel.run();
                            return true;
                        }
                        // In the seed field a space is a character; let it type.
                        return false;
                    }
                    if (keycode == Input.Keys.ESCAPE) {
                        cancel.run();
                        return true;
                    }
                    return false;
                }

                @Override
                public boolean keyUp(InputEvent event, int keycode) {
                    // Stop the slider repeat when the arrow is released (#804).
                    if ((keycode == Input.Keys.LEFT || keycode == Input.Keys.RIGHT)
                            && repeatDirection[0] != 0) {
                        repeatDirection[0] = 0;
                    }
                    return false;
                }

                @Override
                public boolean keyTyped(InputEvent event, char character) {
                    // Never swallow: the seed field and the catalogue fields must receive theirs.
                    return false;
                }
            });

            Table content = dialog.getContentTable();
            content.pad(24, 40, 10, 40);
            content.defaults().space(10);
            content.add(categoryBar).left().row();
            content.add(pageScroll).width(660f).height(340f).row();

            Table buttons = dialog.getButtonTable();
            buttons.defaults().space(24).minWidth(140).minHeight(50);
            // Plain table cells, not Dialog.button(...): that path auto-hides on click, which would
            // close the dialog even when Start must show a validation message and stay open.
            for (TextButton button : footerButtons) {
                buttons.add(button);
            }

            dialog.padTop(46);
            dialog.pack();
            dialog.setWidth(Math.max(dialog.getWidth(), 760f));
            dialog.setHeight(Math.max(dialog.getHeight(), 620f));
            dialog.setPosition((stage.getWidth() - dialog.getWidth()) / 2,
                    (stage.getHeight() - dialog.getHeight()) / 2);

            stage.addActor(blocker);
            stage.addActor(dialog);
            // The Terrain page opens first with its seed focused and selected, so the first
            // keystroke replaces the drawn seed, like the 2D dialog's select-on-focus (#757).
            showCategory.accept(SettingCategory.TERRAIN);
        });
    }

    /**
     * Fills the New Game content with the live Terrain block (#808): seed, water/mountain sliders,
     * the three-colour 100 % bar and the map preview, all wired to the {@link NewGameForm} so Start
     * collects them like the 2D dialog. The widgets are rebuilt when the page is shown; the form
     * (kept in sync live) owns the values, so switching pages never loses an edit.
     */
    private void fillNewGameTerrain(Table fieldsTable, List<Actor> categoryFields, NewGameForm form,
            List<TerrainPreviewView> previews) {
        fieldsTable.clear();
        categoryFields.clear();

        final TextField seedField = new TextField(form.value(NewGameForm.SEED_KEY), skin);
        // The dialog owns Tab/arrow navigation, so the field must not consume Tab itself.
        seedField.setFocusTraversal(false);
        // LWJGL3 synthesises keyTyped('\t') on Tab and hasGlyph lies for the missing glyph: a plain
        // field would type the "?" placeholder, so only accept printable characters.
        seedField.setTextFieldFilter((f, c) -> c >= 32);

        final Slider waterSlider =
                new Slider(0, SettingsCatalog.MAX_TERRAIN_PERCENT, 1, false, skin);
        final Slider mountainSlider =
                new Slider(0, SettingsCatalog.MAX_TERRAIN_PERCENT, 1, false, skin);
        waterSlider.setValue(form.waterPercent());
        mountainSlider.setValue(form.mountainPercent());
        final Label waterValue = new Label(form.waterPercent() + " %", skin, "small");
        final Label mountainValue = new Label(form.mountainPercent() + " %", skin, "small");

        // Live terrain block: the three-colour 100 % bar and the map preview refresh on every
        // slider move and on every valid seed edit (2D parity), reusing the Settings helpers.
        final Table bar = terrainBarTable(form.waterPercent(), form.mountainPercent());
        final TerrainPreviewView preview = new TerrainPreviewView();
        preview.setValues(form.seed(), form.waterPercent(), form.mountainPercent());
        previews.add(preview);
        final Runnable refreshTerrain = () -> {
            updateTerrainBar(bar, form.waterPercent(), form.mountainPercent());
            preview.setValues(form.seed(), form.waterPercent(), form.mountainPercent());
        };

        seedField.setTextFieldListener((field, c) -> {
            int previous = form.seed();
            form.setSeed(field.getText());
            // Keep the last valid seed for the preview when the text does not parse (form ignores
            // the malformed value); only repaint when the seed actually moved.
            if (form.seed() != previous) {
                refreshTerrain.run();
            }
        });
        waterSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                form.setWaterPercent((int) waterSlider.getValue());
                waterValue.setText(form.waterPercent() + " %");
                // The cross-field rule may have clamped the mountain side: refresh it too.
                mountainSlider.setValue(form.mountainPercent());
                mountainValue.setText(form.mountainPercent() + " %");
                refreshTerrain.run();
            }
        });
        mountainSlider.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                form.setMountainPercent((int) mountainSlider.getValue());
                mountainValue.setText(form.mountainPercent() + " %");
                waterSlider.setValue(form.waterPercent());
                waterValue.setText(form.waterPercent() + " %");
                refreshTerrain.run();
            }
        });

        final float labelWidth = 90f;
        final float valueWidth = 80f;

        Table block = new Table();
        block.top().left();
        block.defaults().space(6);

        Table seedRow = new Table();
        seedRow.left();
        seedRow.add(new Label(NewGameText.SEED_LABEL, skin, "small")).width(labelWidth).left();
        seedRow.add(seedField).width(280f).height(38f).left();
        block.add(seedRow).left().row();

        block.add(terrainSliderRow(NewGameText.WATER_LABEL, waterSlider, waterValue, labelWidth,
                valueWidth)).left().row();
        block.add(terrainSliderRow(NewGameText.MOUNTAIN_LABEL, mountainSlider, mountainValue,
                labelWidth, valueWidth)).left().row();

        // Bar under the sliders, then a gap and the map: same order as the 2D terrain block.
        block.add(bar).left().padLeft(labelWidth).row();
        block.add().height(8f).row();
        block.add(preview).left().padLeft(labelWidth).row();

        fieldsTable.add(block).growX().left().top();

        categoryFields.add(seedField);
        categoryFields.add(waterSlider);
        categoryFields.add(mountainSlider);
    }

    /**
     * Fills the New Game content with one row per catalogue field of {@code category}, in catalogue
     * order and bound to the {@link NewGameForm} (the 3D counterpart of the 2D
     * {@code NewGameDialog.buildSection}, #808). No state glyphs: New Game edits every field, so
     * the Settings page's apply-state column would only add noise.
     */
    private void fillNewGameCategory(SettingCategory category, Table fieldsTable,
            List<Actor> categoryFields, NewGameForm form) {
        fieldsTable.clear();
        categoryFields.clear();
        for (SettingDefinition definition : categoryDefinitions(category)) {
            fieldsTable.add(newGameFieldRow(definition, form, categoryFields)).growX().left()
                    .padBottom(6).row();
        }
    }

    /**
     * One New Game field row: label with its unit, and a checkbox (BOOLEAN) or a text field bound
     * to the form. Editable, so it always enters the Tab cycle.
     */
    private Table newGameFieldRow(SettingDefinition definition, NewGameForm form,
            List<Actor> categoryFields) {
        Table row = new Table();
        row.left();
        row.defaults().space(8);
        row.add(new Label(settingsFieldLabel(definition), skin, "default")).width(320f).left();
        String value = form.value(definition.key());
        if (definition.type() == SettingValueType.BOOLEAN) {
            CheckBox checkBox = new CheckBox("", skin);
            checkBox.setChecked(Boolean.parseBoolean(value));
            checkBox.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    form.setValue(definition.key(), checkBox.isChecked() ? "true" : "false");
                }
            });
            row.add(checkBox).width(240f).height(34f).left();
            categoryFields.add(checkBox);
        } else {
            TextField field = new TextField(value, skin);
            field.setFocusTraversal(false);
            field.setTextFieldFilter((f, c) -> c >= 32);
            // Keep the working form in sync so switching pages never loses an edit; Start
            // validates.
            field.setTextFieldListener((f, c) -> form.setValue(definition.key(), f.getText()));
            row.add(field).width(240f).height(34f);
            categoryFields.add(field);
        }
        return row;
    }

    /** Apply-state glyph shared with the 2D Settings dialog (#764). */
    static final String SETTINGS_GLYPH = "●";

    /** Form sections of the Settings dialog, in the shared order. */
    private static final List<SettingCategory> SETTINGS_CATEGORIES =
            List.of(SettingCategory.TERRAIN, SettingCategory.WORLD, SettingCategory.ECONOMY,
                    SettingCategory.CONSTRUCTION, SettingCategory.UI);

    /** Ids of the five Settings footer actions, single-sourced with their labels and mnemonics. */
    static final String APPLY_ID = "apply";
    static final String RESTORE_ID = "restore";
    static final String NEW_GAME_ID = "newGame";
    static final String SAVE_DEFAULTS_ID = "saveDefaults";
    static final String CLOSE_ID = "close";

    /** One footer action of the 3D Settings dialog: its id, label and Alt mnemonic (2D parity). */
    record SettingsAction(String id, String label, char mnemonic) {
    }

    /** The five actions, in display order (`Alt+A/R/N/S/C`), mirroring 2D (#780). */
    static final List<SettingsAction> SETTINGS_ACTIONS =
            List.of(new SettingsAction(APPLY_ID, SettingsText.APPLY, 'A'),
                    new SettingsAction(RESTORE_ID, SettingsText.RESTORE, 'R'),
                    new SettingsAction(NEW_GAME_ID, SettingsText.NEW_GAME, 'N'),
                    new SettingsAction(SAVE_DEFAULTS_ID, SettingsText.SAVE_DEFAULTS, 'S'),
                    new SettingsAction(CLOSE_ID, SettingsText.CLOSE, 'C'));

    /** Letter of the Alt shortcut of a tab: the first letter of its name ({@code T/G/E/B/U}). */
    static char settingsTabLetter(SettingCategory category) {
        return Character.toUpperCase(category.displayName().charAt(0));
    }

    /**
     * Tab selected by an Alt access key: its letter ({@code T/G/E/B/U}, case-insensitive), empty
     * for any other key. Mirror of the 2D {@code NewGameDialog.tabHotkey} (#780).
     */
    static Optional<SettingCategory> settingsTabHotkey(char key) {
        char letter = Character.toLowerCase(key);
        for (SettingCategory category : SETTINGS_CATEGORIES) {
            if (letter == Character.toLowerCase(settingsTabLetter(category))) {
                return Optional.of(category);
            }
        }
        return Optional.empty();
    }

    /**
     * Tab that follows (or precedes, when {@code reverse}) {@code active} in the tab order,
     * wrapping around. Pure maths for the Ctrl+Tab / Ctrl+Shift+Tab cycle (#780).
     */
    static SettingCategory settingsCycleTab(SettingCategory active, boolean reverse) {
        int index = active == null ? -1 : SETTINGS_CATEGORIES.indexOf(active);
        if (index < 0) {
            return SETTINGS_CATEGORIES.get(0);
        }
        int step = reverse ? -1 : 1;
        return SETTINGS_CATEGORIES.get(Math.floorMod(index + step, SETTINGS_CATEGORIES.size()));
    }

    /**
     * Label with its Alt mnemonic highlighted, e.g. {@code "[GREEN]A[]pply"} (the 3D counterpart of
     * the 2D {@code mnemonicRenderer}). The first occurrence of {@code mnemonic} (case-insensitive)
     * is painted green; a label without the letter is returned unchanged.
     */
    static String settingsHotkeyLabel(String label, char mnemonic) {
        int index = -1;
        for (int i = 0; i < label.length(); i++) {
            if (Character.toLowerCase(label.charAt(i)) == Character.toLowerCase(mnemonic)) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            return label;
        }
        return label.substring(0, index) + HOTKEY_MARKUP + label.charAt(index) + MARKUP_RESET
                + label.substring(index + 1);
    }

    /** Sample grid of the Terrain page preview: covers the calibration window, square (#778). */
    static final int TERRAIN_PREVIEW_CELLS = 88;
    /** Render scale of one preview cell, in pixels (2x keeps the 1:1 world cells readable). */
    static final int TERRAIN_PREVIEW_SCALE = 2;
    /** Width of the three-colour 100 % bar, aligned under the terrain sliders (#778). */
    static final int TERRAIN_BAR_WIDTH = 280;

    /**
     * Width of the state-glyph column, shared by the catalogue field rows and the Terrain page so
     * every glyph lines up (#780).
     */
    static final float GLYPH_COLUMN_WIDTH = 24f;

    /**
     * Left gutter of every Settings page, equivalent to one 24px glyph column, so the 3D form keeps
     * the 2D {@code NewGameDialog.LEFT_MARGIN_COLUMNS = 1} indent (#780).
     */
    static final float SETTINGS_LEFT_GUTTER = 24f;

    /**
     * Width of the Terrain page seed box: roughly a third of the slider bar, matching the 2D
     * 10-cell seed box instead of the old over-wide {@link #TERRAIN_BAR_WIDTH} (#780).
     */
    static final int TERRAIN_SEED_WIDTH = 140;

    /** Port the 3D Settings dialog uses to reach the running game; the presenter implements it. */
    public interface SettingsPort {
        /** Applies the editable values and reports the rejections (S3a). */
        SettingsApplyResult apply(Map<String, String> values);

        /** Writes the values as {@code letrain.cfg} defaults; {@code null} on success. */
        String saveDefaults(Map<String, String> values);
    }

    /** Form sections the 3D dialog offers, in display order (shared with 2D). */
    static List<SettingCategory> settingsCategories() {
        return SETTINGS_CATEGORIES;
    }

    /**
     * Catalogue fields of one page, in catalogue order: the shared source of the Settings and New
     * Game page columns (#808). The Terrain page does not use it in either dialog: its live block
     * (seed, sliders, bar, map) is hand-built for the two clients.
     */
    static List<SettingDefinition> categoryDefinitions(SettingCategory category) {
        List<SettingDefinition> definitions = new ArrayList<>();
        for (SettingDefinition definition : SettingsCatalog.definitions()) {
            if (definition.category() == category) {
                definitions.add(definition);
            }
        }
        return definitions;
    }

    /** Field state (scenario lock wins over the catalogue state) both clients show (#764). */
    static SettingFieldState settingsFieldState(SettingDefinition definition,
            Set<String> scenarioKeys) {
        return SettingsCatalog.fieldState(definition, scenarioKeys);
    }

    /** Catalogue label with its unit, e.g. {@code Water (%)}. */
    static String settingsFieldLabel(SettingDefinition definition) {
        String unit = definition.unit();
        return unit == null || unit.isBlank() ? definition.displayName()
                : definition.displayName() + " (" + unit + ")";
    }

    /** Colour of the state glyph, mirroring the 2D {@code SettingsDialog.FieldState} palette. */
    static Color settingsGlyphColor(SettingFieldState state) {
        switch (state) {
            case REAPPLY:
                return Color.YELLOW;
            case NEW_GAME:
                return new Color(1f, 0.35f, 0.35f, 1f);
            case SCENARIO:
                return Color.GRAY;
            default:
                return Color.GREEN;
        }
    }

    /** Markup names of the legend dots, registered from the row palette (#780). */
    static final String LEGEND_LIVE_COLOR = "SETTINGS_LIVE";
    static final String LEGEND_REAPPLY_COLOR = "SETTINGS_REAPPLY";
    static final String LEGEND_NEW_GAME_COLOR = "SETTINGS_NEW_GAME";

    static {
        // libGDX's built-in markup "RED" is pure Color.RED (255,0,0), which reads differently from
        // the row glyphs (1, 0.35, 0.35) on the dialog panel: register the exact row palette
        // (#780).
        Colors.put(LEGEND_LIVE_COLOR, settingsGlyphColor(SettingFieldState.LIVE));
        Colors.put(LEGEND_REAPPLY_COLOR, settingsGlyphColor(SettingFieldState.REAPPLY));
        Colors.put(LEGEND_NEW_GAME_COLOR, settingsGlyphColor(SettingFieldState.NEW_GAME));
    }

    /** Colour legend under the form; the scenario lock has no colour of its own (#767). */
    static String settingsLegendMarkup() {
        return "[" + LEGEND_LIVE_COLOR + "]" + SETTINGS_GLYPH + "[] " + SettingsText.LEGEND_LIVE
                + "   [" + LEGEND_REAPPLY_COLOR + "]" + SETTINGS_GLYPH + "[] "
                + SettingsText.LEGEND_REAPPLY + "   [" + LEGEND_NEW_GAME_COLOR + "]"
                + SETTINGS_GLYPH + "[] " + SettingsText.LEGEND_NEW_GAME;
    }

    /**
     * Segment widths {@code [water, field, mountain]} of the 100 % stacked bar (draw order): the
     * largest-remainder algorithm is copied verbatim from the 2D {@code NewGameDialog} so both
     * clients split the bar identically (#778). Every segment totals exactly {@code width} cells.
     */
    static int[] stackedBarWidths(int waterPercent, int mountainPercent, int width) {
        int[] parts =
                {waterPercent, Math.max(0, 100 - waterPercent - mountainPercent), mountainPercent};
        int[] cells = new int[3];
        int[] remainders = new int[3];
        int used = 0;
        for (int i = 0; i < 3; i++) {
            cells[i] = parts[i] * width / 100;
            remainders[i] = parts[i] * width % 100;
            used += cells[i];
        }
        while (used < width) {
            int best = 0;
            for (int i = 1; i < 3; i++) {
                if (remainders[i] > remainders[best]) {
                    best = i;
                }
            }
            cells[best]++;
            remainders[best] = -1;
            used++;
        }
        return cells;
    }

    /**
     * Day-tone colour of a terrain segment for the Terrain page bar and preview: the world 3D
     * palette at ratio 0 (day). Water and mountain match the 2D tones; the field green is the 3D
     * board green (#778).
     */
    static Color terrainDayColor(VisualPalette.Token token) {
        Color color = new Color();
        Color.rgb888ToColor(color, new VisualPalette().color(token, 0.0));
        // rgb888ToColor leaves the alpha of the (fresh) Color at 0, so without this the bar and the
        // preview pixmap come out fully transparent (#780).
        color.a = 1f;
        return color;
    }

    /** Values of the editable fields (LIVE/REAPPLY, scenario lock included) from a form map. */
    static Map<String, String> editableValues(Map<String, String> form, Set<String> scenarioKeys) {
        Map<String, String> values = new LinkedHashMap<>();
        for (SettingDefinition definition : SettingsCatalog.definitions()) {
            if (form.containsKey(definition.key())
                    && SettingsCatalog.fieldState(definition, scenarioKeys).editable()) {
                values.put(definition.key(), form.get(definition.key()));
            }
        }
        return values;
    }

    /**
     * In-game Settings dialog (S4b, #778): the 3D counterpart of the 2D {@code SettingsDialog}. A
     * category selector swaps the visible page; every catalogue field shows the shared apply-state
     * glyph and is a text field while LIVE/REAPPLY (read-only label otherwise, never editable for
     * NEW_GAME/scenario). The five actions keep the 2D semantics: Apply collects the editable
     * values (confirming first when a REAPPLY field changed) and lists the rejections without
     * closing; Restore resets to the baseline; New game closes and hands the whole form over; Save
     * as defaults confirms and writes {@code letrain.cfg}; Close/Esc closes.
     *
     * @param currentSettings the running game's effective values (Restore baseline)
     * @param scenarioKeys keys fixed by the scenario's {@code configuration { }} (locked)
     * @param port applies values and writes defaults
     * @param onNewGame receives the whole form when the player chooses New game
     */
    public void showSettingsDialog(Map<String, String> currentSettings, Set<String> scenarioKeys,
            SettingsPort port, Consumer<Map<String, String>> onNewGame) {
        if (settingsDialogOpen) {
            return;
        }
        settingsDialogOpen = true;
        Gdx.app.postRunnable(() -> {
            final Actor blocker = new Actor();
            blocker.setBounds(0, 0, stage.getWidth(), stage.getHeight());
            blocker.addListener(new InputListener() {
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer,
                        int button) {
                    return true;
                }
            });

            final Map<String, String> baseline = SettingsCatalog.overDefaults(currentSettings);
            final Map<String, String> form = new LinkedHashMap<>(baseline);
            final List<Actor> categoryFields = new ArrayList<>();
            final List<Actor> focusable = new ArrayList<>();
            final SettingCategory[] active = {SettingCategory.TERRAIN};
            // Terrain page preview views, disposed when the page is swapped or the dialog closes
            // (never touched on the other pages, which leave the list empty).
            final List<TerrainPreviewView> terrainPreviews = new ArrayList<>();

            final com.badlogic.gdx.scenes.scene2d.ui.Dialog dialog =
                    new com.badlogic.gdx.scenes.scene2d.ui.Dialog(SettingsText.TITLE,
                            skin.get("dialog", Window.WindowStyle.class));

            final Label feedback = new Label("", skin, "small");
            final Table fieldsTable = new Table();
            fieldsTable.top().left();
            fieldsTable.padLeft(SETTINGS_LEFT_GUTTER);
            final ScrollPane fieldsScroll = new ScrollPane(fieldsTable, skin);
            fieldsScroll.setFadeScrollBars(false);
            final Map<SettingCategory, TextButton> categoryButtons = new LinkedHashMap<>();
            final Table categoryBar = new Table();

            // Footer buttons carry their Alt mnemonic in the label, like the 2D mnemonicRenderer.
            final Map<String, TextButton> actionButtons = new LinkedHashMap<>();
            for (SettingsAction action : SETTINGS_ACTIONS) {
                actionButtons.put(action.id(), new TextButton(
                        settingsHotkeyLabel(action.label(), action.mnemonic()), skin));
            }
            final List<TextButton> footerButtons = new ArrayList<>(actionButtons.values());
            // The footer buttons and the boolean checkboxes have no caret of their own, so a
            // focused button swaps to the SELECTED_BG background style and a focused checkbox is
            // tinted cyan. Refreshed after navigating, after a page swap and when a confirmation
            // hands the focus back (#778, #780).
            final Runnable paintFooterFocus = () -> {
                Actor focused = stage.getKeyboardFocus();
                for (TextButton button : footerButtons) {
                    paintButtonFocus(button, focused == button);
                }
                for (Actor widget : focusable) {
                    if (widget instanceof CheckBox) {
                        // The tint must go on the Image child: a Table's own colour does not
                        // cascade to its children.
                        ((CheckBox) widget).getImage()
                                .setColor(focused == widget ? Color.CYAN : Color.WHITE);
                    }
                }
            };

            final Runnable close = () -> {
                settingsDialogOpen = false;
                blocker.remove();
                dialog.hide(null);
                disposeTerrainPreviews(terrainPreviews);
            };

            // Swaps the visible page and refreshes the Tab cycle: the fields of the active page
            // plus the five footer buttons (the category selector stays out of it, like 2D #757).
            // The Terrain page is the 2D read-only block (map, sliders, bar), not a field list.
            final Consumer<SettingCategory> showCategory = category -> {
                active[0] = category;
                disposeTerrainPreviews(terrainPreviews);
                if (category == SettingCategory.TERRAIN) {
                    fillSettingsTerrain(fieldsTable, categoryFields, form, terrainPreviews,
                            scenarioKeys);
                } else {
                    fillSettingsCategory(category, fieldsTable, categoryFields, form, scenarioKeys);
                }
                for (Map.Entry<SettingCategory, TextButton> entry : categoryButtons.entrySet()) {
                    entry.getValue().setChecked(entry.getKey() == category);
                }
                focusable.clear();
                focusable.addAll(categoryFields);
                focusable.addAll(footerButtons);
                fieldsScroll.setScrollY(0);
                // Always pin the focus: with a null focus Stage sends keyDown to the root and this
                // dialog never hears it, so on a field-less page (Terrain) Tab/Enter/Esc would be
                // dead (#778). The footer always contributes five buttons.
                focusAndSelect(categoryFields.isEmpty() ? focusable.get(0) : categoryFields.get(0));
                paintFooterFocus.run();
            };
            for (SettingCategory category : SETTINGS_CATEGORIES) {
                final TextButton tab = new TextButton(
                        settingsHotkeyLabel(category.displayName(), settingsTabLetter(category)),
                        skin, "toggle");
                // ClickListener (not ChangeListener): showCategory re-syncs every tab's checked
                // state, so reacting to ChangeEvent would ping-pong the programmatic setChecked
                // back into this listener and recurse until StackOverflowError (#778). The
                // selector is mouse-only (tabs stay out of the Tab cycle), so clicks are enough.
                tab.addListener(new ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        if (active[0] == category) {
                            // Re-clicking the active tab must not refill/reset scroll: keep it
                            // checked without re-triggering showCategory.
                            tab.setChecked(true);
                            return;
                        }
                        showCategory.accept(category);
                    }
                });
                categoryButtons.put(category, tab);
                categoryBar.add(tab).pad(4);
            }

            final Runnable applyAction = () -> {
                Map<String, String> values = editableValues(form, scenarioKeys);
                List<String> reapply = SettingsCatalog.changedReapplyKeys(baseline, values);
                if (!reapply.isEmpty()) {
                    Actor restoreFocus = stage.getKeyboardFocus();
                    showConfirmDialog(SettingsText.REAPPLY_TITLE,
                            SettingsText.reapplyMessage(reapply), SettingsText.APPLY, () -> {
                                applyValues(port, baseline, feedback, values);
                                focusAndSelect(restoreFocus);
                                revealInScroll(restoreFocus, fieldsScroll, fieldsTable);
                                paintFooterFocus.run();
                            }, () -> {
                                focusAndSelect(restoreFocus);
                                revealInScroll(restoreFocus, fieldsScroll, fieldsTable);
                                paintFooterFocus.run();
                            });
                } else {
                    applyValues(port, baseline, feedback, values);
                }
            };
            final Runnable restoreAction = () -> {
                form.clear();
                form.putAll(baseline);
                showCategory.accept(active[0]);
                feedback.setText(SettingsText.RESTORED);
            };
            final Runnable newGameAction = () -> {
                Map<String, String> values = new LinkedHashMap<>(form);
                close.run();
                if (onNewGame != null) {
                    onNewGame.accept(values);
                }
            };
            final Runnable saveDefaultsAction = () -> {
                Actor restoreFocus = stage.getKeyboardFocus();
                showConfirmDialog(SettingsText.SAVE_DEFAULTS_TITLE,
                        SettingsText.SAVE_DEFAULTS_CONFIRM, SettingsText.SAVE_DEFAULTS, () -> {
                            String error = port.saveDefaults(new LinkedHashMap<>(form));
                            feedback.setText(error == null ? SettingsText.SAVED_DEFAULTS
                                    : SettingsText.SAVE_DEFAULTS_FAILED + error);
                            focusAndSelect(restoreFocus);
                            revealInScroll(restoreFocus, fieldsScroll, fieldsTable);
                            paintFooterFocus.run();
                        }, () -> {
                            focusAndSelect(restoreFocus);
                            revealInScroll(restoreFocus, fieldsScroll, fieldsTable);
                            paintFooterFocus.run();
                        });
            };

            Map<String, Runnable> actionRunnables = new LinkedHashMap<>();
            actionRunnables.put(APPLY_ID, applyAction);
            actionRunnables.put(RESTORE_ID, restoreAction);
            actionRunnables.put(NEW_GAME_ID, newGameAction);
            actionRunnables.put(SAVE_DEFAULTS_ID, saveDefaultsAction);
            actionRunnables.put(CLOSE_ID, close);
            for (SettingsAction action : SETTINGS_ACTIONS) {
                actionButtons.get(action.id())
                        .addListener(fireAction(actionRunnables.get(action.id())));
            }
            // Alt+letter access keys: the tabs (< T/G/E/B/U >) are checked first, then the five
            // footer actions (A/R/N/S/C), like the 2D SettingsDialog.
            final Map<Character, Runnable> hotkeyActions = new LinkedHashMap<>();
            for (SettingsAction action : SETTINGS_ACTIONS) {
                hotkeyActions.put(Character.toLowerCase(action.mnemonic()),
                        actionRunnables.get(action.id()));
            }

            Table content = dialog.getContentTable();
            content.pad(24, 40, 10, 40);
            content.defaults().space(10);
            content.add(categoryBar).left().row();
            content.add(fieldsScroll).width(660f).height(340f).row();
            content.add(new Label(settingsLegendMarkup(), skin, "small")).left().row();
            content.add(feedback).left().row();

            Table buttons = dialog.getButtonTable();
            buttons.defaults().space(14).minWidth(150).minHeight(48);
            for (TextButton button : footerButtons) {
                buttons.add(button);
            }

            // Keyboard: Tab/arrows walk the active page's fields and the five buttons, Enter/Space
            // activate a focused button, Esc closes. Text fields keep the left/right arrows for the
            // caret and the space for typing. Alt+letter reaches a tab or action and Ctrl+Tab
            // cycles the tabs, both like the 2D SettingsDialog.
            dialog.addListener(new InputListener() {
                @Override
                public boolean keyDown(InputEvent event, int keycode) {
                    Actor focused = stage.getKeyboardFocus();
                    if (keycode == Input.Keys.TAB && isCtrlPressed()) {
                        // Ctrl+Tab / Ctrl+Shift+Tab cycles the categories and never triggers the
                        // normal Tab navigation.
                        boolean reverse = isShiftPressed();
                        showCategory.accept(settingsCycleTab(active[0], reverse));
                        return true;
                    }
                    if (isAltPressed() && keycode >= Input.Keys.A && keycode <= Input.Keys.Z) {
                        char letter = (char) ('A' + keycode - Input.Keys.A);
                        Optional<SettingCategory> tab = settingsTabHotkey(letter);
                        if (tab.isPresent()) {
                            showCategory.accept(tab.get());
                            return true;
                        }
                        Runnable action = hotkeyActions.get(Character.toLowerCase(letter));
                        if (action != null) {
                            action.run();
                            return true;
                        }
                    }
                    if (keycode == Input.Keys.TAB || keycode == Input.Keys.UP
                            || keycode == Input.Keys.DOWN) {
                        int step = (keycode == Input.Keys.UP
                                || (keycode == Input.Keys.TAB && isShiftPressed())) ? -1 : 1;
                        navigateSettingsFocus(step, focusable, fieldsScroll, fieldsTable);
                        paintFooterFocus.run();
                        return true;
                    }
                    if ((keycode == Input.Keys.LEFT || keycode == Input.Keys.RIGHT)
                            && !(focused instanceof TextField)) {
                        navigateSettingsFocus(keycode == Input.Keys.LEFT ? -1 : 1, focusable,
                                fieldsScroll, fieldsTable);
                        paintFooterFocus.run();
                        return true;
                    }
                    if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER
                            || keycode == Input.Keys.SPACE) {
                        // libGDX 1.14.2: CheckBox extends TextButton, so this branch must run
                        // before the TextButton one (which only fires a ChangeEvent, no toggle).
                        if (focused instanceof CheckBox) {
                            // Toggling fires the ChangeListener, which keeps the form in sync.
                            CheckBox checkBox = (CheckBox) focused;
                            checkBox.setChecked(!checkBox.isChecked());
                            return true;
                        }
                        if (focused instanceof TextButton) {
                            ((TextButton) focused).fire(new ChangeEvent());
                            return true;
                        }
                        return false;
                    }
                    if (keycode == Input.Keys.ESCAPE) {
                        close.run();
                        return true;
                    }
                    return false;
                }

                @Override
                public boolean keyTyped(InputEvent event, char character) {
                    // Never swallow: the active text fields must receive their characters.
                    return false;
                }
            });

            dialog.padTop(46);
            dialog.pack();
            dialog.setWidth(Math.max(dialog.getWidth(), 760f));
            dialog.setHeight(Math.max(dialog.getHeight(), 620f));
            dialog.setPosition((stage.getWidth() - dialog.getWidth()) / 2,
                    (stage.getHeight() - dialog.getHeight()) / 2);

            stage.addActor(blocker);
            stage.addActor(dialog);
            showCategory.accept(SettingCategory.TERRAIN);
        });
    }

    /**
     * Applies the collected values: rejections go to the message panel, success to the feedback.
     */
    private void applyValues(SettingsPort port, Map<String, String> baseline, Label feedback,
            Map<String, String> values) {
        SettingsApplyResult outcome = port.apply(values);
        if (outcome.hasRejections()) {
            feedback.setText("");
            showMessage(SettingsText.REJECTED_TITLE,
                    SettingsText.rejectedMessage(outcome.rejections()));
        } else {
            baseline.putAll(values);
            feedback.setText(SettingsText.APPLIED);
        }
    }

    /** Moves the keyboard focus through the active page, wrapping around, and reveals it. */
    private void navigateSettingsFocus(int step, List<Actor> focusable, ScrollPane fieldsScroll,
            Table content) {
        if (focusable.isEmpty()) {
            return;
        }
        int index = focusable.indexOf(stage.getKeyboardFocus());
        int next = index < 0 ? 0 : Math.floorMod(index + step, focusable.size());
        Actor target = focusable.get(next);
        focusAndSelect(target);
        revealInScroll(target, fieldsScroll, content);
    }

    /**
     * Moves the keyboard focus and, on a text field, selects all its content so typing replaces it
     * (#757). libGDX only selects all from its own {@code TextField.next()} traversal, which the
     * custom navigation never triggers, so every focus move goes through here.
     */
    private void focusAndSelect(Actor target) {
        stage.setKeyboardFocus(target);
        if (target instanceof TextField) {
            ((TextField) target).selectAll();
        }
    }

    /**
     * True when {@code focused} really lives inside {@code container}, so the ScrollPane can map
     * its coordinates. A footer button belongs to the dialog's button table, not to the scroll
     * pane, and {@link Actor#localToAscendantCoordinates} throws {@code IllegalArgumentException}
     * for a non-descendant (libGDX 1.14.2): the guard keeps Tab onto the five footer buttons from
     * scrolling — or crashing — the page. Package-visible to pin it headlessly (S4b feedback,
     * #780).
     */
    static boolean isInsideScroll(Actor focused, Actor container) {
        return focused != null && container != null && focused.isDescendantOf(container);
    }

    /**
     * New scrollY (0 = top) that reveals the rect in the pane's content coordinates, keeping the
     * current scroll when it is already visible. Package-visible to pin it headlessly (#780).
     */
    static float revealScrollY(float rectBottom, float rectHeight, float contentHeight,
            float viewHeight, float scrollY) {
        float windowTop = contentHeight - scrollY;
        float windowBottom = windowTop - viewHeight;
        if (rectBottom + rectHeight > windowTop) {
            return contentHeight - (rectBottom + rectHeight);
        }
        if (rectBottom < windowBottom) {
            return contentHeight - viewHeight - rectBottom;
        }
        return scrollY;
    }

    /**
     * Scrolls {@code fieldsScroll} just enough to reveal {@code focused}, computing the scroll
     * ourselves in the content's own coordinates: {@link ScrollPane#scrollTo} of libGDX 1.14.2
     * clamps {@code [y + height, y + actorArea.height]} after its {@code maxY - y} flip, offsetting
     * both ends by the focused widget's height (one row), so an upward move leaves the field just
     * above the viewport (the bug the owner reported). Shared by focus navigation and the
     * confirmation callbacks that hand the focus back (S4b feedback, #780). A focused actor outside
     * the pane (e.g. a footer button) is skipped: it cannot be mapped into the pane's space.
     */
    private static void revealInScroll(Actor focused, ScrollPane fieldsScroll, Table content) {
        if (!isInsideScroll(focused, fieldsScroll) || !isInsideScroll(focused, content)) {
            return;
        }
        fieldsScroll.validate();
        com.badlogic.gdx.math.Vector2 p =
                focused.localToAscendantCoordinates(content, new com.badlogic.gdx.math.Vector2());
        fieldsScroll.setScrollY(revealScrollY(p.y, focused.getHeight(), content.getHeight(),
                fieldsScroll.getHeight(), fieldsScroll.getScrollY()));
    }

    /** True while either Alt key is held (Alt+letter access keys). */
    private static boolean isAltPressed() {
        return Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT);
    }

    /** True while either Ctrl key is held (Ctrl+Tab category cycle). */
    private static boolean isCtrlPressed() {
        return Gdx.input.isKeyPressed(Input.Keys.CONTROL_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.CONTROL_RIGHT);
    }

    /** True while either Shift key is held (reverse Ctrl+Tab). */
    private static boolean isShiftPressed() {
        return Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
    }

    /** Fills the page with one row per catalogue field of {@code category}, in catalogue order. */
    private void fillSettingsCategory(SettingCategory category, Table fieldsTable,
            List<Actor> categoryFields, Map<String, String> form, Set<String> scenarioKeys) {
        fieldsTable.clear();
        categoryFields.clear();
        for (SettingDefinition definition : categoryDefinitions(category)) {
            fieldsTable.add(settingsFieldRow(definition, scenarioKeys, form, categoryFields))
                    .growX().left().padBottom(6).row();
        }
    }

    /**
     * Terrain page (#778): the 2D New Game terrain block (seed, water/mountain sliders,
     * three-colour 100 % bar and the map preview) with the running world's parameters, read-only.
     * Nothing here enters the Tab cycle (#767): the controls are disabled and
     * {@code categoryFields} stays empty, so the dialog pins the focus to the footer instead.
     */
    private void fillSettingsTerrain(Table fieldsTable, List<Actor> categoryFields,
            Map<String, String> form, List<TerrainPreviewView> previews, Set<String> scenarioKeys) {
        fieldsTable.clear();
        categoryFields.clear();

        int seed = parseIntOr(form.get(NewGameForm.SEED_KEY), 0);
        int water =
                parseIntOr(form.get(NewGameForm.WATER_KEY), SettingsCatalog.DEFAULT_WATER_PERCENT);
        int mountain = parseIntOr(form.get(NewGameForm.MOUNTAIN_KEY),
                SettingsCatalog.DEFAULT_MOUNTAIN_PERCENT);

        final float labelWidth = 90f;
        final float valueWidth = 64f;

        Table block = new Table();
        block.top().left();
        block.defaults().space(6);

        // Seed: read-only text field, greyed out and out of the focus cycle (#767). Every row opens
        // with the state glyph so the Terrain page keeps the dots of the other pages (S4b #780).
        Table seedRow = new Table();
        seedRow.left();
        seedRow.add(settingsGlyphLabel(terrainFieldState(NewGameForm.SEED_KEY, scenarioKeys)))
                .width(GLYPH_COLUMN_WIDTH);
        seedRow.add(new Label(NewGameText.SEED_LABEL, skin, "small")).width(labelWidth).left();
        TextField seedField = new TextField(String.valueOf(seed), skin);
        seedField.setDisabled(true);
        seedField.setFocusTraversal(false);
        seedField.setColor(DISABLED_TINT);
        seedField.setTextFieldFilter((f, c) -> c >= 32);
        seedRow.add(seedField).width(TERRAIN_SEED_WIDTH).height(32f).left();
        block.add(seedRow).left().row();

        block.add(terrainSliderRow(NewGameText.WATER_LABEL, water, NewGameForm.WATER_KEY,
                scenarioKeys, labelWidth, valueWidth)).left().row();
        block.add(terrainSliderRow(NewGameText.MOUNTAIN_LABEL, mountain, NewGameForm.MOUNTAIN_KEY,
                scenarioKeys, labelWidth, valueWidth)).left().row();

        // The bar and the map start under the widgets, past the glyph and the label columns.
        final float widgetIndent = GLYPH_COLUMN_WIDTH + labelWidth;
        // Three-colour 100 % bar, aligned under the sliders (same split as the 2D dialog).
        block.add(terrainBarTable(water, mountain)).left().padLeft(widgetIndent).row();

        // Blank gap, then the map preview from the same sampler as the 2D section. The view is
        // handed to {@code previews} so the caller disposes it when the page is swapped or the
        // dialog closes.
        block.add().height(8f).row();
        TerrainPreviewView preview = new TerrainPreviewView();
        preview.setValues(seed, water, mountain);
        previews.add(preview);
        block.add(preview).left().padLeft(widgetIndent).row();

        fieldsTable.add(block).growX().left().top();
    }

    /**
     * One disabled terrain slider row for the Settings page: state glyph, label, slider and its
     * percentage, greyed out (#767, S4b feedback #780).
     */
    private Table terrainSliderRow(String label, int percent, String key, Set<String> scenarioKeys,
            float labelWidth, float valueWidth) {
        Slider slider = new Slider(0, SettingsCatalog.MAX_TERRAIN_PERCENT, 1, false, skin);
        slider.setValue(percent);
        // Disabled style paints the grey track/knob (no setColor tint, #780).
        slider.setDisabled(true);
        Label value = new Label(percent + " %", skin, "small");
        value.setColor(Color.LIGHT_GRAY);
        Table row = new Table();
        row.left();
        row.add(settingsGlyphLabel(terrainFieldState(key, scenarioKeys))).width(GLYPH_COLUMN_WIDTH);
        row.add(new Label(label, skin, "small")).width(labelWidth).left();
        row.add(slider).width(TERRAIN_BAR_WIDTH).height(30f);
        row.add(value).width(valueWidth).left();
        return row;
    }

    /** Field state of a terrain key: NEW_GAME (red) unless a scenario pins it to grey (#780). */
    private static SettingFieldState terrainFieldState(String key, Set<String> scenarioKeys) {
        return SettingsCatalog.definition(key)
                .map(definition -> SettingsCatalog.fieldState(definition, scenarioKeys))
                .orElse(SettingFieldState.NEW_GAME);
    }

    /**
     * One terrain slider row from existing widgets: label, slider and its value. Shared by the
     * disabled Settings page and the editable New Game dialog (S4b feedback, #780).
     */
    private Table terrainSliderRow(String label, Slider slider, Label value, float labelWidth,
            float valueWidth) {
        Table row = new Table();
        row.left();
        row.add(new Label(label, skin, "small")).width(labelWidth).left();
        row.add(slider).width(TERRAIN_BAR_WIDTH).height(30f);
        row.add(value).width(valueWidth).left();
        return row;
    }

    /** One bar segment: the shared "white" pixel tinted with the world's day tone (#778). */
    private Image terrainColorImage(VisualPalette.Token token) {
        return new Image(skin.newDrawable("white", terrainDayColor(token)));
    }

    /**
     * Three-colour 100 % bar (draw order water/field/mountain), aligned under the terrain sliders:
     * shared by the Settings Terrain page and the live New Game block (#778, #780).
     */
    private Table terrainBarTable(int waterPercent, int mountainPercent) {
        int[] segments = stackedBarWidths(waterPercent, mountainPercent, TERRAIN_BAR_WIDTH);
        Table bar = new Table();
        bar.add(terrainColorImage(VisualPalette.Token.TERRAIN_WATER)).width(segments[0])
                .height(18f);
        bar.add(terrainColorImage(VisualPalette.Token.TERRAIN_FIELDS)).width(segments[1])
                .height(18f);
        bar.add(terrainColorImage(VisualPalette.Token.TERRAIN_MOUNTAIN)).width(segments[2])
                .height(18f);
        return bar;
    }

    /** Resizes the three segments of a bar built by {@link #terrainBarTable} in place. */
    private static void updateTerrainBar(Table bar, int waterPercent, int mountainPercent) {
        int[] segments = stackedBarWidths(waterPercent, mountainPercent, TERRAIN_BAR_WIDTH);
        for (int i = 0; i < 3; i++) {
            bar.getCells().get(i).width(segments[i]);
        }
        bar.invalidateHierarchy();
    }

    /**
     * Live square terrain preview shared by the New Game dialog and the Settings Terrain page: owns
     * an {@link Image}, its {@link Pixmap} and its {@link Texture}. {@link #setValues} repaints the
     * sample on the same pixmap and uploads it with {@code texture.draw(pixmap, 0, 0)}, so the
     * texture is reused instead of recreating the pipeline. The caller disposes it when the dialog
     * closes (New Game) or the page is swapped (Settings).
     */
    static final class TerrainPreviewView extends Image {
        private final Pixmap pixmap;
        private final Texture texture;
        private final int cells;
        private final int scale;

        TerrainPreviewView() {
            this(TERRAIN_PREVIEW_CELLS, TERRAIN_PREVIEW_SCALE);
        }

        TerrainPreviewView(int cells, int scale) {
            this.cells = cells;
            this.scale = scale;
            int pixels = cells * scale;
            this.pixmap = new Pixmap(pixels, pixels, Pixmap.Format.RGBA8888);
            this.texture = new Texture(pixmap);
            this.texture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            setDrawable(new TextureRegionDrawable(new TextureRegion(texture)));
        }

        /** Repaints the sample for {@code seed} and the terrain proportions on the same texture. */
        void setValues(int seed, int waterPercent, int mountainPercent) {
            Color waterColor = terrainDayColor(VisualPalette.Token.TERRAIN_WATER);
            Color fieldsColor = terrainDayColor(VisualPalette.Token.TERRAIN_FIELDS);
            Color mountainColor = terrainDayColor(VisualPalette.Token.TERRAIN_MOUNTAIN);
            TerrainPreview.Grid grid =
                    TerrainPreview.sample(seed, waterPercent, mountainPercent, cells, cells);
            for (int row = 0; row < cells; row++) {
                for (int column = 0; column < cells; column++) {
                    pixmap.setColor(terrainCellColor(grid.cell(column, row), waterColor,
                            fieldsColor, mountainColor));
                    pixmap.fillRectangle(column * scale, row * scale, scale, scale);
                }
            }
            texture.draw(pixmap, 0, 0);
        }

        /** Frees the texture and the pixmap; idempotent. */
        void dispose() {
            texture.dispose();
            pixmap.dispose();
        }
    }

    /** Cell colour of the preview: sea blue, board green or the shared mountain brown. */
    private static Color terrainCellColor(int terrain, Color water, Color fields, Color mountain) {
        if (terrain == TerrainPreview.WATER) {
            return water;
        }
        if (terrain == TerrainPreview.ROCK) {
            return mountain;
        }
        return fields;
    }

    /** Disposes every preview view of a page and empties the list (idempotent). */
    private static void disposeTerrainPreviews(List<TerrainPreviewView> previews) {
        for (TerrainPreviewView preview : previews) {
            preview.dispose();
        }
        previews.clear();
    }

    /** Parses an integer, falling back on malformed or absent text (mirrors the 2D dialog). */
    private static int parseIntOr(String text, int fallback) {
        try {
            return Integer.parseInt(text.trim());
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /** State glyph of a field/Terrain row: {@link #SETTINGS_GLYPH} tinted with the state colour. */
    private Label settingsGlyphLabel(SettingFieldState state) {
        Label glyph = new Label(SETTINGS_GLYPH, skin, "default");
        glyph.setColor(settingsGlyphColor(state));
        return glyph;
    }

    /**
     * One field row: state glyph, label with unit and the editing widget — a checkbox for BOOLEAN
     * settings, a text field otherwise — or a read-only value label when the field is locked.
     */
    private Table settingsFieldRow(SettingDefinition definition, Set<String> scenarioKeys,
            Map<String, String> form, List<Actor> categoryFields) {
        SettingFieldState state = SettingsCatalog.fieldState(definition, scenarioKeys);
        Table row = new Table();
        row.left();
        row.defaults().space(8);
        row.add(settingsGlyphLabel(state)).width(GLYPH_COLUMN_WIDTH);
        row.add(new Label(settingsFieldLabel(definition), skin, "default")).width(280f).left();
        String value = form.getOrDefault(definition.key(), "");
        if (definition.type() == SettingValueType.BOOLEAN) {
            // Boolean settings get a real checkbox (2D parity, #780); a disabled one mirrors the
            // read-only label of the other types and stays out of the Tab cycle.
            CheckBox checkBox = new CheckBox("", skin);
            checkBox.setChecked(Boolean.parseBoolean(value));
            checkBox.setDisabled(!state.editable());
            // Keep the working form in sync: Apply collects editableValues from it, unchanged.
            checkBox.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    form.put(definition.key(), checkBox.isChecked() ? "true" : "false");
                }
            });
            row.add(checkBox).width(240f).height(34f).left();
            if (state.editable()) {
                categoryFields.add(checkBox);
            }
        } else if (state.editable()) {
            TextField field = new TextField(value, skin);
            field.setFocusTraversal(false);
            // LWJGL3 synthesises keyTyped('\t') on Tab and hasGlyph lies for the missing glyph, so
            // a plain field would type the "?" placeholder: only accept printable characters.
            field.setTextFieldFilter((f, c) -> c >= 32);
            // Keep the working form in sync so switching categories never loses an edit.
            field.setTextFieldListener((f, c) -> form.put(definition.key(), f.getText()));
            row.add(field).width(240f).height(34f);
            categoryFields.add(field);
        } else {
            Label valueLabel = new Label(value, skin, "default");
            valueLabel.setColor(Color.LIGHT_GRAY);
            row.add(valueLabel).width(240f).left();
        }
        return row;
    }

    /**
     * Hover tooltip for a disabled File menu row (#802): the terminal has no tooltips, so the 3D
     * client is where the lock reason can still be read. The code-built skin has no tooltip style,
     * so it is assembled here from the small label style and a translucent background.
     */
    private TextTooltip newTooltip(String text) {
        TextTooltip.TextTooltipStyle style = new TextTooltip.TextTooltipStyle();
        style.label = skin.get("small", Label.LabelStyle.class);
        style.background = skin.newDrawable("white", new Color(0f, 0f, 0f, 0.85f));
        return new TextTooltip(text, style);
    }

    /**
     * Alt key of a confirmation's confirm button (#803): its first alphabetic letter, skipping
     * {@code C} so it never collides with Cancel. Falls back to {@code Y} for a label with no other
     * letter.
     */
    static char confirmMnemonic(String label) {
        if (label != null) {
            for (int i = 0; i < label.length(); i++) {
                char c = label.charAt(i);
                if (Character.isLetter(c) && Character.toUpperCase(c) != 'C') {
                    return Character.toUpperCase(c);
                }
            }
        }
        return 'Y';
    }

    /** Moves a slider one step in {@code direction} (-1/+1), clamped to its range (#804). */
    private static void stepSlider(Slider slider, int direction) {
        float step = direction * slider.getStepSize();
        slider.setValue(com.badlogic.gdx.math.MathUtils.clamp(slider.getValue() + step,
                slider.getMinValue(), slider.getMaxValue()));
    }

    /** One button listener that fires its action. */
    private static ChangeListener fireAction(Runnable action) {
        return new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                action.run();
            }
        };
    }

    /**
     * Shows a message (console help/info, listings, errors) in the right-side panel. It is
     * deliberately **not** a dialog: the panel never takes the keyboard focus, so the console and
     * the game stay usable while it is open.
     */
    public void showMessage(String title, String message) {
        Gdx.app.postRunnable(() -> {
            if (messageWindow == null) {
                buildMessageWindow();
            }
            messageWindow.getTitleLabel().setText(title == null ? "" : title);
            messageLabel.setText(message == null ? "" : message);
            float width = Math.min(760f, Math.max(360f, stage.getWidth() * 0.42f));
            float height = Math.min(700f, Math.max(240f, stage.getHeight() * 0.7f));
            messageWindow.setSize(width, height);
            messageWindow.setPosition(stage.getWidth() - width - 16f,
                    Math.max(16f, stage.getHeight() - height - 80f));
            messageLabel.setWidth(width - 56f);
            messageWindow.setVisible(true);
            messageWindow.toFront();
            messageScroll.layout();
            messageScroll.setScrollPercentY(0f);
        });
    }

    private void buildMessageWindow() {
        messageWindow = new Window("", skin.get("dialog", Window.WindowStyle.class));
        messageWindow.setModal(false);
        messageWindow.setMovable(true);
        messageWindow.setResizable(false);
        messageWindow.padTop(40);
        messageWindow.defaults().space(6);

        messageLabel = new Label("", skin, "small");
        messageLabel.setWrap(true);
        messageLabel.setAlignment(com.badlogic.gdx.utils.Align.topLeft);
        messageScroll = new ScrollPane(messageLabel, skin);
        messageScroll.setFadeScrollBars(false);
        messageScroll.setScrollingDisabled(true, false);
        messageWindow.add(messageScroll).grow().padLeft(12).padRight(12);

        messageWindow.row();
        TextButton close = new TextButton("Cerrar", skin);
        close.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                hideMessage();
            }
        });
        messageWindow.add(close).width(140).height(38).padBottom(12);

        // Wheel scrolls the panel only while the pointer is over it.
        messageWindow.addListener(new InputListener() {
            @Override
            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                stage.setScrollFocus(messageScroll);
            }

            @Override
            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                stage.setScrollFocus(null);
            }
        });

        messageWindow.setVisible(false);
        stage.addActor(messageWindow);
    }

    private void hideMessage() {
        if (messageWindow != null) {
            messageWindow.setVisible(false);
        }
        stage.setScrollFocus(null);
    }

    public void showIDE() {
        if (ideWindow != null) {
            ideWindow.toFront();
            Actor editor = ideWindow.findActor("editorTextArea");
            if (editor != null) {
                stage.setKeyboardFocus(editor);
            }
            return;
        }
        Gdx.app.postRunnable(() -> {
            if (ideWindow != null) {
                return;
            }
            final Window window =
                    new Window("LeTrain Editor " + letrain.BuildInfo.versionTag() + " (3D)", skin);
            window.setModal(true);
            window.setMovable(true);
            window.setResizable(true);
            window.padTop(35);

            final com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable windowWhite = skin.get(
                    "window-white", com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable.class);
            final com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable windowBlue = skin.get(
                    "window-blue", com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable.class);
            final Window.WindowStyle ideWindowStyle =
                    new Window.WindowStyle(skin.get(Window.WindowStyle.class));
            ideWindowStyle.background = windowWhite;
            window.setStyle(ideWindowStyle);
            // Center the title (the title table also holds the close/max buttons on the right).
            window.getTitleLabel().setAlignment(com.badlogic.gdx.utils.Align.center);
            window.getTitleTable().getCells().first().expandX();

            // ---- State: three tabs (Scenario / Programming / Config)
            final String[] scenarioBuffer = {view.getScenarioText()};
            final String[] programBuffer =
                    {letrain.command.ScenarioFile.programSection(view.getProgram())};
            final String[] configBuffer = {configSectionOrEmpty(view.getConfigurationText())};
            final int[] activeTab = {0};

            // ---- Editor with line numbers
            final com.badlogic.gdx.scenes.scene2d.ui.TextArea textArea =
                    new com.badlogic.gdx.scenes.scene2d.ui.TextArea(scenarioBuffer[0], skin,
                            "monospace-textarea");
            textArea.setName("editorTextArea");

            final Table lineNumbersTable = new Table();
            lineNumbersTable.top().right();
            final Runnable updateLineNumbers = () -> {
                lineNumbersTable.clearChildren();
                int lines = textArea.getText().split("\n", -1).length;
                float lineHeight = textArea.getStyle().font.getLineHeight();
                for (int i = 1; i <= lines; i++) {
                    Label l = new Label(String.valueOf(i), skin, "monospace");
                    l.setColor(Color.GRAY);
                    lineNumbersTable.add(l).height(lineHeight).top().right().padRight(10).row();
                }
            };
            textArea.setTextFieldListener((textField, c) -> updateLineNumbers.run());
            updateLineNumbers.run();

            float topPad = textArea.getStyle().background != null
                    ? textArea.getStyle().background.getTopHeight()
                    : 0;
            Table editorSubContainer = new Table();
            editorSubContainer.top().left();
            editorSubContainer.add(lineNumbersTable).top().padTop(topPad + 2.5f);
            editorSubContainer.add(textArea).grow().top();
            ScrollPane editorScroll = new ScrollPane(editorSubContainer, skin);
            editorScroll.setFadeScrollBars(false);

            // ---- Status bar (Ln/Col)
            final Label statusLabel = new Label("", skin, "monospace");
            final Runnable updateStatus = () -> {
                String text = textArea.getText();
                int pos = Math.min(textArea.getCursorPosition(), text.length());
                int row = 0;
                int col = 0;
                for (int i = 0; i < pos; i++) {
                    if (text.charAt(i) == '\n') {
                        row++;
                        col = 0;
                    } else {
                        col++;
                    }
                }
                statusLabel.setText("Ln " + (row + 1) + ", Col " + (col + 1));
            };
            updateStatus.run();

            // ---- Quick reference (rebuilt per tab)
            com.badlogic.gdx.scenes.scene2d.ui.Tree.TreeStyle treeStyle =
                    new com.badlogic.gdx.scenes.scene2d.ui.Tree.TreeStyle();
            treeStyle.plus = skin.newDrawable("white", new Color(0.6f, 0.6f, 0.6f, 1f));
            treeStyle.minus = skin.newDrawable("white", new Color(0.6f, 0.6f, 0.6f, 1f));
            treeStyle.selection = skin.newDrawable("white", new Color(0.2f, 0.4f, 0.6f, 0.8f));
            final Tree refTree = new Tree(treeStyle);
            refTree.setPadding(5f);
            refTree.setIconSpacing(6f, 0);
            refTree.setIndentSpacing(12f);
            ScrollPane refScroll = new ScrollPane(refTree, skin);

            final Consumer<String> insertSnippet = snippet -> insertQuickRef(textArea, snippet);

            final Runnable rebuildRef = () -> {
                refTree.clearChildren();
                letrain.command.GrammarReference.Group group =
                        activeTab[0] == 1 ? letrain.command.GrammarReference.Group.PROGRAM
                                : activeTab[0] == 2 ? letrain.command.GrammarReference.Group.CONFIG
                                        : letrain.command.GrammarReference.Group.BUILD;
                class TreeBuilder {
                    Tree.Node build(letrain.command.GrammarReference.Node refNode, String indent) {
                        if (refNode.isHeading) {
                            Label l = new Label(refNode.label, skin, "monospace");
                            l.setColor(Color.ORANGE);
                            return new Tree.Node(l) {};
                        } else if (refNode.snippet != null && refNode.children.isEmpty()) {
                            Label l = new Label("   " + refNode.label, skin, "monospace");
                            Tree.Node n = new Tree.Node(l) {};
                            n.setValue(refNode.snippet);
                            l.addListener(new ClickListener() {
                                @Override
                                public void clicked(InputEvent event, float x, float y) {
                                    insertSnippet.accept(refNode.snippet);
                                    event.stop();
                                }
                            });
                            return n;
                        } else {
                            String prefix = refNode.expanded ? "[-]" : "[+]";
                            Label l = new Label(indent + prefix + " " + refNode.label, skin,
                                    "monospace");
                            Tree.Node n = new Tree.Node(l) {};
                            l.addListener(new ClickListener() {
                                @Override
                                public void clicked(InputEvent event, float x, float y) {
                                    n.setExpanded(!n.isExpanded());
                                    l.setText(n.isExpanded() ? indent + "[-] " + refNode.label
                                            : indent + "[+] " + refNode.label);
                                    event.stop();
                                }
                            });
                            for (letrain.command.GrammarReference.Node child : refNode.children) {
                                n.add(build(child, indent + "  "));
                            }
                            n.setExpanded(refNode.expanded);
                            return n;
                        }
                    }
                }
                TreeBuilder tb = new TreeBuilder();
                for (letrain.command.GrammarReference.Node rootNode : letrain.command.GrammarReference
                        .getReferenceTree(group)) {
                    refTree.add(tb.build(rootNode, "  "));
                }
            };

            // Keyboard navigation inside the quick reference (arrows + Enter to insert/expand).
            refTree.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
                private Tree.Node nextVisible(Tree.Node current) {
                    if (current == null) {
                        return refTree.getRootNodes().size > 0
                                ? (Tree.Node) refTree.getRootNodes().get(0)
                                : null;
                    }
                    if (current.isExpanded() && current.getChildren().size > 0) {
                        return (Tree.Node) current.getChildren().get(0);
                    }
                    Tree.Node node = current;
                    while (node != null) {
                        Tree.Node parent = node.getParent();
                        com.badlogic.gdx.utils.Array siblings =
                                parent == null ? refTree.getRootNodes() : parent.getChildren();
                        int idx = siblings.indexOf(node, true);
                        if (idx < siblings.size - 1) {
                            return (Tree.Node) siblings.get(idx + 1);
                        }
                        node = parent;
                    }
                    return null;
                }

                private Tree.Node prevVisible(Tree.Node current) {
                    if (current == null) {
                        return refTree.getRootNodes().size > 0
                                ? (Tree.Node) refTree.getRootNodes().get(0)
                                : null;
                    }
                    Tree.Node parent = current.getParent();
                    com.badlogic.gdx.utils.Array siblings =
                            parent == null ? refTree.getRootNodes() : parent.getChildren();
                    int idx = siblings.indexOf(current, true);
                    if (idx > 0) {
                        Tree.Node node = (Tree.Node) siblings.get(idx - 1);
                        while (node.isExpanded() && node.getChildren().size > 0) {
                            node = (Tree.Node) node.getChildren().peek();
                        }
                        return node;
                    }
                    return parent;
                }

                private void scrollRefTo(Tree.Node node) {
                    if (node.getActor() != null) {
                        refScroll.scrollTo(node.getActor().getX(), node.getActor().getY(),
                                node.getActor().getWidth(), node.getActor().getHeight());
                    }
                }

                @Override
                public boolean keyDown(InputEvent event, int keycode) {
                    com.badlogic.gdx.utils.Array<Tree.Node> selection =
                            refTree.getSelection().toArray();
                    Tree.Node current = selection.size > 0 ? selection.get(0) : null;
                    if (keycode == com.badlogic.gdx.Input.Keys.DOWN) {
                        Tree.Node next = nextVisible(current);
                        if (next != null) {
                            refTree.getSelection().set(next);
                            scrollRefTo(next);
                        }
                        return true;
                    } else if (keycode == com.badlogic.gdx.Input.Keys.UP) {
                        Tree.Node prev = prevVisible(current);
                        if (prev != null) {
                            refTree.getSelection().set(prev);
                            scrollRefTo(prev);
                        }
                        return true;
                    } else if (keycode == com.badlogic.gdx.Input.Keys.ENTER && current != null) {
                        if (current.getValue() instanceof String) {
                            insertSnippet.accept((String) current.getValue());
                            return true;
                        } else if (current.getActor() instanceof Label) {
                            Label l = (Label) current.getActor();
                            String text = l.getText().toString();
                            if (text.contains("[+]") || text.contains("[-]")) {
                                boolean expanded = !current.isExpanded();
                                current.setExpanded(expanded);
                                l.setText(expanded ? text.replace("[+]", "[-]")
                                        : text.replace("[-]", "[+]"));
                                return true;
                            }
                        }
                    }
                    return false;
                }
            });

            // ---- Tab bar
            Table tabBar = new Table();
            final TextButton scenarioTabBtn = new TextButton(editorTabText(EditorText.TAB_SCENARIO),
                    skin, "monospace-button");
            final TextButton programTabBtn = new TextButton(
                    editorTabText(EditorText.TAB_PROGRAMMING), skin, "monospace-button");
            final TextButton configTabBtn =
                    new TextButton(editorTabText(EditorText.TAB_CONFIG), skin, "monospace-button");
            tabBar.add(scenarioTabBtn).padRight(5);
            tabBar.add(programTabBtn).padRight(5);
            tabBar.add(configTabBtn);

            final Runnable saveActive = () -> {
                if (activeTab[0] == 1) {
                    programBuffer[0] = textArea.getText();
                } else if (activeTab[0] == 2) {
                    configBuffer[0] = textArea.getText();
                } else {
                    scenarioBuffer[0] = textArea.getText();
                }
            };
            final Runnable updateTabHighlight = () -> {
                scenarioTabBtn.setColor(activeTab[0] == 0 ? Color.GREEN : Color.WHITE);
                programTabBtn.setColor(activeTab[0] == 1 ? Color.GREEN : Color.WHITE);
                configTabBtn.setColor(activeTab[0] == 2 ? Color.GREEN : Color.WHITE);
            };
            final java.util.function.IntConsumer switchTo = tab -> {
                if (activeTab[0] == tab) {
                    return;
                }
                saveActive.run();
                activeTab[0] = tab;
                textArea.setText(tab == 1 ? programBuffer[0]
                        : tab == 2 ? configBuffer[0] : scenarioBuffer[0]);
                textArea.setCursorPosition(0);
                updateLineNumbers.run();
                updateStatus.run();
                rebuildRef.run();
                updateTabHighlight.run();
                if (stage != null) {
                    stage.setKeyboardFocus(textArea);
                }
            };
            scenarioTabBtn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    switchTo.accept(0);
                }
            });
            programTabBtn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    switchTo.accept(1);
                }
            });
            configTabBtn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    switchTo.accept(2);
                }
            });
            updateTabHighlight.run();
            rebuildRef.run();

            // Recompute the status and line numbers after the editor handles a key.
            textArea.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
                @Override
                public boolean keyDown(InputEvent event, int keycode) {
                    Gdx.app.postRunnable(() -> {
                        updateStatus.run();
                        updateLineNumbers.run();
                    });
                    return false;
                }
            });

            // ---- Composed scenario + diagnostics
            final java.util.function.Supplier<String> composeFull = () -> {
                saveActive.run();
                try {
                    return letrain.command.ScenarioFile.compose(scenarioBuffer[0], configBuffer[0],
                            programBuffer[0]);
                } catch (Exception e) {
                    return scenarioBuffer[0] + "\n" + configBuffer[0] + "\n" + programBuffer[0];
                }
            };

            final Table errorItems = new Table();
            errorItems.top().left();
            ScrollPane errorScroll = new ScrollPane(errorItems, skin);
            errorScroll.setFadeScrollBars(false);
            final Table errorTable = new CollapsiblePanel();
            errorTable.setBackground(skin.newDrawable("white", Color.MAROON));
            errorTable.add(new Label("ERRORS (click to jump):", skin, "monospace")).left()
                    .padLeft(5).row();
            errorTable.add(errorScroll).growX().height(120).pad(5);
            errorTable.setVisible(false);

            final Consumer<letrain.command.ScenarioCompiler.Diagnostic> jumpTo = d -> {
                letrain.command.ScenarioFile.LineTarget target =
                        letrain.command.ScenarioFile.locateLine(composeFull.get(), d.line());
                switchTo.accept(target.tab());
                int pos = offsetOfLine(textArea.getText(), target.line());
                textArea.setCursorPosition(Math.min(pos, textArea.getText().length()));
                if (stage != null) {
                    stage.setKeyboardFocus(textArea);
                }
            };
            final Consumer<letrain.command.ScenarioCompiler.Result> showDiagnostics = result -> {
                errorItems.clearChildren();
                if (result.ok()) {
                    errorTable.setVisible(false);
                    return;
                }
                for (letrain.command.ScenarioCompiler.Diagnostic d : result.diagnostics()) {
                    TextButton b = new TextButton(d.line() + ":" + d.col() + ": " + d.message(),
                            skin, "monospace-button");
                    b.addListener(new ChangeListener() {
                        @Override
                        public void changed(ChangeEvent event, Actor actor) {
                            jumpTo.accept(d);
                        }
                    });
                    errorItems.add(b).left().padBottom(2).row();
                }
                errorTable.setVisible(true);
                window.invalidateHierarchy();
            };

            // ---- Actions (Save/Load moved to the File menu, issue #743)
            final Runnable refreshAction = () -> {
                scenarioBuffer[0] = view.getScenarioText();
                programBuffer[0] = letrain.command.ScenarioFile.programSection(view.getProgram());
                configBuffer[0] = configSectionOrEmpty(view.getConfigurationText());
                textArea.setText(activeTab[0] == 1 ? programBuffer[0]
                        : activeTab[0] == 2 ? configBuffer[0] : scenarioBuffer[0]);
                textArea.setCursorPosition(0);
                updateLineNumbers.run();
                updateStatus.run();
                rebuildRef.run();
            };
            final Runnable reprogramAction = () -> {
                saveActive.run();
                view.onEditCommands(
                        letrain.command.ScenarioFile.programSectionBody(programBuffer[0]));
            };
            final Runnable exportAction = () -> {
                String full = composeFull.get();
                letrain.command.ScenarioCompiler.Result result =
                        letrain.command.ScenarioCompiler.compile(full);
                showDiagnostics.accept(result);
                showFileDialog("Export Scenario", Gdx3DFileDialog.Mode.SAVE, "scenario.ltr",
                        new String[] {"ltr"}, path -> {
                            if (path != null && !path.trim().isEmpty()) {
                                view.onExportScenarioText(new java.io.File(path), full);
                            }
                        });
            };
            final Runnable importAction = () -> showFileDialog("Open Scenario",
                    Gdx3DFileDialog.Mode.OPEN, "scenario.ltr", new String[] {"ltr"}, path -> {
                        if (path == null || path.trim().isEmpty()) {
                            return;
                        }
                        try {
                            String fileText =
                                    java.nio.file.Files.readString(new java.io.File(path).toPath());
                            letrain.command.ScenarioFile.Parts parts =
                                    letrain.command.ScenarioFile.split(fileText);
                            scenarioBuffer[0] = parts.scenarioText();
                            programBuffer[0] = parts.programText();
                            configBuffer[0] = configSectionOrEmpty(parts.configurationText());
                            textArea.setText(activeTab[0] == 1 ? programBuffer[0]
                                    : activeTab[0] == 2 ? configBuffer[0] : scenarioBuffer[0]);
                            textArea.setCursorPosition(0);
                            updateLineNumbers.run();
                            updateStatus.run();
                            rebuildRef.run();
                        } catch (Exception ex) {
                            showMessage("Scenario Error", String.valueOf(ex.getMessage()));
                        }
                    });
            final Runnable rebuildAction = () -> {
                String full = composeFull.get();
                letrain.command.ScenarioCompiler.Result result =
                        letrain.command.ScenarioCompiler.compile(full);
                showDiagnostics.accept(result);
                if (result.ok()) {
                    confirmRebuildWorld(() -> view.onPlayScenarioText(full));
                }
            };
            final Runnable closeAction = () -> {
                saveActive.run();
                ideWindow = null;
                window.remove();
                model.setMode(letrain.mvp.Model.GameMode.RAILS);
                view.onGameModeSelected(letrain.mvp.Model.GameMode.RAILS);
            };

            // ---- Title bar buttons
            Table titleTable = window.getTitleTable();
            TextButton.TextButtonStyle titleBtnStyle =
                    new TextButton.TextButtonStyle(skin.get(TextButton.TextButtonStyle.class));
            titleBtnStyle.font = skin.getFont("monospace-font");
            titleBtnStyle.up = skin.newDrawable("white", Color.DARK_GRAY);
            TextButton closeBtnTitle = new TextButton(" X ", titleBtnStyle);
            TextButton maxBtnTitle = new TextButton(" [ ] ", titleBtnStyle);
            titleTable.add(maxBtnTitle).size(30, 22).right().padRight(5);
            titleTable.add(closeBtnTitle).size(30, 22).right().padRight(10);
            closeBtnTitle.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    closeAction.run();
                }
            });
            final boolean[] isMaximized = {false};
            final float[] prevX = {0}, prevY = {0}, prevW = {0}, prevH = {0};
            maxBtnTitle.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    if (!isMaximized[0]) {
                        prevX[0] = window.getX();
                        prevY[0] = window.getY();
                        prevW[0] = window.getWidth();
                        prevH[0] = window.getHeight();
                        window.setBounds(0, 0, stage.getWidth(), stage.getHeight());
                        window.setResizable(false);
                        window.setMovable(false);
                        isMaximized[0] = true;
                        maxBtnTitle.setText(" [-] ");
                    } else {
                        window.setResizable(true);
                        window.setMovable(true);
                        window.setBounds(prevX[0], prevY[0], prevW[0], prevH[0]);
                        isMaximized[0] = false;
                        maxBtnTitle.setText(" [ ] ");
                    }
                    window.invalidateHierarchy();
                }
            });
            window.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
                @Override
                public boolean mouseMoved(InputEvent event, float x, float y) {
                    if (!isMaximized[0]) {
                        boolean nearBorder = (x <= 20 || x >= window.getWidth() - 20 || y <= 20
                                || y >= window.getHeight() - 20);
                        ideWindowStyle.background = nearBorder ? windowBlue : windowWhite;
                        window.setBackground(ideWindowStyle.background);
                    }
                    return false;
                }

                @Override
                public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                    if (toActor == null || !toActor.isDescendantOf(window)) {
                        ideWindowStyle.background = windowWhite;
                        window.setBackground(windowWhite);
                    }
                }
            });

            // ---- Footer (Save/Load moved to the File menu, issue #743)
            Table footer = new Table();
            TextButton exportBtn =
                    new TextButton(editorButtonText(EditorText.EXPORT), skin, "monospace-button");
            TextButton importBtn =
                    new TextButton(editorButtonText(EditorText.IMPORT), skin, "monospace-button");
            TextButton refreshBtn = new TextButton(editorButtonText(EditorText.RELOAD_FROM_GAME),
                    skin, "monospace-button");
            TextButton reprogramBtn = new TextButton(editorButtonText(EditorText.APPLY_PROGRAM),
                    skin, "monospace-button");
            TextButton rebuildBtn = new TextButton(editorButtonText(EditorText.REBUILD_WORLD), skin,
                    "monospace-button");
            TextButton closeBtn =
                    new TextButton(editorButtonText(EditorText.CLOSE), skin, "monospace-button");
            footer.add(exportBtn).pad(4);
            footer.add(importBtn).pad(4);
            footer.add(refreshBtn).pad(4);
            footer.add(reprogramBtn).pad(4);
            footer.add(rebuildBtn).pad(4);
            footer.add(closeBtn).pad(4);
            exportBtn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    exportAction.run();
                }
            });
            importBtn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    importAction.run();
                }
            });
            refreshBtn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    refreshAction.run();
                }
            });
            reprogramBtn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    reprogramAction.run();
                }
            });
            rebuildBtn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    rebuildAction.run();
                }
            });
            closeBtn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    closeAction.run();
                }
            });

            // Focus/hotkey highlight: the focused element stands out (cyan) and Alt tints the
            // mnemonics.
            final boolean[] altState = {false};
            window.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions
                    .forever(com.badlogic.gdx.scenes.scene2d.actions.Actions.run(() -> {
                        Actor focused = stage.getKeyboardFocus();
                        scenarioTabBtn.setColor(focused == scenarioTabBtn ? Color.CYAN
                                : (activeTab[0] == 0 ? Color.GREEN : Color.WHITE));
                        programTabBtn.setColor(focused == programTabBtn ? Color.CYAN
                                : (activeTab[0] == 1 ? Color.GREEN : Color.WHITE));
                        configTabBtn.setColor(focused == configTabBtn ? Color.CYAN
                                : (activeTab[0] == 2 ? Color.GREEN : Color.WHITE));
                        exportBtn.setColor(focused == exportBtn ? Color.CYAN : Color.WHITE);
                        importBtn.setColor(focused == importBtn ? Color.CYAN : Color.WHITE);
                        refreshBtn.setColor(focused == refreshBtn ? Color.CYAN : Color.WHITE);
                        reprogramBtn.setColor(focused == reprogramBtn ? Color.CYAN : Color.WHITE);
                        rebuildBtn.setColor(focused == rebuildBtn ? Color.CYAN : Color.WHITE);
                        closeBtn.setColor(focused == closeBtn ? Color.CYAN : Color.WHITE);

                        // The quick reference is only highlighted while it has the keyboard focus.
                        if (focused == refTree) {
                            if (refTree.getSelection().isEmpty()
                                    && refTree.getRootNodes().size > 0) {
                                refTree.getSelection()
                                        .set((Tree.Node) refTree.getRootNodes().get(0));
                            }
                        } else if (!refTree.getSelection().isEmpty()) {
                            refTree.getSelection().clear();
                        }

                        boolean alt = Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.ALT_LEFT)
                                || Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.ALT_RIGHT);
                        if (alt == altState[0]) {
                            return;
                        }
                        altState[0] = alt;
                        exportBtn.setText(alt ? editorButtonAltText(EditorText.EXPORT, 'E')
                                : editorButtonText(EditorText.EXPORT));
                        importBtn.setText(alt ? editorButtonAltText(EditorText.IMPORT, 'I')
                                : editorButtonText(EditorText.IMPORT));
                        refreshBtn
                                .setText(alt ? editorButtonAltText(EditorText.RELOAD_FROM_GAME, 'R')
                                        : editorButtonText(EditorText.RELOAD_FROM_GAME));
                        reprogramBtn
                                .setText(alt ? editorButtonAltText(EditorText.APPLY_PROGRAM, 'P')
                                        : editorButtonText(EditorText.APPLY_PROGRAM));
                        rebuildBtn.setText(alt ? editorButtonAltText(EditorText.REBUILD_WORLD, 'B')
                                : editorButtonText(EditorText.REBUILD_WORLD));
                        closeBtn.setText(alt ? editorButtonAltText(EditorText.CLOSE, 'C')
                                : editorButtonText(EditorText.CLOSE));
                        scenarioTabBtn.setText(alt ? editorTabAltText(EditorText.TAB_SCENARIO, 'a')
                                : editorTabText(EditorText.TAB_SCENARIO));
                        programTabBtn
                                .setText(alt ? editorTabAltText(EditorText.TAB_PROGRAMMING, 'g')
                                        : editorTabText(EditorText.TAB_PROGRAMMING));
                        configTabBtn.setText(alt ? editorTabAltText(EditorText.TAB_CONFIG, 'f')
                                : editorTabText(EditorText.TAB_CONFIG));
                    })));

            Table mainContent = new Table();
            mainContent.add(editorScroll).grow();
            mainContent.add(refScroll).width(340).growY().padLeft(5);

            window.add(tabBar).left().pad(5).row();
            window.add(mainContent).grow().row();
            window.add(errorTable).growX().row();
            window.add(footer).growX().pad(8).row();
            window.add(statusLabel).left().padLeft(10).padBottom(5);

            // Tab cycles focus between the editor, the quick reference and the buttons. Tabs are
            // reached with their hotkeys (Alt+A/G/F or Alt+1/2/3), not with Tab.
            final java.util.List<Actor> focusCycle = new ArrayList<>();
            focusCycle.add(textArea);
            focusCycle.add(refTree);
            focusCycle.add(exportBtn);
            focusCycle.add(importBtn);
            focusCycle.add(refreshBtn);
            focusCycle.add(reprogramBtn);
            focusCycle.add(rebuildBtn);
            focusCycle.add(closeBtn);

            window.addCaptureListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
                @Override
                public boolean keyDown(InputEvent event, int keycode) {
                    if (keycode == com.badlogic.gdx.Input.Keys.TAB) {
                        Actor focused = stage.getKeyboardFocus();
                        int idx = focusCycle.indexOf(focused);
                        boolean shift = Gdx.input
                                .isKeyPressed(com.badlogic.gdx.Input.Keys.SHIFT_LEFT)
                                || Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SHIFT_RIGHT);
                        int dir = shift ? -1 : 1;
                        int next = (idx + dir + focusCycle.size()) % focusCycle.size();
                        stage.setKeyboardFocus(focusCycle.get(next));
                        return true;
                    }
                    Actor focused = stage.getKeyboardFocus();
                    if (focused instanceof com.badlogic.gdx.scenes.scene2d.ui.Button
                            && (keycode == com.badlogic.gdx.Input.Keys.ENTER
                                    || keycode == com.badlogic.gdx.Input.Keys.SPACE)) {
                        ((com.badlogic.gdx.scenes.scene2d.ui.Button) focused)
                                .fire(new ChangeListener.ChangeEvent());
                        return true;
                    }
                    boolean altDown = Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.ALT_LEFT)
                            || Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.ALT_RIGHT);
                    if (keycode == com.badlogic.gdx.Input.Keys.ESCAPE) {
                        closeAction.run();
                        return true;
                    }
                    if (altDown) {
                        if (keycode == com.badlogic.gdx.Input.Keys.NUM_1) {
                            switchTo.accept(0);
                            return true;
                        } else if (keycode == com.badlogic.gdx.Input.Keys.NUM_2) {
                            switchTo.accept(1);
                            return true;
                        } else if (keycode == com.badlogic.gdx.Input.Keys.NUM_3) {
                            switchTo.accept(2);
                            return true;
                        } else if (keycode == com.badlogic.gdx.Input.Keys.A) {
                            switchTo.accept(0);
                            return true;
                        } else if (keycode == com.badlogic.gdx.Input.Keys.G) {
                            switchTo.accept(1);
                            return true;
                        } else if (keycode == com.badlogic.gdx.Input.Keys.F) {
                            switchTo.accept(2);
                            return true;
                        } else if (keycode == com.badlogic.gdx.Input.Keys.E) {
                            exportAction.run();
                            return true;
                        } else if (keycode == com.badlogic.gdx.Input.Keys.I) {
                            importAction.run();
                            return true;
                        } else if (keycode == com.badlogic.gdx.Input.Keys.R) {
                            refreshAction.run();
                            return true;
                        } else if (keycode == com.badlogic.gdx.Input.Keys.P) {
                            reprogramAction.run();
                            return true;
                        } else if (keycode == com.badlogic.gdx.Input.Keys.B) {
                            rebuildAction.run();
                            return true;
                        } else if (keycode == com.badlogic.gdx.Input.Keys.C) {
                            closeAction.run();
                            return true;
                        }
                    }
                    return false;
                }
            });

            ideWindow = window;
            window.setSize(1200, 800);
            window.setPosition((stage.getWidth() - window.getWidth()) / 2,
                    (stage.getHeight() - window.getHeight()) / 2);
            stage.addActor(window);
            stage.setKeyboardFocus(textArea);
        });
    }

    private static String configSectionOrEmpty(String section) {
        return section == null || section.isBlank() ? "configuration {\n}\n" : section;
    }

    private static int offsetOfLine(String text, int line) {
        if (line <= 1) {
            return 0;
        }
        String[] lines = text.split("\n", -1);
        int pos = 0;
        for (int i = 0; i < line - 1 && i < lines.length; i++) {
            pos += lines[i].length() + 1;
        }
        return pos;
    }

    /**
     * Inserts a quick-reference snippet as its own line: moves to the end of the caret's line
     * (never mid-line), adds the snippet on a new line and leaves the caret on the line after it.
     */
    private void insertQuickRef(com.badlogic.gdx.scenes.scene2d.ui.TextArea textArea,
            String snippet) {
        String text = textArea.getText();
        int pos = Math.min(textArea.getCursorPosition(), text.length());
        int lineStart = text.lastIndexOf('\n', Math.max(0, pos - 1)) + 1;
        int lineEnd = text.indexOf('\n', pos);
        if (lineEnd < 0) {
            lineEnd = text.length();
        }
        boolean emptyLine = lineEnd == lineStart;
        String insertion = emptyLine ? snippet + "\n" : "\n" + snippet + "\n";
        String updated = text.substring(0, lineEnd) + insertion + text.substring(lineEnd);
        textArea.setText(updated);
        textArea.setCursorPosition(Math.min(lineEnd + insertion.length(), updated.length()));
        if (stage != null) {
            stage.setKeyboardFocus(textArea);
        }
    }

    public void showFileDialog(String title, Gdx3DFileDialog.Mode mode, String defaultText,
            Consumer<String> onResult) {
        showFileDialog(title, mode, defaultText, new String[] {"json"}, onResult);
    }

    public void showFileDialog(String title, Gdx3DFileDialog.Mode mode, String defaultText,
            String[] extensions, Consumer<String> onResult) {
        showFileDialog(title, mode, defaultText, extensions, onResult, null);
    }

    /**
     * Opens the keyboard-first 3D file dialog (#484).
     *
     * @param onCancel run when the player closes the dialog without picking a file (used by the
     *        boot Load so it returns to the cover instead of leaving an empty world, S4 #777)
     */
    public void showFileDialog(String title, Gdx3DFileDialog.Mode mode, String defaultText,
            String[] extensions, Consumer<String> onResult, Runnable onCancel) {
        Gdx3DFileDialog.show(stage, skin, title, mode, defaultText, extensions, onResult, onCancel);
    }

    public void render(float delta) {
        updateUIData();
        stage.act(delta);
        stage.draw();
    }

    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
        // The cover is painted outside the stage's layout, so it must follow the window itself.
        layoutCoverImage(width, height);
    }

    public void dispose() {
        if (coverTexture != null) {
            coverTexture.dispose();
            coverTexture = null;
        }
        if (stage != null) {
            stage.dispose();
        }
        if (skin != null) {
            skin.dispose();
        }
    }

    /**
     * Editor diagnostics panel: collapses to zero height while hidden so it does not leave a dead
     * band between the editor and the footer. libGDX measures an actor through its preferred size
     * without checking {@link Actor#isVisible()}, so a plain hidden {@code Table} keeps reserving
     * its full size (#652).
     */
    static class CollapsiblePanel extends Table {

        @Override
        public float getPrefHeight() {
            return isVisible() ? super.getPrefHeight() : 0f;
        }

        @Override
        public float getMinHeight() {
            return isVisible() ? super.getMinHeight() : 0f;
        }
    }

}
