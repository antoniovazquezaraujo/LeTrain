package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import letrain.mvp.CoverArt;
import letrain.mvp.CoverText;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #743/#771: the 2D cover layout. Pure helper, so it runs without a terminal: the title sits
 * above the legacy train art used when the coloured braille cover does not fit. There is no
 * "press any key" text in 2D (owner decision); any key still continues.
 */
@DisplayName("2D cover screen lines")
class TerminalViewCoverTest {

    private static final int ROWS = 24;
    /** Content is title + blank + art (11 lines); first row is (24 - 11) / 2. */
    private static final int START = 6;

    @Test
    @DisplayName("the coloured art is chosen from 160x44, the ASCII fallback below it")
    void should_ChooseTheArtBySize() {
        assertTrue(TerminalView.usesColourCover(CoverArt.COLUMNS, CoverArt.ROWS),
                "the exact art size must show the colour cover");
        assertFalse(TerminalView.usesColourCover(CoverArt.COLUMNS - 1, CoverArt.ROWS),
                "one column short falls back to the ASCII train");
        assertFalse(TerminalView.usesColourCover(CoverArt.COLUMNS, CoverArt.ROWS - 1),
                "one row short falls back to the ASCII train");
        assertFalse(TerminalView.usesColourCover(80, 24), "a classic 80x24 terminal uses ASCII");
    }

    @Test
    @DisplayName("the cover is vertically centred and keeps the shared order")
    void should_CentreTheCoverContent() {
        String[] lines = TerminalView.coverScreenLines(ROWS);

        assertEquals(ROWS, lines.length);
        assertEquals(CoverText.TITLE, lines[START].strip());
        // The art keeps its internal left offsets (only right padding is added) so the train is
        // not sheared by per-line centring.
        for (int i = 0; i < CoverText.ASCII_ART.size(); i++) {
            assertEquals(CoverText.ASCII_ART.get(i), lines[START + 2 + i].stripTrailing(),
                    "art line " + i);
        }
        assertTrue(lines[0].isEmpty(), "24 rows leave room above the title");
    }

    @Test
    @DisplayName("2D shows no 'press any key' hint anymore")
    void should_NotShowThePrompt() {
        String[] lines = TerminalView.coverScreenLines(ROWS);

        for (String line : lines) {
            assertFalse(line.contains(CoverText.PROMPT),
                    "the 2D cover must not carry the any-key prompt: '" + line + "'");
        }
    }

    @Test
    @DisplayName("the padded block keeps every content line at the same width")
    void should_AlignTheWholeBlock() {
        String[] lines = TerminalView.coverScreenLines(ROWS);

        int width = lines[START].length();
        for (String line : lines) {
            if (!line.isBlank()) {
                assertEquals(width, line.length(),
                        "every content line is padded to the block width so the art cannot shear");
            }
        }
    }

    @Test
    @DisplayName("a tiny terminal clamps the cover instead of overflowing")
    void should_ClampToTheScreen() {
        String[] lines = TerminalView.coverScreenLines(4);

        assertEquals(4, lines.length);
        assertTrue(lines[0].contains(CoverText.TITLE));
    }
}
