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
import com.badlogic.gdx.scenes.scene2d.ui.TextButton.TextButtonStyle;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import letrain.mvp.impl.graphic.FileDialogEntries.Entry;

/**
 * Keyboard-first file dialog for the 3D client (#484), the replacement for VisUI's
 * {@code FileChooser}. A plain scene2d {@link Dialog} on the code-built skin (already monospace):
 * current path, a scrollable file list, a file-name field and Open/Save + Cancel.
 *
 * <p>
 * Keys are intentionally boring: <b>Tab/Shift+Tab</b> cycle list → name → primary → Cancel;
 * <b>Up/Down</b> move the list selection (clamped, no wrap); <b>Enter</b> opens a folder or picks a
 * file, confirms from the name field and activates a focused button; <b>Left/Backspace</b> go to
 * the parent folder; <b>Esc</b> cancels; <b>Alt+S/O/C</b> hit the tinted mnemonics. Mouse clicks
 * select and double-clicks activate. The browsing/extension maths lives in
 * {@link FileDialogEntries}, which is pure and testable.
 */
public final class Gdx3DFileDialog {

    /** Whether the dialog picks an existing file or names a new one. */
    public enum Mode {
        OPEN, SAVE
    }

    private static final int AREA_LIST = 0;
    private static final int AREA_NAME = 1;
    private static final int AREA_PRIMARY = 2;
    private static final int AREA_CANCEL = 3;
    private static final int AREA_COUNT = 4;

    private static final float CONTENT_WIDTH = 640f;
    private static final float LIST_HEIGHT = 300f;

    /** Only one instance at a time, like the rest of the modal dialogs. */
    private static boolean active;

    private final Stage stage;
    private final Skin skin;
    private final String title;
    private final Mode mode;
    private final String defaultText;
    private final String[] extensions;
    private final Consumer<String> onResult;
    private final Runnable onCancel;

    private Dialog dialog;
    private Actor blocker;
    private Label pathLabel;
    private Table listTable;
    private ScrollPane listScroll;
    private TextField nameField;
    private TextButton primaryButton;
    private TextButton cancelButton;
    private Label statusLabel;

    private File currentDirectory;
    private final List<Entry> entries = new ArrayList<>();
    private final List<TextButton> rows = new ArrayList<>();
    private int selected;
    private int area = AREA_LIST;
    private boolean nameEdited;
    private boolean updatingName;

    private Gdx3DFileDialog(Stage stage, Skin skin, String title, Mode mode, String defaultText,
            String[] extensions, Consumer<String> onResult, Runnable onCancel) {
        this.stage = stage;
        this.skin = skin;
        this.title = title;
        this.mode = mode;
        this.defaultText = defaultText == null ? "" : defaultText;
        this.extensions = extensions == null ? new String[0] : extensions.clone();
        this.onResult = onResult;
        this.onCancel = onCancel;
    }

    /**
     * Opens the dialog. The callbacks run on the GL thread; {@code onCancel} is used by the boot
     * Load to return to the cover.
     */
    public static void show(Stage stage, Skin skin, String title, Mode mode, String defaultText,
            String[] extensions, Consumer<String> onResult, Runnable onCancel) {
        if (active || stage == null || skin == null) {
            return;
        }
        active = true;
        Gdx.app.postRunnable(() -> new Gdx3DFileDialog(stage, skin, title, mode, defaultText,
                extensions, onResult, onCancel).build());
    }

