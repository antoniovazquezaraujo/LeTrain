package letrain.mvp.impl.terminal;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextCharacter;
import com.googlecode.lanterna.graphics.TextGraphics;
import java.util.List;
import letrain.mvp.CoverArt;
import letrain.mvp.CoverArt.Cell;
import letrain.visitor.terminal.TerminalPalette;
import letrain.utils.ValidationUtils;

/**
 * Paints the coloured braille cover art ({@link CoverArt}) on a Lanterna surface (issue #771, arc
 * #742). It is the only 2D cover code that knows about Lanterna, and it reuses
 * {@link TerminalPalette#colorOf(int)} so the art degrades the same way the map does on 24-bit,
 * 256-colour and 16-colour terminals.
 *
 * <p>
 * Colour fallback mirrors the reference ANSI render: a cell with no foreground uses its background
 * (or black), and a cell with no background uses its foreground (or black). In the bundled art the
 * no-background cells are all the solid braille block {@code U+28FF}, so fg == bg still paints a
 * filled cell.
 */
final class CoverArtPainter {

    /** Fallback colour when a cell carries neither a foreground nor a background. */
    static final int BLACK = 0x000000;

    private CoverArtPainter() {}

    /**
     * Whether a terminal of this size can show the whole art without cropping it. Below
     * {@link CoverArt#COLUMNS}×{@link CoverArt#ROWS} the caller must fall back to the legacy ASCII
     * art.
     */
    static boolean fits(int columns, int rows) {
        return columns >= CoverArt.COLUMNS && rows >= CoverArt.ROWS;
    }

    /**
     * Draws the art centred on {@code size}; every cell is painted, so the artwork keeps its exact
     * shape (no per-line shearing).
     */
    static void paint(TextGraphics tg, CoverArt art, TerminalPalette palette, TerminalSize size) {
        ValidationUtils.requireNonNull(tg, "tg must not be null");
        ValidationUtils.requireNonNull(art, "art must not be null");
        ValidationUtils.requireNonNull(palette, "palette must not be null");
        ValidationUtils.requireNonNull(size, "size must not be null");
        int startColumn = Math.max(0, (size.getColumns() - art.width()) / 2);
        int startRow = Math.max(0, (size.getRows() - art.height()) / 2);
        for (int y = 0; y < art.height() && startRow + y < size.getRows(); y++) {
            List<Cell> row = art.row(y);
            for (int x = 0; x < row.size() && startColumn + x < size.getColumns(); x++) {
                tg.setCharacter(startColumn + x, startRow + y, character(row.get(x), palette));
            }
        }
    }

    /** The Lanterna character (glyph + colours) for one cover cell, applying the fallback rules. */
    static TextCharacter character(Cell cell, TerminalPalette palette) {
        int fg = cell.fgRgb() != null ? cell.fgRgb()
                : cell.bgRgb() != null ? cell.bgRgb() : BLACK;
        int bg = cell.bgRgb() != null ? cell.bgRgb()
                : cell.fgRgb() != null ? cell.fgRgb() : BLACK;
        return TextCharacter.fromCharacter((char) cell.codePoint(), palette.colorOf(fg),
                palette.colorOf(bg))[0];
    }
}
