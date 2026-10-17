package letrain.time;

import java.time.LocalTime;
import java.util.Objects;
import java.util.Optional;

/**
 * A time-driven automation trigger of the program (ADR-022 phase 3, contract D1-D7).
 *
 * <p>
 * {@link Kind#AT} fires every day at {@link #at()} (D1/D2). {@link Kind#EVERY} fires periodically
 * on a fixed grid anchored at 00:00 of the game clock (D7): the grid points are
 * {@code from + k * period}, with {@code from} defaulting to 00:00, and they keep their absolute
 * minute spacing across midnight when the period does not divide the day. Both kinds compute the
 * next fire <b>strictly after</b> the given instant, so a trigger registered at its own hour waits
 * for the following occurrence (no catch-up, D4).
 *
 * <p>
 * This record is a pure, deterministic helper: it holds no clock and only works with game time.
 */
public record TemporalTrigger(Kind kind, LocalTime at, int periodMinutes,
        Optional<LocalTime> from) {

    public enum Kind {
        AT, EVERY
    }

    private static final int MINUTES_PER_DAY = 24 * 60;

    public TemporalTrigger {
        Objects.requireNonNull(kind, "kind");
        from = from == null ? Optional.empty() : from;
        if (kind == Kind.AT) {
            Objects.requireNonNull(at, "at");
            if (periodMinutes != 0 || from.isPresent()) {
                throw new IllegalArgumentException("an AT trigger only carries a time");
            }
        } else {
            if (periodMinutes <= 0) {
                throw new IllegalArgumentException("periodMinutes must be positive");
            }
            if (at != null) {
                throw new IllegalArgumentException("an EVERY trigger only carries a period");
            }
        }
    }

    /** Daily trigger at the given time of day ({@code at HH:MM}, D1). */
    public static TemporalTrigger at(LocalTime at) {
        return new TemporalTrigger(Kind.AT, at, 0, Optional.empty());
    }

    /** Periodic trigger on the 00:00-anchored grid (D7). */
    public static TemporalTrigger every(int periodMinutes) {
        return new TemporalTrigger(Kind.EVERY, null, periodMinutes, Optional.empty());
    }

    /** Periodic trigger on the {@code from}-anchored grid (D7). */
    public static TemporalTrigger every(int periodMinutes, LocalTime from) {
        return new TemporalTrigger(Kind.EVERY, null, periodMinutes, Optional.ofNullable(from));
    }

    /**
     * First fire strictly after {@code now}, in game time. Deterministic: game minutes only, never
     * the wall clock.
     */
    public GameTime nextFire(GameTime now) {
        Objects.requireNonNull(now, "now");
        long nowAbsolute = absoluteMinute(now);
        long fire;
        if (kind == Kind.AT) {
            long dayStart = Math.floorDiv(nowAbsolute, MINUTES_PER_DAY) * MINUTES_PER_DAY;
            fire = dayStart + minuteOfDay(at);
            if (fire <= nowAbsolute) {
                fire += MINUTES_PER_DAY; // already passed (or exactly now): next day's occurrence
            }
        } else {
            long anchor = from.map(TemporalTrigger::minuteOfDay).orElse(0);
            long steps = nowAbsolute < anchor ? 0 : (nowAbsolute - anchor) / periodMinutes + 1;
            fire = anchor + steps * periodMinutes;
        }
        return gameTimeAt(fire);
    }

    /** Human-readable form for help and problem notices ({@code every 45m from 06:15}). */
    public String describe() {
        if (kind == Kind.AT) {
            return "at " + formatTime(at);
        }
        StringBuilder text = new StringBuilder("every ");
        if (periodMinutes % MINUTES_PER_DAY == 0) {
            text.append(periodMinutes / MINUTES_PER_DAY).append('d');
        } else if (periodMinutes % 60 == 0) {
            text.append(periodMinutes / 60).append('h');
        } else {
            text.append(periodMinutes).append('m');
        }
        from.ifPresent(time -> text.append(" from ").append(formatTime(time)));
        return text.toString();
    }

    /** Absolute game minute counting from day 1 00:00 (day 1 08:00 = 480). */
    private static long absoluteMinute(GameTime time) {
        return (time.day() - 1L) * MINUTES_PER_DAY + time.minuteOfDay();
    }

    private static GameTime gameTimeAt(long absoluteMinute) {
        long day = Math.floorDiv(absoluteMinute, MINUTES_PER_DAY) + 1;
        int minuteOfDay = (int) Math.floorMod(absoluteMinute, MINUTES_PER_DAY);
        return new GameTime((int) day, minuteOfDay / 60, minuteOfDay % 60);
    }

    private static int minuteOfDay(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }

    private static String formatTime(LocalTime time) {
        return String.format("%02d:%02d", time.getHour(), time.getMinute());
    }
}
