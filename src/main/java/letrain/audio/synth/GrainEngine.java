package letrain.audio.synth;

public class GrainEngine extends AudioGenerator { // keeping name to avoid breaking Main
    private AudioSample sample;
    private volatile double position = 0.0;

    public float getPositionNormalized() {
        if (sample == null || sample.getLength() == 0)
            return 0.0f;
        return (float) (position / sample.getLength());
    }

    // Parameters
    private float speed = 0.0f; // 0..1 input
    private float playbackRate = 1.0f;
    private float distanceFilter = 0.0f; // 0..1 from Mixer

    public void setDistanceFilter(float amount) {
        this.distanceFilter = amount;
    }

    // Loop Modes
    public enum LoopMode {
        WRAP,
        PING_PONG
    }

    private LoopMode loopMode = LoopMode.WRAP;

    public void setLoopMode(LoopMode mode) {
        this.loopMode = mode;
    }

    // Loop Points (0.0 to 1.0)
    private double loopStart = 0.0;
    private double loopEnd = 1.0;

    // Smoothing / Filter
    private float lastVal = 0.0f;

    public void setSample(AudioSample sample) {
        this.sample = sample;
        this.position = 0.0;
    }

    public AudioSample getSample() {
        return sample;
    }

    public void setSpeed(float speed) {
        this.speed = speed;
        // Map 0..1 speed to playback rate:
        // 0% speed = 0.8x playback (Idle rumble)
        // 100% speed = 2.0x playback (High revs)
        this.playbackRate = 0.8f + (speed * 1.2f);
    }

    public void setLoopPoints(float start, float end) {
        this.loopStart = Math.max(0.0, Math.min(1.0, start));
        this.loopEnd = Math.max(0.0, Math.min(1.0, end));

        // Ensure start < end
        if (this.loopStart >= this.loopEnd) {
            this.loopStart = this.loopEnd - 0.01; // Minimum loop size
        }
    }

    public double getLoopStart() {
        return loopStart;
    }

    public double getLoopEnd() {
        return loopEnd;
    }

    public float getSpeed() {
        return speed;
    }

    private boolean reverse = false;

    public void setReverse(boolean reverse) {
        this.reverse = reverse;
    }

    // Randomness Parameters
    private float turnProb = 0.0f; // Chance to flip direction (0..1)
    private float reverseDuration = 1.0f; // Average duration in seconds to stay reversed

    // Internal state for randomness
    private int samplesSinceLastCheck = 0;
    private static final int CHECK_INTERVAL = 4410; // Check ~10 times/sec at 44.1k
    private boolean isInRandomReverse = false; // Track if we are in a forced reverse state due to randomness

    public void setTurnProbability(float prob) {
        this.turnProb = Math.max(0.0f, Math.min(1.0f, prob));
    }

    public void setReverseDuration(float seconds) {
        this.reverseDuration = Math.max(0.1f, seconds);
    }

