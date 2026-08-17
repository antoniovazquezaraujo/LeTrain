package letrain.mvp.impl.graphic;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
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
import com.badlogic.gdx.scenes.scene2d.ui.Tree;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import letrain.mvp.Model;
import letrain.mvp.Model.GameModeMenuOption;
import letrain.utils.FontManager;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Gdx3DHud {
    private static final Logger log = LoggerFactory.getLogger(Gdx3DHud.class);

    private final Model model;
    private final GraphicPresenter view;
    private final Stage stage;
    private Skin skin;
    private Table menuTable;
    private Label descLabel;
    private Label globalHelpLabel;
    private Label balanceLabel;
    private Label incomeLabel;
    private Label expensesLabel;

    private NotchLever notchLever;
    private ShapeRenderer shapeRenderer;
    private Label ideLogContent;
    private Label ideObjsContent;
    private Window ideWindow;

    public Gdx3DHud(Model model, GraphicPresenter view) {
        this.model = model;
        this.view = view;
        this.stage = new Stage(new ScreenViewport());
        this.shapeRenderer = new ShapeRenderer();
        initUI();
    }

    public Stage getStage() {
        return stage;
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
        textButtonStyle.checked = skin.newDrawable("white", new Color(0.3f, 0.4f, 0.6f, 1f));
        textButtonStyle.over = skin.newDrawable("white", new Color(0.15f, 0.15f, 0.15f, 1f));
        textButtonStyle.font = skin.getFont("default");
        textButtonStyle.fontColor = Color.WHITE;
        textButtonStyle.downFontColor = Color.WHITE;
        textButtonStyle.overFontColor = Color.WHITE;
        textButtonStyle.checkedFontColor = Color.WHITE;
        skin.add("default", textButtonStyle);

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

        TextButton.TextButtonStyle monoButtonStyle = new TextButton.TextButtonStyle(textButtonStyle);
        monoButtonStyle.font = skin.getFont("monospace-font");
        monoButtonStyle.up = skin.newDrawable("white", new Color(0.2f, 0.2f, 0.2f, 1f));
        skin.add("monospace-button", monoButtonStyle);

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

        // Window Background - White Border (Default)
        Pixmap pixWhite = new Pixmap(20, 20, Pixmap.Format.RGBA8888);
        pixWhite.setColor(Color.WHITE);
        pixWhite.fill();
        pixWhite.setColor(Color.BLACK);
        pixWhite.fillRectangle(6, 6, 8, 8);
        com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable windowWhite = new com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable(
                new com.badlogic.gdx.graphics.g2d.NinePatch(new Texture(pixWhite), 6, 6, 6, 6));
        skin.add("window-white", windowWhite);

        // Window Background - Cyan Border (Hover)
        Pixmap pixCyan = new Pixmap(20, 20, Pixmap.Format.RGBA8888);
        pixCyan.setColor(Color.CYAN);
        pixCyan.fill();
        pixCyan.setColor(Color.BLACK);
        pixCyan.fillRectangle(6, 6, 8, 8);
        com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable windowCyan = new com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable(
                new com.badlogic.gdx.graphics.g2d.NinePatch(new Texture(pixCyan), 6, 6, 6, 6));
        skin.add("window-cyan", windowCyan);

        // Window Background - Blue Border (Resize Hover)
        Pixmap pixBlue = new Pixmap(20, 20, Pixmap.Format.RGBA8888);
        pixBlue.setColor(new Color(0.2f, 0.55f, 1.0f, 1f));
        pixBlue.fill();
        pixBlue.setColor(Color.BLACK);
        pixBlue.fillRectangle(6, 6, 8, 8);
        com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable windowBlue = new com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable(
                new com.badlogic.gdx.graphics.g2d.NinePatch(new Texture(pixBlue), 6, 6, 6, 6));
        skin.add("window-blue", windowBlue);

        windowStyle.background = windowWhite;
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
        // SplitPane Style
        SplitPane.SplitPaneStyle splitPaneStyle = new SplitPane.SplitPaneStyle();
        com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable handle = (com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable) skin
                .newDrawable("white", Color.WHITE);
        handle.setMinWidth(6f);
        handle.setMinHeight(6f);
        splitPaneStyle.handle = handle;
        skin.add("default-horizontal", splitPaneStyle);
        skin.add("default-vertical", splitPaneStyle);

        SplitPane.SplitPaneStyle splitPaneStyleHover = new SplitPane.SplitPaneStyle(splitPaneStyle);
        com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable handleHover = (com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable) skin
                .newDrawable("white", Color.CYAN);
        handleHover.setMinWidth(6f);
        handleHover.setMinHeight(6f);
        splitPaneStyleHover.handle = handleHover;
        skin.add("default-horizontal-hover", splitPaneStyleHover);
        skin.add("default-vertical-hover", splitPaneStyleHover);

        skin.add("default", scrollPaneStyle);

        // ProgressBar Style
        com.badlogic.gdx.scenes.scene2d.ui.ProgressBar.ProgressBarStyle progressBarStyle = new com.badlogic.gdx.scenes.scene2d.ui.ProgressBar.ProgressBarStyle();
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

        // Bottom UI Container
        Table mainBottomTable = new Table();
        mainBottomTable.setFillParent(true);
        mainBottomTable.bottom();
        stage.addActor(mainBottomTable);

        menuTable = new Table();
        // menuTable is populated in updateMenuButtons()

        descLabel = new Label("", skin, "small");
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

    public void updateMenuButtons() {
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

            menuTable.add(button).pad(5).height(30);
        }
    }

    public void updateHUD() {
        // Force UI update
        updateUIData();
    }

    public void updateUIData() {
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

        Locomotive loco = model.getSelectedLocomotive();
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
        for (Actor actor : menuTable.getChildren()) {
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
                            String desc = option.gameModeDescription();
                            if (model.getMode() == letrain.mvp.Model.GameMode.TRAINS) {
                                String colorName = model.getSelectedWagonType().name();
                                String colorMarkup = "[#" + model.getSelectedWagonType().getColor().toString() + "]";
                                desc = "Selected: " + colorMarkup + colorName + "[] | " + desc;
                            }
                            descLabel.setText(desc);
                        }
                    }
                }
            }
        }
    }

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

    public void showIDE() {
        if (ideWindow != null) {
            ideWindow.toFront();
            stage.setKeyboardFocus(ideWindow.findActor("editorTextArea")); // Need to name the textArea
            return;
        }
        Gdx.app.postRunnable(() -> {
            if (ideWindow != null)
                return; // double check inside runnable
            final Window window = new Window("LT-IDE v1.1 - LeTrain Integrated Development Environment", skin);
            window.setModal(true);
            window.setMovable(true);
            window.setResizable(true);
            window.padTop(35);

            final com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable windowWhite = skin.get("window-white", com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable.class);
            final com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable windowBlue = skin.get("window-blue", com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable.class);
            final Window.WindowStyle ideWindowStyle = new Window.WindowStyle(skin.get(Window.WindowStyle.class));
            ideWindowStyle.background = windowWhite;
            window.setStyle(ideWindowStyle);

            // Title bar buttons
            Table titleTable = window.getTitleTable();
            TextButton.TextButtonStyle titleBtnStyle = new TextButton.TextButtonStyle(
                    skin.get(TextButton.TextButtonStyle.class));
            titleBtnStyle.font = skin.getFont("monospace-font");
            titleBtnStyle.up = skin.newDrawable("white", Color.DARK_GRAY);
            TextButton closeBtnTitle = new TextButton(" X ", titleBtnStyle);
            TextButton maxBtnTitle = new TextButton(" [ ] ", titleBtnStyle);
            titleTable.add(maxBtnTitle).size(30, 22).right().padRight(5);
            titleTable.add(closeBtnTitle).size(30, 22).right().padRight(10);

            closeBtnTitle.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    ideWindow = null;
                    ideLogContent = null;
                    ideObjsContent = null;
                    window.remove();
                    model.setMode(letrain.mvp.Model.GameMode.RAILS);
                    view.onGameModeSelected(letrain.mvp.Model.GameMode.RAILS);
                }
            });

            final boolean[] isMaximized = { false };
            final float[] prevX = { 0 }, prevY = { 0 }, prevW = { 0 }, prevH = { 0 };
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
                        ideWindowStyle.background = windowWhite;
                        window.setBackground(windowWhite);
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
                        // Use a 20px margin to make it easy to trigger and visible
                        boolean nearBorder = (x <= 20 || x >= window.getWidth() - 20 || y <= 20 || y >= window.getHeight() - 20);
                        if (nearBorder) {
                            ideWindowStyle.background = windowBlue;
                            window.setBackground(windowBlue);
                        } else {
                            ideWindowStyle.background = windowWhite;
                            window.setBackground(windowWhite);
                        }
                    } else {
                        ideWindowStyle.background = windowWhite;
                        window.setBackground(windowWhite);
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

            // Toggle Buttons Bar
            Table toggleBar = new Table();
            final TextButton toggleRef = new TextButton("Ref", skin, "monospace-toggle");
            final TextButton toggleObjs = new TextButton("Objs", skin, "monospace-toggle");
            final TextButton toggleEx = new TextButton("Ex", skin, "monospace-toggle");
            final TextButton toggleLog = new TextButton("Logs", skin, "monospace-toggle");

            // Toggles automatically managed by "toggle" style and its internal listeners

            toggleRef.setChecked(true);
            toggleObjs.setChecked(true);
            toggleEx.setChecked(false);

            toggleBar.add(new Label("Panels: ", skin, "monospace")).padRight(5);
            toggleBar.add(toggleRef).padRight(5);
            toggleBar.add(toggleObjs).padRight(5);
            toggleBar.add(toggleEx).padRight(5);
            toggleBar.add(toggleLog);

            // Editor Area
            final com.badlogic.gdx.scenes.scene2d.ui.TextArea textArea = new com.badlogic.gdx.scenes.scene2d.ui.TextArea(
                    view.getProgram(), skin, "monospace-textarea");
            textArea.setName("editorTextArea");

            // Line numbers in a separate table for perfect row-by-row alignment
            final Table lineNumbersTable = new Table();
            lineNumbersTable.top().right();

            Table editorSubContainer = new Table();
            editorSubContainer.top().left();

            Runnable updateLineNumbers = () -> {
                lineNumbersTable.clearChildren();
                String text = textArea.getText();
                int lines = text.split("\n", -1).length;
                float lineHeight = textArea.getStyle().font.getLineHeight();
                for (int i = 1; i <= lines; i++) {
                    Label l = new Label(String.valueOf(i), skin, "monospace");
                    l.setColor(com.badlogic.gdx.graphics.Color.GRAY);
                    lineNumbersTable.add(l).height(lineHeight).top().right().padRight(10).row();
                }
            };
            textArea.setTextFieldListener((textField, c) -> updateLineNumbers.run());
            updateLineNumbers.run();

            float topPad = textArea.getStyle().background != null ? textArea.getStyle().background.getTopHeight() : 0;
            // Add a small manual adjustment (2px) often helps with multi-line alignment in
            // Scene2D
            editorSubContainer.add(lineNumbersTable).top().padTop(topPad + 2.5f);
            editorSubContainer.add(textArea).grow().top();

            ScrollPane editorScroll = new ScrollPane(editorSubContainer, skin);
            editorScroll.setFadeScrollBars(false);

            // Side Panels
            final Table sideTable = new Table();

            // 1. Reference
            final Table refTable = new Table();
            refTable.setBackground(skin.newDrawable("white", new Color(0.1f, 0.1f, 0.1f, 0.95f)));
            Label refTitle = new Label("QUICK REFERENCE", skin, "monospace");
            refTitle.setColor(Color.YELLOW);
            refTable.add(refTitle).pad(5).row();

            com.badlogic.gdx.scenes.scene2d.ui.Tree.TreeStyle treeStyle = new com.badlogic.gdx.scenes.scene2d.ui.Tree.TreeStyle();
            treeStyle.plus = skin.newDrawable("white", new Color(0.6f, 0.6f, 0.6f, 1f));
            treeStyle.minus = skin.newDrawable("white", new Color(0.6f, 0.6f, 0.6f, 1f));
            Tree refTree = new Tree(treeStyle);
            refTree.setPadding(5f);
            refTree.setIconSpacing(6f, 0);
            refTree.setIndentSpacing(12f);

            // Helper: creates a clickable leaf node that inserts a snippet on click
            java.util.function.BiFunction<String, String, Tree.Node> leaf = (labelText, snippet) -> {
                Label l = new Label("   " + labelText, skin, "monospace");
                Tree.Node n = new Tree.Node(l) {};
                n.setValue(snippet);
                l.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        insertAtCursor(textArea, snippet + "\n");
                        event.stop();
                    }
                });
                return n;
            };
            // Helper: section heading (non-clickable, orange)
            java.util.function.Function<String, Tree.Node> heading = (text) -> {
                Label l = new Label(text, skin, "monospace");
                l.setColor(Color.ORANGE);
                return new Tree.Node(l) {};
            };
            // Helper: creates a parent node (click to expand/collapse, toggles +/-)
            java.util.function.Function<String, Tree.Node> parent = (text) -> {
                Label l = new Label(text, skin, "monospace");
                Tree.Node n = new Tree.Node(l) {};
                l.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
                    @Override
                    public void clicked(InputEvent event, float x, float y) {
                        n.setExpanded(!n.isExpanded());
                        l.setText(n.isExpanded()
                            ? text.replace("[+]", "[-]")
                            : text.replace("[-]", "[+]"));
                        event.stop();
                    }
                });
                return n;
            };

            // ── BUILD TREE ──
            refTree.add(heading.apply("ITINERARY DSL"));
            var itin = parent.apply("  [-] create itinerary");
            itin.add(leaf.apply("template", "create itinerary \"\" {\n  add station #\n}"));
            itin.setExpanded(true);
            refTree.add(itin);

            var addSt = parent.apply("  [+] add station [cmd]");
            addSt.add(leaf.apply("load", "add station # load"));
            addSt.add(leaf.apply("unload", "add station # unload"));
            addSt.add(leaf.apply("reverse", "add station # reverse"));
            addSt.add(leaf.apply("stop", "add station # stop"));
            addSt.add(leaf.apply("wait n", "add station # wait #"));
            addSt.add(leaf.apply("speed n", "add station # speed #"));
            refTree.add(addSt);

            var addSe = parent.apply("  [+] add sensor [cmd]");
            addSe.add(leaf.apply("load", "add sensor # load"));
            addSe.add(leaf.apply("unload", "add sensor # unload"));
            addSe.add(leaf.apply("wait n", "add sensor # wait #"));
            refTree.add(addSe);

            refTree.add(leaf.apply("assign", "assign itinerary \"\" to train #;"));
            refTree.add(leaf.apply("autopilot", "train # set autopilot true;"));

            // ── TRIGGERS ──
            refTree.add(heading.apply("TRIGGERS"));

            var sensor = parent.apply("  [+] sensor");
            var snOn = parent.apply("    [-] on train");
            snOn.add(leaf.apply("enter", "sensor # on train enter {\n  \n}"));
            snOn.add(leaf.apply("exit", "sensor # on train exit {\n  \n}"));
            snOn.add(leaf.apply("enter fwd", "sensor # on train enter forward {\n  \n}"));
            snOn.add(leaf.apply("exit bwd", "sensor # on train exit backward {\n  \n}"));
            snOn.setExpanded(true);
            sensor.add(snOn);
            refTree.add(sensor);

            var station = parent.apply("  [+] station");
            var stOn = parent.apply("    [-] on train");
            stOn.add(leaf.apply("enter", "station # on train enter {\n  \n}"));
            stOn.add(leaf.apply("exit", "station # on train exit {\n  \n}"));
            stOn.add(leaf.apply("enter fwd", "station # on train enter forward {\n  \n}"));
            stOn.add(leaf.apply("exit bwd", "station # on train exit backward {\n  \n}"));
            stOn.setExpanded(true);
            station.add(stOn);
            refTree.add(station);

            var fork = parent.apply("  [+] fork");
            var fkOn = parent.apply("    [+] on train");
            fkOn.add(leaf.apply("enter", "fork # on train enter {\n  \n}"));
            fkOn.add(leaf.apply("exit", "fork # on train exit {\n  \n}"));
            fork.add(fkOn);
            refTree.add(fork);

            var semaphore = parent.apply("  [+] semaphore");
            var smOn = parent.apply("    [+] on train");
            smOn.add(leaf.apply("enter", "semaphore # on train enter {\n  \n}"));
            smOn.add(leaf.apply("exit", "semaphore # on train exit {\n  \n}"));
            semaphore.add(smOn);
            refTree.add(semaphore);

            var trainTrig = parent.apply("  [+] train");
            var trOn = parent.apply("    [-] on");
            trOn.add(leaf.apply("enter", "train # on enter {\n  \n}"));
            trOn.add(leaf.apply("exit", "train # on exit {\n  \n}"));
            trOn.add(leaf.apply("link", "train # on link {\n  \n}"));
            trOn.add(leaf.apply("unlink", "train # on unlink {\n  \n}"));
            trOn.add(leaf.apply("crash", "train # on crash {\n  \n}"));
            trOn.add(leaf.apply("contact", "train # on contact {\n  \n}"));
            trOn.add(leaf.apply("crash fwd", "train # on crash forward {\n  \n}"));
            trOn.add(leaf.apply("contact bwd", "train # on contact backward {\n  \n}"));
            trOn.setExpanded(true);
            trainTrig.add(trOn);
            refTree.add(trainTrig);

            // ── ACTIONS ──
            refTree.add(heading.apply("ACTIONS"));

            var trainAct = parent.apply("  [-] train");
            trainAct.add(leaf.apply("set speed", "train # set speed #;"));
            trainAct.add(leaf.apply("accelerate", "train # accelerate;"));
            trainAct.add(leaf.apply("decelerate", "train # decelerate;"));
            trainAct.add(leaf.apply("stop", "train # stop;"));
            trainAct.add(leaf.apply("invert", "train # invert;"));
            trainAct.add(leaf.apply("set forward", "train # set forward;"));
            trainAct.add(leaf.apply("set backward", "train # set backward;"));
            trainAct.add(leaf.apply("load", "train # load;"));
            trainAct.add(leaf.apply("unload", "train # unload;"));
            trainAct.add(leaf.apply("link", "train # link forward #;"));
            trainAct.add(leaf.apply("unlink", "train # unlink backward #;"));
            trainAct.setExpanded(true);
            refTree.add(trainAct);

            var trainAt = parent.apply("  [+] train at");
            trainAt.add(leaf.apply("station", "train at station # stop;"));
            trainAt.add(leaf.apply("sensor", "train at sensor # stop;"));
            trainAt.add(leaf.apply("fork", "train at fork # stop;"));
            trainAt.add(leaf.apply("semaphore", "train at semaphore # stop;"));
            refTree.add(trainAt);

            var forkAct = parent.apply("  [-] fork");
            forkAct.add(leaf.apply("straight", "fork # set straight;"));
            forkAct.add(leaf.apply("curved", "fork # set curved;"));
            forkAct.add(leaf.apply("flip", "fork # set flip;"));
            forkAct.add(leaf.apply("dir...", "fork # set e;"));
            forkAct.setExpanded(true);
            refTree.add(forkAct);

            var semAct = parent.apply("  [-] semaphore");
            semAct.add(leaf.apply("open", "semaphore # set open;"));
            semAct.add(leaf.apply("closed", "semaphore # set closed;"));
            semAct.setExpanded(true);
            refTree.add(semAct);

            // ── SET NAMES ──
            refTree.add(heading.apply("SET NAMES"));
            refTree.add(leaf.apply("station", "station # set name \"\";"));
            refTree.add(leaf.apply("sensor", "sensor # set name \"\";"));
            refTree.add(leaf.apply("train", "train # set name \"\";"));

            ScrollPane refScroll = new ScrollPane(refTree, skin);
            refTable.add(refScroll).grow().pad(5);

            // 2. Objects
            final Table objsTable = new Table();
            objsTable.setBackground(skin.newDrawable("white", new Color(0.12f, 0.12f, 0.12f, 0.95f)));
            Label objsTitle = new Label("OBJECTS STATUS", skin, "monospace");
            objsTitle.setColor(Color.CYAN);
            final Label objsContent = new Label("", skin, "monospace");
            objsContent.setFontScale(1.0f);
            objsTable.add(objsTitle).pad(5).row();
            ScrollPane objsScroll = new ScrollPane(objsContent, skin);
            objsTable.add(objsScroll).grow().pad(5);

            // 3. Examples
            final Table examplesTable = new Table();
            examplesTable.setBackground(skin.newDrawable("white", new Color(0.14f, 0.14f, 0.14f, 0.95f)));
            Label examplesTitle = new Label("EXAMPLES", skin, "monospace");
            examplesTitle.setColor(Color.GREEN);
            Label examplesContent = new Label(
                    "station 1 on train enter {\n" +
                            "  train unlink backward 1;\n" +
                            "  train set speed 2;\n" +
                            "}\n" +
                            "sensor 5 on train enter {\n" +
                            "  train stop;\n" +
                            "}",
                    skin, "monospace");
            examplesContent.setFontScale(1.0f);
            examplesContent.setWrap(true);
            examplesTable.add(examplesTitle).pad(5).row();
            ScrollPane examplesScroll = new ScrollPane(examplesContent, skin);
            examplesTable.add(examplesScroll).grow().pad(5);

            // 4. Logs
            final Table logTable = new Table();
            logTable.setBackground(skin.newDrawable("white", new Color(0.08f, 0.08f, 0.08f, 0.95f)));
            Label logTitle = new Label("LOGS", skin, "monospace");
            logTitle.setColor(Color.ORANGE);
            final Label logContent = new Label("", skin, "monospace");
            logContent.setWrap(true);
            logTable.add(logTitle).pad(5).row();
            ScrollPane logScroll = new ScrollPane(logContent, skin);
            logTable.add(logScroll).grow().pad(5);

            // Hover focus listeners for all panels and editor
            java.util.function.Function<ScrollPane, com.badlogic.gdx.scenes.scene2d.InputListener> createScrollFocusListener = (sp) -> new com.badlogic.gdx.scenes.scene2d.InputListener() {
                @Override
                public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                    if (pointer == -1 && stage != null) {
                        stage.setScrollFocus(sp);
                    }
                }
            };

            refTable.addListener(createScrollFocusListener.apply(refScroll));
            refScroll.addListener(createScrollFocusListener.apply(refScroll));
            objsTable.addListener(createScrollFocusListener.apply(objsScroll));
            objsScroll.addListener(createScrollFocusListener.apply(objsScroll));
            examplesTable.addListener(createScrollFocusListener.apply(examplesScroll));
            examplesScroll.addListener(createScrollFocusListener.apply(examplesScroll));
            logTable.addListener(createScrollFocusListener.apply(logScroll));
            logScroll.addListener(createScrollFocusListener.apply(logScroll));
            editorScroll.addListener(createScrollFocusListener.apply(editorScroll));
            textArea.addListener(createScrollFocusListener.apply(editorScroll));

            sideTable.add(refTable).grow().row();
            sideTable.add(objsTable).grow().row();
            sideTable.add(examplesTable).grow().row();

            // Error Table
            final Table errorTable = new Table();
            errorTable.setBackground(skin.newDrawable("white", Color.MAROON));
            final Label errorLabel = new Label("", skin, "monospace");
            errorLabel.setWrap(true);
            errorTable.add(new Label("ERRORS:", skin, "monospace")).left().padLeft(5).row();
            errorLabel.addListener(new com.badlogic.gdx.scenes.scene2d.utils.ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    String text = textArea.getText();
                    String[] errorLines = errorLabel.getText().toString().split("\n");
                    for (String err : errorLines) {
                        java.util.regex.Matcher m = java.util.regex.Pattern.compile("line (\\d+):(\\d+)").matcher(err);
                        if (m.find()) {
                            int lineNum = Integer.parseInt(m.group(1));
                            int colNum = Integer.parseInt(m.group(2));
                            // Convert line:col to character position in the text area
                            int pos = 0;
                            String[] srcLines = text.split("\n", -1);
                            for (int i = 0; i < Math.min(lineNum - 1, srcLines.length); i++) {
                                pos += srcLines[i].length() + 1; // +1 for the newline
                            }
                            pos += Math.max(0, colNum - 1);
                            textArea.setCursorPosition(Math.min(pos, text.length()));
                            textArea.getStage().setKeyboardFocus(textArea);
                            break;
                        }
                    }
                }
            });
            ScrollPane errorScroll = new ScrollPane(errorLabel, skin);
            errorScroll.setFadeScrollBars(false);
            errorScroll.setScrollingDisabled(true, false);
            errorTable.add(errorScroll).left().padLeft(15).padBottom(5).growX().minHeight(100f);
            errorTable.setVisible(false);

            // Footer
            Table footer = new Table();
            final TextButton applyBtn = new TextButton(" APPLY ", skin, "monospace-button");
            final TextButton saveBtn = new TextButton(" SAVE ", skin, "monospace-button");
            final TextButton loadBtn = new TextButton(" LOAD ", skin, "monospace-button");
            final TextButton okBtn = new TextButton(" OK ", skin, "monospace-button");
            okBtn.setColor(Color.GREEN);
            final TextButton cancelBtn = new TextButton(" CANCEL ", skin, "monospace-button");
            footer.add(applyBtn).pad(5);
            footer.add(saveBtn).pad(5);
            footer.add(loadBtn).pad(5);
            footer.add(okBtn).pad(5);
            footer.add(cancelBtn).pad(5);

            // ASSEMBLY & VISIBILITY SYNC
            Table mainContent = new Table();

            ChangeListener visibilitySync = new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    mainContent.clear();

                    refTable.setVisible(toggleRef.isChecked());
                    objsTable.setVisible(toggleObjs.isChecked());
                    examplesTable.setVisible(toggleEx.isChecked());
                    logTable.setVisible(toggleLog.isChecked());

                    if (toggleObjs.isChecked()) {
                        objsContent.setText(model.getGameObjectsReport());
                    }
                    if (toggleLog.isChecked()) {
                        logContent.setText(String.join("\n", model.getEventLogManager().getEntries()));
                    }

                    // Side panels stack logic
                    List<Actor> visibleSidePanels = new ArrayList<>();
                    if (toggleRef.isChecked())
                        visibleSidePanels.add(refTable);
                    if (toggleObjs.isChecked())
                        visibleSidePanels.add(objsTable);
                    if (toggleEx.isChecked())
                        visibleSidePanels.add(examplesTable);
                    if (toggleLog.isChecked())
                        visibleSidePanels.add(logTable);

                    if (visibleSidePanels.isEmpty()) {
                        mainContent.add(editorScroll).grow();
                    } else {
                        // Create Side Component (Nested vertical split panes)
                        Actor sideComponent = visibleSidePanels.get(visibleSidePanels.size() - 1);
                        for (int i = visibleSidePanels.size() - 2; i >= 0; i--) {
                            final SplitPane sp = new SplitPane(visibleSidePanels.get(i), sideComponent, true, skin,
                                    "default-vertical");
                            sp.setSplitAmount(0.5f);
                            sp.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
                                @Override
                                public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                                    sp.setStyle(skin.get("default-vertical-hover", SplitPane.SplitPaneStyle.class));
                                }

                                @Override
                                public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                                    sp.setStyle(skin.get("default-vertical", SplitPane.SplitPaneStyle.class));
                                }
                            });
                            sideComponent = sp;
                        }

                        final SplitPane mainSplit = new SplitPane(editorScroll, sideComponent, false, skin,
                                "default-horizontal");
                        mainSplit.setSplitAmount(0.75f);
                        mainSplit.addListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
                            @Override
                            public void enter(InputEvent event, float x, float y, int pointer, Actor fromActor) {
                                mainSplit
                                        .setStyle(skin.get("default-horizontal-hover", SplitPane.SplitPaneStyle.class));
                            }

                            @Override
                            public void exit(InputEvent event, float x, float y, int pointer, Actor toActor) {
                                mainSplit.setStyle(skin.get("default-horizontal", SplitPane.SplitPaneStyle.class));
                            }
                        });
                        mainContent.add(mainSplit).grow();
                    }
                    window.invalidateHierarchy();
                }
            };

            toggleRef.addListener(visibilitySync);
            toggleObjs.addListener(visibilitySync);
            toggleEx.addListener(visibilitySync);
            toggleLog.addListener(visibilitySync);
            visibilitySync.changed(null, null);

            window.add(toggleBar).right().padRight(10).padBottom(5).row();
            window.add(mainContent).grow().row();
            window.add(errorTable).growX().row();
            window.add(footer).growX().pad(10);

            // Actions
            applyBtn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    List<String> errors = model.setProgram(textArea.getText());
                    if (errors != null && !errors.isEmpty()) {
                        errorLabel.setText(String.join("\n", errors));
                        errorTable.setVisible(true);
                    } else {
                        errorTable.setVisible(false);
                    }
                }
            });

            saveBtn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    model.setProgram(textArea.getText());
                    view.showSaveDialog();
                }
            });

            loadBtn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    view.showLoadDialog();
                }
            });

            okBtn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    List<String> errors = model.setProgram(textArea.getText());
                    if (errors != null && !errors.isEmpty()) {
                        errorLabel.setText(String.join("\n", errors));
                        errorTable.setVisible(true);
                    } else {
                        errorTable.setVisible(false);
                        ideWindow = null;
                        ideLogContent = null;
                        ideObjsContent = null;
                        window.remove();
                        model.setMode(letrain.mvp.Model.GameMode.RAILS);
                        view.onGameModeSelected(letrain.mvp.Model.GameMode.RAILS);
                    }
                }
            });

            cancelBtn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    ideWindow = null;
                    ideLogContent = null;
                    ideObjsContent = null;
                    window.remove();
                    model.setMode(letrain.mvp.Model.GameMode.RAILS);
                    view.onGameModeSelected(letrain.mvp.Model.GameMode.RAILS);
                }
            });

            window.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.forever(
                com.badlogic.gdx.scenes.scene2d.actions.Actions.run(new Runnable() {
                    @Override
                    public void run() {
                        boolean altDown = com.badlogic.gdx.Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.ALT_LEFT) 
                                       || com.badlogic.gdx.Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.ALT_RIGHT);
                        applyBtn.setText(altDown ? " <A>PPLY " : " APPLY ");
                        saveBtn.setText(altDown ? " <S>AVE " : " SAVE ");
                        loadBtn.setText(altDown ? " <L>OAD " : " LOAD ");
                        okBtn.setText(altDown ? " <O>K " : " OK ");
                        cancelBtn.setText(altDown ? " <C>ANCEL " : " CANCEL ");
                    }
                })
            ));

            window.addCaptureListener(new com.badlogic.gdx.scenes.scene2d.InputListener() {
                @Override
                public boolean keyDown(InputEvent event, int keycode) {
                    boolean altDown = com.badlogic.gdx.Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.ALT_LEFT) 
                                   || com.badlogic.gdx.Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.ALT_RIGHT);
                    if (altDown) {
                        if (keycode == com.badlogic.gdx.Input.Keys.A) {
                            applyBtn.fire(new ChangeListener.ChangeEvent());
                            return true;
                        } else if (keycode == com.badlogic.gdx.Input.Keys.S) {
                            saveBtn.fire(new ChangeListener.ChangeEvent());
                            return true;
                        } else if (keycode == com.badlogic.gdx.Input.Keys.L) {
                            loadBtn.fire(new ChangeListener.ChangeEvent());
                            return true;
                        } else if (keycode == com.badlogic.gdx.Input.Keys.O) {
                            okBtn.fire(new ChangeListener.ChangeEvent());
                            return true;
                        } else if (keycode == com.badlogic.gdx.Input.Keys.C) {
                            cancelBtn.fire(new ChangeListener.ChangeEvent());
                            return true;
                        }
                    }
                    return false;
                }
            });

            ideWindow = window;
            ideLogContent = logContent;
            ideObjsContent = objsContent;

            window.setSize(1200, 800);
            window.setPosition((stage.getWidth() - window.getWidth()) / 2,
                    (stage.getHeight() - window.getHeight()) / 2);
            stage.addActor(window);
            stage.setKeyboardFocus(textArea);
        });
    }

    public void updateIDE() {
        if (ideWindow == null || !ideWindow.isVisible() || ideWindow.getStage() == null)
            return;

        if (ideObjsContent != null) {
            ideObjsContent.setText(model.getGameObjectsReport());
        }

        if (ideLogContent != null) {
            List<String> entries = model.getEventLogManager().getEntries();
            int start = Math.max(0, entries.size() - 20);
            List<String> last20 = entries.subList(start, entries.size());
            ideLogContent.setText(String.join("\n", last20));
        }
    }

    private void insertAtCursor(com.badlogic.gdx.scenes.scene2d.ui.TextArea textArea, String insertion) {
        int pos = textArea.getCursorPosition();
        String text = textArea.getText();
        String before = text.substring(0, pos);
        String after = text.substring(pos);
        textArea.setText(before + insertion + after);
        textArea.setCursorPosition(pos + insertion.length());
        if (stage != null)
            stage.setKeyboardFocus(textArea);
    }

    public void showFileDialog(String title, com.kotcrab.vis.ui.widget.file.FileChooser.Mode mode, String defaultText, Consumer<String> onResult) {
        Gdx.app.postRunnable(() -> {
            if (!com.kotcrab.vis.ui.VisUI.isLoaded()) {
                com.kotcrab.vis.ui.VisUI.load();
                com.kotcrab.vis.ui.widget.file.FileChooser.setDefaultPrefsName("letrain.filechooser");
                
                // Override VisUI fonts with our monospace font to keep aesthetic consistency
                com.badlogic.gdx.scenes.scene2d.ui.Skin visSkin = com.kotcrab.vis.ui.VisUI.getSkin();
                com.badlogic.gdx.graphics.g2d.BitmapFont font = letrain.utils.FontManager.loadFont("JuliaMono-Regular", 18);
                
                for (com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle style : visSkin.getAll(com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle.class).values()) {
                    style.font = font;
                }
                for (com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle style : visSkin.getAll(com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle.class).values()) {
                    style.font = font;
                }
                for (com.badlogic.gdx.scenes.scene2d.ui.Window.WindowStyle style : visSkin.getAll(com.badlogic.gdx.scenes.scene2d.ui.Window.WindowStyle.class).values()) {
                    style.titleFont = font;
                }
                for (com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldStyle style : visSkin.getAll(com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldStyle.class).values()) {
                    style.font = font;
                }
                for (com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle style : visSkin.getAll(com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle.class).values()) {
                    style.font = font;
                }
                for (com.badlogic.gdx.scenes.scene2d.ui.SelectBox.SelectBoxStyle style : visSkin.getAll(com.badlogic.gdx.scenes.scene2d.ui.SelectBox.SelectBoxStyle.class).values()) {
                    style.font = font;
                }
            }
            com.kotcrab.vis.ui.widget.file.FileChooser fileChooser = new com.kotcrab.vis.ui.widget.file.FileChooser(
                    title, mode);
            fileChooser.setSelectionMode(com.kotcrab.vis.ui.widget.file.FileChooser.SelectionMode.FILES);
            
            // Only show .dat files by default
            com.kotcrab.vis.ui.widget.file.FileTypeFilter filter = new com.kotcrab.vis.ui.widget.file.FileTypeFilter(true);
            filter.addRule("Data files (*.dat)", "dat");
            fileChooser.setFileTypeFilter(filter);
            
            fileChooser.setDirectory(Gdx.files.local("."));
            
            fileChooser.setListener(new com.kotcrab.vis.ui.widget.file.FileChooserAdapter() {
                @Override
                public void selected(com.badlogic.gdx.utils.Array<com.badlogic.gdx.files.FileHandle> files) {
                    if (files.size > 0) {
                        onResult.accept(files.get(0).file().getAbsolutePath());
                    }
                }
            });
            
            fileChooser.setSize(750, 500);
            fileChooser.centerWindow();
            stage.addActor(fileChooser.fadeIn());
        });
    }

    public void render(float delta) {
        updateUIData();
        stage.act(delta);
        stage.draw();
    }

    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    public void dispose() {
        if (stage != null)
            stage.dispose();
        if (skin != null)
            skin.dispose();
        if (shapeRenderer != null)
            shapeRenderer.dispose();
    }

    private class NotchLever extends Actor {
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

            com.badlogic.gdx.graphics.GL20 gl = Gdx.gl;
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
            // We need a font for labels. Gdx3DHud could have a reference to a font or use the skin.
            BitmapFont font = skin.getFont("default");
            float oldScaleX = font.getScaleX();
            float oldScaleY = font.getScaleY();
            font.getData().setScale(0.5f);

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

            Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_BLEND);

            batch.begin();
        }
    }
}
