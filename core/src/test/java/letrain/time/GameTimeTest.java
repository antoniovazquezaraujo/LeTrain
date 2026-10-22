package letrain.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

@DisplayName("GameTime: time-of-day grammar shared by the settings and the clients (S2b)")
class GameTimeTest {

    @ParameterizedTest
    @CsvSource({"0:00, 0", "8:00, 480", "08:00, 480", "6:30, 390", "23:59, 1439", "' 06:30 ', 390"})
    @DisplayName("parseMinuteOfDay accepts H:MM and HH:MM and pads the hour")
    void should_ParseMinuteOfDay(String text, int expected) {
        assertEquals(expected, GameTime.parseMinuteOfDay(text));
    }

    @ParameterizedTest
    @CsvSource({"24:00", "12:60", "8:5", "8:xx", "noon", "8", ":30", "08:30:00"})
    @DisplayName("parseMinuteOfDay rejects malformed and out-of-range times")
    void should_RejectInvalidTimes(String text) {
        assertThrows(IllegalArgumentException.class, () -> GameTime.parseMinuteOfDay(text));
    }

    @Test
    @DisplayName("parseMinuteOfDay rejects null and blank")
    void should_RejectNullAndBlank() {
        assertThrows(IllegalArgumentException.class, () -> GameTime.parseMinuteOfDay(null));
        assertThrows(IllegalArgumentException.class, () -> GameTime.parseMinuteOfDay(""));
        assertThrows(IllegalArgumentException.class, () -> GameTime.parseMinuteOfDay("   "));
    }

    @Test
    @DisplayName("formatTimeOfDay zero-pads both fields")
    void should_FormatTimeOfDay() {
        assertEquals("00:00", GameTime.formatTimeOfDay(0));
        assertEquals("06:30", GameTime.formatTimeOfDay(390));
        assertEquals("23:59", GameTime.formatTimeOfDay(1439));
    }

    @Test
    @DisplayName("ofMinuteOfDay builds the instant a clock origin needs")
    void should_BuildFromMinuteOfDay() {
        assertEquals(new GameTime(300, 6, 30), GameTime.ofMinuteOfDay(300, 390));
        assertEquals(new GameTime(1, 0, 0), GameTime.ofMinuteOfDay(1, 0));
    }

    @Test
    @DisplayName("toString uses the English-neutral user-facing format (Day D HH:MM)")
    void should_FormatToStringInNeutralEnglish() {
        assertEquals("Day 1 08:00", new GameTime(1, 8, 0).toString());
        assertEquals("Day 2 09:05", new GameTime(2, 9, 5).toString());
    }
}
