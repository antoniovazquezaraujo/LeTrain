package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextCharacter;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.graphics.TextGraphics;
import java.util.List;
import letrain.mvp.CoverArt;
import letrain.visitor.terminal.TerminalPalette;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Issue #771: painting the coloured braille cover. {@link CoverArtPainter#character} is the pure
 * per-cell step (colour fallback and the dots style) and {@link CoverArtPainter#paint} handles the
 * centred, cropped layout, so both run without a real terminal.
 */
@DisplayName("Coloured cover painting (#771)")
class CoverArtPainterTest {

    private static final TerminalPalette TRUECOLOR =
            new TerminalPalette(TerminalPalette.Depth.TRUECOLOR);

    @Test
    @DisplayName("a missing foreground falls back to the background")
    void should_FallBackToBackground() {
        TextCharacter c = CoverArtPainter.character(new CoverArt.Cell(0x2800, null, 0xFFFFFF),
                TRUECOLOR, CoverStyle.COLOUR);

        assertEquals(0x2800, c.getCharacter());
        assertEquals(TRUECOLOR.colorOf(0xFFFFFF), c.getForegroundColor());
        assertEquals(TRUECOLOR.colorOf(0xFFFFFF), c.getBackgroundColor());
    }

    @Test
    @DisplayName("a missing background falls back to the foreground")
    void should_FallBackToForeground() {
        TextCharacter c = CoverArtPainter.character(new CoverArt.Cell(0x28FF, 0x112233, null),
                TRUECOLOR, CoverStyle.COLOUR);

        assertEquals(TRUECOLOR.colorOf(0x112233), c.getForegroundColor());
        assertEquals(TRUECOLOR.colorOf(0x112233), c.getBackgroundColor());
    }

    @Test
    @DisplayName("a cell with neither colour falls back to black on black")
    void should_FallBackToBlack() {
        TextCharacter c = CoverArtPainter.character(new CoverArt.Cell(' ', null, null), TRUECOLOR,
                CoverStyle.COLOUR);

        assertEquals(TRUECOLOR.colorOf(CoverArtPainter.BLACK), c.getForegroundColor());
        assertEquals(TRUECOLOR.colorOf(CoverArtPainter.BLACK), c.getBackgroundColor());
    }

    @Test
    @DisplayName("both colours are kept when present")
    void should_KeepBothColours() {
        TextCharacter c = CoverArtPainter.character(new CoverArt.Cell('L', 0x00FF00, 0x0000FF),
                TRUECOLOR, CoverStyle.COLOUR);

        assertEquals('L', c.getCharacter());
        assertEquals(TRUECOLOR.colorOf(0x00FF00), c.getForegroundColor());
        assertEquals(TRUECOLOR.colorOf(0x0000FF), c.getBackgroundColor());
    }

    @Test
    @DisplayName("dots style paints a visible braille glyph in colour over the default background")
    void should_PaintBrailleDotsOverDefaultBackground() {
        TextCharacter c = CoverArtPainter.character(new CoverArt.Cell(0x28FF, 0x2A3A2C, null),
                TRUECOLOR, CoverStyle.DOTS);

        assertEquals(0x28FF, c.getCharacter());
        assertEquals(TRUECOLOR.colorOf(0x2A3A2C), c.getForegroundColor());
        assertEquals(TextColor.ANSI.DEFAULT, c.getBackgroundColor(),
                "dots never paint their own background");
    }

    @Test
    @DisplayName("dots style paints a visible ASCII glyph in colour over the default background")
    void should_PaintAsciiDotsOverDefaultBackground() {
        TextCharacter c = CoverArtPainter.character(new CoverArt.Cell('L', 0xDDDDDD, 0x000000),
                TRUECOLOR, CoverStyle.DOTS);

        assertEquals('L', c.getCharacter());
        assertEquals(TRUECOLOR.colorOf(0xDDDDDD), c.getForegroundColor());
        assertEquals(TextColor.ANSI.DEFAULT, c.getBackgroundColor());
    }

    @Test
    @DisplayName("dots style skips blank cells so the terminal background shows through")
    void should_SkipBlankCellsInDotsStyle() {
        // The white "plate" is a blank glyph (space or braille blank) over a white background.
        assertNull(CoverArtPainter.character(new CoverArt.Cell(0x0020, null, 0xFFFFFF), TRUECOLOR,
                CoverStyle.DOTS));
        assertNull(CoverArtPainter.character(new CoverArt.Cell(0x2800, null, 0xFFFFFF), TRUECOLOR,
                CoverStyle.DOTS));
    }

    @Test
    @DisplayName("colour style still paints blank cells (they carry the panel background)")
    void should_PaintBlankCellsInColourStyle() {
        TextCharacter c = CoverArtPainter.character(new CoverArt.Cell(0x2800, null, 0xFFFFFF),
                TRUECOLOR, CoverStyle.COLOUR);

        assertEquals(TRUECOLOR.colorOf(0xFFFFFF), c.getBackgroundColor());
    }

    @Test
    @DisplayName("the art is centred on a larger screen, one character per cell")
    void should_CentreTheArt() {
        CoverArt art = CoverArt.parse(List.of("0041ff0000------" + "0042------0000ff"));
        TextGraphics tg = mock(TextGraphics.class);

        CoverArtPainter.paint(tg, art, TRUECOLOR, new TerminalSize(6, 3), CoverStyle.COLOUR);

        // 2 columns in 6 -> start at 2; 1 row in 3 -> start at 1.
        verify(tg).setCharacter(eq(2), eq(1),
                eq(CoverArtPainter.character(art.row(0).get(0), TRUECOLOR, CoverStyle.COLOUR)));
        verify(tg).setCharacter(eq(3), eq(1),
                eq(CoverArtPainter.character(art.row(0).get(1), TRUECOLOR, CoverStyle.COLOUR)));
    }

    @Test
    @DisplayName("centring floors (size - art) / 2 on both axes, offsetting the first cell")
    void should_CentreWithExactOffsets() {
        // 1x1 art in a 4x4 terminal -> (4 - 1) / 2 = 1 on each axis.
        CoverArt art = CoverArt.parse(List.of("0041ff0000------"));
        TextGraphics tg = mock(TextGraphics.class);

        CoverArtPainter.paint(tg, art, TRUECOLOR, new TerminalSize(4, 4), CoverStyle.COLOUR);

        verify(tg).setCharacter(eq(1), eq(1),
                eq(CoverArtPainter.character(art.row(0).get(0), TRUECOLOR, CoverStyle.COLOUR)));
    }

    @Test
    @DisplayName("the art is drawn from the top-left corner when the terminal is exactly its size")
    void should_StartAtOriginWhenItJustFits() {
        CoverArt art = CoverArt.parse(List.of("0041ff0000------" + "0042------0000ff"));
        TextGraphics tg = mock(TextGraphics.class);

        CoverArtPainter.paint(tg, art, TRUECOLOR, new TerminalSize(2, 1), CoverStyle.COLOUR);

        verify(tg).setCharacter(eq(0), eq(0),
                eq(CoverArtPainter.character(art.row(0).get(0), TRUECOLOR, CoverStyle.COLOUR)));
        verify(tg).setCharacter(eq(1), eq(0),
                eq(CoverArtPainter.character(art.row(0).get(1), TRUECOLOR, CoverStyle.COLOUR)));
    }

    @Test
    @DisplayName("a terminal smaller than the art is cropped symmetrically, never negative coords")
    void should_CropSymmetricallyWhenSmaller() {
        CoverArt art = CoverArt.parse(List.of("0041ff0000------" + "0042------0000ff",
                "0043ff0000------" + "0044------0000ff"));
        TextGraphics tg = mock(TextGraphics.class);

        CoverArtPainter.paint(tg, art, TRUECOLOR, new TerminalSize(1, 1), CoverStyle.COLOUR);

        // 2x2 art in 1x1 -> offset floorDiv(1 - 2, 2) = -1 on both axes: only the last cell
        // survives, at the origin. Nothing off-screen is ever asked of Lanterna.
        verify(tg).setCharacter(eq(0), eq(0),
                eq(CoverArtPainter.character(art.row(1).get(1), TRUECOLOR, CoverStyle.COLOUR)));
        verifyNoMoreInteractions(tg);
    }

    @Test
    @DisplayName("cropping drops the leading columns/rows and keeps the rest in bounds")
    void should_DropLeadingCellsWhenCropped() {
        CoverArt art = CoverArt.parse(List.of(
                "0041ff0000------" + "0042ff0000------" + "0043ff0000------" + "0044ff0000------"));
        TextGraphics tg = mock(TextGraphics.class);

        CoverArtPainter.paint(tg, art, TRUECOLOR, new TerminalSize(3, 1), CoverStyle.COLOUR);

        // 4 columns in 3 -> offset floorDiv(3 - 4, 2) = -1: column 0 is dropped, 1..3 fill 0..2.
        verify(tg).setCharacter(eq(0), eq(0),
                eq(CoverArtPainter.character(art.row(0).get(1), TRUECOLOR, CoverStyle.COLOUR)));
        verify(tg).setCharacter(eq(1), eq(0),
                eq(CoverArtPainter.character(art.row(0).get(2), TRUECOLOR, CoverStyle.COLOUR)));
        verify(tg).setCharacter(eq(2), eq(0),
                eq(CoverArtPainter.character(art.row(0).get(3), TRUECOLOR, CoverStyle.COLOUR)));
        verifyNoMoreInteractions(tg);
    }

    @Test
    @DisplayName("dots style skips blank cells while painting the visible ones")
    void should_PaintOnlyVisibleCellsInDotsStyle() {
        // Blank braille over white, then a visible braille glyph.
        CoverArt art = CoverArt.parse(List.of("2800------ffffff" + "28ffff0000------"));
        TextGraphics tg = mock(TextGraphics.class);

        CoverArtPainter.paint(tg, art, TRUECOLOR, new TerminalSize(2, 1), CoverStyle.DOTS);

        verify(tg).setCharacter(eq(1), eq(0),
                eq(CoverArtPainter.character(art.row(0).get(1), TRUECOLOR, CoverStyle.DOTS)));
        verify(tg, never()).setCharacter(eq(0), anyInt(), any());
        verifyNoMoreInteractions(tg);
    }

    @Test
    @DisplayName("the version row is the one below the art when there is room")
    void should_PlaceVersionBelowArt_When_ThereIsRoom() {
        assertEquals(47, CoverArtPainter.versionRow(3, CoverArt.ROWS, 50));
        assertEquals(6, CoverArtPainter.versionRow(4, 2, 10));
    }

    @Test
    @DisplayName("the version row falls back to the last row when the art is cropped or fills the screen")
    void should_PlaceVersionOnLastRow_When_ArtIsCropped() {
        assertEquals(39, CoverArtPainter.versionRow(-2, CoverArt.ROWS, 40));
        assertEquals(43, CoverArtPainter.versionRow(0, CoverArt.ROWS, 44),
                "no row below an exact fit: the last row is the only visible one");
        assertEquals(0, CoverArtPainter.versionRow(0, 0, 0));
    }

    @Test
    @DisplayName("paintVersion centres the caption just below the art with explicit colours")
    void should_PaintCentredVersionBelowArt() {
        CoverArt art = CoverArt.parse(List.of("0041ff0000------" + "0042------0000ff"));
        TextGraphics tg = mock(TextGraphics.class);

        CoverArtPainter.paintVersion(tg, art, TRUECOLOR, new TerminalSize(10, 10),
                CoverStyle.COLOUR, "v1");

        verify(tg).setForegroundColor(TRUECOLOR.colorOf(0xFFFFFF));
        verify(tg).setBackgroundColor(TRUECOLOR.colorOf(CoverArtPainter.BLACK));
        verify(tg).putString(eq(4), eq(5), eq("v1"));
    }

    @Test
    @DisplayName("paintVersion stays legible in dots style and on a cropped short screen")
    void should_PaintVersionInDotsStyleOnLastRow() {
        CoverArt art = CoverArt.parse(List.of("0041ff0000------" + "0042------0000ff"));
        TextGraphics tg = mock(TextGraphics.class);

        CoverArtPainter.paintVersion(tg, art, TRUECOLOR, new TerminalSize(10, 1), CoverStyle.DOTS,
                "v1");

        verify(tg).putString(eq(4), eq(0), eq("v1"));
    }

    @Test
    @DisplayName("paintVersion clips a caption wider than the terminal and ignores empty screens")
    void should_ClipVersionToTerminalWidth() {
        CoverArt art = CoverArt.parse(List.of("0041ff0000------" + "0042------0000ff"));
        TextGraphics tg = mock(TextGraphics.class);

        CoverArtPainter.paintVersion(tg, art, TRUECOLOR, new TerminalSize(3, 10), CoverStyle.COLOUR,
                "v1.0.0");
        verify(tg).putString(eq(0), eq(5), eq("v1."));

        TextGraphics empty = mock(TextGraphics.class);
        CoverArtPainter.paintVersion(empty, art, TRUECOLOR, new TerminalSize(0, 10),
                CoverStyle.COLOUR, "v1");
        verifyNoMoreInteractions(empty);
    }

    @Test
    @DisplayName("painting rejects null collaborators instead of failing deeper")
    void should_RejectNullArguments() {
        TextGraphics tg = mock(TextGraphics.class);
        CoverArt art = CoverArt.parse(List.of("0041ff0000------"));
        TerminalSize size = new TerminalSize(4, 4);

        assertThrows(NullPointerException.class,
                () -> CoverArtPainter.paint(null, art, TRUECOLOR, size, CoverStyle.COLOUR));
        assertThrows(NullPointerException.class,
                () -> CoverArtPainter.paint(tg, null, TRUECOLOR, size, CoverStyle.COLOUR));
        assertThrows(NullPointerException.class,
                () -> CoverArtPainter.paint(tg, art, null, size, CoverStyle.COLOUR));
        assertThrows(NullPointerException.class,
                () -> CoverArtPainter.paint(tg, art, TRUECOLOR, null, CoverStyle.COLOUR));
        assertThrows(NullPointerException.class,
                () -> CoverArtPainter.paint(tg, art, TRUECOLOR, size, null));
        assertThrows(NullPointerException.class,
                () -> CoverArtPainter.character(null, TRUECOLOR, CoverStyle.COLOUR));
        assertThrows(NullPointerException.class, () -> CoverArtPainter
                .character(new CoverArt.Cell(' ', null, null), null, CoverStyle.COLOUR));
        assertThrows(NullPointerException.class, () -> CoverArtPainter
                .character(new CoverArt.Cell(' ', null, null), TRUECOLOR, null));
    }

    @Test
    @DisplayName("centredOffset floors (size - art) / 2, going negative for a symmetric crop")
    void should_ComputeCentredOffset() {
        assertEquals(2, CoverArtPainter.centredOffset(6, 2), "even slack is split in half");
        assertEquals(1, CoverArtPainter.centredOffset(4, 1),
                "odd slack keeps the extra on the right");
        assertEquals(0, CoverArtPainter.centredOffset(2, 2), "exact fit adds no offset");
        assertEquals(-1, CoverArtPainter.centredOffset(1, 2), "an art wider than the screen crops");
        assertEquals(-1, CoverArtPainter.centredOffset(3, 4),
                "4 in 3 leaves one cell off each side");
    }

    @Test
    @DisplayName("isVisible is true only for real glyphs, never for space or the braille blank")
    void should_DetectVisibleCells() {
        assertFalse(CoverArtPainter.isVisible(new CoverArt.Cell(0x20, null, 0xFFFFFF)), "space");
        assertFalse(CoverArtPainter.isVisible(new CoverArt.Cell(0x2800, null, 0xFFFFFF)),
                "braille blank");
        assertTrue(CoverArtPainter.isVisible(new CoverArt.Cell(0x28FF, 0xFFFFFF, null)),
                "solid braille");
        assertTrue(CoverArtPainter.isVisible(new CoverArt.Cell('L', 0xFFFFFF, null)), "ASCII");
    }

    @Test
    @DisplayName("dots style is centred with the same offsets as the colour panel")
    void should_CentreDotsStyle() {
        CoverArt art = CoverArt.parse(List.of("28ffff0000------" + "28fe0000ff------"));
        TextGraphics tg = mock(TextGraphics.class);

        CoverArtPainter.paint(tg, art, TRUECOLOR, new TerminalSize(6, 3), CoverStyle.DOTS);

        // 2 columns in 6 -> start at 2; 1 row in 3 -> start at 1, same as the colour panel.
        verify(tg).setCharacter(eq(2), eq(1),
                eq(CoverArtPainter.character(art.row(0).get(0), TRUECOLOR, CoverStyle.DOTS)));
        verify(tg).setCharacter(eq(3), eq(1),
                eq(CoverArtPainter.character(art.row(0).get(1), TRUECOLOR, CoverStyle.DOTS)));
    }

    @Test
    @DisplayName("dots style crops symmetrically with a negative offset, all cells in-bounds")
    void should_CropDotsStyleSymmetricallyWhenSmaller() {
        CoverArt art = CoverArt.parse(List.of("28ffff0000------" + "28fe0000ff------",
                "28fd0000ff------" + "28fc0000ff------"));
        TextGraphics tg = mock(TextGraphics.class);

        CoverArtPainter.paint(tg, art, TRUECOLOR, new TerminalSize(1, 1), CoverStyle.DOTS);

        // 2x2 art in 1x1 -> offset -1 on both axes: only the last cell survives at the origin.
        verify(tg).setCharacter(eq(0), eq(0),
                eq(CoverArtPainter.character(art.row(1).get(1), TRUECOLOR, CoverStyle.DOTS)));
        verifyNoMoreInteractions(tg);
    }

    @Test
    @DisplayName("no style ever asks Lanterna to draw outside the screen on a small terminal")
    void should_NeverPaintOutsideTheScreen() {
        CoverArt art = CoverArt.load();
        TerminalSize small = new TerminalSize(40, 12);

        for (CoverStyle style : CoverStyle.values()) {
            TextGraphics tg = mock(TextGraphics.class);

            CoverArtPainter.paint(tg, art, TRUECOLOR, small, style);

            ArgumentCaptor<Integer> columns = ArgumentCaptor.forClass(Integer.class);
            ArgumentCaptor<Integer> rows = ArgumentCaptor.forClass(Integer.class);
            verify(tg, atLeastOnce()).setCharacter(columns.capture(), rows.capture(),
                    any(TextCharacter.class));
            for (int i = 0; i < columns.getAllValues().size(); i++) {
                int x = columns.getAllValues().get(i);
                int y = rows.getAllValues().get(i);
                assertTrue(x >= 0 && x < small.getColumns(), style + " column off-screen: " + x);
                assertTrue(y >= 0 && y < small.getRows(), style + " row off-screen: " + y);
            }
        }
    }

    @Test
    @DisplayName("dots style paints exactly the visible cells of the bundled art (7040 - blanks)")
    void should_PaintTheVisibleBundledCellsInDotsStyle() {
        CoverArt art = CoverArt.load();
        long visible = art.rows().stream().flatMap(List::stream).filter(CoverArtPainter::isVisible)
                .count();
        TextGraphics tg = mock(TextGraphics.class);

        CoverArtPainter.paint(tg, art, TRUECOLOR, new TerminalSize(CoverArt.COLUMNS, CoverArt.ROWS),
                CoverStyle.DOTS);

        // At the exact art size the offset is 0, so every visible cell is in-bounds and painted
        // once, while the blanks (the logo plate) are skipped.
        verify(tg, times((int) visible)).setCharacter(anyInt(), anyInt(), any(TextCharacter.class));
        assertTrue(visible > 0 && visible < (long) art.width() * art.height(),
                "the asset must carry blank cells for the dots style to skip");
    }
}
