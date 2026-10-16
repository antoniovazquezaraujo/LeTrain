package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import letrain.visitor.terminal.TerminalPalette;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Issue #771: the 2D cover style selection exposed by {@link TerminalView#coverStyle}. It is the
 * view-side entry point to {@link CoverStyle#forPalette}: the full colour panel on real-colour
 * terminals and dots over the terminal background on dumb 16-colour terminals.
 */
@DisplayName("2D cover style selection (#771)")
class TerminalViewCoverStyleTest {

    @Test
    @DisplayName("truecolour and 256-colour terminals get the full colour panel")
    void should_UseThePanelOnRealColourTerminals() {
        assertEquals(CoverStyle.COLOUR,
                TerminalView.coverStyle(new TerminalPalette(TerminalPalette.Depth.TRUECOLOR)));
        assertEquals(CoverStyle.COLOUR,
                TerminalView.coverStyle(new TerminalPalette(TerminalPalette.Depth.INDEXED_256)));
    }

    @Test
    @DisplayName("dumb 16-colour terminals get the dots-only style")
    void should_UseDotsOnDumbTerminals() {
        assertEquals(CoverStyle.DOTS,
                TerminalView.coverStyle(new TerminalPalette(TerminalPalette.Depth.ANSI_16)));
    }

    @Test
    @DisplayName("a null palette is rejected instead of failing deeper")
    void should_RejectNullPalette() {
        assertThrows(NullPointerException.class, () -> TerminalView.coverStyle(null));
    }
}
