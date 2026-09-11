package letrain.command;

import java.util.ArrayList;
import java.util.List;
import letrain.map.Point;
import letrain.mvp.Model;
import letrain.utils.ValidationUtils;

/**
 * In-memory undo/redo history for paused editing (ADR-020 roadmap, item 3). It records, in order,
 * every edit the player made while pause-editing was on (auto-captured by the recorder funnels),
 * and stores periodic full-model snapshots (checkpoints) so that an undo can <b>reset the world to
 * a checkpoint and re-execute the recorded commands</b> instead of replaying the whole history.
 *
 * <p>
 * The history is a <b>planner</b>, not an executor: it never mutates a world itself. It records the
 * canonical edit commands and their checkpoints and, on undo/redo, hands back an {@link UndoPlan}
 * describing the base snapshot to restore plus the exact sub-range of commands to re-run. The
 * caller (the UI presenter, or a headless test) applies the base model through its own
 * {@code applyModel} routine and then re-executes the slice through the same command machinery the
 * console uses (PlayerCommandExecutor + turtle), exactly like the golden replay tests. This keeps
 * the core deterministic and UI-free while guaranteeing byte-identical results: the golden replay
 * tests prove that replaying recorded commands on a fresh copy of a checkpoint reproduces the same
 * state.
 *
 * <p>
 * Contract (must be honoured by callers):
 * <ul>
 * <li>Recording only happens while pause-editing is on (world frozen); outside pause there is no
 * undo history (the simulation makes journal replay non-deterministic).</li>
 * <li>{@link #begin} takes the base snapshot of the paused world; call it once when the editing
 * session starts. {@link #end} clears the history (e.g. when leaving pause-editing).</li>
 * <li>{@link #record} must be called with the canonical command text <b>after</b> it was already
 * applied to the live world, and with {@link #bind} pointing at that live model so periodic
 * checkpoints can snapshot it.</li>
 * <li>{@link #planUndo}/{@link #planRedo} only compute the plan; the caller executes it and then
 * calls {@link #commit} with the target command count.</li>
 * </ul>
 */
public class UndoRedoHistory {

    /** Serializes/deserializes whole models (in-memory checkpoints). */
    public interface Codec {
        byte[] toBytes(Model model);

        Model fromBytes(byte[] data);
    }

    /**
     * A computed undo/redo step: which checkpoint base to restore ({@code null} base means "keep
     * the current live model", i.e. a redo that goes forward from the present state), the exact
     * command slice {@code [fromIndex, toIndex)} to re-execute on that base, and the resulting
     * {@code target} command count to pass to {@link #commit}.
     */
    public record UndoPlan(byte[] baseBytes, int fromIndex, int toIndex, int target) {
        public List<String> commandsToReplay(List<String> journal) {
            return new ArrayList<>(journal.subList(fromIndex, toIndex));
        }
    }

    /** How many recorded commands apart a fresh full-model checkpoint is taken. */
    private static final int CHECKPOINT_EVERY = 10;

    private record Checkpoint(int commandIndex, byte[] bytes) {}

    private final Codec codec;

    /** Canonical edit commands in application order (the redo stack tail when {@code applied} lags). */
    private final List<String> commands = new ArrayList<>();
    private final List<Checkpoint> checkpoints = new ArrayList<>();

    /**
     * Per-command optional "resume origin": the position of the previous rail piece this command
     * chained from when it was executed (aligned 1:1 with {@link #commands}; null entries when the
     * command started a new, disconnected piece or is not a build). The {@link RailTrackMaker}'s
     * chaining state ({@code oldTrack}) is transient UI state, so a slice replayed after restoring
     * a checkpoint cannot know that the first piece must continue the one laid just before the
     * slice. Storing the origin here lets the caller re-seed the maker and keep undo byte-identical
     * to a fresh full replay.
     */
    private final List<Point> resumeFroms = new ArrayList<>();

    /** Number of {@link #commands} currently reflected in the bound live model. */
    private int applied = 0;

    /** The live model this history is bound to (updated on begin/bind), used for checkpoints. */
    private Model live;

    public UndoRedoHistory(Codec codec) {
        this.codec = ValidationUtils.requireNonNull(codec, "codec");
    }

    /** Starts an editing session over {@code baseModel}, snapshotting it as checkpoint 0. */
    public void begin(Model baseModel) {
        this.live = ValidationUtils.requireNonNull(baseModel, "baseModel");
        this.commands.clear();
        this.checkpoints.clear();
        this.resumeFroms.clear();
        this.applied = 0;
        this.checkpoints.add(new Checkpoint(0, codec.toBytes(baseModel)));
    }

    /** Clears the whole history (leaving pause-editing / ending the editing session). */
    public void end() {
        this.live = null;
        this.commands.clear();
        this.checkpoints.clear();
        this.resumeFroms.clear();
        this.applied = 0;
    }

    /** Rebinds this history to a new live model instance (after an applyModel swap). */
    public void bind(Model model) {
        this.live = ValidationUtils.requireNonNull(model, "model");
    }

    public Model live() {
        return live;
    }

    public boolean isEmpty() {
        return commands.isEmpty();
    }

    public int size() {
        return commands.size();
    }

    public int applied() {
        return applied;
    }

    public int canUndo() {
        return applied;
    }

    public int canRedo() {
        return commands.size() - applied;
    }

