package letrain.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import letrain.time.impl.SimpleGameClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Game clock")
class SimpleGameClockTest {

    @Test
    @DisplayName("starts at 08:00 of day 1")
    void should_StartAtEight() {
        SimpleGameClock clock = new SimpleGameClock();

        assertEquals(1, clock.now().day());
        assertEquals(8, clock.now().hour());
        assertEquals(0, clock.now().minute());
        assertFalse(clock.isNight());
    }

    @Test
    @DisplayName("one real second advances one game minute by default")
    void should_AdvanceOneGameMinute_PerRealSecond() {
        SimpleGameClock clock = new SimpleGameClock();

        for (int i = 0; i < GameClock.TICKS_PER_SECOND; i++) {
            clock.tick();
        }

        assertEquals(8, clock.now().hour());
        assertEquals(1, clock.now().minute());
    }

    @Test
    @DisplayName("a full game day takes dayDurationSeconds real seconds")
    void should_CompleteDay_AfterConfiguredSeconds() {
        SimpleGameClock clock = new SimpleGameClock(720);

        for (int i = 0; i < 720 * GameClock.TICKS_PER_SECOND; i++) {
            clock.tick();
        }

        assertEquals(2, clock.now().day());
        assertEquals(8, clock.now().hour());
        assertEquals(0, clock.now().minute());
    }

    @Test
    @DisplayName("fires hour, day and day-night events")
    void should_FireEvents() {
        SimpleGameClock clock = new SimpleGameClock();
        List<String> events = new ArrayList<>();
        clock.addListener(new GameClockListener() {
            @Override
            public void onHourChanged(GameTime time) {
                events.add("hour " + time.hour());
            }

            @Override
            public void onDayChanged(GameTime time) {
                events.add("day " + time.day());
            }

            @Override
            public void onDayNightChanged(GameTime time, boolean night) {
                events.add("night " + night);
            }
        });

        for (int i = 0; i < 60 * GameClock.TICKS_PER_SECOND * 14; i++) {
            clock.tick();
        }

        assertTrue(events.contains("hour 9"), events.toString());
        assertTrue(events.contains("hour 21"), events.toString());
        assertTrue(events.contains("night true"), events.toString());
    }

    @Test
    @DisplayName("night ratio goes from daylight to full night")
    void should_ReportDayNightRatio() {
        SimpleGameClock clock = new SimpleGameClock();

        assertEquals(0f, clock.getDayNightRatio(), 1e-6);
        for (int i = 0; i < 14 * 60 * GameClock.TICKS_PER_SECOND; i++) {
            clock.tick();
        }

        assertEquals(1f, clock.getDayNightRatio(), 1e-6);
        assertTrue(clock.isNight());
    }

    @Test
    @DisplayName("elapsed ticks can be restored")
    void should_RestoreElapsedTicks() {
        SimpleGameClock clock = new SimpleGameClock();
        clock.setElapsedTicks(60L * GameClock.TICKS_PER_SECOND);

        assertEquals(9, clock.now().hour());
        assertEquals(0, clock.now().minute());
    }

    @Test
    @DisplayName("setTime jumps to the requested instant of the same day")
    void should_SetTime() {
        SimpleGameClock clock = new SimpleGameClock();

        clock.setTime(new GameTime(1, 8, 30));

        assertEquals(1, clock.now().day());
        assertEquals(8, clock.now().hour());
        assertEquals(30, clock.now().minute());
        assertEquals(30L * GameClock.TICKS_PER_SECOND, clock.elapsedTicks());
    }

    @Test
    @DisplayName("setTime fires the hour, day and day-night events of the jump")
    void should_FireEventsOnSetTime() {
        SimpleGameClock clock = new SimpleGameClock();
        List<String> events = new ArrayList<>();
        clock.addListener(new GameClockListener() {
            @Override
            public void onHourChanged(GameTime time) {
                events.add("hour " + time.hour());
            }

            @Override
            public void onDayChanged(GameTime time) {
                events.add("day " + time.day());
            }

            @Override
            public void onDayNightChanged(GameTime time, boolean night) {
                events.add("night " + night);
            }
        });

        clock.setTime(new GameTime(3, 22, 0));

        assertEquals(3, clock.now().day());
        assertTrue(events.contains("hour 22"), events.toString());
        assertTrue(events.contains("day 3"), events.toString());
        assertTrue(events.contains("night true"), events.toString());
    }

    @Test
    @DisplayName("setTime before the start epoch rolls over to the next day")
    void should_RollForward_WhenSettingTimeBeforeStart() {
        SimpleGameClock clock = new SimpleGameClock();

        clock.setTime(new GameTime(1, 6, 0));

        assertEquals(2, clock.now().day());
        assertEquals(6, clock.now().hour());
        assertEquals(0, clock.now().minute());
    }

