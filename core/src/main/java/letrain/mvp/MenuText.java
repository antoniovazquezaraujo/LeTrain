package letrain.mvp;

/**
 * Presentation-neutral parser for the menu labels defined by the model
 * ({@link Model.GameModeMenuOption#gameModeName()}). The convention shared by both clients is that
 * the first {@code &} marks the following character as the keyboard shortcut ({@code "&Rails"},
 * {@code "S&ensors"}, {@code "Statio&ns"}).
 *
 * <p>
 * The 2D terminal and the 3D HUD build their menu from the same {@link Model#getMenuModel()}
 * entries; parsing the labels here keeps wording, structure and shortcuts from drifting apart.
 * Mapping the parsed parts to colours or markup stays client-specific (Lanterna {@code TextColor}
 * in 2D, libGDX {@code [COLOR]} markup in 3D).
 */
public final class MenuText {

    /**
     * A menu label split into the text before the shortcut, the shortcut itself and the remaining
     * text. {@code "S&ensors"} parses to {@code ("S", "e", "nsors")}.
     */
    public record Label(String prefix, String hotkey, String suffix) {

        /** True when the label declares a keyboard shortcut. */
        public boolean hasHotkey() {
            return !hotkey.isEmpty();
        }

        /** The label text with the {@code &} marker removed, i.e. what the player reads. */
        public String plainText() {
            return prefix + hotkey + suffix;
        }
    }

    private static final char HOTKEY_MARKER = '&';

    private MenuText() {}

    /**
     * Splits {@code rawName} on its shortcut marker. A label without {@code &} keeps its whole text
     * as prefix and has no shortcut. Extra markers are dropped from the suffix, so a malformed
     * label never breaks a client (the 2D renderer used to assume the marker was present).
     */
    public static Label parse(String rawName) {
        String source = rawName == null ? "" : rawName;
        int marker = source.indexOf(HOTKEY_MARKER);
        if (marker < 0) {
            return new Label(source, "", "");
        }
        String prefix = source.substring(0, marker);
        String rest = source.substring(marker + 1);
        if (rest.isEmpty()) {
            return new Label(prefix, "", "");
        }
        String hotkey = rest.substring(0, 1);
        String suffix = rest.substring(1).replace(String.valueOf(HOTKEY_MARKER), "");
        return new Label(prefix, hotkey, suffix);
    }

    /**
     * Hint line shown for the selected mode: the model description plus the Record/Experiment
     * state. Worded once here so the 2D help bar and the 3D description label show the same text
     * (the recording flag comes from the command journal in both clients).
     */
    public static String selectedHint(String description, boolean recording) {
        return description + " | [R]:Record " + (recording ? "ON" : "OFF") + " | [X]:Experiment";
    }
}
