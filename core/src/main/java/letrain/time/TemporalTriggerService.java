package letrain.time;

import java.util.List;

/**
 * World registry of temporal triggers (ADR-022 phase 3, D5: global scope, not per train) and their
 * scheduling on the tick-based simulation scheduler.
 *
 * <p>
 * Each registered trigger keeps exactly one fire armed on the {@code SimulationScheduler}, computed
 * from {@link GameClock#ticksUntil(GameTime)}; when the fire is due the service re-arms the next
 * occurrence. {@link #clear()} drops the registry and invalidates the fires already armed, so a
 * program replace never leaves stale triggers behind while the shared scheduler keeps the autopilot
 * holds untouched.
 *
 * <p>
 * Phase 3a (issue #731) only parses, registers and schedules: the trigger block actions are not
 * executed yet. Execution lands in phase 3b.
 */
public interface TemporalTriggerService {

    /** Maximum number of active temporal triggers (contract: exceeding it is a visible warning). */
    int MAX_TRIGGERS = 64;

    /** Result of a registration attempt, so the caller can report the matching visible notice. */
    enum Registration {
        REGISTERED, DUPLICATE, LIMIT_REACHED
    }

    /**
     * Registers a trigger and arms its next fire from the game clock. Exact duplicates are rejected
     * and the active limit is enforced; the returned outcome tells the caller which warning (if
     * any) to report.
     */
    Registration register(TemporalTrigger trigger);

    /** Registered triggers, in registration order. */
    List<TemporalTrigger> triggers();

    /** Ticks remaining until the fire armed for {@code trigger}; -1 when it is not registered. */
    long ticksUntilNextFire(TemporalTrigger trigger);

    /** Drops every trigger and cancels the fires already armed (program replace). */
    void clear();
}
