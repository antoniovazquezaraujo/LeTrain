package letrain.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit tests for the typed-command notice collector and its panel threshold. */
@DisplayName("Command notices: command bar vs panel")
class CommandNoticesTest {

    @Test
    @DisplayName("a single short notice fits the command bar")
    void shortNotice_fitsTheLine() {
        CommandNotices notices = new CommandNotices();
        notices.add("Train", "Train 99 not found; order ignored");

        assertFalse(notices.isEmpty());
        assertFalse(notices.needsPanel());
        assertEquals("Train 99 not found; order ignored", notices.shortText());
        assertEquals("Train 99 not found; order ignored", notices.panelText());
    }

    @Test
    @DisplayName("a notice over 60 chars opens the panel (same rule as syntax errors)")
    void longNotice_needsPanel() {
        CommandNotices notices = new CommandNotices();
        notices.add("Time", "x".repeat(61));

        assertTrue(SyntaxMessages.needsPanel(notices.shortText()));
        assertTrue(notices.needsPanel());
    }

    @Test
    @DisplayName("exactly 60 chars still fits the line")
    void boundaryLength_fitsTheLine() {
        assertFalse(SyntaxMessages.needsPanel("x".repeat(60)));
        assertTrue(SyntaxMessages.needsPanel("x".repeat(61)));
    }

    @Test
    @DisplayName("a multiline notice opens the panel")
    void multilineNotice_needsPanel() {
        CommandNotices notices = new CommandNotices();
        notices.add("Command", "first line\nsecond line");

        assertTrue(notices.needsPanel());
    }

    @Test
    @DisplayName("notices join with ' | ' on the line and one per line on the panel")
    void severalNotices_areJoinedPerChannel() {
        CommandNotices notices = new CommandNotices();
        notices.add("Train", "Train 99 not found; order ignored");
        notices.add("Fork", "Fork 99 not found; order ignored");

        assertEquals("Train 99 not found; order ignored | Fork 99 not found; order ignored",
                notices.shortText());
        assertEquals("Train 99 not found; order ignored\nFork 99 not found; order ignored",
                notices.panelText());
        assertTrue(notices.needsPanel(), "the joined set is over 60 chars");
    }

    @Test
    @DisplayName("null and empty texts are ignored")
    void emptyTexts_areIgnored() {
        CommandNotices notices = new CommandNotices();
        notices.add("A", null);
        notices.add("B", "");

        assertTrue(notices.isEmpty());
        assertEquals("", notices.shortText());
        assertFalse(notices.needsPanel());
    }
}
