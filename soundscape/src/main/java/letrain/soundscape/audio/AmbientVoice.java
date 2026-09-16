package letrain.soundscape.audio;

import java.util.Random;

/**
 * One looping voice: plays its sample with a crossfade jump to a random offset at the end, so the
 * repetition is hard to notice. The gain follows its target with a slow ease, and the voice goes
 * silent (and can be discarded) when the target is zero and the fade has finished.
 */
public class AmbientVoice {

    /** Crossfade window at the loop jump (50 ms at 44.1 kHz). */
    private static final int FADE_FRAMES = 2205;
    /** Only long materials (beds) jump randomly; short event takes wrap cleanly. */
    private static final float RANDOM_JUMP_MIN_SECONDS = 6f;
    /** Ease time constant for gain changes, in seconds. */
    private static final float GAIN_FADE_SECONDS = 0.8f;
    private static final float SILENT = 0.001f;

    private final SoundSample sample;
    private final Random random;
    private float gain;
    private float target;
    private boolean started;
    private int position;
    private int crossNext;
    private int crossRemaining;

    public AmbientVoice(SoundSample sample, Random random) {
        this.sample = sample;
        this.random = random;
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
            position = randomOffset();
        }
        for (int i = 0; i < frames; i++) {
            mono[i] += nextSample() * gain;
        }
    }

    private float nextSample() {
        int length = sample.length();
        if (length < (int) (RANDOM_JUMP_MIN_SECONDS * sample.sampleRate())) {
            float value = sample.frame(position);
            position = (position + 1) % length;
            return value;
        }
        if (crossRemaining > 0) {
            float t = 1f - crossRemaining / (float) FADE_FRAMES;
            float value = sample.frame(position) * (1f - t) + sample.frame(crossNext) * t;
            position++;
            crossNext++;
            crossRemaining--;
            if (crossRemaining == 0) {
                position = crossNext;
            }
            return value;
        }
        if (position + FADE_FRAMES >= length) {
            crossNext = randomOffset();
            crossRemaining = FADE_FRAMES;
            return nextSample();
        }
        return sample.frame(position++);
    }

    private int randomOffset() {
        // Leave room for the crossfade and for at least FADE_FRAMES of audio before the next jump.
        int maxOffset = Math.max(1, sample.length() - 2 * FADE_FRAMES - 1);
        return random.nextInt(maxOffset);
    }
}
