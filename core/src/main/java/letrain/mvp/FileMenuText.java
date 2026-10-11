package letrain.mvp;

import java.util.List;
import letrain.utils.ValidationUtils;

/**
 * Presentation-neutral definition of the File menu (opened with {@code Esc} in both clients). The
 * 2D terminal and the 3D HUD build their menu from {@link #items(MenuState)} so the structure, the
 * words and the states cannot drift apart.
 *
 * <p>
 * Issue #746 (arc #742): {@code New} is enabled in the 2D client now that the New Game dialog
 * exists. At the boot menu (cover dismissed, no game yet) {@code Save} is disabled with its reason
 * (D8: one contextual menu, no auto-start). {@code Settings} still waits for its slice in 2D and
 * the whole 3D parity slice (S4) keeps {@code New}/{@code Settings} disabled with the parity
 * reason. {@code Load} works and {@code Quit} replaces the old direct {@code Esc} exit.
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

    /** Reason shown while the 3D client waits for the parity slice (S4). */
    public static final String PARITY_LOCK_REASON = "3D parity slice";
    /** Reason shown while {@code Settings} waits for its slice (S3 in 2D, S4 in 3D). */
    public static final String SETTINGS_LOCK_REASON = "arrives with the settings slice";
    /** Reason shown on {@code Save} at the boot menu: there is no game to save yet (D8). */
    public static final String SAVE_NO_GAME_REASON = "no game in progress";

    /**
     * What the client opening the menu can offer and in which state: whether a game is running
     * (boot menu vs in-game), whether the New Game flow is available, and the lock reasons the
     * client wants to show.
     */
    public record MenuState(boolean hasGame, boolean newGameEnabled, String newGameLockReason,
            String settingsLockReason) {
        public MenuState {
            ValidationUtils.requireNonNull(newGameLockReason, "newGameLockReason must not be null");
            ValidationUtils.requireNonNull(settingsLockReason,
                    "settingsLockReason must not be null");
        }

        /**
         * 2D terminal state. {@code hasGame} is false at the boot menu (cover dismissed, no game
         * started yet): {@code Save} is then disabled with {@link #SAVE_NO_GAME_REASON}.
         */
        public static MenuState terminal(boolean hasGame) {
            return new MenuState(hasGame, true, "", SETTINGS_LOCK_REASON);
        }

        /**
         * 3D client state until S4: no New Game flow yet, so {@code New} and {@code Settings} stay
         * visible but disabled with {@link #PARITY_LOCK_REASON}.
         */
        public static MenuState graphic() {
            return new MenuState(true, false, PARITY_LOCK_REASON, PARITY_LOCK_REASON);
        }
    }

    private static final String NEW_LABEL = "New";
    private static final String LOAD_LABEL = "Load";
    private static final String SAVE_LABEL = "Save";
    private static final String SETTINGS_LABEL = "Settings";
    private static final String QUIT_LABEL = "Quit";

    /**
     * The rows in display order for the given state; both clients iterate this list to build their
     * menu.
     */
    public static List<Item> items(MenuState state) {
        ValidationUtils.requireNonNull(state, "state must not be null");
        return List.of(
                new Item(Command.NEW, NEW_LABEL, state.newGameEnabled(), state.newGameLockReason()),
                new Item(Command.LOAD, LOAD_LABEL, true, ""),
                new Item(Command.SAVE, SAVE_LABEL, state.hasGame(), SAVE_NO_GAME_REASON),
                new Item(Command.SETTINGS, SETTINGS_LABEL, false, state.settingsLockReason()),
                new Item(Command.QUIT, QUIT_LABEL, true, ""));
    }

    /** What the player reads: the label, plus the lock reason in parentheses while disabled. */
    public static String displayLabel(Item item) {
        ValidationUtils.requireNonNull(item, "item must not be null");
        if (item.enabled() || item.disabledReason().isBlank()) {
            return item.label();
        }
        return item.label() + " (" + item.disabledReason() + ")";
    }

    private FileMenuText() {}
}
