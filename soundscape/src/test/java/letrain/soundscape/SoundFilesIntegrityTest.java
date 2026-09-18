package letrain.soundscape;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Sound files integrity")
class SoundFilesIntegrityTest {

    private static final List<String> SOUND_PATHS = List.of("/sounds/sea/waves-01.wav",
            "/sounds/sea/waves-02.wav", "/sounds/sea/waves-03.wav", "/sounds/sea/waves-04.wav",
            "/sounds/sea/seagull-01.wav", "/sounds/field/cicada-01.wav",
            "/sounds/field/cricket-01.wav", "/sounds/field/dog-01.wav",
            "/sounds/field/rooster-01.wav", "/sounds/field/birds-01.wav",
            "/sounds/field/birds-02.wav", "/sounds/mountain/wind-01.wav",
            "/sounds/mountain/hawk-01.wav", "/sounds/weather/rain-01.wav",
            "/sounds/weather/rain-02.wav", "/sounds/weather/rain-03.wav",
            "/sounds/weather/rain-04.wav", "/sounds/weather/wind-01.wav",
            "/sounds/weather/thunder-01.wav", "/sounds/weather/thunder-06.wav",
            "/sounds/weather/thunder-07.wav", "/sounds/sky/wind-altitude-01.wav",
            "/sounds/synth/wind-breeze-01.wav", "/sounds/synth/wind-medium-01.wav",
            "/sounds/synth/wind-gale-01.wav", "/sounds/synth/wind-gusts-01.wav",
            "/sounds/synth/rain-light-01.wav", "/sounds/synth/rain-medium-01.wav",
            "/sounds/synth/rain-heavy-01.wav", "/sounds/synth/rain-glass-01.wav",
            "/sounds/synth/rain-near-01.wav", "/sounds/synth/rain-far-01.wav",
            "/sounds/synth/waves-calm-01.wav", "/sounds/synth/waves-surf-01.wav",
            "/sounds/industrial/producer-zone-01.wav",
            "/sounds/industrial/consumer-zone-01.wav");

    @Test
    @DisplayName("every sound asset exists and is a valid PCM 16-bit 44.1kHz mono WAV")
    void should_BeValidPcmWav_When_ReadingAssets()
            throws IOException, UnsupportedAudioFileException {
        for (String path : SOUND_PATHS) {
            URL url = getClass().getResource(path);
            assertNotNull(url, "Sound file not found on classpath: " + path);

            try (AudioInputStream ais = AudioSystem.getAudioInputStream(url)) {
                AudioFormat format = ais.getFormat();
                assertEquals(AudioFormat.Encoding.PCM_SIGNED, format.getEncoding(),
                        "Encoding should be PCM_SIGNED for " + path);
                assertEquals(1, format.getChannels(), "Channels should be mono (1) for " + path);
                assertEquals(44100.0f, format.getSampleRate(), 1e-3,
                        "Sample rate should be 44100 Hz for " + path);
                assertEquals(16, format.getSampleSizeInBits(),
                        "Sample size should be 16-bit for " + path);
                assertTrue(ais.getFrameLength() > 0, "Audio file should contain frames: " + path);
            }
        }
    }
}
