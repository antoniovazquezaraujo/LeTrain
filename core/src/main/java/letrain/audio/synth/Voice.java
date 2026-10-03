package letrain.audio.synth;

import java.util.Arrays;
import java.util.Objects;

/**
 * One mixed voice of the train synthesizer (ADR-029 §2/§4): a {@link GrainEngine} plus a per-sample
 * gain envelope and an optional material gain applied by the mix bus.
 *
 * <p>
 * {@link #mixInto(float[])} reads the engine into a private scratch buffer, so every voice keeps an
 * independent gain. The engine output is then accumulated weighted by the envelope gain and the
 * material gain. Material gain bypasses {@link GrainEngine#setVolume(float)} on purpose: that
 * setter clamps to {@code 0..1} and could not carry the positive-dB gains measured for some
 * materials (ADR-029 §5).
 *
 * <p>
 * The envelope advances one step per sample, so crossfades (200 ms), cuts (100 ms) and onset
 * fade-ins (10-20 ms) are applied sample by sample without zipper noise (ADR-029 §4).
 *
 * <p>
 * Consistency rule (ADR-029): the sample of an audible voice must never be swapped in place.
 * {@link #setSample(AudioSample)} throws {@link IllegalStateException} unless the voice is silent,
 * so callers must fade the voice out before switching material.
 *
 * <p>
 * Not thread-safe by design: the audio thread advances the envelope while the game thread calls the
 * setters; races are expected to be benign (a fade may start one block late).
 */
final class Voice {

    private final String name;
    private final GrainEngine engine;

    /** Reused scratch buffer (mixer block size is 1024 frames). */
    private float[] scratch = new float[0];

    private float gain = 0.0f;
    private float targetGain = 0.0f;
    private float gainStep = 0.0f;
    private float materialGain = 1.0f;

    Voice(String name, GrainEngine engine) {
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.engine = Objects.requireNonNull(engine, "engine must not be null");
    }

    /** Engine behind this voice (sample, loop points, speed, filter, ...). */
    GrainEngine engine() {
        return engine;
    }

    /** Current envelope gain; advanced one step per sample by {@link #mixInto(float[])}. */
    float getGain() {
        return gain;
    }

    /**
     * Envelope target reached when the running fade completes (equals {@link #getGain()} when
     * idle).
     */
    float getTargetGain() {
        return targetGain;
    }

    /** Linear material gain applied in the mix sum. */
    float getMaterialGain() {
        return materialGain;
    }

    /** True when neither the current nor the target gain produces sound. */
    boolean isSilent() {
        return gain <= 0.0f && targetGain <= 0.0f;
    }

    /** Sets the envelope gain immediately, cancelling any running fade. */
    void setGain(float value) {
        requireGain(value, "gain");
        gain = value;
        targetGain = value;
        gainStep = 0.0f;
    }

    /**
     * Starts a linear per-sample fade to {@code targetGain}; the current gain moves one
     * {@code gainStep} per sample until it reaches the target. A non-positive duration applies the
     * target immediately.
     */
    void fadeTo(float targetGain, float seconds) {
        requireGain(targetGain, "targetGain");
        this.targetGain = targetGain;
        float rate = engine.getSampleRate();
        if (seconds <= 0.0f || rate <= 0.0f) {
            gain = targetGain;
            gainStep = 0.0f;
            return;
        }
        gainStep = (targetGain - gain) / (seconds * rate);
        if (gainStep == 0.0f) {
            gain = targetGain;
        }
    }

    /** Material gain in dB, applied in the mix sum; positive values are allowed and not clamped. */
    void setMaterialGainDb(double gainDb) {
        if (!Double.isFinite(gainDb)) {
            throw new IllegalArgumentException("gainDb must be finite: " + gainDb);
        }
        float linear = (float) Math.pow(10.0, gainDb / 20.0);
        if (!Float.isFinite(linear)) {
            throw new IllegalArgumentException("gainDb is out of range: " + gainDb);
        }
        materialGain = linear;
    }

    /**
     * Swaps the voice sample. Enforces the ADR-029 consistency rule: an audible voice must be faded
     * out first because replacing its sample mid-flight would click.
     */
    void setSample(AudioSample sample) {
        Objects.requireNonNull(sample, "sample must not be null");
        if (!isSilent()) {
            throw new IllegalStateException("cannot swap the sample of audible voice '" + name
                    + "' (gain=" + gain + ", targetGain=" + targetGain + ")");
        }
        engine.setSample(sample);
    }

    /**
     * Reads this voice into {@code buffer}, accumulating the engine output weighted by the
     * per-sample envelope gain and the material gain. Silent voices are skipped entirely, which
     * freezes their engine until they are faded in again.
     */
    void mixInto(float[] buffer) {
        if (isSilent()) {
            return;
        }
        if (scratch.length != buffer.length) {
            scratch = new float[buffer.length];
        }
        Arrays.fill(scratch, 0.0f);
        engine.read(scratch);
        for (int i = 0; i < buffer.length; i++) {
            buffer[i] += scratch[i] * nextGain() * materialGain;
        }
    }

    private float nextGain() {
        float current = gain;
        if (gain != targetGain) {
            gain += gainStep;
            if (gainStep > 0.0f ? gain >= targetGain : gain <= targetGain) {
                gain = targetGain;
                gainStep = 0.0f;
            }
        }
        return current;
    }

    private static void requireGain(float value, String field) {
        if (!Float.isFinite(value) || value < 0.0f) {
            throw new IllegalArgumentException(field + " must be finite and >= 0: " + value);
        }
    }
}
