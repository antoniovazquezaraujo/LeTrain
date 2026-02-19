package letrain.audio.synth;

import java.io.File;
import java.io.IOException;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

public class AudioSample {
    private float[] samples;
    private float sampleRate;

    public AudioSample(File file) throws UnsupportedAudioFileException, IOException {
        this(AudioSystem.getAudioInputStream(file));
    }

    public AudioSample(java.net.URL url) throws UnsupportedAudioFileException, IOException {
        this(AudioSystem.getAudioInputStream(url));
    }

    private AudioSample(AudioInputStream ais) throws IOException {
        AudioFormat format = ais.getFormat();

        // Ensure we can handle the format (simplify to 16-bit mono/stereo for now)
        // ideally we convert everything to float mono

        byte[] bytes = ais.readAllBytes();
        int frames = bytes.length / format.getFrameSize();
        this.samples = new float[frames];
        this.sampleRate = format.getSampleRate();
        System.out.println("AudioSample loaded. Frames: " + frames + ", Rate: " + sampleRate);

        boolean isBigEndian = format.isBigEndian();
        int channels = format.getChannels();

        for (int i = 0; i < frames; i++) {
            float sample = 0.0f;

            // Read first channel (mono) or mix down stereo
            // quick n dirty 16-bit parsing
            int lb = 0, hb = 0;
            if (format.getSampleSizeInBits() == 16) {
                int baseIndex = i * format.getFrameSize();
                if (isBigEndian) {
                    hb = bytes[baseIndex];
                    lb = bytes[baseIndex + 1];
                } else {
                    lb = bytes[baseIndex];
                    hb = bytes[baseIndex + 1];
                }
                int val = (hb << 8) | (lb & 0xFF);
                sample = val / 32768.0f;
            } else if (format.getSampleSizeInBits() == 24) {
                int baseIndex = i * format.getFrameSize();
                int b0, b1, b2;
                if (isBigEndian) {
                    b2 = bytes[baseIndex];
                    b1 = bytes[baseIndex + 1];
                    b0 = bytes[baseIndex + 2];
                } else {
                    b0 = bytes[baseIndex];
                    b1 = bytes[baseIndex + 1];
                    b2 = bytes[baseIndex + 2];
                }
                // 24-bit signed: (b2 << 16) | (b1 << 8) | b0
                int val = (b2 << 16) | ((b1 & 0xFF) << 8) | (b0 & 0xFF);
                sample = val / 8388608.0f;
            } else if (format.getSampleSizeInBits() == 8) {
                int baseIndex = i * format.getFrameSize();
                int val = bytes[baseIndex] & 0xFF; // Treat as unsigned
                // 8-bit WAV is usually unsigned 0..255, center 128
                sample = (val - 128) / 128.0f;
            } else {
                System.err.println("Unsupported bit depth: " + format.getSampleSizeInBits());
            }

            this.samples[i] = sample;
        }
        ais.close();
    }

    public float getSample(int index) {
        if (index < 0 || index >= samples.length)
            return 0.0f;
        return samples[index];
    }

    // Linear Interpolation for smooth pitch shifting
    public float getSampleLinear(float index) {
        int i = (int) index;
        if (i < 0 || i >= samples.length - 1)
            return 0.0f;

        float frac = index - i;
        float s1 = samples[i];
        float s2 = samples[i + 1];

        return s1 + frac * (s2 - s1);
    }

    public int getLength() {
        return samples.length;
    }

    public float getSampleRate() {
        return sampleRate;
    }
}
