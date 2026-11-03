package letrain.mvp.impl.graphic;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Dialog;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import letrain.construction.PlannedWork;
import letrain.construction.WorkQuote;
import letrain.mvp.Model;

/**
 * Scene2D Planning window of the 3D client.
 *
 * <p>
 * It is the view counterpart of the 2D {@code ConstructionPlansDialog}: it shows the existing
 * planned works, keeps the selected row across refreshes and translates the action buttons into
 * {@link GraphicPresenter} calls. It deliberately offers <b>no Add/Edit</b>: the 3D route preview
 * is out of scope, so the window only manages works created elsewhere (2D or a loaded game).
 *
 * <p>
 * The table formatting lives in {@link PlanningTableModel}; this class only builds actors and wires
 * input. All decisions about lifecycle, cash and quotes stay in the presenter.
 */
final class Gdx3DPlanningWindow {

    private static final float WINDOW_MAX_WIDTH = 1180f;
    private static final float WINDOW_MAX_HEIGHT = 620f;
    private static final Color ROW_BG = new Color(0.12f, 0.12f, 0.12f, 1f);

    private final Stage stage;
    private final Skin skin;
    private final Model model;
    private final GraphicPresenter view;

    private Window window;
    private boolean openScheduled;
    private boolean disposed;
    private Actor blocker;
    private Table rowsTable;
    private ScrollPane rowsScroll;
    private Label totalLabel;
    private TextButton runButton;
    private TextButton pauseButton;
    private TextButton resumeButton;
    private TextButton cancelButton;
    private TextButton deleteButton;
    private TextButton quotesButton;

    private List<PlannedWork> works = List.of();
    private final Map<Integer, Table> rowButtons = new LinkedHashMap<>();
    private int selectedId = -1;
    private String lastSignature = "";
    private Model.GameMode returnMode;
    private Dialog quoteDialog;
    private Actor quoteBlocker;

    Gdx3DPlanningWindow(Stage stage, Skin skin, Model model, GraphicPresenter view) {
        this.stage = stage;
        this.skin = skin;
        this.model = model;
        this.view = view;
    }

    boolean isOpen() {
        return window != null || openScheduled;
    }

    /** Creates and centres the window on the first call; brings it back to front afterwards. */
    void open() {
        if (disposed) {
            return;
        }
        if (window != null) {
            window.toFront();
            return;
        }
        if (openScheduled) {
            return;
        }
        openScheduled = true;
        Gdx.app.postRunnable(this::build);
    }

    void toFront() {
        if (window != null) {
            window.toFront();
        }
    }

