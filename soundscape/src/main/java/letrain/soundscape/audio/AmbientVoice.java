package letrain.soundscape.audio;

/**
 * One looping voice: plays its sample from start to end and restarts. The gain follows its target
 * with a slow ease, and the voice goes silent (and can be discarded) when the target is zero and
 * the fade has finished.
 *
 * <p>
 * Distance: {@link #setDistance(float)} moves a one-pole low-pass (air absorption) between 20 kHz
 * (0, next to the listener) and 1.2 kHz (1, far away), so distance changes the timbre and not only
 * the level.
 *
 * <p>
 * Random jump/crossfade between takes is parked for now: materials will be cut to loop cleanly
 * instead.
 */
public class AmbientVoice {

    /** Ease time constant for gain changes, in seconds. */
    private static final float GAIN_FADE_SECONDS = 0.8f;
    /** Very short anti-click ramp used on a focus cut (ADR-025). */
    private static final float CUT_FADE_SECONDS = 0.012f;
    private static final float CUT_EPSILON = 0.01f;
    private static final float DISTANCE_FADE_SECONDS = 0.4f;
    private static final float BYPASS_CUTOFF = 20000f;
    private static final float FAR_CUTOFF = 1200f;
    private static final float SILENT = 0.001f;

    private final SoundSample sample;
    private float gain;
    private float target;
    private boolean cutting;
    private boolean started;
    private int position;
    private float distance;
    private float cutoff = BYPASS_CUTOFF;
    private float lowpass;

    public AmbientVoice(SoundSample sample) {
        this.sample = sample;
    }

    public void setTarget(float target) {
        this.target = Math.max(0f, target);
    }

    /** Focus cut: reach the target with a very short anti-click ramp instead of the slow ease. */
    public void cutTo(float target) {
        this.target = Math.max(0f, target);
        this.cutting = true;
    }

    /** Distance absorption: 0 = next to the listener, 1 = far away (muffled highs). */
    public void setDistance(float distance) {
        this.distance = Math.max(0f, Math.min(1f, distance));
    }

    public boolean isFinished() {
        return target <= 0f && gain <= SILENT;
    }

    /** Adds this voice to a mono mix buffer. */
    public void render(float[] mono, int frames) {
        float fadeSeconds = cutting ? CUT_FADE_SECONDS : GAIN_FADE_SECONDS;
        float alpha = 1f - (float) Math.exp(-frames / (sample.sampleRate() * fadeSeconds));
        gain += (target - gain) * alpha;
        if (cutting && Math.abs(target - gain) < CUT_EPSILON) {
            cutting = false;
        }
        if (gain <= SILENT && target <= 0f) {
            return;
        }
        if (!started && target > 0f) {
            started = true;
            position = 0;
        }
        boolean filtering = updateDistanceFilter(frames);
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

    /** Eases the cutoff towards the distance target; returns whether the filter is audible. */
    private boolean updateDistanceFilter(int frames) {
        float targetCutoff = cutoffForDistance();
        float alpha =
                1f - (float) Math.exp(-frames / (sample.sampleRate() * DISTANCE_FADE_SECONDS));
        cutoff += (targetCutoff - cutoff) * alpha;
        if (distance <= 0f && cutoff > BYPASS_CUTOFF * 0.999f) {
            cutoff = BYPASS_CUTOFF;
        }
        return cutoff < BYPASS_CUTOFF;
    }

    private float cutoffForDistance() {
        return BYPASS_CUTOFF * (float) Math.pow(FAR_CUTOFF / BYPASS_CUTOFF, distance);
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
