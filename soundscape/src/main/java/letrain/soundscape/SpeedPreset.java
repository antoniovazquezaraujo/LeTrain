package letrain.soundscape;

/**
 * How fast the game clock advances. Durations are provisional (ADR-022/ADR-023) and are meant to be
 * tuned by ear: they decide how long a full day takes in real minutes.
 */
public enum SpeedPreset {
    SLOW("slow", 60), NORMAL("normal", 40), FAST("fast", 20);

    private static final int GAME_MINUTES_PER_DAY = 24 * 60;

    private final String label;
    private final int dayDurationMinutes;

    SpeedPreset(String label, int dayDurationMinutes) {
        this.label = label;
        this.dayDurationMinutes = dayDurationMinutes;
    }

    public String label() {
        return label;
    }

    public int dayDurationMinutes() {
        return dayDurationMinutes;
    }

    /**
     * Game minutes that pass per real second: a full day in {@code dayDurationMinutes} real
     * minutes.
     */
    public double gameMinutesPerRealSecond() {
        return GAME_MINUTES_PER_DAY / (dayDurationMinutes * 60.0);
    }

    @Override
    public String toString() {
        return label + " (day " + dayDurationMinutes + " min)";
    }
}
