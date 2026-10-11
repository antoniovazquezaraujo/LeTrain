package letrain.mvp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #743: the title cover shown at startup. Both clients share the title and the any-key
 * prompt; the 2D terminal paints the ASCII train art.
 */
@DisplayName("Cover wording (#743)")
class CoverTextTest {

    @Test
    @DisplayName("title, prompt and art are defined once for both clients")
    void should_DefineTheCoverText() {
        assertEquals("LeTrain", CoverText.TITLE);
        assertEquals("Press any key to continue", CoverText.PROMPT);
    }

    @Test
    @DisplayName("the 2D art carries the train under the title")
    void should_DefineTheAsciiArt() {
        assertFalse(CoverText.ASCII_ART.isEmpty(), "the 2D cover needs its train art");
        assertTrue(CoverText.ASCII_ART.stream().anyMatch(line -> line.contains("LeTrain")),
                "the art must show the game name: " + CoverText.ASCII_ART);
        assertTrue(CoverText.ASCII_ART.stream().anyMatch(line -> line.contains("(O)")),
                "the art must show the train wheels: " + CoverText.ASCII_ART);
    }
}
