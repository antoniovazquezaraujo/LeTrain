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

    private boolean loggedFirstRead = false;

    @Override
    public boolean read(float[] buffer) {
        try {
            if (!active || sample == null)
                return false;

            if (!loggedFirstRead) {
                System.out.println("AmbientSource.read() called — active=" + active + " volume=" + volume + " sampleRate=" + sample.getSampleRate() + " looping=" + looping);
                loggedFirstRead = true;
            }

            int len = buffer.length;
            for (int i = 0; i < len; i++) {
                if ((int) cursor >= sample.getLength()) {
                    if (looping) {
                        cursor = 0;
                    } else {
                        active = false;
                        for (int j = i; j < len; j++)
                            buffer[j] = 0;
                        return true;
                    }
                }

                float raw = sample.getSample((int) cursor);
                float smoothed = (filterAmount == 0.0f) ? raw : lastVal + (raw - lastVal) * (1.0f - filterAmount);
                lastVal = smoothed;

                buffer[i] = smoothed * volume;

                float rateRatio = sample.getSampleRate() / AudioMixer.SAMPLE_RATE;
                cursor += rateRatio;
            }
            return true;
        } catch (Exception e) {
            System.err.println("AmbientSource.read() crashed: " + e.getMessage());
            e.printStackTrace();
            active = false;
            return false;
        }
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

    public void setStartDelay(int delay) {
        // TODO: Implement delay logic if needed
    }
}
