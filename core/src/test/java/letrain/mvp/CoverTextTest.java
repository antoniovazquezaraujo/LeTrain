package letrain.mvp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #743: the title cover shown at startup. Both clients share the title and the any-key
 * prompt; the 2D terminal paints the coloured braille art ({@link CoverArt}) instead of text.
 */
@DisplayName("Cover wording (#743)")
class CoverTextTest {

    @Test
    @DisplayName("title and prompt are defined once for the clients")
    void should_DefineTheCoverText() {
        assertEquals("LeTrain", CoverText.TITLE);
        assertEquals("Press any key to continue", CoverText.PROMPT);
    }
}
