package letrain.itinerary;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalTime;
import letrain.time.GameTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * ADR-022 phase 2a timetable semantics: times are read in sequence, so a smaller time than the
 * previous one belongs to the next day (midnight rollover). The helper is pure and deterministic;
 * retention behaviour lands in phase 2b.
 */
@DisplayName("Timetable semantics (ADR-022 phase 2a)")
class TimetableTest {

    @Test
    @DisplayName("minuteOfDay maps 00:00 to 0 and 23:59 to 1439")
    void minuteOfDay_mapsBounds() {
        assertEquals(0, Timetable.minuteOfDay(LocalTime.of(0, 0)));
        assertEquals(1439, Timetable.minuteOfDay(LocalTime.of(23, 59)));
        assertEquals(9 * 60 + 20, Timetable.minuteOfDay(LocalTime.of(9, 20)));
    }

    @Test
    @DisplayName("a later time stays on the same day")
    void resolveAfter_laterTime_sameDay() {
        assertEquals(10 * 60 + 30, Timetable.resolveAfter(10 * 60 + 23, LocalTime.of(10, 30)));
    }

    @Test
    @DisplayName("an equal time stays on the same day (zero dwell)")
    void resolveAfter_equalTime_sameDay() {
        assertEquals(9 * 60 + 15, Timetable.resolveAfter(9 * 60 + 15, LocalTime.of(9, 15)));
    }

    @Test
    @DisplayName("23:50 -> 00:10 rolls over to the next day")
    void resolveAfter_midnightRollover_nextDay() {
        long arrival = Timetable.minuteOfDay(LocalTime.of(23, 50));

        long departure = Timetable.resolveAfter(arrival, LocalTime.of(0, 10));

        assertEquals(Timetable.MINUTES_PER_DAY + 10, departure,
                "00:10 after 23:50 belongs to the next day");
    }

    @Test
    @DisplayName("rollover keeps counting days on multi-day sequences")
    void resolveAfter_staysOnNextDayWhenStillLater() {
        long previous = Timetable.MINUTES_PER_DAY + Timetable.minuteOfDay(LocalTime.of(0, 10));

        assertEquals(Timetable.MINUTES_PER_DAY + 5 * 60,
                Timetable.resolveAfter(previous, LocalTime.of(5, 0)));
    }

    @ParameterizedTest(name = "dwell {0} -> {1} = {2} game minutes")
    @CsvSource({"09:20, 09:30, 10", "23:50, 00:10, 20", "00:00, 00:00, 0"})
    @DisplayName("dwell = departure - arrival in game minutes, rolling over midnight")
    void dwellMinutes_rollsOverMidnight(String arrival, String departure, int expected) {
        assertEquals(expected,
                Timetable.dwellMinutes(LocalTime.parse(arrival), LocalTime.parse(departure)));
    }

    @Test
    @DisplayName("absolute minutes count from day 1 00:00 and round-trip through GameTime")
    void absoluteMinute_roundTrip() {
        assertEquals(0, Timetable.absoluteMinute(new GameTime(1, 0, 0)));
        assertEquals(480, Timetable.absoluteMinute(new GameTime(1, 8, 0)));
        assertEquals(Timetable.MINUTES_PER_DAY + 10,
                Timetable.absoluteMinute(new GameTime(2, 0, 10)));
        assertEquals(new GameTime(2, 0, 10), Timetable.toGameTime(Timetable.MINUTES_PER_DAY + 10));
    }

    @Test
    @DisplayName("resolveNearest picks the same-day occurrence when the train is late")
    void resolveNearest_late_sameDay() {
        long now = Timetable.absoluteMinute(new GameTime(1, 8, 10));

        long target = Timetable.resolveNearest(now, LocalTime.of(8, 5));

        assertEquals(Timetable.absoluteMinute(new GameTime(1, 8, 5)), target);
        assertEquals(5, now - target, "late by five game minutes");
    }

    @Test
    @DisplayName("resolveNearest picks the same-day occurrence when the train is early")
    void resolveNearest_early_sameDay() {
        long now = Timetable.absoluteMinute(new GameTime(1, 8, 0));

        long target = Timetable.resolveNearest(now, LocalTime.of(8, 5));

        assertEquals(Timetable.absoluteMinute(new GameTime(1, 8, 5)), target);
        assertEquals(-5, now - target, "early by five game minutes");
    }

    @Test
    @DisplayName("resolveNearest at night picks the next morning when it is closer than half a day")
    void resolveNearest_night_picksNextMorning() {
        long now = Timetable.absoluteMinute(new GameTime(1, 22, 0));

        long target = Timetable.resolveNearest(now, LocalTime.of(6, 0));

        assertEquals(Timetable.absoluteMinute(new GameTime(2, 6, 0)), target,
                "departure 06:00 at 22:00 belongs to the next morning");
    }
}
