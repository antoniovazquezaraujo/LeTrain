package letrain.time.impl;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import letrain.time.GameClock;
import letrain.time.GameClockListener;
import letrain.time.GameTime;
import letrain.time.SolarModel;

/**
 * Tick-driven clock starting at 08:00 of day 1 (ADR-022). One real second is
 * {@link GameClock#TICKS_PER_SECOND} ticks; with the default 1440-second day each tick advances
 * three game seconds.
 */
public class SimpleGameClock implements GameClock {

    private static final int START_MINUTE_OF_DAY = 8 * 60;
    private static final int MINUTES_PER_DAY = 24 * 60;

    private final List<GameClockListener> listeners = new CopyOnWriteArrayList<>();
    private long elapsedTicks;
    private int dayDurationSeconds = DEFAULT_DAY_DURATION_SECONDS;
    private double latitude = SolarModel.DEFAULT_LATITUDE;
    private GameTime last;
    private boolean lastNight;

    public SimpleGameClock() {
        startOver();
    }

    public SimpleGameClock(int dayDurationSeconds) {
        setDayDurationSeconds(dayDurationSeconds);
        startOver();
    }

    @Override
    public long elapsedTicks() {
        return elapsedTicks;
    }

    @Override
    public void tick() {
        elapsedTicks++;
        notifyChanges();
    }

    @Override
    public void setTime(GameTime time) {
        if (time == null) {
            return;
        }
        double minutesFromStart = (time.day() - 1) * (double) MINUTES_PER_DAY + time.minuteOfDay()
                - START_MINUTE_OF_DAY;
        double ticksPerMinute = dayDurationSeconds * TICKS_PER_SECOND / (double) MINUTES_PER_DAY;
        long ticks = Math.round(minutesFromStart * ticksPerMinute);
        if (ticks < 0) {
            ticks += Math.round(MINUTES_PER_DAY * ticksPerMinute);
        }
        elapsedTicks = Math.max(0, ticks);
        notifyChanges();
    }

    @Override
    public GameTime now() {
        double totalMinutes = START_MINUTE_OF_DAY
                + elapsedTicks * (double) MINUTES_PER_DAY / (dayDurationSeconds * TICKS_PER_SECOND);
        int day = 1 + (int) (totalMinutes / MINUTES_PER_DAY);
        int minuteOfDay = (int) (totalMinutes % MINUTES_PER_DAY);
        return new GameTime(day, minuteOfDay / 60, minuteOfDay % 60);
    }

    @Override
    public boolean isNight() {
        return getDayNightRatio() > 0.5f;
    }

    @Override
    public float getDayNightRatio() {
        GameTime current = now();
        double hour = current.hour() + current.minute() / 60.0;
        return (float) SolarModel.dayNightRatio(SolarModel.dayOfYear(current.day()), hour,
                latitude);
    }

    @Override
    public void setDayDurationSeconds(int seconds) {
        dayDurationSeconds = Math.max(1, seconds);
    }

    @Override
    public void setLatitude(double latitude) {
        this.latitude = Math.max(-90.0, Math.min(90.0, latitude));
    }

    @Override
    public double getLatitude() {
        return latitude;
    }

    @Override
    public long ticksUntil(GameTime time) {
        if (time == null) {
            return 0;
        }
        double minutesFromStart = (time.day() - 1) * (double) MINUTES_PER_DAY + time.minuteOfDay()
                - START_MINUTE_OF_DAY;
        double ticksPerMinute = dayDurationSeconds * TICKS_PER_SECOND / (double) MINUTES_PER_DAY;
        // ceil so the target tick is the first one whose displayed minute is the requested one
        // (never earlier); the tiny epsilon absorbs floating-point noise on exact multiples.
        long targetTicks = (long) Math.ceil(minutesFromStart * ticksPerMinute - 1e-9);
        return targetTicks - elapsedTicks;
    }

    @Override
    public void addListener(GameClockListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    private void notifyChanges() {
        GameTime current = now();
        if (current.hour() != last.hour()) {
            for (GameClockListener listener : listeners) {
                listener.onHourChanged(current);
            }
        }
        if (current.day() != last.day()) {
            for (GameClockListener listener : listeners) {
                listener.onDayChanged(current);
            }
        }
        boolean night = isNight();
        if (night != lastNight) {
            for (GameClockListener listener : listeners) {
                listener.onDayNightChanged(current, night);
            }
            lastNight = night;
        }
        last = current;
    }

    /** Restores the clock counter (used by deserialization). */
    public void setElapsedTicks(long elapsedTicks) {
        this.elapsedTicks = Math.max(0, elapsedTicks);
        startOver();
    }

    private void startOver() {
        last = now();
        lastNight = isNight();
    }
}
