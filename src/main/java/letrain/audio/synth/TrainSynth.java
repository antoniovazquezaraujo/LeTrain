package letrain.audio.synth;

public class TrainSynth {
    private Oscillator engineHum;
    private NoiseGenerator steamChug;
    private NoiseGenerator hiss; // Constant background hiss

    private float speed = 0.0f; // 0.0 to 1.0
    private float masterVolume = 1.0f;
    private float chugPhase = 0.0f;

    // Synthesis Parameters
    private float chuffRateResult = 0.0f; // Calculated chuffs per second

    public TrainSynth() {
        engineHum = new Oscillator();
        engineHum.setWaveform(Oscillator.Waveform.SQUARE); // Square wave for diesel growl
        engineHum.setVolume(0.0f); // Starts silent

        steamChug = new NoiseGenerator();
        steamChug.setTone(0.3f); // Dull noise for exhaust
        steamChug.setVolume(0.0f);

        hiss = new NoiseGenerator();
        hiss.setTone(0.8f); // Sharper noise for steam/air
        hiss.setVolume(0.05f); // Low constant volume
    }

    public void setSampleRate(float rate) {
        engineHum.setSampleRate(rate);
        steamChug.setSampleRate(rate);
        hiss.setSampleRate(rate);
    }

    public void setSpeed(float speed) {
        this.speed = Math.max(0.0f, Math.min(1.0f, speed));
    }

    public void setVolume(float vol) {
        this.masterVolume = vol;
    }

    public void read(float[] buffer) {
        // Clear buffer first
        for (int i = 0; i < buffer.length; i++) buffer[i] = 0.0f;

        // --- Logic Update (Per buffer to save CPU, sound changes slowly anyway) ---

        // 1. Engine Hum logic (Diesel idle vs rev)
        // Idle pitch ~50Hz, Max pitch ~150Hz
        float targetPitch = 50.0f + (speed * 100.0f);
        engineHum.setFrequency(targetPitch);
        // Hum gets louder with speed (load)
        engineHum.setVolume(0.2f + (speed * 0.3f));

        // 2. Chug Logic (Rhythmic exhaust)
        // Chuff rate: 2 chuffs/sec at idle, up to 12 chuffs/sec at speed
        float targetChuffRate = 2.0f + (speed * 10.0f);

        // 3. Hiss Logic (Constant air pressure)
        hiss.setVolume(0.05f + (speed * 0.05f));

        // --- Audio Generation ---

        // We need temporary buffers if we want to mix properly or just additive mix directly?
        // Additive mixing directly into 'buffer' is fine if components add up.

        // A. Generate Hum
        engineHum.read(buffer);

        // B. Generate Hiss
        // We'll add hiss directly
        float[] hissBuf = new float[buffer.length];
        hiss.read(hissBuf);
        for (int i = 0; i < buffer.length; i++) buffer[i] += hissBuf[i];

        // C. Generate Chug (Amplitude Modulated Noise)
        float[] noiseBuf = new float[buffer.length];
        steamChug.read(noiseBuf);

        // Apply envelope to noise to make it "chug"
        float sampleRate = 44100.0f; // Assuming default for phase math
        float phaseInc = (float) (Math.PI * 2 * targetChuffRate) / sampleRate;

        for (int i = 0; i < buffer.length; i++) {
            // Chug Envelope: Sine wave mapped to 0..1, raised to power for sharpness
            // sin(x) -> -1..1 -> +1 -> 0..2 -> /2 -> 0..1
            float chugEnv = (float) (Math.sin(chugPhase) + 1.0f) / 2.0f;
            // Make pulses sharper: pow(x, 4)
            chugEnv = chugEnv * chugEnv * chugEnv;

            // At higher speeds, chugs blur together, volume increases
            float chugVol = (0.3f + (speed * 0.5f)) * chugEnv;

            buffer[i] += noiseBuf[i] * chugVol;

            chugPhase += phaseInc;
            if (chugPhase > Math.PI * 2) {
                chugPhase -= Math.PI * 2;
            }
        }

        // D. Master Volume
        for (int i = 0; i < buffer.length; i++) {
            buffer[i] *= masterVolume;
            // Hard limiter to prevent clipping
            if (buffer[i] > 1.0f) {
                buffer[i] = 1.0f;
            }
            if (buffer[i] < -1.0f) {
                buffer[i] = -1.0f;
            }
        }
    }
}
