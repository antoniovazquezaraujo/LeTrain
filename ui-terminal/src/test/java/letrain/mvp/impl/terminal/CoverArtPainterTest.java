package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextCharacter;
import com.googlecode.lanterna.graphics.TextGraphics;
import java.util.List;
import letrain.mvp.CoverArt;
import letrain.visitor.terminal.TerminalPalette;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #771: painting the coloured braille cover. {@link CoverArtPainter#fits(int, int)} gates the
 * new art against the legacy fallback and {@link CoverArtPainter#character} is the pure
 * colour-fallback step, so both run without a real terminal.
 */
@DisplayName("Coloured cover painting (#771)")
class CoverArtPainterTest {

    private static final TerminalPalette TRUECOLOR =
            new TerminalPalette(TerminalPalette.Depth.TRUECOLOR);

    @Test
    @DisplayName("the coloured art needs a terminal of at least 160x44")
    void should_RequireTheArtSize() {
        assertTrue(CoverArtPainter.fits(CoverArt.COLUMNS, CoverArt.ROWS));
        assertTrue(CoverArtPainter.fits(200, 60));
        assertFalse(CoverArtPainter.fits(CoverArt.COLUMNS - 1, CoverArt.ROWS));
        assertFalse(CoverArtPainter.fits(CoverArt.COLUMNS, CoverArt.ROWS - 1));
        assertFalse(CoverArtPainter.fits(80, 24));
    }

    @Test
    @DisplayName("a missing foreground falls back to the background")
    void should_FallBackToBackground() {
        TextCharacter c = CoverArtPainter.character(new CoverArt.Cell(0x2800, null, 0xFFFFFF),
                TRUECOLOR);

        assertEquals(0x2800, c.getCharacter());
        assertEquals(TRUECOLOR.colorOf(0xFFFFFF), c.getForegroundColor());
        assertEquals(TRUECOLOR.colorOf(0xFFFFFF), c.getBackgroundColor());
    }

    @Test
    @DisplayName("a missing background falls back to the foreground")
    void should_FallBackToForeground() {
        TextCharacter c = CoverArtPainter.character(new CoverArt.Cell(0x28FF, 0x112233, null),
                TRUECOLOR);

        assertEquals(TRUECOLOR.colorOf(0x112233), c.getForegroundColor());
        assertEquals(TRUECOLOR.colorOf(0x112233), c.getBackgroundColor());
    }

    @Test
    @DisplayName("a cell with neither colour falls back to black on black")
    void should_FallBackToBlack() {
        TextCharacter c = CoverArtPainter.character(new CoverArt.Cell(' ', null, null), TRUECOLOR);

        assertEquals(TRUECOLOR.colorOf(CoverArtPainter.BLACK), c.getForegroundColor());
        assertEquals(TRUECOLOR.colorOf(CoverArtPainter.BLACK), c.getBackgroundColor());
    }

    @Test
    @DisplayName("both colours are kept when present")
    void should_KeepBothColours() {
        TextCharacter c = CoverArtPainter.character(new CoverArt.Cell('L', 0x00FF00, 0x0000FF),
                TRUECOLOR);

        assertEquals('L', c.getCharacter());
        assertEquals(TRUECOLOR.colorOf(0x00FF00), c.getForegroundColor());
        assertEquals(TRUECOLOR.colorOf(0x0000FF), c.getBackgroundColor());
    }

    @Test
    @DisplayName("the art is centred on a larger screen, one character per cell")
    void should_CentreTheArt() {
        CoverArt art = CoverArt.parse(List.of("0041ff0000------" + "0042------0000ff"));
        TextGraphics tg = mock(TextGraphics.class);

        CoverArtPainter.paint(tg, art, TRUECOLOR, new TerminalSize(6, 3));

        // 2 columns in 6 -> start at 2; 1 row in 3 -> start at 1.
        verify(tg).setCharacter(eq(2), eq(1), eq(CoverArtPainter.character(art.row(0).get(0),
                TRUECOLOR)));
        verify(tg).setCharacter(eq(3), eq(1), eq(CoverArtPainter.character(art.row(0).get(1),
                TRUECOLOR)));
    }
}
