package letrain.time;

/** Time events (ADR-022); consumers subscribe instead of polling the clock every tick. */
public interface GameClockListener {

    default void onHourChanged(GameTime time) {}

    default void onDayChanged(GameTime time) {}

    default void onDayNightChanged(GameTime time, boolean night) {}
}
