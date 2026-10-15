package letrain.mvp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #771: the coloured braille cover asset bundled by {@code core}. The parser turns the
 * fixed 16-character cells back into codepoints plus optional RGB colours; it is pure, so it runs
 * without a terminal.
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
        assertTrue(flat.stream().anyMatch(c -> c.codePoint() == 0x2800 && c.bgRgb() == 0xFFFFFF
                && c.fgRgb() == null), "the white plate must be in the art");
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
        CoverArt art = CoverArt.parse(List.of("2800------ffffff", "2800------ffffff2800------ffffff"));

        assertEquals(2, art.height());
        assertEquals(2, art.width());
        assertNotNull(art.rows());
        assertFalse(art.rows().isEmpty());
    }
}
