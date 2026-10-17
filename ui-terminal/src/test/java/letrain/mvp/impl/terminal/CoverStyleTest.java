package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import letrain.visitor.terminal.TerminalPalette;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #771: the 2D cover style is chosen from the terminal colour depth. Dumb terminals
 * ({@code ANSI_16}: {@code TERM=dumb}, blank or absent) draw dots over the terminal background;
 * every other depth draws the full colour panel.
 */
@DisplayName("2D cover style (#771)")
class CoverStyleTest {

    @Test
    @DisplayName("real-colour terminals paint the full colour panel")
    void should_UseColourOnRealTerminals() {
        assertEquals(CoverStyle.COLOUR,
                CoverStyle.forPalette(new TerminalPalette(TerminalPalette.Depth.TRUECOLOR)));
        assertEquals(CoverStyle.COLOUR,
                CoverStyle.forPalette(new TerminalPalette(TerminalPalette.Depth.INDEXED_256)));
    }

    @Test
    @DisplayName("dumb terminals paint dots over the terminal background")
    void should_UseDotsOnDumbTerminals() {
        assertEquals(CoverStyle.DOTS,
                CoverStyle.forPalette(new TerminalPalette(TerminalPalette.Depth.ANSI_16)));
    }

    @Test
    @DisplayName("a null palette is rejected instead of failing deeper")
    void should_RejectNullPalette() {
        assertThrows(NullPointerException.class, () -> CoverStyle.forPalette(null));
    }
}
