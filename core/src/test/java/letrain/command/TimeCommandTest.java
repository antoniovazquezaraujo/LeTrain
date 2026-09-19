package letrain.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import letrain.mvp.impl.Model;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Console time command (ADR-022 phase 0)")
class TimeCommandTest {

    /** Runs a command and returns the message the executor pushed to the UI (or null). */
    private static String run(Model model, String script) {
        String[] captured = {null};
        String error = PlayerCommandExecutor.execute(script, model, null, null, null,
                (title, text) -> captured[0] = text, null, null, null, null, null, false);
        assertNull(error, error);
        return captured[0];
    }

    @Test
    @DisplayName("time; reports the current game time")
    void time_reportsCurrentTime() {
        Model model = new Model(1);

        String text = run(model, "time;");

        assertTrue(text.contains("Día 1"), text);
        assertTrue(text.contains("08:00"), text);
    }

    @Test
    @DisplayName("time set HH:MM; moves the clock and reports the new time")
    void timeSet_movesTheClock() {
        Model model = new Model(1);

        String text = run(model, "time set 18:45;");

        assertEquals(1, model.getGameClock().now().day());
        assertEquals(18, model.getGameClock().now().hour());
        assertEquals(45, model.getGameClock().now().minute());
        assertTrue(text.contains("18:45"), text);
    }

    @Test
    @DisplayName("time set accepts single-digit hours")
    void timeSet_acceptsSingleDigitHour() {
        Model model = new Model(1);

        run(model, "time set 9:05;");

        assertEquals(9, model.getGameClock().now().hour());
        assertEquals(5, model.getGameClock().now().minute());
    }
}
