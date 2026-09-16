package letrain.soundscape.audio;

/**
 * One looping voice: plays its sample from start to end and restarts. The gain follows its target
 * with a slow ease, and the voice goes silent (and can be discarded) when the target is zero and
 * the fade has finished.
 *
 * <p>
 * Random jump/crossfade between takes is parked for now: materials will be cut to loop cleanly
 * instead.
 */
public class AmbientVoice {

    /** Ease time constant for gain changes, in seconds. */
    private static final float GAIN_FADE_SECONDS = 0.8f;
    private static final float SILENT = 0.001f;

    private final SoundSample sample;
    private float gain;
    private float target;
    private boolean started;
    private int position;

    public AmbientVoice(SoundSample sample) {
        this.sample = sample;
    }

    public void setTarget(float target) {
        this.target = Math.max(0f, target);
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
        for (int i = 0; i < frames; i++) {
            mono[i] += nextSample() * gain;
        }
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
