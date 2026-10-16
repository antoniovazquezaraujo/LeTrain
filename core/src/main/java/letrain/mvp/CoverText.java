package letrain.mvp;

/**
 * Title cover (splash screen) shared by both clients (issue #743). The game starts on this cover
 * and continues into the world on any key. The 2D terminal paints the coloured braille art
 * ({@link CoverArt}, issue #771) — full colour, or dots on dumb terminals — and shows no any-key
 * prompt text; the 3D client renders {@link #TITLE} and {@link #PROMPT} as text.
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

    private CoverText() {}
}
