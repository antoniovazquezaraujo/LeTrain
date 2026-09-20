package letrain.time;

/** Logical game clock (ADR-022): deterministic, derived from simulation ticks. */
public interface GameClock {

    int TICKS_PER_SECOND = 20;

    /** Real seconds a full game day lasts by default (one day every 24 real minutes). */
    int DEFAULT_DAY_DURATION_SECONDS = 1440;

    long elapsedTicks();

    void tick();

    GameTime now();

    /**
     * Jumps the clock to the given instant (console {@code time set}, scenarios and tests). The
     * jump is deterministic and fires the pending hour/day/night listener events. Instants before
     * the start epoch (day 1, 08:00) roll over to the next day, so the clock never goes back past
     * its origin.
     */
    void setTime(GameTime time);

    boolean isNight();

    /** 0.0 = broad daylight, 1.0 = full night; used by the views for dusk and dawn fades. */
    float getDayNightRatio();

    /** Real seconds a full game day lasts (default 1440: one day every 24 real minutes). */
    void setDayDurationSeconds(int seconds);

    /** World latitude for the solar day/night cycle (ADR-022 phase 1), clamped to [-90, 90]. */
    void setLatitude(double latitude);

    double getLatitude();

    void addListener(GameClockListener listener);
}