    @Override
    public void read(float[] buffer) {
        if (sample == null)
            return;

        // Simple Low Pass Filter amount based on speed
        float rawFilter = 0.8f - (speed * 0.8f);
        float speedFilter = Math.max(0.0f, Math.min(0.95f, rawFilter));

        // Combine filters: keep the stronger one? or add?
        // Let's take the Maximum filtering required.
        float filterAmount = Math.max(speedFilter, distanceFilter);

        double len = sample.getLength();
        double startPos = len * loopStart;
        double endPos = len * loopEnd;

        // Ensure we are inside loop
        if (position < startPos || position >= endPos) {
            position = reverse ? endPos : startPos;
        }

        // Crossfade parameters
        double loopLen = endPos - startPos;
        double crossfadeLen = 5000; // Target implementation
        if (loopLen < crossfadeLen * 2) {
            crossfadeLen = loopLen / 2;
        }

        for (int i = 0; i < buffer.length; i++) {
            // --- Randomness Logic (Periodic Check) ---
            samplesSinceLastCheck++;
            if (samplesSinceLastCheck >= CHECK_INTERVAL) {
                samplesSinceLastCheck = 0;

                // Calculate probabilities
                // turnProb is "chance to enter reverse state" per check?
                // Or chance per second? Let's treat it as chance per Check (simple scaling)
                // If TurnProb is 0.5 (50%), and we check 10 times/sec, that's very frequent.
                // Let's scale it: User input 0..1 maps to 0..0.05 per check (approx 50% per
                // sec)
                float checkProb = turnProb * 0.1f;

                if (!isInRandomReverse) {
                    // We are in normal mode (following 'reverse' flag or forward)
                    // Actually 'reverse' flag is the *base* state (PingPong uses it).
                    // This random logic acts ON TOP of base state? Or modifies it?
                    // Let's make it modify the current direction.

                    if (Math.random() < checkProb) {
                        isInRandomReverse = true;
                        reverse = !reverse; // Flip direction
                    }
                } else {
                    // We are in random reverse state. Check if we should exit.
                    // Prob of exiting = 1.0 / (Duration / IntervalSeconds)
                    // Duration 1.0s, Interval 0.1s -> 10 checks. Prob = 1/10 = 0.1
                    float intervalSec = (float) CHECK_INTERVAL / sampleRate;
                    float exitProb = intervalSec / reverseDuration;

                    if (Math.random() < exitProb) {
                        isInRandomReverse = false;
                        reverse = !reverse; // Flip back
                    }
                }
            }

            // 1. Read Variable Rate (Main Head)
            float raw = sample.getSampleLinear((float) position);

            // 2. Crossfade Logic
            if (!reverse) {
                // Forward Crossfade: Approaching End
                if (position > endPos - crossfadeLen) {
                    double distFromEnd = endPos - position;
                    double crossfadeFactor = 1.0 - (distFromEnd / crossfadeLen);

                    double startReadPos = startPos + (crossfadeLen - distFromEnd);
                    float startRaw = sample.getSampleLinear((float) startReadPos);

                    raw = (float) (raw * (1.0 - crossfadeFactor) + startRaw * crossfadeFactor);
                }
            } else {
                // Reverse Crossfade: Approaching Start
                if (position < startPos + crossfadeLen) {
                    double distFromStart = position - startPos;
                    double crossfadeFactor = 1.0 - (distFromStart / crossfadeLen);

                    // Read from end of loop
                    double endReadPos = endPos - (crossfadeLen - distFromStart);
                    float endRaw = sample.getSampleLinear((float) endReadPos);

                    raw = (float) (raw * (1.0 - crossfadeFactor) + endRaw * crossfadeFactor);
                }
            }

            // 3. Simple Low Pass smoothing
            // correct LPF: output = last + alpha * (input - last)
            float alpha = 1.0f - filterAmount;
            float smoothed = lastVal + (raw - lastVal) * alpha;
            lastVal = smoothed;

            buffer[i] += smoothed * volume;

            // 4. Advance Position
            position += reverse ? -playbackRate : playbackRate;

            // 5. Loop
            if (!reverse) {
                if (position >= endPos) {
                    if (loopMode == LoopMode.PING_PONG) {
                        reverse = true;
                        position = endPos - (position - endPos); // Bounce back
                    } else {
                        // WRAP
                        position = startPos + crossfadeLen + (position - endPos);
                        if (position >= endPos)
                            position = startPos;
                    }
                }
            } else {
                if (position <= startPos) {
                    if (loopMode == LoopMode.PING_PONG) {
                        reverse = false;
                        position = startPos + (startPos - position); // Bounce forward
                    } else {
                        // WRAP (Reverse wrapping)
                        position = endPos - crossfadeLen + (position - startPos);
                        if (position <= startPos)
                            position = endPos;
                    }
                }
            }
        }
    }
}