    private void build() {
        openScheduled = false;
        if (disposed || window != null) {
            return;
        }
        returnMode = model.getMode() == Model.GameMode.PLANNED_WORKS ? model.getPreviousMode()
                : model.getMode();
        works = List.copyOf(model.getPlannedWorks().works());
        selectedId = works.isEmpty() ? -1 : works.get(0).id();

        window = new Window("Planning", skin.get("dialog", Window.WindowStyle.class));
        window.setModal(true);
        window.setMovable(true);
        window.setResizable(false);
        window.padTop(35f);

        Table root = new Table();
        root.pad(10f);
        root.defaults().space(6f);

        Table headerRow = new Table();
        headerRow.setBackground(skin.newDrawable("white", Gdx3DHud.SELECTED_BG));
        Label header = new Label(PlanningTableModel.header(), skin, "monospace");
        header.setColor(Color.WHITE);
        // No extra horizontal padding: the header and every data row start at the same x, so the
        // fixed-width columns line up exactly as they do in the 2D terminal.
        headerRow.add(header).left().expandX().pad(2f, 0f, 2f, 0f);
        root.add(headerRow).growX().row();

        rowsTable = new Table();
        rowsTable.top().left();
        rowsTable.defaults().space(2f);
        rowsScroll = new ScrollPane(rowsTable, skin);
        rowsScroll.setFadeScrollBars(false);
        // A very narrow window can still show the whole fixed-width table by scrolling sideways.
        rowsScroll.setScrollingDisabled(false, false);
        rowsScroll.setScrollbarsOnTop(true);
        root.add(rowsScroll).grow().row();

        totalLabel = new Label("", skin, "monospace");
        totalLabel.setColor(Color.WHITE);
        root.add(totalLabel).left().row();

        Table actions = new Table();
        actions.defaults().space(8f);
        runButton = actionButton("Run", this::runSelected);
        pauseButton = actionButton("Pause", this::pauseSelected);
        resumeButton = actionButton("Resume", this::resumeSelected);
        cancelButton = actionButton("Cancel", this::cancelSelected);
        deleteButton = actionButton("Delete", this::deleteSelected);
        quotesButton = actionButton("Quotes", this::chooseQuote);
        TextButton closeButton = actionButton("Close", this::close);
        actions.add(runButton);
        actions.add(pauseButton);
        actions.add(resumeButton);
        actions.add(cancelButton);
        actions.add(deleteButton);
        actions.add(quotesButton);
        actions.add(closeButton);
        root.add(actions).left().row();

        window.add(root).grow();
        installKeyboard();

        window.pack();
        float width = Math.min(WINDOW_MAX_WIDTH, Math.max(720f, stage.getWidth() - 80f));
        float height = Math.min(WINDOW_MAX_HEIGHT, Math.max(360f, stage.getHeight() - 100f));
        window.setSize(width, height);
        window.setPosition((stage.getWidth() - width) / 2f, (stage.getHeight() - height) / 2f);
        blocker = new Actor();
        blocker.setBounds(0, 0, stage.getWidth(), stage.getHeight());
        blocker.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                return true;
            }
        });
        stage.addActor(blocker);
        stage.addActor(window);
        stage.setKeyboardFocus(rowsTable);

        refresh();
    }

    private TextButton actionButton(String label, Runnable action) {
        TextButton button = new TextButton(label, skin);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                if (!button.isDisabled()) {
                    action.run();
                }
            }
        });
        return button;
    }

    /**
     * Window-level keys: j/k and arrows move the selection, letter mnemonics run the available
     * actions and Esc closes. The listener lives on the window, so it also receives the events
     * bubbled from the focused row or footer button.
     */
    private void installKeyboard() {
        window.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == Input.Keys.ESCAPE) {
                    close();
                    return true;
                }
                if (keycode == Input.Keys.UP || keycode == Input.Keys.K) {
                    moveSelection(-1);
                    return true;
                }
                if (keycode == Input.Keys.DOWN || keycode == Input.Keys.J) {
                    moveSelection(1);
                    return true;
                }
                if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) {
                    if (!runButton.isDisabled()) {
                        runSelected();
                    }
                    return true;
                }
                return true;
            }

            @Override
            public boolean keyTyped(InputEvent event, char character) {
                char lower = Character.toLowerCase(character);
                switch (lower) {
                    case 'r' -> {
                        if (!runButton.isDisabled()) {
                            runSelected();
                        }
                    }
                    case 'p' -> {
                        if (!pauseButton.isDisabled()) {
                            pauseSelected();
                        }
                    }
                    case 's' -> {
                        if (!resumeButton.isDisabled()) {
                            resumeSelected();
                        }
                    }
                    case 'x' -> {
                        if (!cancelButton.isDisabled()) {
                            cancelSelected();
                        }
                    }
                    case 'd' -> {
                        if (!deleteButton.isDisabled()) {
                            deleteSelected();
                        }
                    }
                    case 'q' -> {
                        if (!quotesButton.isDisabled()) {
                            chooseQuote();
                        }
                    }
                    case 'c' -> close();
                    default -> {
                        // No shortcut; the key is still consumed by the modal window.
                    }
                }
                return true;
            }
        });
    }

    private void pauseSelected() {
        PlannedWork work = selected();
        if (work != null) {
            view.pauseActivePlannedWork(work.id());
            refresh();
        }
    }

    /**
     * Refreshes the table only when the works list or its states/quotes changed, so ticking the
     * simulation while the window is open does not rebuild the actors every frame.
     */
    void update() {
        if (window == null) {
            return;
        }
        String signature = signature();
        if (!signature.equals(lastSignature)) {
            refresh();
        }
    }

    private String signature() {
        StringBuilder builder = new StringBuilder();
        for (PlannedWork work : model.getPlannedWorks().works()) {
            builder.append(work.id()).append(':').append(work.state()).append(':')
                    .append(work.quote()
                            .map(quote -> quote.company().id() + "/" + quote.totalCost())
                            .orElse("-"))
                    .append(':')
                    .append(work.spentCost().isPresent() ? work.spentCost().getAsDouble() : -1)
                    .append(';');
        }
        return builder.toString();
    }

    private void refresh() {
        works = List.copyOf(model.getPlannedWorks().works());
        int selectedIndex = indexOfSelected();
        if (selectedIndex < 0) {
            selectedIndex = PlanningTableModel.clampIndex(0, works.size());
        }
        selectedId = selectedIndex >= 0 ? works.get(selectedIndex).id() : -1;

        rowsTable.clearChildren();
        rowButtons.clear();
        for (int index = 0; index < works.size(); index++) {
            PlannedWork work = works.get(index);
            String text = PlanningTableModel.formatRow(PlanningTableModel.row(work));
            Label label = new Label(text, skin, "monospace");
            label.setAlignment(com.badlogic.gdx.utils.Align.left);
            Table row = new Table();
            row.add(label).left().expandX().pad(2f, 0f, 2f, 0f);
            row.setBackground(skin.newDrawable("white",
                    work.id() == selectedId ? Gdx3DHud.SELECTED_BG : ROW_BG));
            final int rowIndex = index;
            row.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    select(rowIndex);
                }
            });
            rowsTable.add(row).growX().row();
            rowButtons.put(work.id(), row);
        }
        if (works.isEmpty()) {
            rowsTable.add(new Label("No planned works yet. Create routes in the 2D client.", skin,
                    "monospace")).left().row();
        }
        totalLabel.setText(PlanningTableModel.totalLine(model.getPlannedWorks()));
        updateButtons();
        lastSignature = signature();
        if (rowsScroll != null) {
            rowsScroll.layout();
        }
        if (!works.isEmpty() && selectedId >= 0 && rowButtons.containsKey(selectedId)) {
            stage.setKeyboardFocus(rowButtons.get(selectedId));
        } else {
            // Keep the window-level shortcuts alive after deleting the last row: a removed button
            // can no longer receive keyboard events.
            stage.setKeyboardFocus(rowsTable);
        }
    }

    private void updateButtons() {
        PlannedWork selected = selected();
        // Match the 2D list: while a work is running or paused, the list actions that would
        // change another row stay disabled; Resume/Cancel are the only way forward.
        boolean blockingWork = PlanningTableModel.hasBlockingWork(works);
        runButton.setDisabled(blockingWork || !PlanningTableModel.canRun(selected));
        pauseButton.setDisabled(!PlanningTableModel.canPause(selected));
        resumeButton.setDisabled(!PlanningTableModel.canResume(selected));
        cancelButton.setDisabled(!PlanningTableModel.canCancel(selected));
        deleteButton.setDisabled(blockingWork || !PlanningTableModel.canDelete(selected));
        quotesButton.setDisabled(blockingWork || !PlanningTableModel.canChooseQuote(selected));
    }

    private PlannedWork selected() {
        return model.getPlannedWorks().find(selectedId).orElse(null);
    }

    private int indexOfSelected() {
        for (int index = 0; index < works.size(); index++) {
            if (works.get(index).id() == selectedId) {
                return index;
            }
        }
        return -1;
    }

    private void select(int index) {
        if (index < 0 || index >= works.size()) {
            return;
        }
        selectedId = works.get(index).id();
        for (Map.Entry<Integer, Table> entry : rowButtons.entrySet()) {
            entry.getValue().setBackground(skin.newDrawable("white",
                    entry.getKey() == selectedId ? Gdx3DHud.SELECTED_BG : ROW_BG));
        }
        updateButtons();
        Table row = rowButtons.get(selectedId);
        if (row != null) {
            stage.setKeyboardFocus(row);
        }
    }

    private void moveSelection(int delta) {
        if (works.isEmpty()) {
            return;
        }
        int index = PlanningTableModel.moveIndex(indexOfSelected(), works.size(), delta);
        select(index);
    }

    private void runSelected() {
        PlannedWork work = selected();
        if (work == null) {
            return;
        }
        view.runPlannedWork(work.id());
        refresh();
    }

    private void resumeSelected() {
        PlannedWork work = selected();
        if (work == null) {
            return;
        }
        view.resumePlannedWork(work.id());
        refresh();
    }

    private void cancelSelected() {
        PlannedWork work = selected();
        if (work == null) {
            return;
        }
        view.showConfirmDialog("Cancel work", "Cancel work #" + work.id()
                + "? Built track remains and the unused budget is refunded minus the cancellation"
                + " penalty.", "Cancel work", () -> {
                    view.cancelPlannedWork(work.id());
                    refresh();
                    focusSelectedRow();
                }, this::focusSelectedRow);
    }

    private void deleteSelected() {
        PlannedWork work = selected();
        if (work == null) {
            return;
        }
        view.deletePlannedWork(work.id());
        refresh();
    }

    private void chooseQuote() {
        PlannedWork work = selected();
        if (work == null) {
            return;
        }
        List<WorkQuote> quotes = view.quotesFor(work);
        if (quoteDialog != null) {
            closeQuoteDialog();
        }
        quoteDialog = new Dialog("Company quotes", skin.get("dialog", Window.WindowStyle.class));
        quoteDialog.setModal(true);
        quoteDialog.setMovable(true);
        quoteDialog.padTop(35f);

        Table headerRow = new Table();
        headerRow.setBackground(skin.newDrawable("white", Gdx3DHud.SELECTED_BG));
        Label header = new Label(PlanningTableModel.quoteHeader(), skin, "monospace");
        header.setColor(Color.WHITE);
        headerRow.add(header).left().expandX().pad(2f, 0f, 2f, 0f);
        quoteDialog.getContentTable().add(headerRow).growX().padBottom(6f).row();

        Table quoteRows = new Table();
        quoteRows.top().left();
        quoteRows.defaults().space(2f);
        for (WorkQuote quote : quotes) {
            Label label = new Label(PlanningTableModel.formatQuoteRow(quote), skin, "monospace");
            label.setAlignment(com.badlogic.gdx.utils.Align.left);
            label.setColor(quote.feasible() ? Color.WHITE : Color.GRAY);
            Table row = new Table();
            row.add(label).left().expandX().pad(2f, 0f, 2f, 0f);
            row.setBackground(skin.newDrawable("white", ROW_BG));
            row.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    if (quote.feasible()) {
                        view.selectQuote(work, quote);
                        closeQuoteDialog();
                        refresh();
                    }
                }
            });
            quoteRows.add(row).growX().row();
        }
        ScrollPane scroll = new ScrollPane(quoteRows, skin);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(false, false);
        scroll.setScrollbarsOnTop(true);
        quoteDialog.getContentTable().add(scroll).grow().pad(0f, 12f, 8f, 12f).row();

        TextButton close = new TextButton("Close", skin);
        close.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                closeQuoteDialog();
            }
        });
        quoteDialog.getButtonTable().add(close).width(140f).height(40f).pad(8f);
        quoteBlocker = new Actor();
        quoteBlocker.setBounds(0, 0, stage.getWidth(), stage.getHeight());
        quoteBlocker.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                return true;
            }
        });
        stage.addActor(quoteBlocker);
        // The chooser is modal too: Esc closes it and no game shortcut leaks through while it is
        // open.
        quoteDialog.addListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                if (keycode == Input.Keys.ESCAPE) {
                    closeQuoteDialog();
                }
                return true;
            }

            @Override
            public boolean keyTyped(InputEvent event, char character) {
                return true;
            }
        });
        quoteDialog.show(stage);
        quoteDialog.setWidth(Math.min(WINDOW_MAX_WIDTH, Math.max(720f, stage.getWidth() - 160f)));
        quoteDialog.setHeight(Math.min(420f, Math.max(240f, stage.getHeight() - 160f)));
        quoteDialog.setPosition((stage.getWidth() - quoteDialog.getWidth()) / 2f,
                (stage.getHeight() - quoteDialog.getHeight()) / 2f);
    }

    private void closeQuoteDialog() {
        if (quoteBlocker != null) {
            quoteBlocker.remove();
            quoteBlocker = null;
        }
        if (quoteDialog != null) {
            quoteDialog.hide();
            quoteDialog.remove();
            quoteDialog = null;
        }
        focusSelectedRow();
    }

    private void focusSelectedRow() {
        Table row = rowButtons.get(selectedId);
        stage.setKeyboardFocus(row != null ? row : rowsTable);
    }

    /** Closes the window and restores the mode the player had before opening it. */
    private void close() {
        openScheduled = false;
        closeQuoteDialog();
        if (blocker != null) {
            blocker.remove();
            blocker = null;
        }
        if (window != null) {
            window.remove();
            window = null;
        }
        rowButtons.clear();
        if (model.getMode() == Model.GameMode.PLANNED_WORKS) {
            model.setMode(returnMode);
        }
        stage.setKeyboardFocus(null);
    }

    void dispose() {
        disposed = true;
        openScheduled = false;
        closeQuoteDialog();
        if (blocker != null) {
            blocker.remove();
            blocker = null;
        }
        if (window != null) {
            window.remove();
            window = null;
        }
        rowButtons.clear();
    }
}
