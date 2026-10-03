package letrain.soundscape.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Sample loader")
class SampleLoaderTest {

    private final SampleLoader loader = new SampleLoader();

    @Test
    @DisplayName("reads little-endian PCM samples without swapping the bytes")
    void should_ReadLittleEndianSamples() throws Exception {
        // Two frames: +0.5 (0x4000) and -0.5 (0xC000), stored little-endian.
        byte[] pcm = {0x00, 0x40, 0x00, (byte) 0xC0};
        AudioFormat format = new AudioFormat(44100f, 16, 1, true, false);
        AudioInputStream input = new AudioInputStream(new ByteArrayInputStream(pcm), format, 2);
        Path file = Files.createTempFile("sample", ".wav");
        AudioSystem.write(input, AudioFileFormat.Type.WAVE, file.toFile());

        SoundSample sample = loader.load(file.toUri().toURL());

        assertEquals(0.5f, sample.frame(0), 1e-4);
        assertEquals(-0.5f, sample.frame(1), 1e-4);
        Files.deleteIfExists(file);
    }
}
