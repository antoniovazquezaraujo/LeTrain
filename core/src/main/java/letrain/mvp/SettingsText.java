package letrain.mvp;

import java.util.Map;

/**
 * Shared wording of the in-game Settings dialog (S3b, concept §5.2/D9/D10). Both clients must read
 * the same words so the 2D terminal and the future 3D parity slice cannot drift apart (same
 * convention as {@link NewGameText}, {@link FileMenuText} and {@link EditorText}).
 */
public final class SettingsText {

    /** Dialog title. */
    public static final String TITLE = "Settings";

    /** Action buttons, in display order. */
    public static final String APPLY = "Apply";
    public static final String RESTORE = "Restore";
    public static final String NEW_GAME = "New game";
    public static final String SAVE_DEFAULTS = "Save as defaults";
    public static final String CLOSE = "Close";

    /**
     * Legend labels of the coloured field states (the scenario lock has no colour of its own since
     * #767); the glyph is drawn by the client.
     */
    public static final String LEGEND_LIVE = "live";
    public static final String LEGEND_REAPPLY = "reapply";
    public static final String LEGEND_NEW_GAME = "requires a new game";

    /** Confirmation shown before applying settings that need a reapply (🟡). */
    public static final String REAPPLY_TITLE = "Reapply settings";

    public static String reapplyMessage(java.util.List<String> keys, boolean clockJump) {
        StringBuilder text = new StringBuilder("These settings need a reapply: ")
                .append(String.join(", ", keys));
        if (clockJump) {
            text.append("\nThe clock will jump: the sun and seasons follow the new day and the")
                    .append(" at/every triggers are recalculated.");
        }
        text.append("\nApply them to the running game?");
        return text.toString();
    }

    /** Live clock fields of the Game tab (#769): not catalogue settings. */
    public static final String CURRENT_DAY = "Current day";
    public static final String CURRENT_TIME = "Current time";

    /** Tooltips of the live clock fields (#769). */
    public static final String CLOCK_DAY_TOOLTIP = "Minimum: 1";
    public static final String CLOCK_TIME_TOOLTIP = "Allowed: 00:00..23:59";
    public static final String CLOCK_NOT_BEFORE_START = "Allowed: not before the game start";

    /** Confirmation shown before writing the form values into {@code letrain.cfg} (D4). */
    public static final String SAVE_DEFAULTS_TITLE = "Save as defaults";
    public static final String SAVE_DEFAULTS_CONFIRM = "Write these values into letrain.cfg?";

    /** Title of the dialog listing the rejected keys and their reasons. */
    public static final String REJECTED_TITLE = "Settings not applied";

    /** One {@code key — reason} line per rejection, ready for the message dialog. */
    public static String rejectedMessage(Map<String, String> rejections) {
        StringBuilder text = new StringBuilder();
        rejections.forEach((key, reason) -> {
            if (!text.isEmpty()) {
                text.append('\n');
            }
            text.append(key).append(" — ").append(reason);
        });
        return text.toString();
    }

    /** Feedback line of the dialog after a successful action. */
    public static final String APPLIED = "Settings applied.";
    public static final String RESTORED = "Form restored to the current values.";
    public static final String SAVED_DEFAULTS = "Saved as defaults.";
    public static final String SAVE_DEFAULTS_FAILED = "Could not save defaults: ";

    private SettingsText() {}
}
