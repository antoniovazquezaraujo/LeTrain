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
 * trigger re-arms the next occurrence; {@link #clear()} bumps a generation counter so the fires
 * still armed for a replaced program are harmless no-ops, without touching the shared scheduler
 * (the autopilot holds live there too).
 *
 * <p>
 * The scheduler ticks before the game clock in {@code SimulationController}, so a fire can land one
 * tick early (or the clock can be rewound with {@code time set}); the due check then re-arms the
 * same target. Execution of the block actions is phase 3b: this class only schedules.
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
    public Registration register(TemporalTrigger trigger) {
        Objects.requireNonNull(trigger, "trigger");
        for (Entry entry : entries) {
            if (entry.trigger.equals(trigger)) {
                return Registration.DUPLICATE;
            }
        }
        if (entries.size() >= MAX_TRIGGERS) {
            return Registration.LIMIT_REACHED;
        }
        Entry entry = new Entry(trigger);
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
        entry.ticks = Math.max(1, clock.ticksUntil(entry.next));
        scheduler.schedule((int) Math.min(entry.ticks, Integer.MAX_VALUE),
                () -> onDue(entry, armedGeneration));
    }

    /**
     * A scheduled fire. A fire from a cleared/replaced program or one whose trigger was dropped is
     * ignored; an early fire (scheduler-before-clock order, or a rewound clock) is re-armed for the
     * same target. Phase 3b runs the block actions right before re-arming.
     */
    private void onDue(Entry entry, long armedGeneration) {
        if (armedGeneration != generation || !entries.contains(entry)) {
            return;
        }
        if (clock.now().compareTo(entry.next) < 0) {
            entry.ticks = Math.max(1, clock.ticksUntil(entry.next));
            scheduler.schedule((int) Math.min(entry.ticks, Integer.MAX_VALUE),
                    () -> onDue(entry, armedGeneration));
            return;
        }
        arm(entry);
    }

    /** One registered trigger and the fire currently armed for it. */
    private static final class Entry {
        private final TemporalTrigger trigger;
        private GameTime next;
        private long ticks;

        private Entry(TemporalTrigger trigger) {
            this.trigger = trigger;
        }
    }
}
