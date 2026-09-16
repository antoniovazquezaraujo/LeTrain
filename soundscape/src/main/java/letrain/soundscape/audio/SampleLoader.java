package letrain.soundscape.audio;

import java.io.IOException;
import java.net.URL;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

/** Loads 16-bit PCM WAV resources into mono float samples. */
public class SampleLoader {

    public SoundSample load(URL url) throws IOException {
        try (AudioInputStream input = AudioSystem.getAudioInputStream(url)) {
            AudioFormat format = input.getFormat();
            if (format.getSampleSizeInBits() != 16) {
                throw new IOException("only 16-bit PCM is supported: " + url);
            }
            return new SoundSample(fileName(url), toMono(input.readAllBytes(), format),
                    format.getSampleRate());
        } catch (UnsupportedAudioFileException e) {
            throw new IOException("unsupported audio file: " + url, e);
        }
    }

    private float[] toMono(byte[] bytes, AudioFormat format) {
        int frameSize = format.getFrameSize();
        int channels = format.getChannels();
        boolean bigEndian = format.isBigEndian();
        int frames = bytes.length / frameSize;
        float[] mono = new float[frames];
        for (int frame = 0; frame < frames; frame++) {
            float sum = 0f;
            for (int channel = 0; channel < channels; channel++) {
                int index = frame * frameSize + channel * 2;
                // Little-endian PCM stores the least significant byte first; big-endian the
                // opposite.
                int first = bytes[index] & 0xFF;
                int second = bytes[index + 1] & 0xFF;
                short value = (short) (bigEndian ? (first << 8) | second : (second << 8) | first);
                sum += value / 32768f;
            }
            mono[frame] = sum / channels;
        }
        return mono;
    }

    private String fileName(URL url) {
        String path = url.getPath();
        int slash = path.lastIndexOf('/');
        return slash >= 0 ? path.substring(slash + 1) : path;
    }
}
