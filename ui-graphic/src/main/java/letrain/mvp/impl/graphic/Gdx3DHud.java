package letrain.mvp.impl.graphic;

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
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import letrain.mvp.Model;
import letrain.mvp.Model.GameModeMenuOption;
import letrain.utils.FontManager;
import letrain.vehicle.rail.impl.Locomotive;
import letrain.vehicle.rail.impl.Train;

public class Gdx3DHud {

    private final Model model;
    private final GraphicPresenter view;
    private final Stage stage;
    private Skin skin;
    private Table menuTable;
    private Label descLabel;
    private Label globalHelpLabel;
    private Label recDot;
    private Label balanceLabel;
    private Label incomeLabel;
    private Label expensesLabel;

    private NotchLever notchLever;
    private ShapeRenderer shapeRenderer;
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

    /** True while the PROGRAM editor window is open (game shortcuts must be ignored then). */
    public boolean isIDEOpen() {
        return ideWindow != null;
    }

    private boolean hasScenarioToExport() {
        return model.getCommandJournal() != null
                && !model.getCommandJournal().appliedEntries().isEmpty();
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

        TextButton.TextButtonStyle monoButtonStyle =
                new TextButton.TextButtonStyle(textButtonStyle);
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
        stage.addActor(mainTopTable);

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
                "[LIGHT_GRAY][ALT+⏶⏷/kj / MOUSE WHEEL]: ZOOM | [ALT+⏴⏵/hl]: ROTATE CAMERA | [Z]: CHANGE CAMERA VIEW | [R]: RECORD[]",
                skin, "tiny");
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
        Label.LabelStyle incomeStyle =
                new Label.LabelStyle(skin.get("small", Label.LabelStyle.class));
        incomeStyle.fontColor = com.badlogic.gdx.graphics.Color.GREEN;
        incomeLabel = new Label("+ $0", incomeStyle);

        Label.LabelStyle expensesStyle =
                new Label.LabelStyle(skin.get("small", Label.LabelStyle.class));
        expensesStyle.fontColor = com.badlogic.gdx.graphics.Color.RED;
        expensesLabel = new Label("- $0", expensesStyle);

