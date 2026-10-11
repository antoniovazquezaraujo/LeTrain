package letrain.mvp;

/**
 * Shared wording of the New Game flow (2D dialog today, 3D parity in S4). Both clients must read
 * the same words so the 2D terminal and the 3D HUD cannot drift apart (same convention as
 * {@link FileMenuText}, {@link CoverText} and {@link EditorText}).
 */
public final class NewGameText {

    /** Dialog title. */
    public static final String TITLE = "New Game";

    /** Seed row. */
    public static final String SEED_LABEL = "Seed";

    /** Button that draws a fresh random seed. */
    public static final String NEW_SEED = "new";

    /** Terrain slider labels. */
    public static final String WATER_LABEL = "Water %";
    public static final String MOUNTAIN_LABEL = "Mountain %";

    /** Dialog actions (same words as the rest of the UI). */
    public static final String START = "Start";
    public static final String CANCEL = "Cancel";

    /** Confirmation shown before discarding a game in progress (simple flow until #417). */
    public static final String CONFIRM_TITLE = "New Game";
    public static final String CONFIRM_MESSAGE =
            "Discard the current game and start a new one?\nUnsaved changes will be lost.";

    /** Error dialog title when a catalog value does not validate on Start. */
    public static final String INVALID_TITLE = "Invalid setting";

    /** Shown on the terrain section when the terminal cannot fit a preview (< 80x24). */
    public static final String NO_PREVIEW =
            "No preview (needs 80x24+): target and measured are shown above.";

    /** Target line of the terrain block; always visible, preview or not. */
    public static String targetLine(int waterPercent, int mountainPercent) {
        return "Target: water " + waterPercent + "% · mountain " + mountainPercent + "% · field "
                + fieldPercent(waterPercent, mountainPercent) + "%";
    }

    /**
     * Measured proportions appended to the target line (same line, compact slashes, so the whole
     * block still fits a 70-column terminal; #749). Always visible, preview or not.
     */
    public static String measuredSuffix(int waterPercent, int mountainPercent) {
        return "  measured: " + waterPercent + "%/" + mountainPercent + "%/"
                + fieldPercent(waterPercent, mountainPercent) + "%";
    }

    /** Target + measured in one line: what the player reads under the 100 % bar. */
    public static String terrainStatusLine(int targetWater, int targetMountain, int measuredWater,
            int measuredMountain) {
        return targetLine(targetWater, targetMountain)
                + measuredSuffix(measuredWater, measuredMountain);
    }

    /** Field proportion: what is left after water and mountain (the 100 % bar's remainder). */
    public static int fieldPercent(int waterPercent, int mountainPercent) {
        return Math.max(0, 100 - waterPercent - mountainPercent);
    }

    /** Status bar notice after a game was started from the dialog. */
    public static String startedMessage(int seed) {
        return "New game started (seed " + seed + ")";
    }

    private NewGameText() {}
}
