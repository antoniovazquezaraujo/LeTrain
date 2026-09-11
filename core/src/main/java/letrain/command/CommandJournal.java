package letrain.command;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * In-memory command journal (ADR-020 item 2): an ordered list of the canonical DSL commands the
 * recorder captured. It behaves like a text editor's history: a cursor ({@code applied}) marks how
 * many commands are reflected in the live world, and a {@code base} marks how many are "baked" into
 * it (undo cannot go below the base). Exporting writes only {@code [0, applied)}.
 *
 * <p>
 * Consumers: the scenario export uses {@link #appliedEntries()}; undo/redo move {@link #applied}
 * (see {@link #setApplied}/{@link #undo}/{@link #redo}); {@link #bake()} commits the current edits
 * as the new base when an editing session ends. The journal is passive: it only stores what the
 * recorder feeds it.
 */
public class CommandJournal {

    private final List<String> commands = new ArrayList<>();
    private boolean recording = false;

    /** Number of commands currently reflected in the live world (and exported). */
    private int applied = 0;

    /** Number of commands baked into the current base world (undo cannot go below this). */
    private int base = 0;

    /** True while the recorder accepts commands (during the Record/edit mode). */
    public boolean isRecording() {
        return recording;
    }

    public void startRecording() {
        recording = true;
    }

    public void stopRecording() {
        recording = false;
    }

    public void toggleRecording() {
        recording = !recording;
    }

    /**
     * Appends a command (discarding any redo tail first, like a text editor) and marks it applied.
     */
    public void record(String command) {
        if (command == null || command.trim().isEmpty()) {
            return;
        }
        if (applied < commands.size()) {
            commands.subList(applied, commands.size()).clear();
        }
        commands.add(command.trim());
        applied = commands.size();
    }

    /** All commands, including any redo tail (mainly for inspection/debug). */
    public List<String> entries() {
        return Collections.unmodifiableList(commands);
    }

    /** The commands currently reflected in the world ({@code [0, applied)}), i.e. what exports. */
    public List<String> appliedEntries() {
        return Collections.unmodifiableList(new ArrayList<>(commands.subList(0, applied)));
    }

    /**
     * Records like {@link #record}, but collapses consecutive {@code signal N set limit X} tweaks for
     * the same signal into a single entry (the last value wins). Any other command, or a limit for a
     * different signal, starts a new entry.
     */
    public void recordCoalescing(String command) {
        String c = command == null ? null : command.trim();
        if (c == null || c.isEmpty()) {
            return;
        }
        if (applied == commands.size() && applied > 0
                && CommandMerge.consecutiveSignalLimit(commands.get(applied - 1), c)) {
            commands.set(applied - 1, c);
            return;
        }
        record(c);
    }

    public int size() {
        return commands.size();
    }

    public int applied() {
        return applied;
    }

    public int base() {
        return base;
    }

    /** Moves the applied cursor, clamped to {@code [base, size]}. */
    public void setApplied(int value) {
        this.applied = Math.max(base, Math.min(value, commands.size()));
    }

    /** Rewinds the applied cursor by {@code n} (undo), never below the base. */
    public void undo(int steps) {
        setApplied(applied - Math.max(0, steps));
    }

    /** Advances the applied cursor by {@code n} (redo), never past the commands. */
    public void redo(int steps) {
        setApplied(applied + Math.max(0, steps));
    }

    /**
     * Commits the applied commands as the new base (used when the edit mode ends): the redo tail is
     * discarded and undo can no longer cross this point.
     */
    public void bake() {
        if (applied < commands.size()) {
            commands.subList(applied, commands.size()).clear();
        }
        base = applied;
    }

    public boolean isEmpty() {
        return commands.isEmpty();
    }

    public void clear() {
        commands.clear();
        applied = 0;
        base = 0;
    }
}
