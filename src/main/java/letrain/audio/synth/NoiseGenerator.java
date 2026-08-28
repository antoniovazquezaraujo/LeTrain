package letrain.audio.synth;

import java.util.Random;

public class NoiseGenerator extends AudioGenerator {
    private Random random = new Random();
    private float lastSample = 0.0f;
    private float filterCoeff = 0.5f; // Simple 1-pole Low Pass Filter

    public void setTone(float brightness) {
        // brightness 0.0 (dull) to 1.0 (harsh)
        this.filterCoeff = Math.max(0.01f, Math.min(0.99f, brightness));
    }

    @Override
    public void read(float[] buffer) {
        for (int i = 0; i < buffer.length; i++) {
            float white = (random.nextFloat() * 2.0f) - 1.0f;

            // Simple Low Pass Filter: y[n] = x[n]*a + y[n-1]*(1-a)
            // Here 'filterCoeff' acts as the brightness control.
            // Closer to 1.0 passes more high freq (white noise), closer to 0.0 is smoother (pink-ish)
            float sample = (white * filterCoeff) + (lastSample * (1.0f - filterCoeff));
            lastSample = sample;

            buffer[i] += sample * volume;
        }
    }
}
