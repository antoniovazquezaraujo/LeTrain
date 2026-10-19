package letrain.mvp.impl.graphic;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.FocusManager;
import com.kotcrab.vis.ui.widget.ListView;
import com.kotcrab.vis.ui.widget.file.FileChooser;
import com.kotcrab.vis.ui.widget.file.internal.FileListAdapter;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Keyboard layer over VisUI's {@link FileChooser} (#484). VisUI's default Tab traversal only walks
 * the text fields, so the file tree and the Open/Cancel buttons are mouse-only; the tree itself is
 * a {@code ListView} wrapper whose items are {@link FileChooser.FileItem}s (focusable tables).
 *
 * <p>
 * The wrapper keeps the widget and its look untouched and adds a <em>capture</em> listener to the
 * chooser, so it sees {@code Tab} before the focused {@link TextField} consumes it. The ring is
 * path field → file field → file list → Open → Cancel; in the list, Up/Down move the selection,
 * Enter opens a folder or picks a file, Left/Backspace goes to the parent folder, and Esc keeps
 * cancelling through VisUI's own {@code closeOnEscape}. The selection highlight is the focus
 * feedback. Mouse behaviour is left as-is.
 *
 * <p>
 * Pinned to VisUI 1.5.9: VisUI exposes no public access to the list/fields/buttons, so they are
 * read reflectively and the item selection calls the private {@code handleSelectClick(boolean)}
 * (the same path a mouse click takes). If any internal disappears in a future VisUI the wrapper
 * logs and installs nothing, leaving the dialog exactly as before.
 */
final class VisFileChooserKeyboard {

    /** Ring slots: path field, file field, file list, Open, Cancel. */
    private static final int PATH_SLOT = 0;
    private static final int FILE_SLOT = 1;
    private static final int LIST_SLOT = 2;
    private static final int OPEN_SLOT = 3;
    private static final int CANCEL_SLOT = 4;

    private VisFileChooserKeyboard() {}

