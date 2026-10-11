package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import letrain.mvp.CoverText;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #743: the 2D cover layout. Pure helper, so it runs without a terminal: the title sits above
 * the train art and the any-key prompt closes the cover.
 */
@DisplayName("2D cover screen lines")
class TerminalViewCoverTest {

    private static final int ROWS = 24;
    /** First content row: (24 - 13 content lines) / 2. */
    private static final int START = 5;

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
        assertEquals(CoverText.PROMPT, lines[START + 2 + CoverText.ASCII_ART.size() + 1].strip());
        assertTrue(lines[0].isEmpty(), "24 rows leave room above the title");
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