    @Test
    @DisplayName("setTime(null) is ignored")
    void should_IgnoreNullTime() {
        SimpleGameClock clock = new SimpleGameClock();
        long before = clock.elapsedTicks();

        clock.setTime(null);

        assertEquals(before, clock.elapsedTicks());
        assertEquals(8, clock.now().hour());
    }

    @Test
    @DisplayName("ticksUntil counts the ticks to the requested minute and is negative in the past")
    void should_CountTicksUntil() {
        SimpleGameClock clock = new SimpleGameClock();

        assertEquals(100, clock.ticksUntil(new GameTime(1, 8, 5)));
        assertEquals(0, clock.ticksUntil(new GameTime(1, 8, 0)));
        assertEquals(-20, clock.ticksUntil(new GameTime(1, 7, 59)));

        for (int i = 0; i < 99; i++) {
            clock.tick();
        }
        assertEquals(1, clock.ticksUntil(new GameTime(1, 8, 5)),
                "the release must not happen before the target minute");
    }

    @Test
    @DisplayName("ticksUntil follows the configured day scale")
    void should_CountTicksUntil_WithCustomScale() {
        SimpleGameClock clock = new SimpleGameClock(720);

        // 720 real seconds per day => 10 ticks per game minute.
        assertEquals(50, clock.ticksUntil(new GameTime(1, 8, 5)));
    }

    @Test
    @DisplayName("ticksUntil matches setTime jumps")
    void should_CountTicksUntil_AfterSetTime() {
        SimpleGameClock clock = new SimpleGameClock();
        clock.setTime(new GameTime(1, 8, 4));

        assertEquals(20, clock.ticksUntil(new GameTime(1, 8, 5)));
    }

    @Test
    @DisplayName("a custom start instant shifts now, setTime and ticksUntil (S2b)")
    void should_UseCustomStart() {
        SimpleGameClock clock = new SimpleGameClock();
        clock.setStartTime(new GameTime(300, 6, 30));

        assertEquals(new GameTime(300, 6, 30), clock.now());
        assertEquals(new GameTime(300, 6, 30), clock.startTime());
        assertEquals(0, clock.elapsedTicks());
        assertEquals(100, clock.ticksUntil(new GameTime(300, 6, 35)));

        clock.setTime(new GameTime(300, 7, 0));

        assertEquals(new GameTime(300, 7, 0), clock.now());
        assertEquals(30L * GameClock.TICKS_PER_SECOND, clock.elapsedTicks());
        assertEquals(0, clock.ticksUntil(new GameTime(300, 7, 0)),
                "the jumped-to instant is the current tick");
        assertEquals(600, clock.ticksUntil(new GameTime(300, 7, 30)));
    }

    @Test
    @DisplayName("the clock crosses into the next day from a start before 08:00 (S2b)")
    void should_CrossIntoTheNextDay_FromAnEarlyStart() {
        SimpleGameClock clock = new SimpleGameClock();
        clock.setStartTime(new GameTime(10, 23, 55));

        for (int i = 0; i < 10 * GameClock.TICKS_PER_SECOND; i++) {
            clock.tick();
        }

        assertEquals(new GameTime(11, 0, 5), clock.now(),
                "10 game minutes after 23:55 the day rolls over at midnight");
    }

    @Test
    @DisplayName("setTime before a custom origin rolls forward day by day (S2b)")
    void should_RollForward_WhenSettingTimeBeforeCustomStart() {
        SimpleGameClock clock = new SimpleGameClock();
        clock.setStartTime(new GameTime(5, 6, 30));

        clock.setTime(new GameTime(5, 6, 0));

        assertEquals(new GameTime(6, 6, 0), clock.now(),
                "one full day is added, like the day 1 08:00 roll-over");
    }

    @Test
    @DisplayName("changing the origin keeps the running counter (savegame restore, S2b)")
    void should_KeepElapsedTicks_WhenChangingStart() {
        SimpleGameClock clock = new SimpleGameClock();
        for (int i = 0; i < 60 * GameClock.TICKS_PER_SECOND; i++) {
            clock.tick();
        }

        clock.setStartTime(new GameTime(300, 6, 30));

        assertEquals(new GameTime(300, 7, 30), clock.now());
        assertEquals(60L * GameClock.TICKS_PER_SECOND, clock.elapsedTicks());
    }

    @Test
    @DisplayName("setStartTime(null) is ignored and the listeners stay silent (S2b)")
    void should_IgnoreNullStart() {
        SimpleGameClock clock = new SimpleGameClock();
        List<String> events = new ArrayList<>();
        clock.addListener(new GameClockListener() {
            @Override
            public void onHourChanged(GameTime time) {
                events.add("hour " + time.hour());
            }
        });

        clock.setStartTime(null);
        assertEquals(new GameTime(1, 8, 0), clock.startTime(), "null is ignored");

        clock.setStartTime(new GameTime(9, 5, 0));

        assertEquals(0, clock.elapsedTicks(), "the running counter is never reset");
        assertEquals(new GameTime(9, 5, 0), clock.now());
        assertTrue(events.isEmpty(), events.toString());
    }
}
