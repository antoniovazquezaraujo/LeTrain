package letrain.mvp.impl.terminal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("2D terminal: overlay word-wrap")
class TerminalViewWrapTest {

    @Test
    @DisplayName("lines shorter than the width are untouched")
    void shortLines() {
        assertEquals(List.of("hello", "world"),
                TerminalView.wrapLines(new String[] {"hello", "world"}, 10));
    }

    @Test
    @DisplayName("wraps at the last space that fits")
    void wrapsAtSpaces() {
        assertEquals(List.of("the quick", "brown fox"),
                TerminalView.wrapLines(new String[] {"the quick brown fox"}, 10));
    }

    @Test
    @DisplayName("hard-breaks words that do not fit")
    void hardBreaksLongWords() {
        assertEquals(List.of("abcdefgh", "ij"), TerminalView.wrapLines(new String[] {"abcdefghij"}, 8));
    }

    @Test
    @DisplayName("keeps empty lines")
    void keepsEmptyLines() {
        assertEquals(List.of("a", "", "b"), TerminalView.wrapLines(new String[] {"a", "", "b"}, 10));
    }
}
