package letrain.soundscape.audio;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.LocalTime;
import java.util.Map;
import letrain.soundscape.Composition;
import letrain.soundscape.CompositionInput;
import letrain.soundscape.SoundscapeEngine;
import letrain.soundscape.SoundscapeStyle;
import letrain.soundscape.impl.SoundscapeEngineImpl;
import letrain.soundscape.impl.TextStyleLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Ambient player offline mix")
class AmbientPlayerMixTest {

    @Test
    @DisplayName("a busy mix stays below full scale after the master limiter")
    void should_NotClip_When_MixIsBusy() throws IOException {
        SoundscapeStyle style = new TextStyleLoader().loadResource("/styles/valle-norte.sound");
        AmbientPlayer player = new AmbientPlayer(style, 1);
        SoundscapeEngine engine = new SoundscapeEngineImpl();
        Composition composition = engine.compose(style, new CompositionInput(LocalTime.NOON,
                Map.of("fields", 0.7f, "sea", 0.5f), 0f, 0f, 0.1f, 0f));
        player.updateTargets(composition);

        float[] mono = new float[1024];
        float rawPeak = 0f;
        float limitedPeak = 0f;
        for (int buffer = 0; buffer < 400; buffer++) {
            java.util.Arrays.fill(mono, 0f);
            player.renderBuffer(mono);
            for (float value : mono) {
                assertFalse(Float.isNaN(value), "mix must not contain NaN");
                rawPeak = Math.max(rawPeak, Math.abs(value));
                limitedPeak = Math.max(limitedPeak, Math.abs(AmbientPlayer.masterSample(value)));
            }
        }
        assertTrue(rawPeak > 1.0f, "test should exercise a hot mix, raw peak was " + rawPeak);
        assertTrue(limitedPeak < 0.999f,
                "the soft limiter must keep samples below full scale, was " + limitedPeak);
    }
}
