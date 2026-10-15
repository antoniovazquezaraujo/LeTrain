package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextCharacter;
import com.googlecode.lanterna.graphics.TextGraphics;
import java.util.List;
import letrain.mvp.CoverArt;
import letrain.visitor.terminal.TerminalPalette;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #771: painting the coloured braille cover. {@link CoverArtPainter#useColourArt} gates the
 * new art against the legacy ASCII cover (dumb terminals only) and
 * {@link CoverArtPainter#character} is the pure colour-fallback step, so both run without a real
 * terminal.
 */
@DisplayName("Coloured cover painting (#771)")
class CoverArtPainterTest {

    private static final TerminalPalette TRUECOLOR =
            new TerminalPalette(TerminalPalette.Depth.TRUECOLOR);

    @Test
    @DisplayName("only dumb terminals (ANSI_16) drop the colour art")
    void should_OnlyFallBackOnDumbTerminals() {
        assertTrue(CoverArtPainter.useColourArt(new TerminalPalette(TerminalPalette.Depth.TRUECOLOR)),
                "truecolour keeps the colour art");
        assertTrue(CoverArtPainter.useColourArt(new TerminalPalette(TerminalPalette.Depth.INDEXED_256)),
                "256 colours keep the colour art");
        assertFalse(CoverArtPainter.useColourArt(new TerminalPalette(TerminalPalette.Depth.ANSI_16)),
                "a dumb 16-colour terminal falls back to the ASCII train");
    }

    @Test
    @DisplayName("the dumb-terminal decision rejects a null palette")
    void should_RejectNullPalette() {
        assertThrows(NullPointerException.class, () -> CoverArtPainter.useColourArt(null));
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

    @Test
    @DisplayName("centring floors (size - art) / 2 on both axes, offsetting the first cell")
    void should_CentreWithExactOffsets() {
        // 1x1 art in a 4x4 terminal -> (4 - 1) / 2 = 1 on each axis.
        CoverArt art = CoverArt.parse(List.of("0041ff0000------"));
        TextGraphics tg = mock(TextGraphics.class);

        CoverArtPainter.paint(tg, art, TRUECOLOR, new TerminalSize(4, 4));

        verify(tg).setCharacter(eq(1), eq(1),
                eq(CoverArtPainter.character(art.row(0).get(0), TRUECOLOR)));
    }

    @Test
    @DisplayName("the art is drawn from the top-left corner when the terminal is exactly its size")
    void should_StartAtOriginWhenItJustFits() {
        CoverArt art = CoverArt.parse(List.of("0041ff0000------" + "0042------0000ff"));
        TextGraphics tg = mock(TextGraphics.class);

        CoverArtPainter.paint(tg, art, TRUECOLOR, new TerminalSize(2, 1));

        verify(tg).setCharacter(eq(0), eq(0),
                eq(CoverArtPainter.character(art.row(0).get(0), TRUECOLOR)));
        verify(tg).setCharacter(eq(1), eq(0),
                eq(CoverArtPainter.character(art.row(0).get(1), TRUECOLOR)));
    }

    @Test
    @DisplayName("a terminal smaller than the art is cropped symmetrically, never negative coords")
    void should_CropSymmetricallyWhenSmaller() {
        CoverArt art = CoverArt.parse(List.of("0041ff0000------" + "0042------0000ff",
                "0043ff0000------" + "0044------0000ff"));
        TextGraphics tg = mock(TextGraphics.class);

        CoverArtPainter.paint(tg, art, TRUECOLOR, new TerminalSize(1, 1));

        // 2x2 art in 1x1 -> offset floorDiv(1 - 2, 2) = -1 on both axes: only the last cell
        // survives, at the origin. Nothing off-screen is ever asked of Lanterna.
        verify(tg).setCharacter(eq(0), eq(0),
                eq(CoverArtPainter.character(art.row(1).get(1), TRUECOLOR)));
        verifyNoMoreInteractions(tg);
    }

    @Test
    @DisplayName("cropping drops the leading columns/rows and keeps the rest in bounds")
    void should_DropLeadingCellsWhenCropped() {
        CoverArt art = CoverArt.parse(List.of("0041ff0000------" + "0042ff0000------"
                + "0043ff0000------" + "0044ff0000------"));
        TextGraphics tg = mock(TextGraphics.class);

        CoverArtPainter.paint(tg, art, TRUECOLOR, new TerminalSize(3, 1));

        // 4 columns in 3 -> offset floorDiv(3 - 4, 2) = -1: column 0 is dropped, 1..3 fill 0..2.
        verify(tg).setCharacter(eq(0), eq(0),
                eq(CoverArtPainter.character(art.row(0).get(1), TRUECOLOR)));
        verify(tg).setCharacter(eq(1), eq(0),
                eq(CoverArtPainter.character(art.row(0).get(2), TRUECOLOR)));
        verify(tg).setCharacter(eq(2), eq(0),
                eq(CoverArtPainter.character(art.row(0).get(3), TRUECOLOR)));
        verifyNoMoreInteractions(tg);
    }

    @Test
    @DisplayName("painting rejects null collaborators instead of failing deeper")
    void should_RejectNullArguments() {
        TextGraphics tg = mock(TextGraphics.class);
        CoverArt art = CoverArt.parse(List.of("0041ff0000------"));
        TerminalSize size = new TerminalSize(4, 4);

        assertThrows(NullPointerException.class,
                () -> CoverArtPainter.paint(null, art, TRUECOLOR, size));
        assertThrows(NullPointerException.class,
                () -> CoverArtPainter.paint(tg, null, TRUECOLOR, size));
        assertThrows(NullPointerException.class,
                () -> CoverArtPainter.paint(tg, art, null, size));
        assertThrows(NullPointerException.class,
                () -> CoverArtPainter.paint(tg, art, TRUECOLOR, null));
    }
}
