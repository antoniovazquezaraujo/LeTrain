package letrain.itinerary;

import java.time.LocalTime;
import letrain.time.GameTime;

/**
 * ADR-022 phase 2 timetable semantics for waypoint times.
 *
 * <p>
 * Waypoint times are <b>time-of-day</b> values ({@code H:MM} / {@code HH:MM}, 24 h). The schedule
 * is read <b>in sequence</b>: a time smaller than the previous one belongs to the <b>next day</b>
 * (midnight rollover, e.g. {@code arrival 23:50} followed by {@code departure 00:10}). The dwell at
 * a stop is therefore {@code departure - arrival} in game minutes, invariant to
 * {@code time.dayDurationSeconds}.
 *
 * <p>
 * This class is a pure, deterministic helper: it holds no clock and does not retain or regulate
 * trains; the autopilot keeps the sequence cursor and asks {@link #resolveAfter} /
 * {@link #resolveNearest} to turn a time of day into a monotonic absolute minute.
 */
public final class Timetable {

    public static final int MINUTES_PER_DAY = 24 * 60;

    private Timetable() {}

    /**
     * Absolute game minute of an instant, counting from day 1 {@code 00:00} (day 1 08:00 = 480).
     */
    public static long absoluteMinute(GameTime time) {
        return (time.day() - 1L) * MINUTES_PER_DAY + time.minuteOfDay();
    }

    /** Game instant at an absolute minute ({@link #absoluteMinute} inverse). */
    public static GameTime toGameTime(long absoluteMinute) {
        long day = Math.floorDiv(absoluteMinute, MINUTES_PER_DAY) + 1;
        int minuteOfDay = (int) Math.floorMod(absoluteMinute, MINUTES_PER_DAY);
        return new GameTime((int) day, minuteOfDay / 60, minuteOfDay % 60);
    }

    /**
     * Nearest occurrence of a time of day when the schedule has no previous event yet (fresh
     * activation, load): the occurrence within half a day of now, so an evening start
     * ({@code departure 06:00} at 22:00) waits for the next morning while a late train
     * ({@code departure 08:05} at 08:10) departs immediately. With a cursor available always prefer
     * {@link #resolveAfter}.
     */
    public static long resolveNearest(long nowAbsoluteMinute, LocalTime time) {
        long dayStart = Math.floorDiv(nowAbsoluteMinute, MINUTES_PER_DAY) * MINUTES_PER_DAY;
        long candidate = dayStart + minuteOfDay(time);
        if (nowAbsoluteMinute - candidate > MINUTES_PER_DAY / 2) {
            candidate += MINUTES_PER_DAY;
        } else if (candidate - nowAbsoluteMinute > MINUTES_PER_DAY / 2) {
            candidate -= MINUTES_PER_DAY;
        }
        return candidate;
    }

    /** Minute of the day of a time-of-day value ({@code 00:00} = 0 … {@code 23:59} = 1439). */
    public static int minuteOfDay(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }

    /**
     * Resolves {@code time} onto a monotonic timeline that starts at {@code previousAbsoluteMinute}
     * (an absolute minute count where day 0 is {@code [0, 1440)}). Returns the first absolute
     * minute at or after the previous one whose time of day equals {@code time}: if the time of day
     * is smaller than the previous one, it belongs to the next day. Equal times stay on the same
     * day (zero dwell).
     */
    public static long resolveAfter(long previousAbsoluteMinute, LocalTime time) {
        long dayStart = Math.floorDiv(previousAbsoluteMinute, MINUTES_PER_DAY) * MINUTES_PER_DAY;
        long candidate = dayStart + minuteOfDay(time);
        return candidate < previousAbsoluteMinute ? candidate + MINUTES_PER_DAY : candidate;
    }

    /**
     * Dwell between an {@code arrival} and its {@code departure}, in game minutes, rolling over
     * midnight ({@code 23:50 -> 00:10} is 20 minutes).
     */
    public static int dwellMinutes(LocalTime arrival, LocalTime departure) {
        long arrivalMinute = minuteOfDay(arrival);
        return (int) (resolveAfter(arrivalMinute, departure) - arrivalMinute);
    }
}
