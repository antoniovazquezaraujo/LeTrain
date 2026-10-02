package letrain.audio.material;

/**
 * Loop window of a material in seconds, measured from the start of the file (ADR-029 §5).
 *
 * <p>
 * {@code startSeconds} is the first sample of the window and {@code lengthSeconds} its duration;
 * {@code GrainEngine} applies its own 5000-sample crossfade inside that window.
 */
public record LoopPoints(double startSeconds, double lengthSeconds) {

    public LoopPoints {
        if (!Double.isFinite(startSeconds) || startSeconds < 0.0) {
            throw new IllegalArgumentException("loop start must be finite and >= 0: " + startSeconds);
        }
        if (!Double.isFinite(lengthSeconds) || lengthSeconds <= 0.0) {
            throw new IllegalArgumentException("loop length must be finite and > 0: " + lengthSeconds);
        }
    }

    /** End of the loop window in seconds. */
    public double endSeconds() {
        return startSeconds + lengthSeconds;
    }
}
