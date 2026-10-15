package letrain.mvp;

import java.util.List;

/**
 * Title cover (splash screen) shared by both clients (issue #743). The game starts on this cover
 * and continues into the world on any key. The 2D terminal paints the coloured braille art
 * ({@link CoverArt}, issue #771) and only falls back to {@link #ASCII_ART} on dumb terminals (no
 * colour depth); there is no any-key prompt text in 2D. The 3D client renders {@link #TITLE} and
 * {@link #PROMPT} as text.
 *
 * <p>
 * Headless runs, {@code letrain-check} and direct scenario launches skip the cover; they never
 * reach the presenter's {@code start()} by default or disable it explicitly.
 */
public final class CoverText {

    /** Game title shown on the cover in both clients. */
    public static final String TITLE = "LeTrain";

    /** Hint under the art: any key starts the game (3D client only; the 2D cover has no text). */
    public static final String PROMPT = "Press any key to continue";

    /**
     * Legacy ASCII art of the 2D cover: a small train carrying the game name. Kept as the fallback
     * for dumb terminals only (no colour depth).
     */
    public static final List<String> ASCII_ART = List.of("              *   *   *",
            "            *   *   *   *", "      ________________________",
            "     |  LeTrain  Express      |_______", "     |_______________________|       |",
            "  ___|  ___  ___  ___  ___  _|       |", " |   _   _   _   _   _   _   |_______|",
            " |__|_|_|_|_|_|_|_|_|_|_|_|__|_______|", "    (O)      (O)     (O)     (O)");

    private CoverText() {}
}