    private void build() {
        dialog = new Dialog(title, skin.get("dialog", Window.WindowStyle.class));
        blocker = new Actor();
        blocker.setBounds(0, 0, stage.getWidth(), stage.getHeight());
        blocker.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                return true;
            }
        });

        pathLabel = new Label("", skin, "small");
        pathLabel.setEllipsis(true);
        listTable = new Table();
        listTable.top().left();
        listScroll = new ScrollPane(listTable, skin);
        listScroll.setFadeScrollBars(false);
        listScroll.setScrollingDisabled(true, false);

        nameField = new TextField(mode == Mode.SAVE ? defaultText : "", skin);
        nameField.setFocusTraversal(false);
        // LWJGL3 synthesises keyTyped('\t') on Tab: only accept printable characters.
        nameField.setTextFieldFilter((field, c) -> c >= 32);
        nameField.setTextFieldListener((field, c) -> {
            if (!updatingName) {
                nameEdited = true;
            }
        });

        statusLabel = new Label("", skin, "small");
        statusLabel.setColor(new Color(1f, 0.4f, 0.4f, 1f));
        statusLabel.setWrap(true);

        primaryButton = new TextButton(
                Gdx3DHud.settingsHotkeyLabel(primaryLabel(), primaryMnemonic()), skin);
        cancelButton = new TextButton(Gdx3DHud.settingsHotkeyLabel("Cancel", 'C'), skin);

        Table content = dialog.getContentTable();
        content.pad(16, 24, 8, 24);
        content.defaults().space(8);
        content.add(pathLabel).width(CONTENT_WIDTH).left().row();
        content.add(listScroll).width(CONTENT_WIDTH).height(LIST_HEIGHT).row();

        Table nameRow = new Table();
        nameRow.left();
        nameRow.add(new Label("Name", skin, "small")).width(52f).left();
        nameRow.add(nameField).width(CONTENT_WIDTH - 52f).height(34f).left();
        content.add(nameRow).width(CONTENT_WIDTH).left().row();
        content.add(statusLabel).width(CONTENT_WIDTH).left().row();

        Table buttons = dialog.getButtonTable();
        buttons.defaults().space(20).minWidth(140).minHeight(48);
        buttons.add(primaryButton);
        buttons.add(cancelButton);

        primaryButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                confirm();
            }
        });
        cancelButton.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                cancel();
            }
        });
        dialog.addCaptureListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                return handleKey(event, keycode);
            }
        });

        dialog.padTop(46);
        dialog.pack();
        dialog.setWidth(Math.max(dialog.getWidth(), 720f));
        dialog.setHeight(Math.max(dialog.getHeight(), 540f));
        dialog.setPosition((stage.getWidth() - dialog.getWidth()) / 2,
                (stage.getHeight() - dialog.getHeight()) / 2);

        stage.addActor(blocker);
        stage.addActor(dialog);

        File initial = new File(".").getAbsoluteFile();
        if (!initial.isDirectory() || !initial.canRead()) {
            initial = new File(System.getProperty("user.home")).getAbsoluteFile();
        }
        loadDirectory(initial, null);
        if (mode == Mode.OPEN && !defaultText.isBlank()) {
            selectFileName(defaultText);
        }
        // OPEN starts browsing; SAVE starts in the pre-filled name field so Enter saves directly.
        focusArea(mode == Mode.SAVE ? AREA_NAME : AREA_LIST);
    }

    private boolean handleKey(InputEvent event, int keycode) {
        if (keycode == Input.Keys.ESCAPE) {
            cancel();
            event.stop();
            return true;
        }
        if (keycode == Input.Keys.TAB) {
            // Capture phase: the focused TextField would otherwise consume Tab with its own
            // traversal and skip the list and the buttons.
            focusArea(Math.floorMod(area + (isShiftPressed() ? -1 : 1), AREA_COUNT));
            event.stop();
            return true;
        }
        if (isAltPressed() && keycode >= Input.Keys.A && keycode <= Input.Keys.Z) {
            char letter = Character.toLowerCase((char) ('A' + keycode - Input.Keys.A));
            if (letter == Character.toLowerCase(primaryMnemonic())) {
                confirm();
                event.stop();
                return true;
            }
            if (letter == 'c') {
                cancel();
                event.stop();
                return true;
            }
        }
        if (area == AREA_LIST) {
            switch (keycode) {
                case Input.Keys.UP:
                    moveSelection(-1);
                    event.stop();
                    return true;
                case Input.Keys.DOWN:
                    moveSelection(1);
                    event.stop();
                    return true;
                case Input.Keys.ENTER:
                case Input.Keys.NUMPAD_ENTER:
                    activateSelection();
                    event.stop();
                    return true;
                case Input.Keys.LEFT:
                case Input.Keys.BACKSPACE:
                    goToParent();
                    event.stop();
                    return true;
                default:
                    return false;
            }
        }
        if (area == AREA_NAME) {
            if (keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER) {
                confirm();
                event.stop();
                return true;
            }
            return false;
        }
        if ((keycode == Input.Keys.ENTER || keycode == Input.Keys.NUMPAD_ENTER
                || keycode == Input.Keys.SPACE) && (area == AREA_PRIMARY || area == AREA_CANCEL)) {
            if (area == AREA_PRIMARY) {
                confirm();
            } else {
                cancel();
            }
            event.stop();
            return true;
        }
        return false;
    }

    private void focusArea(int next) {
        area = next;
        switch (next) {
            case AREA_LIST:
                stage.setKeyboardFocus(listTable);
                break;
            case AREA_NAME:
                stage.setKeyboardFocus(nameField);
                nameField.selectAll();
                break;
            case AREA_PRIMARY:
                stage.setKeyboardFocus(primaryButton);
                break;
            case AREA_CANCEL:
                stage.setKeyboardFocus(cancelButton);
                break;
            default:
                break;
        }
        paintFocus();
    }

    /** The focused footer button gets the SELECTED_BG style, like the other 3D dialogs. */
    private void paintFocus() {
        primaryButton.setStyle(
                skin.get(area == AREA_PRIMARY ? "focused" : "default", TextButtonStyle.class));
        cancelButton.setStyle(
                skin.get(area == AREA_CANCEL ? "focused" : "default", TextButtonStyle.class));
    }

    private void moveSelection(int step) {
        if (entries.isEmpty()) {
            return;
        }
        int next = Math.max(0, Math.min(entries.size() - 1, selected + step));
        select(next);
    }

    private void select(int index) {
        if (entries.isEmpty()) {
            return;
        }
        selected = Math.max(0, Math.min(entries.size() - 1, index));
        for (int i = 0; i < rows.size(); i++) {
            rows.get(i).setChecked(i == selected);
        }
        revealSelected();
        if (mode == Mode.OPEN) {
            Entry entry = entries.get(selected);
            setNamesProgrammatically(entry.directory() ? "" : entry.file().getName());
        }
    }

    /** Fills the name field without flagging it as user-edited (OPEN mirrors the selection). */
    private void setNamesProgrammatically(String text) {
        updatingName = true;
        nameField.setText(text);
        updatingName = false;
        nameEdited = false;
    }

    private void revealSelected() {
        if (selected < 0 || selected >= rows.size()) {
            return;
        }
        TextButton row = rows.get(selected);
        listTable.layout();
        listScroll.validate();
        listScroll.scrollTo(row.getX(), row.getY(), row.getWidth(), row.getHeight(), false, true);
    }

    private void activateSelection() {
        Entry entry = selectedEntry();
        if (entry == null) {
            status(entries.isEmpty() ? "Nothing here" : "Choose a file");
            return;
        }
        activate(entry);
    }

    private void activate(Entry entry) {
        if (entry.directory()) {
            loadDirectory(entry.file(), entry.parent() ? currentDirectory : null);
            return;
        }
        deliver(entry.file().getAbsolutePath());
    }

    private void goToParent() {
        File parent = FileDialogEntries.parentOf(currentDirectory);
        if (parent != null) {
            loadDirectory(parent, currentDirectory);
        }
    }

    private void loadDirectory(File directory, File selectAfter) {
        File target = directory.getAbsoluteFile();
        if (!target.isDirectory()) {
            status("Not a folder: " + target.getName());
            return;
        }
        if (!target.canRead()) {
            status("Cannot read " + target.getPath());
            return;
        }
        currentDirectory = target;
        pathLabel.setText(target.getAbsolutePath());
        status("");
        entries.clear();
        entries.addAll(FileDialogEntries.entries(target, extensions));
        rebuildRows();
        int index = indexOfFile(selectAfter);
        selected = index < 0 ? 0 : index;
        select(selected);
        focusArea(AREA_LIST);
    }

    private void rebuildRows() {
        listTable.clearChildren();
        rows.clear();
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            final int index = i;
            TextButton row = new TextButton(entry.label(), skin, "menu-button");
            row.setProgrammaticChangeEvents(false);
            row.getLabel().setEllipsis(true);
            row.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    focusArea(AREA_LIST);
                    select(index);
                    if (getTapCount() >= 2) {
                        activate(entries.get(index));
                    }
                }
            });
            listTable.add(row).growX().left().padBottom(2).row();
            rows.add(row);
        }
        if (!FileDialogEntries.hasContent(entries)) {
            listTable.add(new Label("(empty)", skin, "small")).left().pad(6).row();
        }
    }

    private void confirm() {
        if (mode == Mode.SAVE) {
            String name = nameField.getText().trim();
            if (name.isEmpty()) {
                status("Enter a file name");
                return;
            }
            String withExtension = FileDialogEntries.appendExtension(name, extensions);
            deliver(FileDialogEntries.resolve(currentDirectory, withExtension).getAbsolutePath());
            return;
        }
        String typed = nameField.getText().trim();
        if (nameEdited && !typed.isEmpty()) {
            File candidate = FileDialogEntries.resolve(currentDirectory, typed);
            if (candidate.isDirectory()) {
                loadDirectory(candidate, null);
            } else if (candidate.isFile()) {
                deliver(candidate.getAbsolutePath());
            } else {
                status("File not found: " + candidate.getName());
            }
            return;
        }
        Entry entry = selectedEntry();
        if (entry == null) {
            status("Choose a file");
            return;
        }
        if (entry.directory()) {
            loadDirectory(entry.file(), entry.parent() ? currentDirectory : null);
            return;
        }
        deliver(entry.file().getAbsolutePath());
    }

    private Entry selectedEntry() {
        if (selected < 0 || selected >= entries.size()) {
            return null;
        }
        return entries.get(selected);
    }

    private int indexOfFile(File file) {
        if (file == null) {
            return -1;
        }
        File absolute = file.getAbsoluteFile();
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).file().getAbsoluteFile().equals(absolute)) {
                return i;
            }
        }
        return -1;
    }

    /** Preselects a file by name when opening (the old default-name behaviour). */
    private void selectFileName(String name) {
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (!entry.directory() && entry.file().getName().equalsIgnoreCase(name)) {
                select(i);
                return;
            }
        }
    }

    private void deliver(String absolutePath) {
        finish();
        if (onResult != null) {
            onResult.accept(absolutePath);
        }
    }

    private void cancel() {
        finish();
        if (onCancel != null) {
            onCancel.run();
        }
    }

    private void finish() {
        active = false;
        if (stage != null) {
            stage.setKeyboardFocus(null);
        }
        if (blocker != null) {
            blocker.remove();
        }
        if (dialog != null) {
            dialog.hide(null);
        }
    }

    private void status(String message) {
        statusLabel.setText(message == null ? "" : message);
    }

    private String primaryLabel() {
        return mode == Mode.SAVE ? "Save" : "Open";
    }

    private char primaryMnemonic() {
        return mode == Mode.SAVE ? 'S' : 'O';
    }

    private static boolean isAltPressed() {
        return Gdx.input.isKeyPressed(Input.Keys.ALT_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.ALT_RIGHT);
    }

    private static boolean isShiftPressed() {
        return Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
    }
}
