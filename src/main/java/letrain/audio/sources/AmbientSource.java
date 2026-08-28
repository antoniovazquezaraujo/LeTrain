package letrain.audio.sources;

import letrain.audio.core.AudioMixer;
import letrain.audio.core.AudioSource;
import letrain.audio.synth.AudioSample;

public class AmbientSource implements AudioSource {

    private AudioSample sample;
    private float x, y, z;
    private float cursor = 0;
    private boolean looping = true;
    private float volume = 1.0f;
    private boolean active = true;

    private float refDistance = 100.0f;
    private float maxDistance = 2000.0f;

    public AmbientSource(AudioSample sample) {
        this.sample = sample;
    }

    public void setLooping(boolean looping) {
        this.looping = looping;
    }

    public void setVolume(float volume) {
        this.volume = volume;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    private float filterAmount = 0.0f;
    private float lastVal = 0.0f;

    @Override
    public boolean read(float[] buffer) {
        if (!active || sample == null)
            return false;

        int len = buffer.length;
        for (int i = 0; i < len; i++) {
            if ((int) cursor >= sample.getLength()) {
                if (looping) {
                    cursor = 0;
                } else {
                    active = false;
                    // Fill rest with silence
                    for (int j = i; j < len; j++)
                        buffer[j] = 0;
                    return true; // Finished but filled buffer partially
                }
            }

            float raw = sample.getSample((int) cursor);

            // Apply Simple LPF
            // output = last + alpha * (input - last)
            float alpha = 1.0f - filterAmount;
            float smoothed = lastVal + (raw - lastVal) * alpha;
            lastVal = smoothed;

            buffer[i] = smoothed * volume;

            // Advance cursor
            // Assuming sample rate matches Mixer (44100)
            // If sample is different rate, we need simple resampling
            float rateRatio = sample.getSampleRate() / AudioMixer.SAMPLE_RATE;
            cursor += rateRatio;
        }
        return true;
    }

    private float filterSensitivity = 1.0f;

    @Override
    public void setDistanceFilter(float amount) {
        // Apply sensitivity and cap at 0.99f (almost silence)
        this.filterAmount = Math.min(0.99f, amount * filterSensitivity);
    }

    public void setFilterSensitivity(float sensitivity) {
        this.filterSensitivity = sensitivity;
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

    public void setRange(float ref, float max) {
        this.refDistance = ref;
        this.maxDistance = max;
    }
}
