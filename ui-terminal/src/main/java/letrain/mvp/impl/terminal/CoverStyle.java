package letrain.mvp.impl.terminal;

import letrain.utils.ValidationUtils;
import letrain.visitor.terminal.TerminalPalette;

/**
 * How the 2D title cover art is painted (issue #771). It is derived from the terminal colour depth
 * once, so the painter stays a pure function of the style.
 *
 * <p>
 * On a real-colour terminal the picture is painted whole (glyph, background and foreground). On a
 * dumb terminal (16 ANSI colours, where {@code TERM=dumb}, blank or absent lands) the owner found
 * the full panel posterises into blocks; drawing only the coloured braille/ASCII dots over the
 * terminal's own black background reads far better.
 */
enum CoverStyle {

    /** Full panel: every cell paints its glyph, its background and its foreground. */
    COLOUR,

    /** Dots only: visible glyphs in colour over the terminal's default background. */
    DOTS;

    /**
     * Style for the detected terminal: {@link #DOTS} on dumb terminals, {@link #COLOUR} otherwise.
     */
    static CoverStyle forPalette(TerminalPalette palette) {
        ValidationUtils.requireNonNull(palette, "palette must not be null");
        return palette.depth() == TerminalPalette.Depth.ANSI_16 ? DOTS : COLOUR;
    }
}