        Label.LabelStyle balanceStyle =
                new Label.LabelStyle(skin.get("medium", Label.LabelStyle.class));
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
            return "[WHITE]" + prefix + "[]" + "[CYAN]" + hotkeyChar + "[]" + "[WHITE]" + suffix
                    + "[]";
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
        // Blinking REC indicator while the command journal records.
        if (recDot != null) {
            boolean recording = model.getCommandJournal() != null
                    && model.getCommandJournal().isRecording() && model.isSimulationPaused();
            recDot.setVisible(recording && (System.currentTimeMillis() / 500) % 2 == 0);
        }
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
                                String colorMarkup = "[#"
                                        + model.getSelectedWagonType().getColor() + "]";
                                desc = "Selected: " + colorMarkup + colorName + "[] | " + desc;
                            }
                            descLabel.setText(desc);
                        }
                    }
                }
            }
        }

        if (model.getMode() == letrain.mvp.Model.GameMode.COMMAND) {
            String desc = ":" + model.getCommandText() + "_";
            if (model.getCommandError() != null && !model.getCommandError().isEmpty()) {
                desc += " [RED][ERROR: " + model.getCommandError() + "][]";
            }
            descLabel.setText(desc);
        }
    }

    public void showMessage(String title, String message) {
        Gdx.app.postRunnable(() -> {
            com.badlogic.gdx.scenes.scene2d.ui.Dialog dialog =
                    new com.badlogic.gdx.scenes.scene2d.ui.Dialog(title, skin) {
                        @Override
                        protected void result(Object object) {
                            this.remove();
                        }
                    };
            dialog.text(message);
            dialog.button("OK");
            dialog.pack();
            dialog.setPosition((stage.getWidth() - dialog.getWidth()) / 2,
                    (stage.getHeight() - dialog.getHeight()) / 2);
            stage.addActor(dialog);
        });
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

            // ---- State: three tabs (Scenario / Program / Config)
            final String[] scenarioBuffer = {view.getScenarioText()};
            final String[] programBuffer = {letrain.command.ScenarioFile.programSection(view.getProgram())};
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
                    ? textArea.getStyle().background.getTopHeight() : 0;
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
                letrain.command.GrammarReference.Group group = activeTab[0] == 1
                        ? letrain.command.GrammarReference.Group.PROGRAM
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
                            if (refNode.expanded) {
                                n.setExpanded(true);
                            }
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
                                ? (Tree.Node) refTree.getRootNodes().get(0) : null;
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
                                ? (Tree.Node) refTree.getRootNodes().get(0) : null;
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

                @Override
                public boolean keyDown(InputEvent event, int keycode) {
                    com.badlogic.gdx.utils.Array<Tree.Node> selection =
                            refTree.getSelection().toArray();
                    Tree.Node current = selection.size > 0 ? selection.get(0) : null;
                    if (keycode == com.badlogic.gdx.Input.Keys.DOWN) {
                        Tree.Node next = nextVisible(current);
                        if (next != null) {
                            refTree.getSelection().set(next);
                        }
                        return true;
                    } else if (keycode == com.badlogic.gdx.Input.Keys.UP) {
                        Tree.Node prev = prevVisible(current);
                        if (prev != null) {
                            refTree.getSelection().set(prev);
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
                                l.setText(current.isExpanded() ? text.replace("[+]", "[-]")
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
            final TextButton scenarioTabBtn = new TextButton(" Scenario ", skin, "monospace-button");
            final TextButton programTabBtn = new TextButton(" Program ", skin, "monospace-button");
            final TextButton configTabBtn = new TextButton(" Config ", skin, "monospace-button");
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
            final Table errorTable = new Table();
            errorTable.setBackground(skin.newDrawable("white", Color.MAROON));
            errorTable.add(new Label("ERRORS (click to jump):", skin, "monospace")).left().padLeft(5)
                    .row();
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
                    TextButton b = new TextButton(d.line() + ":" + d.col() + ": " + d.message(), skin,
                            "monospace-button");
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

            // ---- Actions
            final Runnable saveAction = () -> {
                saveActive.run();
                view.showSaveDialog();
            };
            final Runnable loadAction = () -> view.showLoadDialog();
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
                view.onEditCommands(letrain.command.ScenarioFile.programSectionBody(programBuffer[0]));
            };
            final Runnable exportAction = () -> {
                String full = composeFull.get();
                letrain.command.ScenarioCompiler.Result result =
                        letrain.command.ScenarioCompiler.compile(full);
                showDiagnostics.accept(result);
                showFileDialog("Export Scenario",
                        com.kotcrab.vis.ui.widget.file.FileChooser.Mode.SAVE, "scenario.ltr",
                        new String[] {"ltr"}, path -> {
                            if (path != null && !path.trim().isEmpty()) {
                                view.onExportScenarioText(new java.io.File(path), full);
                            }
                        });
            };
            final Runnable importAction = () -> showFileDialog("Open Scenario",
                    com.kotcrab.vis.ui.widget.file.FileChooser.Mode.OPEN, "scenario.ltr",
                    new String[] {"ltr"}, path -> {
                        if (path == null || path.trim().isEmpty()) {
                            return;
                        }
                        try {
                            String fileText = java.nio.file.Files
                                    .readString(new java.io.File(path).toPath());
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
                    view.onPlayScenarioText(full);
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

            // ---- Footer
            Table footer = new Table();
            TextButton saveBtn = new TextButton(" SAVE ", skin, "monospace-button");
            TextButton loadBtn = new TextButton(" LOAD ", skin, "monospace-button");
            TextButton exportBtn = new TextButton(" EXPORT ", skin, "monospace-button");
            TextButton importBtn = new TextButton(" IMPORT ", skin, "monospace-button");
            TextButton refreshBtn = new TextButton(" REFRESH ", skin, "monospace-button");
            TextButton reprogramBtn = new TextButton(" REPROGRAM ", skin, "monospace-button");
            TextButton rebuildBtn = new TextButton(" REBUILD ", skin, "monospace-button");
            TextButton closeBtn = new TextButton(" CLOSE ", skin, "monospace-button");
            footer.add(saveBtn).pad(4);
            footer.add(loadBtn).pad(4);
            footer.add(exportBtn).pad(4);
            footer.add(importBtn).pad(4);
            footer.add(refreshBtn).pad(4);
            footer.add(reprogramBtn).pad(4);
            footer.add(rebuildBtn).pad(4);
            footer.add(closeBtn).pad(4);
            saveBtn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    saveAction.run();
                }
            });
            loadBtn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    loadAction.run();
                }
            });
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

            // Focus/hotkey highlight: the focused element stands out (cyan) and Alt tints the mnemonics.
            final boolean[] altState = {false};
            window.addAction(com.badlogic.gdx.scenes.scene2d.actions.Actions.forever(
                    com.badlogic.gdx.scenes.scene2d.actions.Actions.run(() -> {
                        Actor focused = stage.getKeyboardFocus();
                        scenarioTabBtn.setColor(focused == scenarioTabBtn ? Color.CYAN
                                : (activeTab[0] == 0 ? Color.GREEN : Color.WHITE));
                        programTabBtn.setColor(focused == programTabBtn ? Color.CYAN
                                : (activeTab[0] == 1 ? Color.GREEN : Color.WHITE));
                        configTabBtn.setColor(focused == configTabBtn ? Color.CYAN
                                : (activeTab[0] == 2 ? Color.GREEN : Color.WHITE));
                        saveBtn.setColor(focused == saveBtn ? Color.CYAN : Color.WHITE);
                        loadBtn.setColor(focused == loadBtn ? Color.CYAN : Color.WHITE);
                        exportBtn.setColor(focused == exportBtn ? Color.CYAN : Color.WHITE);
                        importBtn.setColor(focused == importBtn ? Color.CYAN : Color.WHITE);
                        refreshBtn.setColor(focused == refreshBtn ? Color.CYAN : Color.WHITE);
                        reprogramBtn.setColor(focused == reprogramBtn ? Color.CYAN : Color.WHITE);
                        rebuildBtn.setColor(focused == rebuildBtn ? Color.CYAN : Color.WHITE);
                        closeBtn.setColor(focused == closeBtn ? Color.CYAN : Color.WHITE);

                        boolean alt = Gdx.input
                                .isKeyPressed(com.badlogic.gdx.Input.Keys.ALT_LEFT)
                                || Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.ALT_RIGHT);
                        if (alt == altState[0]) {
                            return;
                        }
                        altState[0] = alt;
                        saveBtn.setText(alt ? " [#42A5F5]S[]AVE " : " SAVE ");
                        loadBtn.setText(alt ? " [#42A5F5]L[]OAD " : " LOAD ");
                        exportBtn.setText(alt ? " [#42A5F5]E[]XPORT " : " EXPORT ");
                        importBtn.setText(alt ? " [#42A5F5]I[]MPORT " : " IMPORT ");
                        refreshBtn.setText(alt ? " [#42A5F5]R[]EFRESH " : " REFRESH ");
                        reprogramBtn.setText(alt ? " RE[#42A5F5]P[]ROGRAM " : " REPROGRAM ");
                        rebuildBtn.setText(alt ? " RE[#42A5F5]B[]UILD " : " REBUILD ");
                        closeBtn.setText(alt ? " [#42A5F5]C[]LOSE " : " CLOSE ");
                        scenarioTabBtn.setText(alt ? " Scen[#42A5F5]a[]rio " : " Scenario ");
                        programTabBtn.setText(alt ? " Pro[#42A5F5]g[]ram " : " Program ");
                        configTabBtn.setText(alt ? " Con[#42A5F5]f[]ig " : " Config ");
                    })));

            Table mainContent = new Table();
            mainContent.add(editorScroll).grow();
            mainContent.add(refScroll).width(340).growY().padLeft(5);

            window.add(tabBar).left().pad(5).row();
            window.add(mainContent).grow().row();
            window.add(errorTable).growX().row();
            window.add(footer).growX().pad(8).row();
            window.add(statusLabel).left().padLeft(10).padBottom(5);

            // Tab cycles focus between the editor, the tabs, the quick reference and the buttons.
            final java.util.List<Actor> focusCycle = new ArrayList<>();
            focusCycle.add(textArea);
            focusCycle.add(scenarioTabBtn);
            focusCycle.add(programTabBtn);
            focusCycle.add(configTabBtn);
            focusCycle.add(refTree);
            focusCycle.add(saveBtn);
            focusCycle.add(loadBtn);
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
                        } else if (keycode == com.badlogic.gdx.Input.Keys.S) {
                            saveAction.run();
                            return true;
                        } else if (keycode == com.badlogic.gdx.Input.Keys.L) {
                            loadAction.run();
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
     * Inserts a quick-reference snippet as its own line: moves to the end of the caret's line (never
     * mid-line), adds the snippet on a new line and leaves the caret on the line after it.
     */
    private void insertQuickRef(com.badlogic.gdx.scenes.scene2d.ui.TextArea textArea, String snippet) {
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

    public void showFileDialog(String title, com.kotcrab.vis.ui.widget.file.FileChooser.Mode mode,
            String defaultText, Consumer<String> onResult) {
        showFileDialog(title, mode, defaultText, new String[] {"json"}, onResult);
    }

    public void showFileDialog(String title, com.kotcrab.vis.ui.widget.file.FileChooser.Mode mode,
            String defaultText, String[] extensions, Consumer<String> onResult) {
        Gdx.app.postRunnable(() -> {
            if (!com.kotcrab.vis.ui.VisUI.isLoaded()) {
                com.kotcrab.vis.ui.VisUI.load();
                com.kotcrab.vis.ui.widget.file.FileChooser
                        .setDefaultPrefsName("letrain.filechooser");

                // Override VisUI fonts with our monospace font to keep aesthetic
                // consistency
                com.badlogic.gdx.scenes.scene2d.ui.Skin visSkin =
                        com.kotcrab.vis.ui.VisUI.getSkin();
                com.badlogic.gdx.graphics.g2d.BitmapFont font =
                        letrain.utils.FontManager.loadFont("JuliaMono-Regular", 18);

                for (com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle style : visSkin
                        .getAll(com.badlogic.gdx.scenes.scene2d.ui.Label.LabelStyle.class)
                        .values()) {
                    style.font = font;
                }
                for (com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle style : visSkin
                        .getAll(com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle.class)
                        .values()) {
                    style.font = font;
                }
                for (com.badlogic.gdx.scenes.scene2d.ui.Window.WindowStyle style : visSkin
                        .getAll(com.badlogic.gdx.scenes.scene2d.ui.Window.WindowStyle.class)
                        .values()) {
                    style.titleFont = font;
                }
                for (com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldStyle style : visSkin
                        .getAll(com.badlogic.gdx.scenes.scene2d.ui.TextField.TextFieldStyle.class)
                        .values()) {
                    style.font = font;
                }
                for (com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle style : visSkin
                        .getAll(com.badlogic.gdx.scenes.scene2d.ui.List.ListStyle.class).values()) {
                    style.font = font;
                }
                for (com.badlogic.gdx.scenes.scene2d.ui.SelectBox.SelectBoxStyle style : visSkin
                        .getAll(com.badlogic.gdx.scenes.scene2d.ui.SelectBox.SelectBoxStyle.class)
                        .values()) {
                    style.font = font;
                }
            }
            com.kotcrab.vis.ui.widget.file.FileChooser fileChooser =
                    new com.kotcrab.vis.ui.widget.file.FileChooser(title, mode);
            fileChooser.setSelectionMode(
                    com.kotcrab.vis.ui.widget.file.FileChooser.SelectionMode.FILES);

            // Filter by the requested extensions (json for savegames, ltr for scenarios).
            com.kotcrab.vis.ui.widget.file.FileTypeFilter filter =
                    new com.kotcrab.vis.ui.widget.file.FileTypeFilter(true);
            for (String ext : extensions) {
                filter.addRule(ext.toUpperCase() + " files (*." + ext + ")", ext);
            }
            fileChooser.setFileTypeFilter(filter);

            fileChooser.setDirectory(Gdx.files.local("."));

            fileChooser.setListener(new com.kotcrab.vis.ui.widget.file.FileChooserAdapter() {
                @Override
                public void selected(
                        com.badlogic.gdx.utils.Array<com.badlogic.gdx.files.FileHandle> files) {
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
        if (stage != null) {
            stage.dispose();
        }
        if (skin != null) {
            skin.dispose();
        }
        if (shapeRenderer != null) {
            shapeRenderer.dispose();
        }
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
            shapeRenderer.rect(x - 40, y - 20, 65, h + 40); // Even taller to fully enclose labels 0
                                                            // and 10

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
                com.badlogic.gdx.graphics.g2d.GlyphLayout layout =
                        new com.badlogic.gdx.graphics.g2d.GlyphLayout(font, txt);
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
