package letrain.mvp.impl.gdx3d;

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
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import letrain.mvp.Model;
import letrain.mvp.Model.GameModeMenuOption;
import letrain.utils.FontManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class Gdx3DHud {
    private static final Logger log = LoggerFactory.getLogger(Gdx3DHud.class);

    private final Model model;
    private final Gdx3DView view;
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

    public Gdx3DHud(Model model, Gdx3DView view) {
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
                    } else {
                        window.setResizable(true);
                        window.setMovable(true);
                        window.setBounds(prevX[0], prevY[0], prevW[0], prevH[0]);
                        isMaximized[0] = false;
                    }
                    window.invalidateHierarchy();
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

            Table refScrollContent = new Table();
            refScrollContent.top().left();

            String[][] refs = {
                    { "TRIGGERS", "" },
                    { "  sensor on train enter", "sensor 1 on train enter {\n  \n}" },
                    { "  sensor on train exit", "sensor 1 on train exit {\n  \n}" },
                    { "  sensor on train ent fwd", "sensor 1 on train enter forward {\n  \n}" },
                    { "  sensor on train ext bwd", "sensor 1 on train exit backward {\n  \n}" },
                    { "  fork on train enter", "fork 1 on train enter {\n  \n}" },
                    { "  fork on train exit", "fork 1 on train exit {\n  \n}" },
                    { "  station on train enter", "station 1 on train enter {\n  \n}" },
                    { "  station on train exit", "station 1 on train exit {\n  \n}" },
                    { "  station on tr enter fwd", "station 1 on train enter forward {\n  \n}" },
                    { "  train on crash (fast)", "train 1 on crash {\n  \n}" },
                    { "  train on contact (slow)", "train 1 on contact {\n  \n}" },
                    { "  train on contact fwd", "train 1 on contact forward {\n  \n}" },
                    { "  train on crash bwd", "train 1 on crash backward {\n  \n}" },
                    { "", "" },
                    { "ACTIONS", "" },
                    { "  train set speed", "train 1 set speed 5;" },
                    { "  train set forward", "train 1 set forward;" },
                    { "  train set backward", "train 1 set backward;" },
                    { "  train accelerate", "train 1 accelerate;" },
                    { "  train decelerate", "train 1 decelerate;" },
                    { "  train stop", "train 1 stop;" },
                    { "  train invert", "train 1 invert;" },
                    { "  train load", "train 1 load;" },
                    { "  train unload", "train 1 unload;" },
                    { "  train link back", "train 1 link backward 1;" },
                    { "  train unlink back", "train 1 unlink backward 1;" },
                    { "  train at station ...", "train at station 1 stop;" },
                    { "  fork set straight", "fork 1 set straight;" },
                    { "  fork set curved", "fork 1 set curved;" },
                    { "  fork set flip", "fork 1 set flip;" },
                    { "  semaphore set open", "semaphore 1 set open;" },
                    { "  semaphore set closed", "semaphore 1 set closed;" }
            };

            for (String[] r : refs) {
                if (r[0].isEmpty()) {
                    refScrollContent.add(new Label("", skin)).row();
                    continue;
                }
                if (r[1].isEmpty()) {
                    Label l = new Label(r[0], skin, "monospace");
                    l.setColor(Color.ORANGE);
                    refScrollContent.add(l).left().pad(5).row();
                } else {
                    Table row = new Table();
                    Label l = new Label(r[0], skin, "monospace");
                    // Create a button with the green triangle
                    // Create a button with white/green triangle hover behavior
                    com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle bs = new com.badlogic.gdx.scenes.scene2d.ui.Button.ButtonStyle();
                    bs.up = skin.getDrawable("white-triangle");
                    bs.over = skin.getDrawable("green-triangle");
                    bs.down = skin.getDrawable("green-triangle");
                    com.badlogic.gdx.scenes.scene2d.ui.Button addBtn = new com.badlogic.gdx.scenes.scene2d.ui.Button(
                            bs);

                    addBtn.addListener(new ChangeListener() {
                        @Override
                        public void changed(ChangeEvent event, Actor actor) {
                            insertAtCursor(textArea, r[1] + "\n");
                        }
                    });
                    row.add(addBtn).left().size(16, 16).padRight(10);
                    row.add(l).left().expandX();
                    refScrollContent.add(row).growX().padLeft(10).padRight(5).row();
                }
            }

            ScrollPane refScroll = new ScrollPane(refScrollContent, skin);
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
                    "station 1 on load {\n" +
                            "  train unlink back 1;\n" +
                            "  train set speed 2;\n" +
                            "}\n" +
                            "sensor 5 on enter {\n" +
                            "  train stop;\n" +
                            "}",
                    skin, "monospace");
            examplesContent.setFontScale(1.0f);
            examplesContent.setWrap(true);
            examplesTable.add(examplesTitle).pad(5).row();
            examplesTable.add(examplesContent).growX().pad(5);

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

            sideTable.add(refTable).grow().row();
            sideTable.add(objsTable).grow().row();
            sideTable.add(examplesTable).grow().row();

            // Error Table
            final Table errorTable = new Table();
            errorTable.setBackground(skin.newDrawable("white", Color.MAROON));
            final Label errorLabel = new Label("", skin, "monospace");
            errorTable.add(new Label("ERRORS:", skin, "monospace")).left().padLeft(5).row();
            errorTable.add(errorLabel).left().padLeft(15).padBottom(5);
            errorTable.setVisible(false);

            // Footer
            Table footer = new Table();
            TextButton applyBtn = new TextButton(" APPLY ", skin, "monospace-button");
            TextButton saveBtn = new TextButton(" SAVE ", skin, "monospace-button");
            TextButton loadBtn = new TextButton(" LOAD ", skin, "monospace-button");
            TextButton cancelBtn = new TextButton(" CANCEL ", skin, "monospace-button");
            footer.add(applyBtn).pad(5);
            footer.add(saveBtn).pad(5);
            footer.add(loadBtn).pad(5);
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
                    view.showSaveDialog();
                }
            });

            loadBtn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    view.showLoadDialog();
                    ideWindow = null;
                    ideLogContent = null;
                    ideObjsContent = null;
                    window.remove();
                    model.setMode(letrain.mvp.Model.GameMode.RAILS);
                    view.onGameModeSelected(letrain.mvp.Model.GameMode.RAILS);
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

    public void showFileDialog(String title, String defaultText, Consumer<String> onResult) {
        Gdx.app.postRunnable(() -> {
            Window window = new Window(title, skin);
            window.getTitleTable().pad(10);
            window.pad(20);

            Label label = new Label("Filename:",
                    skin);
            TextField textField = new TextField(
                    defaultText, skin);

            TextButton okBtn = new TextButton("OK", skin);
            TextButton cancelBtn = new TextButton("Cancel", skin);

            okBtn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    onResult.accept(textField.getText());
                    window.remove();
                }
            });

            cancelBtn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
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
