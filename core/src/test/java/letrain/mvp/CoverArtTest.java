package letrain.mvp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #771: the coloured braille cover asset bundled by {@code core}. The parser turns the fixed
 * 16-character cells back into codepoints plus optional RGB colours; it is pure, so it runs without
 * a terminal.
 */
@DisplayName("Cover art asset (#771)")
class CoverArtTest {

    @Test
    @DisplayName("the bundled resource parses to the expected 44x160 grid")
    void should_LoadTheBundledArt() {
        CoverArt art = CoverArt.load();

        assertEquals(CoverArt.ROWS, art.height(), "rows");
        assertEquals(CoverArt.COLUMNS, art.width(), "columns");
        for (int y = 0; y < art.height(); y++) {
            assertEquals(CoverArt.COLUMNS, art.row(y).size(), "row " + y + " width");
        }
    }

    @Test
    @DisplayName("every cell carries a printable codepoint and at least one colour")
    void should_DefineUsableCells() {
        CoverArt art = CoverArt.load();

        for (int y = 0; y < art.height(); y++) {
            for (CoverArt.Cell cell : art.row(y)) {
                assertTrue(cell.codePoint() >= 0x20 && cell.codePoint() <= 0x28FF,
                        "codepoint out of the expected range: " + cell);
                assertTrue(cell.fgRgb() != null || cell.bgRgb() != null,
                        "every cell needs at least one colour: " + cell);
            }
        }
    }

    @Test
    @DisplayName("the art carries the plate, the logo and solid blocks")
    void should_ContainTheSignaturePieces() {
        CoverArt art = CoverArt.load();
        List<CoverArt.Cell> flat = art.rows().stream().flatMap(List::stream).toList();

        assertTrue(flat.stream().anyMatch(c -> c.codePoint() == 'L'),
                "the LeTrain slant logo must be in the art");
        assertTrue(flat.stream().anyMatch(c -> c.codePoint() == 'S' && c.fgRgb() != null),
                "the subtitle letters must be in the art");
        // The white "plate" (blank glyph over a white background) opens the picture.
        assertTrue(
                flat.stream().anyMatch(
                        c -> c.codePoint() == 0x2800 && c.bgRgb() == 0xFFFFFF && c.fgRgb() == null),
                "the white plate must be in the art");
        // The no-background cells are the solid braille block.
        assertTrue(flat.stream().anyMatch(c -> c.codePoint() == 0x28FF && c.bgRgb() == null),
                "the solid braille blocks must be in the art");
    }

    @Test
    @DisplayName("the parser keeps absent colours as null and decodes present ones")
    void should_ParseColours() {
        CoverArt art = CoverArt.parse(List.of("2800------ffffff" + "0041ff0000------"));
        CoverArt.Cell blank = art.row(0).get(0);
        CoverArt.Cell letter = art.row(0).get(1);

        assertEquals(0x2800, blank.codePoint());
        assertNull(blank.fgRgb(), "an absent foreground must stay null");
        assertEquals(0xFFFFFF, blank.bgRgb());
        assertEquals('A', letter.codePoint());
        assertEquals(0xFF0000, letter.fgRgb());
        assertNull(letter.bgRgb(), "an absent background must stay null");
    }

    @Test
    @DisplayName("a malformed line is rejected instead of silently ignored")
    void should_RejectMalformedLines() {
        assertThrows(IllegalArgumentException.class, () -> CoverArt.parse(List.of("2800ffffff")));
        assertThrows(IllegalArgumentException.class,
                () -> CoverArt.parse(List.of("zzzz------ffffff")));
        assertThrows(IllegalArgumentException.class,
                () -> CoverArt.parse(List.of("2800------zzzzzz")));
    }

    @Test
    @DisplayName("width follows the widest row, so an uneven file still renders")
    void should_UseTheWidestRow() {
        CoverArt art =
                CoverArt.parse(List.of("2800------ffffff", "2800------ffffff2800------ffffff"));

        assertEquals(2, art.height());
        assertEquals(2, art.width());
        assertNotNull(art.rows());
        assertFalse(art.rows().isEmpty());
    }

    @Test
    @DisplayName("a null reader or null line list is rejected, an empty source yields an empty art")
    void should_RejectNullSourcesAndAllowEmpty() {
        assertThrows(NullPointerException.class, () -> CoverArt.parse((Reader) null));
        assertThrows(NullPointerException.class, () -> CoverArt.parse((List<String>) null));

        CoverArt empty = CoverArt.parse(List.of());
        assertEquals(0, empty.height());
        assertEquals(0, empty.width());
        assertTrue(empty.rows().isEmpty());
        assertThrows(IndexOutOfBoundsException.class, () -> empty.row(0));
    }

