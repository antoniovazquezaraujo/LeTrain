package letrain.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import letrain.mvp.impl.Model;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Console info commands: help, ls, info")
class CommandHelpTest {

    /** Runs a command and returns the message the executor pushed to the UI (or null). */
    private static String run(Model model, String script) {
        String[] captured = {null};
        String error = PlayerCommandExecutor.execute(script, model, null, null, null,
                (title, text) -> captured[0] = text, null, null, null, null, null, false);
        assertNull(error, error);
        return captured[0];
    }

    @Test
    @DisplayName("help; dumps every section")
    void help_listsEverything() {
        String text = run(new Model(1), "help;");
        assertNotNull(text);
        assertTrue(text.contains("CONSOLE"), text);
        assertTrue(text.contains("journal;"), text);
        assertTrue(text.contains("ITINERARY DSL"), text);
    }

    @Test
    @DisplayName("help <topic>; filters, and unknown topics get a hint")
    void help_topic() {
        Model model = new Model(1);
        String console = run(model, "help console;");
        assertTrue(console.contains("journal;"), console);
        assertFalse(console.contains("ITINERARY DSL"), console);

        assertTrue(run(model, "help ls;").contains("ls;"));
        assertTrue(run(model, "help nonsense;").contains("No help"));
    }

    @Test
    @DisplayName("info; without arguments shows everything")
    void info_noArgs() {
        String text = run(new Model(1), "info;");
        assertNotNull(text);
        assertTrue(text.contains("TRAINS"), text);
    }

    @Test
    @DisplayName("info <type>; without an id lists that type")
    void info_typeOnly() {
        String text = run(new Model(1), "info station;");
        assertNotNull(text);
        assertTrue(text.contains("Stations:"), text);
        assertFalse(text.contains("Trains:"), text);
    }

    @Test
    @DisplayName("ls; without arguments lists every type")
    void ls_noArgs() {
        String text = run(new Model(1), "ls;");
        assertNotNull(text);
        assertTrue(text.contains("Trains:"), text);
        assertTrue(text.contains("Stations:"), text);
        assertTrue(text.contains("Forks:"), text);
    }

    @Test
    @DisplayName("ls <type>; lists only that type")
    void ls_type() {
        String text = run(new Model(1), "ls fork;");
        assertNotNull(text);
        assertTrue(text.contains("Forks:"), text);
        assertFalse(text.contains("Trains:"), text);
    }
}
