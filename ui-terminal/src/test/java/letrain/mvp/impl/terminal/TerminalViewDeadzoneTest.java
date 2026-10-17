package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import letrain.map.Point;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Camera dead-zone math of the 2D view (issue #697): the scroll window of {@code ensureVisible} and
 * the flash rectangle. Pure helpers, so they run without a terminal.
 */
@DisplayName("2D camera dead zone: scroll and flash math")
class TerminalViewDeadzoneTest {

    private static final int COLS = 80;
    private static final int ROWS = 30;
    private static final Point SCROLL = new Point(100, 50);

    @Test
    @DisplayName("a full-screen dead zone keeps the viewport while the target is on screen")
    void fullScreenDeadzone_doesNotScroll_whileTargetIsVisible() {
        assertEquals(SCROLL, TerminalView.scrollToKeepVisible(SCROLL, COLS, ROWS, 100, 50,
                TerminalView.FULL_SCREEN_DEADZONE, false), "top-left corner is inside");
        assertEquals(SCROLL, TerminalView.scrollToKeepVisible(SCROLL, COLS, ROWS, 140, 65,
                TerminalView.FULL_SCREEN_DEADZONE, false), "the centre is inside");
        assertEquals(SCROLL,
                TerminalView.scrollToKeepVisible(SCROLL, COLS, ROWS, 100 + COLS - 1, 50 + ROWS - 1,
                        TerminalView.FULL_SCREEN_DEADZONE, false),
                "the last visible cell is still inside");
    }

    @Test
    @DisplayName("a full-screen dead zone scrolls by the overflow once the target leaves the screen")
    void fullScreenDeadzone_scrollsAtTheBorder() {
        assertEquals(new Point(101, 51),
                TerminalView.scrollToKeepVisible(SCROLL, COLS, ROWS, 100 + COLS, 50 + ROWS,
                        TerminalView.FULL_SCREEN_DEADZONE, false),
                "one cell past the bottom-right edge scrolls by exactly one");
        assertEquals(new Point(99, 49),
                TerminalView.scrollToKeepVisible(SCROLL, COLS, ROWS, 99, 49,
                        TerminalView.FULL_SCREEN_DEADZONE, false),
                "one cell before the top-left edge scrolls by exactly one");
    }

    @Test
    @DisplayName("a small dead zone keeps the target inside its box")
    void smallDeadzone_scrollsOnlyWhenLeavingTheBox() {
        // Radius 5 around the viewport centre (140, 65): x 127..153, y 60..70.
        assertEquals(SCROLL,
                TerminalView.scrollToKeepVisible(SCROLL, COLS, ROWS, 153, 70, 5, false));
        assertEquals(new Point(101, 50),
                TerminalView.scrollToKeepVisible(SCROLL, COLS, ROWS, 154, 70, 5, false));
        assertEquals(new Point(100, 51),
                TerminalView.scrollToKeepVisible(SCROLL, COLS, ROWS, 140, 71, 5, false));
    }

    @Test
    @DisplayName("the full-screen flash rectangle is the map border, without overshooting")
    void fullScreenFlash_isTheMapBorder() {
        assertArrayEquals(new int[] {0, 0, COLS - 1, ROWS - 1},
                TerminalView.deadzoneRect(COLS, ROWS, TerminalView.FULL_SCREEN_DEADZONE));
    }

    @Test
    @DisplayName("small flash rectangles stay inside the map")
    void smallFlash_clampsToTheMap() {
        // Radius 5 on an 80x30 map: centre (40, 15), radiusX = round(5 * 80 / 30) = 13.
        assertArrayEquals(new int[] {27, 10, 53, 20}, TerminalView.deadzoneRect(COLS, ROWS, 5));
        // A radius that would overflow the map is clamped to its border.
        assertArrayEquals(new int[] {0, 0, COLS - 1, ROWS - 1},
                TerminalView.deadzoneRect(COLS, ROWS, 25));
    }
}
