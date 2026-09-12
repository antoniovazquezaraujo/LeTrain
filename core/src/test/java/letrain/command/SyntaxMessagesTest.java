package letrain.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SyntaxMessages: short, single-line parser diagnostics")
class SyntaxMessagesTest {

    @Test
    @DisplayName("drops the long expected-token set")
    void dropsExpectedTokenSet() {
        assertEquals("mismatched input '<EOF>'",
                SyntaxMessages.shorten("mismatched input '<EOF>' expecting {TRAIN, 'create', 'assign'}"));
    }

    @Test
    @DisplayName("caps very long messages")
    void capsLongMessages() {
        String shortened = SyntaxMessages.shorten("x".repeat(200));
        assertTrue(shortened.length() <= 63, shortened);
        assertTrue(shortened.endsWith("..."), shortened);
    }

    @Test
    @DisplayName("null becomes a generic message")
    void nullMessage() {
        assertEquals("syntax error", SyntaxMessages.shorten(null));
        assertFalse(SyntaxMessages.shorten("missing ';'").contains("expecting"));
    }
}
