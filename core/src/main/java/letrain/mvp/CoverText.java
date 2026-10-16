package letrain.mvp;

import java.util.List;

/**
 * Title cover (splash screen) shared by both clients (issue #743). The game starts on this cover
 * and continues into the world on any key. The 2D terminal paints {@link #ASCII_ART} under the
 * title; since #775 the 3D client paints the letter-train capture full screen and shows no text, so
 * only the terminal uses {@link #TITLE} and {@link #PROMPT}.
 *
 * <p>
 * Headless runs, {@code letrain-check} and direct scenario launches skip the cover; they never
 * reach the presenter's {@code start()} by default or disable it explicitly.
 */
public final class CoverText {

    /** Game title shown on the 2D cover. */
    public static final String TITLE = "LeTrain";

    /** Hint under the 2D art: any key starts the game. */
    public static final String PROMPT = "Press any key to continue";

    /** ASCII art of the 2D cover: a small train carrying the game name. */
    public static final List<String> ASCII_ART = List.of("              *   *   *",
            "            *   *   *   *", "      ________________________",
            "     |  LeTrain  Express      |_______", "     |_______________________|       |",
            "  ___|  ___  ___  ___  ___  _|       |", " |   _   _   _   _   _   _   |_______|",
            " |__|_|_|_|_|_|_|_|_|_|_|_|__|_______|", "    (O)      (O)     (O)     (O)");

    private CoverText() {}
}
