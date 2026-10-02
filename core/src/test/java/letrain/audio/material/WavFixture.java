package letrain.audio.material;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import letrain.audio.synth.AudioSample;

/**
 * Test helper that writes short synthetic mono 44.1 kHz PCM16 WAV files, so unit tests do not
 * depend on the 25 MiB of real assets (ADR-029 §7). Public so tests of other packages can reuse it
 * (e.g. the synth voice tests of PR C).
 */
public final class WavFixture {

    public static final int SAMPLE_RATE = 44100;

    private WavFixture() {}

    /** Writes a mono 44.1 kHz PCM16 sine wave and returns the file. */
    public static Path writeSine(Path dir, String fileName, int frames, double frequencyHz)
            throws IOException {
        float[] samples = new float[frames];
        for (int i = 0; i < frames; i++) {
            samples[i] = (float) (0.5 * Math.sin(2.0 * Math.PI * frequencyHz * i / SAMPLE_RATE));
        }
        return writeMonoPcm16(dir, fileName, samples);
    }

    /** Writes a mono 44.1 kHz PCM16 WAV with the given samples and returns the file. */
    public static Path writeMonoPcm16(Path dir, String fileName, float[] samples)
            throws IOException {
        Path file = dir.resolve(fileName);
        int dataBytes = samples.length * Short.BYTES;
        ByteBuffer buffer = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN);
        putAscii(buffer, "RIFF");
        buffer.putInt(36 + dataBytes);
        putAscii(buffer, "WAVE");
        putAscii(buffer, "fmt ");
        buffer.putInt(16);
        buffer.putShort((short) 1); // PCM
        buffer.putShort((short) 1); // mono
        buffer.putInt(SAMPLE_RATE);
        buffer.putInt(SAMPLE_RATE * Short.BYTES); // byte rate
        buffer.putShort((short) Short.BYTES); // block align
        buffer.putShort((short) 16); // bits per sample
        putAscii(buffer, "data");
        buffer.putInt(dataBytes);
        for (float sample : samples) {
            float clamped = Math.max(-1.0f, Math.min(1.0f, sample));
            buffer.putShort((short) (clamped * Short.MAX_VALUE));
        }
        Files.write(file, buffer.array());
        return file;
    }

    /** Writes a short sine and decodes it as an {@link AudioSample}. */
    public static AudioSample sample(Path dir, String fileName) throws Exception {
        Path file = writeSine(dir, fileName, 256, 220.0);
        return new AudioSample(file.toFile());
    }

    private static void putAscii(ByteBuffer buffer, String value) {
        buffer.put(value.getBytes(StandardCharsets.US_ASCII));
    }
}
