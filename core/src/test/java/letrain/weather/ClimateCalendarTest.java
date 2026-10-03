package letrain.weather;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Climate calendar")
class ClimateCalendarTest {

    private final WeatherProbabilities winter = new WeatherProbabilities(0.6f, 0.5f, 0.2f);
    private final WeatherProbabilities summer = new WeatherProbabilities(0.1f, 0.2f, 0.0f);

    @Test
    @DisplayName("looks up the range containing a day")
    void should_LookupRange() {
        ClimateCalendar calendar = new ClimateCalendar(
                List.of(new SeasonRange(1, 80, winter), new SeasonRange(172, 264, summer)),
                new WeatherProbabilities(0.3f, 0.3f, 0.1f));

        assertEquals(0.6f, calendar.probabilitiesOn(1).rain(), 1e-6);
        assertEquals(0.6f, calendar.probabilitiesOn(80).rain(), 1e-6);
        assertEquals(0.1f, calendar.probabilitiesOn(200).rain(), 1e-6);
        assertEquals(0.3f, calendar.probabilitiesOn(120).rain(), 1e-6);
    }

    @Test
    @DisplayName("ranges can cross the year boundary")
    void should_ContainDays_When_RangeWraps() {
        SeasonRange wrapping = new SeasonRange(335, 59, winter);

        assertEquals(true, wrapping.contains(340));
        assertEquals(true, wrapping.contains(10));
        assertEquals(false, wrapping.contains(200));
    }

    @Test
    @DisplayName("overlapping ranges are rejected")
    void should_RejectOverlaps() {
        assertThrows(IllegalArgumentException.class,
                () -> new ClimateCalendar(
                        List.of(new SeasonRange(1, 80, winter), new SeasonRange(80, 120, summer)),
                        winter));
    }
}
