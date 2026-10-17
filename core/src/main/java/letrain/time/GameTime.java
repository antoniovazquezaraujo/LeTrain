package letrain.time;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Immutable game date and time: day (1-based), hour and minute. */
public record GameTime(int day, int hour, int minute) implements Comparable<GameTime> {

    /** {@code H:MM} or {@code HH:MM}; the bounds are checked after parsing. */
    private static final Pattern TIME_OF_DAY_PATTERN = Pattern.compile("(\\d{1,2}):(\\d{2})");

    public GameTime {
        day = Math.max(1, day);
        hour = Math.floorMod(hour, 24);
        minute = Math.floorMod(minute, 60);
    }

    public int minuteOfDay() {
        return hour * 60 + minute;
    }

    /** Instant at {@code day} and {@code minuteOfDay} (0..1439): the pair a clock origin needs. */
    public static GameTime ofMinuteOfDay(int day, int minuteOfDay) {
        int normalized = Math.floorMod(minuteOfDay, GameClock.MINUTES_PER_DAY);
        return new GameTime(day, normalized / 60, normalized % 60);
    }

    /**
     * Parses a time of day written as {@code H:MM} or {@code HH:MM} and returns its minute of day
     * (00:00..23:59). Shared by the settings catalogue ({@code time.startTime}) and the clients'
     * fields, so 2D and 3D normalise exactly the same.
     *
     * @throws IllegalArgumentException when the text is null, blank, malformed or out of range
     */
    public static int parseMinuteOfDay(String text) {
        String trimmed = text == null ? "" : text.trim();
        Matcher matcher = TIME_OF_DAY_PATTERN.matcher(trimmed);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "Time of day must be HH:MM (00:00..23:59), got: '" + trimmed + "'");
        }
        int hour = Integer.parseInt(matcher.group(1));
        int minute = Integer.parseInt(matcher.group(2));
        if (hour > 23 || minute > 59) {
            throw new IllegalArgumentException(
                    "Time of day must be between 00:00 and 23:59, got: '" + trimmed + "'");
        }
        return hour * 60 + minute;
    }

    /** Formats a minute of day (0..1439) as zero-padded {@code HH:MM}. */
    public static String formatTimeOfDay(int minuteOfDay) {
        int normalized = Math.floorMod(minuteOfDay, GameClock.MINUTES_PER_DAY);
        return String.format("%02d:%02d", normalized / 60, normalized % 60);
    }

    @Override
    public int compareTo(GameTime other) {
        if (day != other.day) {
            return Integer.compare(day, other.day);
        }
        return Integer.compare(minuteOfDay(), other.minuteOfDay());
    }

    @Override
    public String toString() {
        return String.format("Día %d %02d:%02d", day, hour, minute);
    }
}