    @Test
    @DisplayName("a blank line is rejected, like any run that is not a whole number of cells")
    void should_RejectBlankAndMisalignedLines() {
        // "" + "" joins to a single blank line before EOF.
        assertThrows(IllegalArgumentException.class, () -> CoverArt.parse(List.of("", "")));
        // 10, 15 and 17 chars are not multiples of the 16-char cell.
        assertThrows(IllegalArgumentException.class, () -> CoverArt.parse(List.of("2800ffffff")));
        assertThrows(IllegalArgumentException.class,
                () -> CoverArt.parse(List.of("2800------fffff")));
        assertThrows(IllegalArgumentException.class,
                () -> CoverArt.parse(List.of("2800------ffffff2")));
    }

    @Test
    @DisplayName("invalid hex in any of the three fields is rejected with the raw field")
    void should_RejectInvalidHexInEveryField() {
        assertThrows(IllegalArgumentException.class,
                () -> CoverArt.parse(List.of("zzzz------ffffff")));
        assertThrows(IllegalArgumentException.class,
                () -> CoverArt.parse(List.of("2800zzzzzzffffff")));
        assertThrows(IllegalArgumentException.class,
                () -> CoverArt.parse(List.of("2800------zzzzzz")));
    }

    @Test
    @DisplayName("a failing reader surfaces as an UncheckedIOException, never a silent empty art")
    void should_WrapReaderFailures() {
        Reader failing = new Reader() {
            @Override
            public int read(char[] cbuf, int off, int len) throws IOException {
                throw new IOException("boom");
            }

            @Override
            public void close() {
                // nothing to release
            }
        };

        assertThrows(UncheckedIOException.class, () -> CoverArt.parse(failing));
    }

    @Test
    @DisplayName("braille, ASCII and the space codepoint are all accepted")
    void should_AcceptPrintableCodepoints() {
        CoverArt art = CoverArt.parse(List.of(
                "2800------ffffff" + "28ff------ffffff" + "0041------ffffff" + "0020------ffffff"));

        assertEquals(0x2800, art.row(0).get(0).codePoint(), "braille blank");
        assertEquals(0x28FF, art.row(0).get(1).codePoint(), "braille solid");
        assertEquals('A', art.row(0).get(2).codePoint(), "ASCII");
        assertEquals(0x20, art.row(0).get(3).codePoint(), "space");
    }

    @Test
    @DisplayName("a cell rejects a negative codepoint and out-of-range RGB values")
    void should_RestrictCellRanges() {
        assertThrows(IllegalArgumentException.class, () -> new CoverArt.Cell(-1, null, 0xFFFFFF));
        assertThrows(IllegalArgumentException.class, () -> new CoverArt.Cell('A', 0x1000000, null));
        assertThrows(IllegalArgumentException.class, () -> new CoverArt.Cell('A', null, -1));
    }

    @Test
    @DisplayName("the declared grid constants match the asset format")
    void should_DeclareTheGridConstants() {
        assertEquals(160, CoverArt.COLUMNS);
        assertEquals(44, CoverArt.ROWS);
        assertEquals(16, CoverArt.CELL_CHARS);
        assertEquals(6, CoverArt.COLOR_CHARS);
        assertEquals(4, CoverArt.CODE_POINT_CHARS);
        assertEquals("------", CoverArt.NO_COLOR);
    }

    /** Cells that carry real ink: neither a braille glyph nor the space codepoint. */
    private static boolean isTextCell(CoverArt.Cell cell) {
        return !(cell.codePoint() >= 0x2800 && cell.codePoint() <= 0x28FF)
                && cell.codePoint() != 0x20;
    }

    @Test
    @DisplayName("the asset spells the slant logo and the subtitle with exactly 128 text cells")
    void should_CarryTheSlantLogoAndSubtitle() {
        CoverArt art = CoverArt.load();
        List<CoverArt.Cell> flat = art.rows().stream().flatMap(List::stream).toList();

        List<CoverArt.Cell> text = flat.stream().filter(CoverArtTest::isTextCell).toList();
        assertEquals(128, text.size(), "the logo strokes plus the subtitle are exactly 128 cells");

        String spelled = text.stream().map(c -> new String(Character.toChars(c.codePoint())))
                .collect(Collectors.joining());
        assertTrue(spelled.endsWith("TheLetterTrainSimulator"),
                "the subtitle must spell itself without spaces: " + spelled);
        String logo = spelled.substring(0, spelled.length() - "TheLetterTrainSimulator".length());
        assertTrue(logo.contains("/") && logo.contains("\\"),
                "the slant logo is drawn with diagonal strokes: " + logo);
        assertTrue(logo.contains("_"), "the slant logo needs its baseline strokes: " + logo);
    }
}
