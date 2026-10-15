package letrain.mvp.impl.terminal;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextCharacter;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import java.util.List;
import letrain.mvp.CoverArt;
import letrain.mvp.CoverArt.Cell;
import letrain.utils.ValidationUtils;
import letrain.visitor.terminal.TerminalPalette;

/**
 * Paints the coloured braille cover art ({@link CoverArt}) on a Lanterna surface (issue #771, arc
 * #742). It is the only 2D cover code that knows about Lanterna, and it reuses
 * {@link TerminalPalette#colorOf(int)} so the art degrades the same way the map does on 24-bit,
 * 256-colour and 16-colour terminals.
 *
 * <p>
 * Two styles ({@link CoverStyle}): {@link CoverStyle#COLOUR} paints every cell with its glyph and
 * both colours; {@link CoverStyle#DOTS} paints only the visible glyphs in colour over the
 * terminal's default background (blank cells are skipped, so the white "plate" of the logo leaves
 * clean holes). The style is chosen from the colour depth by {@link CoverStyle#forPalette}.
 *
 * <p>
 * Colour fallback mirrors the reference ANSI render: a cell with no foreground uses its background
 * (or black), and a cell with no background uses its foreground (or black). In the bundled art the
 * no-background cells are all the solid braille block {@code U+28FF}, so fg == bg still paints a
 * filled cell.
 *
 * <p>
 * Size is not a reason to drop the artwork (owner decision, 16-oct): the picture is always drawn,
 * centred, and symmetrically cropped when the terminal is smaller than
 * {@link CoverArt#COLUMNS}×{@link CoverArt#ROWS}.
 */
final class CoverArtPainter {

    /** Fallback colour when a cell carries neither a foreground nor a background. */
    static final int BLACK = 0x000000;

    private static final int SPACE = 0x20;

    /** Braille blank pattern ({@code U+2800}): a blank glyph that still carries its background. */
    private static final int BRAILLE_BLANK = 0x2800;

    private CoverArtPainter() {}

    /**
     * Offset of a centred extent of {@code artExtent} inside a screen extent of {@code screenExtent}
     * (floor division, so cropping a picture that does not fit is symmetric and may be negative).
     */
    static int centredOffset(int screenExtent, int artExtent) {
        return Math.floorDiv(screenExtent - artExtent, 2);
    }

    /** Whether a cell carries an actual glyph; blank cells (space or braille blank) do not. */
    static boolean isVisible(Cell cell) {
        return cell.codePoint() != SPACE && cell.codePoint() != BRAILLE_BLANK;
    }

    /**
     * Draws the art centred on {@code size} in the given {@code style}. Every in-bounds cell is
     * painted, so the artwork keeps its exact shape (no per-line shearing); cells that fall outside
     * the screen after centring are skipped (never asked of Lanterna with negative coordinates).
     */
    static void paint(TextGraphics tg, CoverArt art, TerminalPalette palette, TerminalSize size,
            CoverStyle style) {
        ValidationUtils.requireNonNull(tg, "tg must not be null");
        ValidationUtils.requireNonNull(art, "art must not be null");
        ValidationUtils.requireNonNull(palette, "palette must not be null");
        ValidationUtils.requireNonNull(size, "size must not be null");
        ValidationUtils.requireNonNull(style, "style must not be null");
        int startColumn = centredOffset(size.getColumns(), art.width());
        int startRow = centredOffset(size.getRows(), art.height());
        for (int y = 0; y < art.height(); y++) {
            int screenRow = startRow + y;
            if (screenRow < 0 || screenRow >= size.getRows()) {
                continue;
            }
            List<Cell> row = art.row(y);
            for (int x = 0; x < row.size(); x++) {
                int screenColumn = startColumn + x;
                if (screenColumn < 0 || screenColumn >= size.getColumns()) {
                    continue;
                }
                TextCharacter character = character(row.get(x), palette, style);
                if (character != null) {
                    tg.setCharacter(screenColumn, screenRow, character);
                }
            }
        }
    }

    /**
     * The Lanterna character (glyph + colours) for one cover cell, applying the fallback rules and
     * the style. Returns {@code null} in {@link CoverStyle#DOTS} for a blank cell: nothing must be
     * painted there, so the terminal background shows through.
     */
    static TextCharacter character(Cell cell, TerminalPalette palette, CoverStyle style) {
        ValidationUtils.requireNonNull(cell, "cell must not be null");
        ValidationUtils.requireNonNull(palette, "palette must not be null");
        ValidationUtils.requireNonNull(style, "style must not be null");
        int fg = cell.fgRgb() != null ? cell.fgRgb()
                : cell.bgRgb() != null ? cell.bgRgb() : BLACK;
        if (style == CoverStyle.DOTS) {
            if (!isVisible(cell)) {
                return null;
            }
            return TextCharacter.fromCharacter((char) cell.codePoint(), palette.colorOf(fg),
                    TextColor.ANSI.DEFAULT)[0];
        }
        int bg = cell.bgRgb() != null ? cell.bgRgb()
                : cell.fgRgb() != null ? cell.fgRgb() : BLACK;
        return TextCharacter.fromCharacter((char) cell.codePoint(), palette.colorOf(fg),
                palette.colorOf(bg))[0];
    }
}
