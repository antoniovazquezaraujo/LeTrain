package letrain.audio.material;

import java.util.Optional;

/**
 * Resolved metadata of one material line in a profile descriptor (ADR-029 §5).
 *
 * @param path    classpath path, e.g. {@code sound/train/generic/notch-1.wav}
 * @param loop    loop window; empty for one-shots and for loops still missing measured points
 * @param gainDb  per-material gain in dB (0.0 when not declared)
 * @param effort  optional informational label for the licensor contract (parsed, unused today)
 * @param torque  optional informational label for the licensor contract (parsed, unused today)
 */
public record MaterialRef(String path, Optional<LoopPoints> loop, double gainDb,
        Optional<String> effort, Optional<String> torque) {

    public MaterialRef {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("path must not be blank");
        }
        if (!Double.isFinite(gainDb)) {
            throw new IllegalArgumentException("gainDb must be finite: " + gainDb);
        }
        loop = loop == null ? Optional.empty() : loop;
        effort = effort == null ? Optional.empty() : effort;
        torque = torque == null ? Optional.empty() : torque;
    }

    /** Convenience constructor without optional labels. */
    public MaterialRef(String path, Optional<LoopPoints> loop, double gainDb) {
        this(path, loop, gainDb, Optional.empty(), Optional.empty());
    }

    public boolean hasLoop() {
        return loop.isPresent();
    }
}
