package letrain.mvp.impl.graphic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("3D HUD help levels (2D parity)")
class HudHelpTest {

    @Test
    @DisplayName("Tab cycles full -> compact -> hidden -> full")
    void should_CycleLevels() {
        assertEquals(HudHelp.COMPACT, HudHelp.cycle(HudHelp.FULL));
        assertEquals(HudHelp.HIDDEN, HudHelp.cycle(HudHelp.COMPACT));
        assertEquals(HudHelp.FULL, HudHelp.cycle(HudHelp.HIDDEN));
    }

    @Test
    @DisplayName("out-of-range levels clamp before cycling")
    void should_FallBackToFull() {
        assertEquals(HudHelp.FULL, HudHelp.cycle(-3),
                "below hidden: clamps to hidden and cycles to full");
        assertEquals(HudHelp.COMPACT, HudHelp.cycle(9),
                "above full: clamps to full and cycles down");
    }

    @Test
    @DisplayName("the bottom panel hides only at level 0; the key help only shows at full")
    void should_MapVisibility() {
        assertTrue(HudHelp.showBottomPanel(HudHelp.FULL));
        assertTrue(HudHelp.showBottomPanel(HudHelp.COMPACT));
        assertFalse(HudHelp.showBottomPanel(HudHelp.HIDDEN));

        assertTrue(HudHelp.showKeyHelp(HudHelp.FULL));
        assertFalse(HudHelp.showKeyHelp(HudHelp.COMPACT));
        assertFalse(HudHelp.showKeyHelp(HudHelp.HIDDEN));
    }
}
