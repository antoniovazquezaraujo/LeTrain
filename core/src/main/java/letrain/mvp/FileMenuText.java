package letrain.mvp;

import java.util.List;
import letrain.utils.ValidationUtils;

/**
 * Presentation-neutral definition of the File menu (opened with {@code Esc} in both clients). The
 * 2D terminal and the 3D HUD build their menu from {@link #items()} so the structure, the words and
 * the lock states cannot drift apart.
 *
 * <p>
 * Issue #743 (arc #742): {@code New} and {@code Settings} are visible but locked in this slice;
 * their reasons are shown next to the label. {@code Load}/{@code Save} work and {@code Quit}
 * replaces the old direct {@code Esc} exit.
 */
public final class FileMenuText {

    /** Window title of the menu in both clients. */
    public static final String TITLE = "File";

    /** What a row does when the player picks it. */
    public enum Command {
        NEW, LOAD, SAVE, SETTINGS, QUIT
    }

    /**
     * One row of the menu: its command, the wording, whether it is enabled and the reason shown
     * while it is locked.
     */
    public record Item(Command command, String label, boolean enabled, String disabledReason) {
        public Item {
            ValidationUtils.requireNonNull(command, "command must not be null");
            ValidationUtils.requireNonNull(label, "label must not be null");
        }
    }

    /** Reason shown while {@code New} waits for the New Game flow of the settings slice. */
    public static final String NEW_LOCK_REASON = "settings arrive in the next slice";
    /** Reason shown while {@code Settings} waits for its slice. */
    public static final String SETTINGS_LOCK_REASON = "arrives with the settings slice";

    private static final List<Item> ITEMS = List.of(
            new Item(Command.NEW, "New", false, NEW_LOCK_REASON),
            new Item(Command.LOAD, "Load", true, ""), new Item(Command.SAVE, "Save", true, ""),
            new Item(Command.SETTINGS, "Settings", false, SETTINGS_LOCK_REASON),
            new Item(Command.QUIT, "Quit", true, ""));

    /** The rows in display order; both clients iterate this list to build their menu. */
    public static List<Item> items() {
        return ITEMS;
    }

    /** What the player reads: the label, plus the lock reason in parentheses while disabled. */
    public static String displayLabel(Item item) {
        ValidationUtils.requireNonNull(item, "item must not be null");
        return item.enabled() ? item.label() : item.label() + " (" + item.disabledReason() + ")";
    }

    private FileMenuText() {}
}
