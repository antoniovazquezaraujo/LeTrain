package letrain.soundscape.audio;

/**
 * One looping voice: plays its sample from start to end and restarts. The gain follows its target
 * with a slow ease, and the voice goes silent (and can be discarded) when the target is zero and
 * the fade has finished.
 *
 * <p>
 * Air absorption: {@link #setAir(float)} moves a one-pole low-pass between 20 kHz (0, next to the
 * listener) and 1.2 kHz (1, far away), so distance changes the timbre and not only the level.
 *
 * <p>
 * Random jump/crossfade between takes is parked for now: materials will be cut to loop cleanly
 * instead.
 */
public class AmbientVoice {

    /** Ease time constant for gain changes, in seconds. */
    private static final float GAIN_FADE_SECONDS = 0.8f;
    private static final float AIR_FADE_SECONDS = 0.4f;
    private static final float BYPASS_CUTOFF = 20000f;
    private static final float FAR_CUTOFF = 1200f;
    private static final float SILENT = 0.001f;

    private final SoundSample sample;
    private float gain;
    private float target;
    private boolean started;
    private int position;
    private float air;
    private float cutoff = BYPASS_CUTOFF;
    private float lowpass;

    public AmbientVoice(SoundSample sample) {
        this.sample = sample;
    }

    public void setTarget(float target) {
        this.target = Math.max(0f, target);
    }

    /** Air absorption: 0 = next to the listener, 1 = far away (muffled highs). */
    public void setAir(float air) {
        this.air = Math.max(0f, Math.min(1f, air));
    }

    public boolean isFinished() {
        return target <= 0f && gain <= SILENT;
    }

    /** Adds this voice to a mono mix buffer. */
    public void render(float[] mono, int frames) {
        float alpha = 1f - (float) Math.exp(-frames / (sample.sampleRate() * GAIN_FADE_SECONDS));
        gain += (target - gain) * alpha;
        if (gain <= SILENT && target <= 0f) {
            return;
        }
        if (!started && target > 0f) {
            started = true;
            position = 0;
        }
        boolean filtering = updateAirFilter(frames);
        float coefficient =
                filtering ? (float) (1 - Math.exp(-2 * Math.PI * cutoff / sample.sampleRate()))
                        : 0f;
        for (int i = 0; i < frames; i++) {
            float value = nextSample();
            if (filtering) {
                lowpass += coefficient * (value - lowpass);
                value = lowpass;
            }
            mono[i] += value * gain;
        }
    }

    /** Eases the cutoff towards the air target; returns whether the filter is audible. */
    private boolean updateAirFilter(int frames) {
        float targetCutoff = cutoffForAir();
        float alpha = 1f - (float) Math.exp(-frames / (sample.sampleRate() * AIR_FADE_SECONDS));
        cutoff += (targetCutoff - cutoff) * alpha;
        if (air <= 0f && cutoff > BYPASS_CUTOFF * 0.999f) {
            cutoff = BYPASS_CUTOFF;
        }
        return cutoff < BYPASS_CUTOFF;
    }

    private float cutoffForAir() {
        return BYPASS_CUTOFF * (float) Math.pow(FAR_CUTOFF / BYPASS_CUTOFF, air);
    }

    private float nextSample() {
        float value = sample.frame(position);
        position++;
        if (position >= sample.length()) {
            position = 0;
        }
        return value;
    }
}
