package letrain.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalTime;
import letrain.time.impl.SimpleGameClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * ADR-022 phase 3, F3a: deterministic next-fire math of the temporal triggers (contract D1-D7) and
 * its translation to ticks with the real game clock (default 1440-second day: 20 ticks per game
 * minute). No wall clock, no randomness.
 */
@DisplayName("Temporal triggers: next fire (ADR-022 phase 3, F3a)")
class TemporalTriggerTest {

    @Nested
    @DisplayName("at HH:MM: daily occurrence (D1/D2)")
    class At {

        @Test
        @DisplayName("same day while the time is still ahead")
        void timeAhead_firesToday() {
            assertEquals(new GameTime(1, 6, 30),
                    TemporalTrigger.at(LocalTime.of(6, 30)).nextFire(new GameTime(1, 6, 0)));
        }

        @Test
        @DisplayName("at the exact time the next occurrence is tomorrow (strictly after, no catch-up)")
        void exactTime_waitsForTomorrow() {
            assertEquals(new GameTime(2, 6, 30),
                    TemporalTrigger.at(LocalTime.of(6, 30)).nextFire(new GameTime(1, 6, 30)));
        }

        @Test
        @DisplayName("a time already passed today is scheduled for the next day")
        void timePassed_goesToTomorrow() {
            assertEquals(new GameTime(4, 9, 0),
                    TemporalTrigger.at(LocalTime.of(9, 0)).nextFire(new GameTime(3, 10, 0)));
        }

        @Test
        @DisplayName("rolls over midnight (23:50 -> 00:10 belongs to the next day)")
        void midnightRollover() {
            assertEquals(new GameTime(2, 0, 10),
                    TemporalTrigger.at(LocalTime.of(0, 10)).nextFire(new GameTime(1, 23, 50)));
        }
    }

    @Nested
    @DisplayName("every: fixed grid anchored at 00:00 (D7)")
    class Every {

        @Test
        @DisplayName("30m from a mid-slot registration: 10:07 -> 10:30")
        void midSlot_goesToTheNextGridPoint() {
            assertEquals(new GameTime(1, 10, 30),
                    TemporalTrigger.every(30).nextFire(new GameTime(1, 10, 7)));
        }

        @Test
        @DisplayName("at an exact grid point the next one is used (strictly after)")
        void exactGridPoint_goesToTheNextOne() {
            assertEquals(new GameTime(1, 11, 0),
                    TemporalTrigger.every(30).nextFire(new GameTime(1, 10, 30)));
        }

        @Test
        @DisplayName("1h keeps the top of the hour")
        void hourly_keepsTheHour() {
            assertEquals(new GameTime(1, 11, 0),
                    TemporalTrigger.every(60).nextFire(new GameTime(1, 10, 0)));
        }

        @Test
        @DisplayName("from anchors the grid: 30m from 06:15 at 10:07 -> 10:15")
        void from_anchorsTheGrid() {
            assertEquals(new GameTime(1, 10, 15), TemporalTrigger.every(30, LocalTime.of(6, 15))
                    .nextFire(new GameTime(1, 10, 7)));
        }

        @Test
        @DisplayName("a from still ahead of now keeps its first occurrence")
        void fromAheadOfNow_keepsTheFirstOccurrence() {
            assertEquals(new GameTime(1, 6, 15),
                    TemporalTrigger.every(45, LocalTime.of(6, 15)).nextFire(new GameTime(1, 6, 0)));
        }

        @Test
        @DisplayName("a period that does not fit the day drifts across midnight (45m from 06:15)")
        void drift_acrossMidnight() {
            // 06:15, 07:00, ..., 23:30, then 00:15 of the next day.
            assertEquals(new GameTime(2, 0, 15), TemporalTrigger.every(45, LocalTime.of(6, 15))
                    .nextFire(new GameTime(1, 23, 50)));
        }

        @Test
        @DisplayName("1d on the 00:00 grid fires at midnight")
        void daily_firesAtMidnight() {
            assertEquals(new GameTime(2, 0, 0),
                    TemporalTrigger.every(1440).nextFire(new GameTime(1, 23, 50)));
        }

        @Test
        @DisplayName("1d from 06:00 keeps its anchor every day")
        void dailyFrom_keepsTheAnchor() {
            assertEquals(new GameTime(2, 6, 0), TemporalTrigger.every(1440, LocalTime.of(6, 0))
                    .nextFire(new GameTime(1, 23, 50)));
        }

        @Test
        @DisplayName("a non-positive period is rejected by the model")
        void nonPositivePeriod_isRejected() {
            assertThrows(IllegalArgumentException.class, () -> TemporalTrigger.every(0));
            assertThrows(IllegalArgumentException.class, () -> TemporalTrigger.every(-5));
        }
    }

    @Nested
    @DisplayName("ticks from the real game clock (20 ticks per game minute)")
    class Ticks {

        @Test
        @DisplayName("registered at 10:07, every 30m: next fire is 460 ticks away (10:30)")
        void every30m_ticksToNextFire() {
            SimpleGameClock clock = new SimpleGameClock();
            clock.setTime(new GameTime(1, 10, 7));

            TemporalTrigger trigger = TemporalTrigger.every(30);
            GameTime next = trigger.nextFire(clock.now());

            assertEquals(new GameTime(1, 10, 30), next);
            assertEquals(460, clock.ticksUntil(next));
        }

        @Test
        @DisplayName("at 06:30 registered at 08:00: next fire is tomorrow (27000 ticks)")
        void atTimeAlreadyPassed_ticksToTomorrow() {
            SimpleGameClock clock = new SimpleGameClock();
            clock.setTime(new GameTime(1, 8, 0));

            TemporalTrigger trigger = TemporalTrigger.at(LocalTime.of(6, 30));
            GameTime next = trigger.nextFire(clock.now());

            assertEquals(new GameTime(2, 6, 30), next);
            assertEquals(27000, clock.ticksUntil(next));
        }
    }

    @Test
    @DisplayName("describe(): compact form for help and warnings")
    void describe_isCompact() {
        assertEquals("at 06:30", TemporalTrigger.at(LocalTime.of(6, 30)).describe());
        assertEquals("every 30m", TemporalTrigger.every(30).describe());
        assertEquals("every 2h", TemporalTrigger.every(120).describe());
        assertEquals("every 1d", TemporalTrigger.every(1440).describe());
        assertEquals("every 45m from 06:15",
                TemporalTrigger.every(45, LocalTime.of(6, 15)).describe());
    }
}
