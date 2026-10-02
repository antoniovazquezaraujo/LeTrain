package letrain.weather;

/**
 * A closed range of days of the year (1..366) with the phenomenon probabilities for those days.
 * Ranges can cross the year boundary ({@code Dec-01 to Feb-28}), so containment is checked in
 * circular time.
 */
public record SeasonRange(int startDay, int endDay, WeatherProbabilities probabilities) {

    public SeasonRange {
        startDay = normalize(startDay);
        endDay = normalize(endDay);
    }

    public boolean contains(int dayOfYear) {
        int day = normalize(dayOfYear);
        if (startDay <= endDay) {
            return day >= startDay && day <= endDay;
        }
        return day >= startDay || day <= endDay;
    }

    private static int normalize(int dayOfYear) {
        int day = dayOfYear % 366;
        if (day <= 0) {
            day += 366;
        }
        return day;
    }
}
