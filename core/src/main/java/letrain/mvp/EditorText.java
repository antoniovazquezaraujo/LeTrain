package letrain.mvp;

/**
 * Wording of the in-game editor window, shared by the 2D terminal and the 3D HUD (issue #740,
 * decision D7): the tabs and the footer buttons must read the same in both clients. The 3D HUD
 * renders the footer buttons in its own uppercase button style, but the words are defined once here
 * so they cannot drift apart.
 *
 * <p>
 * The confirmation shown before rebuilding the world is worded here too, because both clients must
 * warn the player about the same thing: the current world is discarded.
 */
public final class EditorText {

    // ---- Tabs
    public static final String TAB_SCENARIO = "Scenario";
    public static final String TAB_PROGRAMMING = "Programming";
    public static final String TAB_CONFIG = "Config";

    // ---- Footer buttons
    public static final String SAVE = "Save";
    public static final String LOAD = "Load";
    public static final String EXPORT = "Export";
    public static final String IMPORT = "Import";
    public static final String RELOAD_FROM_GAME = "Reload from game";
    public static final String APPLY_PROGRAM = "Apply program";
    public static final String REBUILD_WORLD = "Rebuild world";
    public static final String CLOSE = "Close";

    // ---- Confirmation before rebuilding the world from the scenario
    public static final String REBUILD_CONFIRM_TITLE = "Rebuild world";
    public static final String REBUILD_CONFIRM_MESSAGE =
            "Rebuild the world from the scenario? The current world and any running trains will be"
                    + " discarded.";

    private EditorText() {}
}
