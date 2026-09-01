package letrain.audio.synth;

public class Oscillator extends AudioGenerator {
    private double frequency = 440.0;
    private double phase = 0.0;
    private Waveform waveform = Waveform.SINE;

    public enum Waveform {
        SINE, SQUARE, SAWTOOTH, TRIANGLE
    }

    public void setFrequency(double frequency) {
        this.frequency = frequency;
    }

    public void setWaveform(Waveform waveform) {
        this.waveform = waveform;
    }

    @Override
    public void read(float[] buffer) {
        double phaseIncrement = (Math.PI * 2 * frequency) / sampleRate;

        for (int i = 0; i < buffer.length; i++) {
            float sample = 0.0f;

            switch (waveform) {
                case SINE:
                    sample = (float) Math.sin(phase);
                    break;
                case SQUARE:
                    sample = (Math.sin(phase) > 0) ? 1.0f : -1.0f;
                    break;
                case SAWTOOTH:
                    // Normalize phase to 0-1 for saw calculation
                    double normalizedPhase = (phase / (Math.PI * 2));
                    sample = (float) (2.0 * (normalizedPhase - Math.floor(normalizedPhase + 0.5)));
                    break;
                case TRIANGLE:
                    sample = (float) (2.0 / Math.PI * Math.asin(Math.sin(phase)));
                    break;
            }

            buffer[i] += sample * volume;

            phase += phaseIncrement;
            if (phase > Math.PI * 2) {
                phase -= Math.PI * 2;
            }
        }
    }
}
