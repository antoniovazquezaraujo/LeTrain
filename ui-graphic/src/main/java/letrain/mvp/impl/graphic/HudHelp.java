package letrain.mvp.impl.graphic;

/**
 * HUD help levels, mirroring the 2D terminal (Tab cycles 2 → 1 → 0 → 2): <b>full</b>,
 * <b>compact</b> and <b>hidden</b>. The 3D HUD uses it to show or hide its bottom panel.
 */
final class HudHelp {

    static final int FULL = 2;
    static final int COMPACT = 1;
    static final int HIDDEN = 0;

    private HudHelp() {}

    /** Next level in the cycle: full → compact → hidden → full. Out-of-range levels clamp first. */
    static int cycle(int level) {
        int clamped = Math.max(HIDDEN, Math.min(FULL, level));
        return clamped <= HIDDEN ? FULL : clamped - 1;
    }

    /** The bottom panel is hidden at level 0, like the 2D terminal's menu box. */
    static boolean showBottomPanel(int level) {
        return level > HIDDEN;
    }

    /** The long key-help line only shows at the full level, like the 2D terminal's help bar. */
    static boolean showKeyHelp(int level) {
        return level >= FULL;
    }
}
