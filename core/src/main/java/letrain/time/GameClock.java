package letrain.time;

/** Logical game clock (ADR-022): deterministic, derived from simulation ticks. */
public interface GameClock {

    int TICKS_PER_SECOND = 20;

    /** Real seconds a full game day lasts by default (one day every 24 real minutes). */
    int DEFAULT_DAY_DURATION_SECONDS = 1440;

    long elapsedTicks();

    void tick();

    GameTime now();

    boolean isNight();

    /** 0.0 = broad daylight, 1.0 = full night; used by the views for dusk and dawn fades. */
    float getDayNightRatio();

    /** Real seconds a full game day lasts (default 1440: one day every 24 real minutes). */
    void setDayDurationSeconds(int seconds);

    void addListener(GameClockListener listener);
}