    public List<String> entries() {
        return java.util.Collections.unmodifiableList(commands);
    }

    /**
     * Records a canonical edit command. If the history was rewound (undo) this discards the redo
     * tail first, exactly like a linear text-editor undo/redo. The command is expected to have
     * already been applied to the live model. A periodic checkpoint of the live model is taken every
     * {@value #CHECKPOINT_EVERY} commands.
     */
    public void record(String canonicalCommand) {
        record(canonicalCommand, null);
    }

    /**
     * Records a canonical edit command, optionally with the {@code resumeFrom} origin of the rail
     * piece it chained from (see the {@link #resumeFroms} field). If the history was rewound (undo)
     * this discards the redo tail first, exactly like a linear text-editor undo/redo. The command is
     * expected to have already been applied to the live model. A periodic checkpoint of the live
     * model is taken every {@value #CHECKPOINT_EVERY} commands.
     */
    public void record(String canonicalCommand, Point resumeFrom) {
        String command = canonicalCommand == null ? null : canonicalCommand.trim();
        if (command == null || command.isEmpty() || live == null) {
            return;
        }
        if (applied < commands.size()) {
            commands.subList(applied, commands.size()).clear();
            resumeFroms.subList(applied, resumeFroms.size()).clear();
            checkpoints.removeIf(c -> c.commandIndex() > applied);
        }
        commands.add(command);
        resumeFroms.add(resumeFrom);
        applied = commands.size();
        maybeCheckpoint();
    }

    /**
     * Records like {@link #record}, but collapses consecutive {@code signal N set limit X} tweaks for
     * the same signal into a single entry (the last value wins). Any checkpoint at or after the
     * replaced command is dropped, since its snapshot would no longer match the rewritten command.
     */
    public void recordCoalescing(String canonicalCommand) {
        recordCoalescing(canonicalCommand, null);
    }

    /** Coalescing variant of {@link #record(String, Point)}. */
    public void recordCoalescing(String canonicalCommand, Point resumeFrom) {
        String command = canonicalCommand == null ? null : canonicalCommand.trim();
        if (command == null || command.isEmpty() || live == null) {
            return;
        }
        if (applied == commands.size() && applied > 0
                && CommandMerge.consecutiveProperty(commands.get(applied - 1), command)) {
            commands.set(applied - 1, command);
            resumeFroms.set(applied - 1, resumeFrom);
            checkpoints.removeIf(c -> c.commandIndex() >= applied);
            return;
        }
        record(command, resumeFrom);
    }

    /**
     * The optional position of the rail piece that the command at {@code commandIndex} chained
     * from, or null when the command was a fresh/disconnected piece or not a build. Only meaningful
     * for commands recorded by the keyboard funnel; used by undo/redo slice replay to re-seed the
     * fresh track maker.
     */
    public Point resumeFrom(int commandIndex) {
        if (commandIndex < 0 || commandIndex >= resumeFroms.size()) {
            return null;
        }
        return resumeFroms.get(commandIndex);
    }

    private void maybeCheckpoint() {
        if (applied % CHECKPOINT_EVERY == 0) {
            checkpoints.add(new Checkpoint(applied, codec.toBytes(live)));
        }
    }

    /**
     * Computes the plan to undo {@code steps} commands (at most {@link #canUndo()}), or null when
     * there is nothing to undo. The plan restores the nearest checkpoint at or before the target and
     * replays the commands between that checkpoint and the target. Execute it (restore
     * {@link UndoPlan#baseBytes()} as a fresh model, apply it, replay the slice) and finish with
     * {@link #commit}.
     */
    public UndoPlan planUndo(int steps) {
        if (steps < 1 || applied == 0) {
            return null;
        }
        int target = Math.max(0, applied - steps);
        Checkpoint base = checkpointAtOrBefore(target);
        if (base == null) {
            return null;
        }
        return new UndoPlan(base.bytes(), base.commandIndex(), target, target);
    }

    /**
     * Computes the plan to redo {@code steps} commands (at most {@link #canRedo()}), or null when
     * there is nothing to redo. Redo always moves forward from the current live model, so the plan
     * has a {@code null} base and replays {@code [applied, target)} in place.
     */
    public UndoPlan planRedo(int steps) {
        if (steps < 1) {
            return null;
        }
        int target = Math.min(commands.size(), applied + steps);
        if (target == applied) {
            return null;
        }
        return new UndoPlan(null, applied, target, target);
    }

    /** Confirms that the live model now reflects {@code appliedCommandCount} recorded commands. */
    public void commit(int appliedCommandCount) {
        this.applied = Math.max(0, Math.min(appliedCommandCount, commands.size()));
    }

    /**
     * Restores the checkpoint base of {@code plan} as a fresh, fully initialized model, or null when
     * the plan keeps the current live model (a redo). The caller is responsible for swapping the
     * returned model into the running presenters and for {@link #bind}ing this history to it.
     */
    public Model restore(UndoPlan plan) {
        if (plan == null || plan.baseBytes() == null) {
            return null;
        }
        return codec.fromBytes(plan.baseBytes());
    }

    private Checkpoint checkpointAtOrBefore(int target) {
        Checkpoint best = null;
        for (Checkpoint c : checkpoints) {
            if (c.commandIndex() <= target && (best == null || c.commandIndex() > best.commandIndex())) {
                best = c;
            }
        }
        return best;
    }
}
