package letrain.time;

/** Logical game clock (ADR-022): deterministic, derived from simulation ticks. */
public interface GameClock {

    int TICKS_PER_SECOND = 20;

    /** Game minutes in a full day. */
    int MINUTES_PER_DAY = 24 * 60;

    /** Real seconds a full game day lasts by default (one day every 24 real minutes). */
    int DEFAULT_DAY_DURATION_SECONDS = 1440;

    /** Default start day of a new game (ADR-022; configurable since S2b). */
    int DEFAULT_START_DAY = 1;

    /** Default start time of a new game: 08:00, the start of the working day (ADR-022). */
    int DEFAULT_START_MINUTE_OF_DAY = 8 * 60;

    long elapsedTicks();

    void tick();

    GameTime now();

    /**
     * Jumps the clock to the given instant (console {@code time set}, scenarios and tests). The
     * jump is deterministic and fires the pending hour/day/night listener events. Instants before
     * the clock's start instant roll forward day by day until they reach it, so the clock never
     * goes back past its origin (day 1, 08:00 by default).
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

    /**
     * Sets the creation-time start instant: what {@link #now()} reads when the counter is zero (day
     * 1, 08:00 by default, ADR-022; S2b). A new game applies its {@code time.startDay} /
     * {@code time.startTime} settings here before the first tick; loading a savegame restores the
     * origin so the saved {@code elapsedTicks} keeps pointing at the same instant. The running
     * counter is kept and no listener event fires.
     */
    void setStartTime(GameTime startTime);

    /** Creation-time start instant of the clock (day 1, 08:00 by default). */
    GameTime startTime();

    /**
     * Number of ticks until the clock reaches the given game instant. Negative when the instant is
     * already in the past. The result is exact (never releases before the requested time, even with
     * fractional ticks-per-minute) and deterministic; schedule holds (ADR-022 phase 2b) rely on it.
     */
    long ticksUntil(GameTime time);

    void addListener(GameClockListener listener);
}
