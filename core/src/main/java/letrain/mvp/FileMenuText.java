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
 * exists. At the boot menu (cover dismissed, no game yet) {@code Save} and {@code Settings} are
 * disabled with their reason (D8: one contextual menu, no auto-start; S3b: settings need a running
 * game). Since S4 (#777) the 3D client reaches the same boot flow: {@code New} is enabled and opens
 * the 3D New Game dialog, and since S4b (#778) {@code Settings} is enabled in-game with the shared
 * 3D Settings dialog. {@code Load} works and {@code Quit} replaces the old direct {@code Esc} exit.
 */
public final class FileMenuText {

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

    /**
     * Reason shown on {@code Save}/{@code Settings} at the boot menu: there is no running game to
     * save or configure yet (D8, S3b).
     */
    public static final String SAVE_NO_GAME_REASON = "no game in progress";

    /**
     * What the client opening the menu can offer and in which state: whether a game is running
     * (boot menu vs in-game), whether the New Game flow is available, whether the in-game Settings
     * dialog is available (S3b) and the lock reasons the client wants to show.
     */
    public record MenuState(boolean hasGame, boolean newGameEnabled, String newGameLockReason,
            boolean settingsEnabled, String settingsLockReason) {
        public MenuState {
            ValidationUtils.requireNonNull(newGameLockReason, "newGameLockReason must not be null");
            ValidationUtils.requireNonNull(settingsLockReason,
                    "settingsLockReason must not be null");
        }

        /**
         * 2D terminal state. {@code hasGame} is false at the boot menu (cover dismissed, no game
         * started yet): {@code Save} and {@code Settings} are then disabled with
         * {@link #SAVE_NO_GAME_REASON}.
         */
        public static MenuState terminal(boolean hasGame) {
            return new MenuState(hasGame, true, "", hasGame, SAVE_NO_GAME_REASON);
        }

        /**
         * 3D client state since S4 (#777)/S4b (#778): {@code New} is enabled and opens the 3D New
         * Game dialog, {@code Settings} is enabled with a running game and opens the 3D Settings
         * dialog. {@code hasGame} is false at the boot menu (cover dismissed, no game started yet),
         * so {@code Save} and {@code Settings} are disabled with {@link #SAVE_NO_GAME_REASON},
         * exactly like 2D.
         */
        public static MenuState graphic(boolean hasGame) {
            return new MenuState(hasGame, true, "", hasGame, SAVE_NO_GAME_REASON);
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
                new Item(Command.SETTINGS, SETTINGS_LABEL, state.settingsEnabled(),
                        state.settingsLockReason()),
                new Item(Command.QUIT, QUIT_LABEL, true, ""));
    }

    /**
     * Alt access key of a row (#803): {@code N/L/S/E/Q}. Settings takes {@code E}
     * (s<em>e</em>ttings) because {@code S} already belongs to Save; the clients tint the matching
     * letter like the rest of their dialogs.
     */
    public static char mnemonic(Command command) {
        ValidationUtils.requireNonNull(command, "command must not be null");
        switch (command) {
            case NEW:
                return 'N';
            case LOAD:
                return 'L';
            case SAVE:
                return 'S';
            case SETTINGS:
                return 'E';
            case QUIT:
                return 'Q';
            default:
                return '?';
        }
    }

    /** Command whose Alt access key is {@code key} (case-insensitive), empty for any other. */
    public static java.util.Optional<Command> byHotkey(char key) {
        char letter = Character.toUpperCase(key);
        for (Command command : Command.values()) {
            if (mnemonic(command) == letter) {
                return java.util.Optional.of(command);
            }
        }
        return java.util.Optional.empty();
    }

    /**
     * What the player reads: always the bare label. The disabled state is enough; the lock reason
     * stays in {@link Item#disabledReason()} for the clients that can show it on hover (the 3D
     * tooltip, #802) and the terminal simply greys the row out.
     */
    public static String displayLabel(Item item) {
        ValidationUtils.requireNonNull(item, "item must not be null");
        return item.label();
    }

    private FileMenuText() {}
}
