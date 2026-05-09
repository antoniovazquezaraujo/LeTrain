package letrain.audio.sources;

import letrain.audio.core.AudioMixer;
import letrain.audio.core.AudioSource;
import letrain.audio.synth.AudioSample;

public class SequencedAmbientSource implements AudioSource {

    public enum State {
        IDLE, STARTING, WORKING, ENDING
    }

    private State state = State.IDLE;
    private boolean desiredActive = false;
    private AudioSample sample;
    private float x, y, z;
    private float cursor = 0;
    private float volume = 1.0f;

    private float refDistance = 100.0f;
    private float maxDistance = 2000.0f;

    private int startStart, startEnd;
    private int workStart, workEnd;
    private int endStart, endEnd;

    public SequencedAmbientSource(AudioSample sample) {
        this.sample = sample;
        float rate = sample.getSampleRate();
        // Labels from hammer-labels.txt
        startStart = 0;
        startEnd = (int) (0.935026f * rate);
        workStart = startEnd;
        workEnd = (int) (10.025561f * rate);
        endStart = workEnd;
        endEnd = (int) (10.545021f * rate);
    }

    /**
     * Creates a looping ambient source that plays the entire sample with no
     * start/end sequencing. The sound starts immediately and loops forever.
     */
    public SequencedAmbientSource(AudioSample sample, boolean simpleLoop) {
        this.sample = sample;
        int totalFrames = sample.getLength();
        this.startStart = 0;
        this.startEnd = 0;
        this.workStart = 0;
        this.workEnd = totalFrames;
        this.endStart = totalFrames;
        this.endEnd = totalFrames;
        this.state = State.IDLE;
    }

    public void setActive(boolean active) {
        this.desiredActive = active;
        if (active) {
            if (state == State.IDLE || state == State.ENDING) {
                state = State.STARTING;
                cursor = startStart;
            }
        } else {
            if (state == State.STARTING || state == State.WORKING) {
                state = State.ENDING;
                cursor = endStart;
            }
        }
    }

    public void setVolume(float volume) {
        this.volume = volume;
    }

    private float filterAmount = 0.0f;
    private float lastVal = 0.0f;
    private float filterSensitivity = 1.0f;

    @Override
    public boolean read(float[] buffer) {
        if (state == State.IDLE || sample == null) {
            return false;
        }

        int len = buffer.length;
        float rateRatio = sample.getSampleRate() / AudioMixer.SAMPLE_RATE;

        for (int i = 0; i < len; i++) {
            // State machine processing
            if (state == State.STARTING && cursor >= startEnd) {
                if (desiredActive) {
                    state = State.WORKING;
                    cursor = workStart + (cursor - startEnd);
                } else {
                    state = State.ENDING;
                    cursor = endStart + (cursor - startEnd);
                }
            } else if (state == State.WORKING && cursor >= workEnd) {
                if (desiredActive) {
                    cursor = workStart + (cursor - workEnd); // Loop
                } else {
                    state = State.ENDING;
                    cursor = endStart + (cursor - workEnd);
                }
            } else if (state == State.ENDING && cursor >= endEnd) {
                state = State.IDLE;
                // Fill rest of buffer with silence
                for (int j = i; j < len; j++)
                    buffer[j] = 0;
                return true;
            }

            float raw = sample.getSampleLinear(cursor);

            // Apply Simple LPF
            float alpha = 1.0f - filterAmount;
            float smoothed = lastVal + (raw - lastVal) * alpha;
            lastVal = smoothed;

            buffer[i] = smoothed * volume;

            // Advance cursor
            cursor += rateRatio;
        }
        return true;
    }

    @Override
    public void setDistanceFilter(float amount) {
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
