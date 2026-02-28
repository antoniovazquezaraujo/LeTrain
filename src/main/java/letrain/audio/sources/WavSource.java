package letrain.audio.sources;

/**
 * A one-shot WAV audio source that plays once and then deactivates.
 */
import letrain.audio.core.AudioMixer;
import letrain.audio.core.AudioSource;
import letrain.audio.synth.AudioSample;

public class WavSource implements AudioSource {
    private final AudioSample sample;
    private float x, y, z;
    private float cursor = 0;
    private float volume = 1.0f;
    private boolean active = true;
    private float filterAmount = 0.0f;
    private float lastVal = 0.0f;
    private float refDistance = 1000.0f;
    private float maxDistance = 5000.0f;

    public WavSource(AudioSample sample) {
        this.sample = sample;
    }

    @Override
    public boolean read(float[] buffer) {
        if (!active || sample == null) {
            return false;
        }

        int len = buffer.length;
        float rateRatio = sample.getSampleRate() / AudioMixer.SAMPLE_RATE;

        for (int i = 0; i < len; i++) {
            if ((int) cursor >= sample.getLength()) {
                active = false;
                // Fill rest of buffer with silence
                for (int j = i; j < len; j++) {
                    buffer[j] = 0;
                }
                return true; // We filled what we could and we are done
            }

            float raw = sample.getSample((int) cursor);

            // Simple LPF (matches AmbientSource logic)
            float alpha = 1.0f - filterAmount;
            float smoothed = lastVal + (raw - lastVal) * alpha;
            lastVal = smoothed;

            buffer[i] = smoothed * volume;
            cursor += rateRatio;
        }

        return true;
    }

    @Override
    public void setPosition(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public float getX() {
        return x;
    }

    @Override
    public float getY() {
        return y;
    }

    @Override
    public float getZ() {
        return z;
    }

    @Override
    public float getReferenceDistance() {
        return refDistance;
    }

    @Override
    public float getMaxDistance() {
        return maxDistance;
    }

    @Override
    public void setDistanceFilter(float amount) {
        this.filterAmount = Math.min(0.99f, amount);
    }

    public void setVolume(float volume) {
        this.volume = volume;
    }

    public boolean isActive() {
        return active;
    }
}
