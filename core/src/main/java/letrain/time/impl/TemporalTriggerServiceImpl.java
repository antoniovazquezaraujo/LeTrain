package letrain.time.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import letrain.time.GameClock;
import letrain.time.GameTime;
import letrain.time.TemporalTrigger;
import letrain.time.TemporalTriggerService;
import letrain.utils.SimulationScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Schedules each temporal trigger on the tick-based {@link SimulationScheduler} using
 * {@link GameClock#ticksUntil(GameTime)} (ADR-022 phase 3: deterministic, never wall time). A due
 * trigger runs its action block — through the same deferred runner as the event triggers — and then
 * re-arms the next occurrence; {@link #clear()} bumps a generation counter so the fires still armed
 * for a replaced program are harmless no-ops, without touching the shared scheduler (the autopilot
 * holds live there too).
 *
 * <p>
 * The scheduler ticks before the game clock in {@code SimulationController}, so a fire can land one
 * tick early (or the clock can be rewound with {@code time set}); the due check then re-arms the
 * same target. A failing action is logged and does not take down the simulation tick, and the
 * trigger still re-arms unless its program was replaced while the action ran.
 */
public class TemporalTriggerServiceImpl implements TemporalTriggerService {

    private static final Logger log = LoggerFactory.getLogger(TemporalTriggerServiceImpl.class);

    private final GameClock clock;
    private final SimulationScheduler scheduler;
    private final List<Entry> entries = new ArrayList<>();
    private long generation;

    public TemporalTriggerServiceImpl(GameClock clock, SimulationScheduler scheduler) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    @Override
    public Registration register(TemporalTrigger trigger, Runnable onFire) {
        Objects.requireNonNull(trigger, "trigger");
        Objects.requireNonNull(onFire, "onFire");
        for (Entry entry : entries) {
            if (entry.trigger.equals(trigger)) {
                return Registration.DUPLICATE;
            }
        }
        if (entries.size() >= MAX_TRIGGERS) {
            return Registration.LIMIT_REACHED;
        }
        Entry entry = new Entry(trigger, onFire);
        entries.add(entry);
        arm(entry);
        log.info("[Trigger] {} registered; next fire {} ({} ticks)", trigger.describe(), entry.next,
                entry.ticks);
        return Registration.REGISTERED;
    }

    @Override
    public List<TemporalTrigger> triggers() {
        return entries.stream().map(entry -> entry.trigger).toList();
    }

    @Override
    public long ticksUntilNextFire(TemporalTrigger trigger) {
        for (Entry entry : entries) {
            if (entry.trigger.equals(trigger)) {
                return entry.ticks;
            }
        }
        return -1;
    }

    @Override
    public void clear() {
        generation++;
        entries.clear();
    }

    /**
     * Computes the next fire from the clock and arms it. The generation captured here identifies
     * the program generation the fire belongs to.
     */
    private void arm(Entry entry) {
        long armedGeneration = generation;
        entry.next = entry.trigger.nextFire(clock.now());
        armAt(entry, armedGeneration);
    }

    /** Arms {@code entry.next} for the given program generation. */
    private void armAt(Entry entry, long armedGeneration) {
        entry.ticks = Math.max(1, clock.ticksUntil(entry.next));
        scheduler.schedule((int) Math.min(entry.ticks, Integer.MAX_VALUE),
                () -> onDue(entry, armedGeneration));
    }

    /**
     * A scheduled fire. A fire from a cleared/replaced program or one whose trigger was dropped is
     * ignored; an early fire (scheduler-before-clock order, or a rewound clock) is re-armed for the
     * same target. A due fire runs the block actions first and then re-arms the next occurrence,
     * unless the action itself replaced the program.
     */
    private void onDue(Entry entry, long armedGeneration) {
        if (isStale(entry, armedGeneration)) {
            return;
        }
        if (clock.now().compareTo(entry.next) < 0) {
            armAt(entry, armedGeneration);
            return;
        }
        runAction(entry);
        if (isStale(entry, armedGeneration)) {
            return;
        }
        arm(entry);
    }

    /** True when the fire belongs to a replaced program or to a trigger no longer registered. */
    private boolean isStale(Entry entry, long armedGeneration) {
        return armedGeneration != generation || !entries.contains(entry);
    }

    /**
     * Runs the trigger's block through the deferred runner the caller provided (the same one the
     * event triggers use). A failing action is logged and swallowed: it must neither stop the timer
     * nor take down the simulation tick. The visible channel for rejected actions is the runner's
     * job, not this class's.
     */
    private void runAction(Entry entry) {
        try {
            entry.onFire.run();
        } catch (RuntimeException e) {
            log.error("[Trigger] {} action failed", entry.trigger.describe(), e);
        }
    }

    /** One registered trigger, its block action and the fire currently armed for it. */
    private static final class Entry {
        private final TemporalTrigger trigger;
        private final Runnable onFire;
        private GameTime next;
        private long ticks;

        private Entry(TemporalTrigger trigger, Runnable onFire) {
            this.trigger = trigger;
            this.onFire = onFire;
        }
    }
}