    /**
     * Installs the keyboard navigation on {@code chooser}.
     *
     * @return {@code false} when a VisUI 1.5.9 internal is missing (the dialog keeps working as
     *         before, just without tree keyboard access)
     */
    static boolean install(FileChooser chooser) {
        ListView<?> listView = (ListView<?>) readField(chooser, "fileListView");
        TextField pathField = (TextField) readField(chooser, "currentPath");
        TextField fileField = (TextField) readField(chooser, "selectedFileTextField");
        TextButton openButton = (TextButton) readField(chooser, "confirmButton");
        if (listView == null || pathField == null || fileField == null || openButton == null) {
            Gdx.app.error("LeTrain",
                    "VisUI FileChooser internals changed; keyboard tree navigation disabled (#484)");
            return false;
        }
        TextButton cancelButton = findCancelButton(chooser, openButton);
        final int slots = cancelButton == null ? CANCEL_SLOT : CANCEL_SLOT + 1;

        final boolean[] listActive = {false};
        final int[] listIndex = {-1};

        chooser.addCaptureListener(new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int keycode) {
                Stage stage = chooser.getStage();
                if (stage == null) {
                    return false;
                }
                Actor focus = stage.getKeyboardFocus();
                Array<FileChooser.FileItem> items = orderedItems(listView);
                // A mouse click leaves a FileItem focused; treat that as the list being active.
                if (focus instanceof FileChooser.FileItem) {
                    listActive[0] = true;
                    listIndex[0] = indexOf(items, focus);
                }

                if (keycode == Input.Keys.TAB) {
                    // Capture phase: returning true only marks the event handled, so the event must
                    // also be stopped or the focused TextField would still run its
                    // text-field-only traversal and skip the tree and the buttons.
                    int slot = listActive[0] ? LIST_SLOT
                            : slotOf(focus, pathField, fileField, openButton, cancelButton);
                    int step = isShiftPressed() ? -1 : 1;
                    focusSlot(nextSlot(slot, step, slots), stage, chooser, listView, pathField,
                            fileField, openButton, cancelButton, items, listActive, listIndex);
                    event.stop();
                    return true;
                }

                if (!listActive[0]) {
                    // libGDX buttons do not activate on Enter/Space by themselves; do it for the
                    // two ring buttons once Tab reaches them.
                    boolean activate = keycode == Input.Keys.ENTER
                            || keycode == Input.Keys.NUMPAD_ENTER || keycode == Input.Keys.SPACE;
                    if (activate && (focus == openButton || focus == cancelButton)) {
                        ((TextButton) focus).fire(
                                new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent());
                        event.stop();
                        return true;
                    }
                    return false;
                }
                switch (keycode) {
                    case Input.Keys.UP:
                        moveSelection(-1, stage, chooser, items, listIndex);
                        event.stop();
                        return true;
                    case Input.Keys.DOWN:
                        moveSelection(1, stage, chooser, items, listIndex);
                        event.stop();
                        return true;
                    case Input.Keys.ENTER:
                    case Input.Keys.NUMPAD_ENTER:
                        activate(stage, chooser, items, listIndex, openButton, pathField,
                                listActive);
                        event.stop();
                        return true;
                    case Input.Keys.LEFT:
                    case Input.Keys.BACKSPACE:
                        goToParent(stage, chooser, pathField, listActive);
                        event.stop();
                        return true;
                    default:
                        return false;
                }
            }
        });
        return true;
    }

    /** Next slot in the ring, wrapping around. Package-visible so the cycling maths is testable. */
    static int nextSlot(int slot, int step, int slots) {
        return Math.floorMod(slot + step, slots);
    }

    private static void focusSlot(int slot, Stage stage, FileChooser chooser, ListView<?> listView,
            TextField pathField, TextField fileField, TextButton openButton,
            TextButton cancelButton, Array<FileChooser.FileItem> items, boolean[] listActive,
            int[] listIndex) {
        listActive[0] = slot == LIST_SLOT;
        switch (slot) {
            case PATH_SLOT:
                focusWidget(stage, pathField);
                break;
            case FILE_SLOT:
                focusWidget(stage, fileField);
                break;
            case LIST_SLOT:
                enterList(stage, chooser, items, listIndex);
                if (items.size == 0) {
                    listActive[0] = false;
                }
                break;
            case OPEN_SLOT:
                focusWidget(stage, openButton);
                break;
            case CANCEL_SLOT:
                if (cancelButton != null) {
                    focusWidget(stage, cancelButton);
                }
                break;
            default:
                break;
        }
    }

    private static void enterList(Stage stage, FileChooser chooser,
            Array<FileChooser.FileItem> items, int[] listIndex) {
        if (items.size == 0) {
            // Nothing to navigate: leave the list marked inactive and fall through to the ring.
            listIndex[0] = -1;
            return;
        }
        if (listIndex[0] < 0 || listIndex[0] >= items.size) {
            // Start from the item VisUI has selected (a mouse click or its type-ahead may have
            // moved it), falling back to the first row.
            int selected = selectedIndex(chooser, items);
            listIndex[0] = selected >= 0 ? selected : 0;
        }
        focusItem(stage, chooser, items.get(listIndex[0]));
    }

    private static void moveSelection(int step, Stage stage, FileChooser chooser,
            Array<FileChooser.FileItem> items, int[] listIndex) {
        if (items.size == 0) {
            return;
        }
        // Follow the actually selected row: VisUI's built-in type-ahead also moves the selection
        // without telling this wrapper.
        int selected = selectedIndex(chooser, items);
        int index = selected >= 0 ? selected : (listIndex[0] < 0 ? 0 : listIndex[0]);
        int next = Math.max(0, Math.min(items.size - 1, index + step));
        listIndex[0] = next;
        focusItem(stage, chooser, items.get(next));
    }

    private static void activate(Stage stage, FileChooser chooser,
            Array<FileChooser.FileItem> items, int[] listIndex, TextButton openButton,
            TextField pathField, boolean[] listActive) {
        if (items.size == 0 || listIndex[0] < 0 || listIndex[0] >= items.size) {
            return;
        }
        FileChooser.FileItem item = items.get(listIndex[0]);
        if (item.isDirectory()) {
            // The list rebuild is asynchronous, so hand the keyboard back to the path field; Tab
            // reaches the (new) list again once it has loaded.
            listActive[0] = false;
            chooser.setDirectory(item.getFile(), FileChooser.HistoryPolicy.ADD);
            focusWidget(stage, pathField);
        } else {
            // Same path as clicking Open: the button's ChangeListener runs selectionFinished().
            openButton.fire(new com.badlogic.gdx.scenes.scene2d.utils.ChangeListener.ChangeEvent());
        }
    }

    private static void goToParent(Stage stage, FileChooser chooser, TextField pathField,
            boolean[] listActive) {
        com.badlogic.gdx.files.FileHandle current = chooser.getCurrentDirectory();
        com.badlogic.gdx.files.FileHandle parent = current == null ? null : current.parent();
        if (parent == null || parent.equals(current)) {
            return;
        }
        listActive[0] = false;
        chooser.setDirectory(parent, FileChooser.HistoryPolicy.ADD);
        focusWidget(stage, pathField);
    }

    /**
     * Selects {@code item} exactly like a mouse click would (deselect the rest first) and scrolls
     * it into view. The selection highlight doubles as the keyboard focus feedback.
     */
    private static void focusItem(Stage stage, FileChooser chooser, FileChooser.FileItem item) {
        selectItem(item);
        chooser.highlightFiles(item.getFile());
        FocusManager.switchFocus(stage, item);
        stage.setKeyboardFocus(item);
    }

    /** Clears VisUI's focus border and focuses a plain widget (field or button). */
    private static void focusWidget(Stage stage, Actor widget) {
        FocusManager.resetFocus(stage);
        stage.setKeyboardFocus(widget);
    }

    private static int slotOf(Actor focus, TextField pathField, TextField fileField,
            TextButton openButton, TextButton cancelButton) {
        if (focus == pathField) {
            return PATH_SLOT;
        }
        if (focus == fileField) {
            return FILE_SLOT;
        }
        if (focus == openButton) {
            return OPEN_SLOT;
        }
        if (focus != null && focus == cancelButton) {
            return CANCEL_SLOT;
        }
        // Unknown or null focus: assume the file field, so Tab moves on to the list.
        return FILE_SLOT;
    }

    /**
     * Items of the file list in display order. {@code fillTable} rebuilds this array; reading it
     * lazily on each key means a directory change is picked up without caching.
     */
    private static Array<FileChooser.FileItem> orderedItems(ListView<?> listView) {
        if (listView.getAdapter() instanceof FileListAdapter) {
            return ((FileListAdapter) listView.getAdapter()).getOrderedViews();
        }
        return new Array<>();
    }

    private static int indexOf(Array<FileChooser.FileItem> items, Actor actor) {
        for (int i = 0; i < items.size; i++) {
            if (items.get(i) == actor) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Row of VisUI's single selected item, or -1. Keeps the arrow cursor in sync with a mouse click
     * or the chooser's built-in type-ahead (it only highlights, it does not move our index).
     */
    private static int selectedIndex(FileChooser chooser, Array<FileChooser.FileItem> items) {
        Object selected = readField(chooser, "selectedItems");
        if (selected instanceof Array<?> selectedArray && selectedArray.size == 1) {
            Object item = selectedArray.get(0);
            return item instanceof Actor ? indexOf(items, (Actor) item) : -1;
        }
        return -1;
    }

    /**
     * Runs VisUI's own click-selection path (private in 1.5.9): deselect every item and select this
     * one, updating the file-name field. Pinned on purpose; the caller is a no-op if the method is
     * gone.
     */
    private static void selectItem(FileChooser.FileItem item) {
        try {
            Method select = item.getClass().getDeclaredMethod("handleSelectClick", boolean.class);
            select.setAccessible(true);
            select.invoke(item, false);
        } catch (ReflectiveOperationException e) {
            Gdx.app.error("LeTrain",
                    "VisUI FileChooser item selection changed; keyboard highlight unavailable (#484)");
        }
    }

    private static TextButton findCancelButton(Group group, TextButton openButton) {
        for (Actor child : group.getChildren()) {
            if (child instanceof TextButton && child != openButton) {
                return (TextButton) child;
            }
            if (child instanceof Group) {
                TextButton found = findCancelButton((Group) child, openButton);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static Object readField(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private static boolean isShiftPressed() {
        return Gdx.input.isKeyPressed(Input.Keys.SHIFT_LEFT)
                || Gdx.input.isKeyPressed(Input.Keys.SHIFT_RIGHT);
    }
}
