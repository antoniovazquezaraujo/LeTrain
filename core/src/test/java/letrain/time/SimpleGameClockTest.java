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
}
