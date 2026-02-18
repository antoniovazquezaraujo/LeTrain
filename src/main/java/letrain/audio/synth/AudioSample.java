package letrain.audio.synth;

import javax.sound.sampled.*;
import java.io.File;
import java.io.IOException;

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
            } else {
                // 8-bit support? skip for now, assumed 16bit wav
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
