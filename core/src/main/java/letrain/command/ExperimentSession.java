package letrain.command;

import letrain.mvp.Model;
import letrain.utils.ValidationUtils;

/**
 * In-memory "experiment mode" session (ADR-020 roadmap, item 5). While the user experiments with the
 * live simulation (no journal, no undo), this keeps a full snapshot of the model taken on entry and
 * restores it on exit, like a time machine.
 *
 * <p>
 * It is a small state holder, UI-free: the presenter owns it, snapshots through its own
 * {@link Codec} (the same in-memory serialization used for undo checkpoints) and, on exit, applies
 * the returned model through its usual {@code applyModel}.
 *
 * <p>
 * Contract: {@link #begin(Model)} snapshots once (idempotent while active); {@link #end()} deactivates
 * and returns the restored model (or null if there was no session); {@link #abandon()} deactivates
 * without restoring (used when the model is replaced by a load/new game).
 */
public class ExperimentSession {

    /** Serializes/deserializes whole models (in-memory snapshot). */
    public interface Codec {
        byte[] toBytes(Model model);

        Model fromBytes(byte[] data);
    }

    private final Codec codec;
    private byte[] snapshot;
    private boolean active;

    public ExperimentSession(Codec codec) {
        this.codec = ValidationUtils.requireNonNull(codec, "codec");
    }

    /** Takes the entry snapshot of {@code live}; a no-op if a session is already active. */
    public void begin(Model live) {
        if (active) {
            return;
        }
        this.snapshot = codec.toBytes(ValidationUtils.requireNonNull(live, "live"));
        this.active = true;
    }

    public boolean isActive() {
        return active;
    }

    /**
     * Ends the session and returns the model restored from the entry snapshot, or null when there
     * was no active session. The caller applies it through its own model-swap routine.
     */
    public Model end() {
        if (!active) {
            return null;
        }
        Model restored = codec.fromBytes(snapshot);
        abandon();
        return restored;
    }

    /** Ends the session without restoring anything (e.g. the live model was replaced externally). */
    public void abandon() {
        this.snapshot = null;
        this.active = false;
    }
}
