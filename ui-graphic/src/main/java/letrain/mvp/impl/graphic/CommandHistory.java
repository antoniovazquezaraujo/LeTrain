package letrain.mvp.impl.graphic;

import java.util.ArrayList;
import java.util.List;

/**
 * Console (' :') command history with a cursor for navigation, mirroring the terminal presenter.
 * Owned by the {@link GraphicPresenter} (not the input handler) so it survives the model/handler
 * swaps that an undo/redo performs (each undo recreates the {@link Gdx3DInputHandler}).
 */
class CommandHistory {

    private final List<String> entries = new ArrayList<>();
    private int index = -1;

    /** Records a just-executed command (deduplicating a consecutive repeat) and resets the cursor. */
    void remember(String command) {
        if (entries.isEmpty() || !entries.get(entries.size() - 1).equals(command)) {
            entries.add(command);
        }
        index = entries.size();
    }

    /** Points the navigation cursor just past the newest entry (fresh command line). */
    void resetToNew() {
        index = entries.size();
    }

    /** Returns the previous entry text, or null when there is none to go up to. */
    String up() {
        if (index > 0) {
            index--;
            return entries.get(index);
        }
        return null;
    }

    /** Returns the next entry text (or "" past the newest), or null when there is none. */
    String down() {
        if (index < entries.size() - 1) {
            index++;
            return entries.get(index);
        }
        if (index == entries.size() - 1) {
            index++;
            return "";
        }
        return null;
    }

    String last() {
        return entries.isEmpty() ? null : entries.get(entries.size() - 1);
    }

    boolean isEmpty() {
        return entries.isEmpty();
    }
}
