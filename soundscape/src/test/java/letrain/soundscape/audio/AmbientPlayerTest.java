package letrain.soundscape.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import letrain.soundscape.SoundscapeStyle;
import letrain.soundscape.impl.TextStyleLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Ambient player assets")
class AmbientPlayerTest {

    @Test
    @DisplayName("loads every catalog sound with an asset and reports the missing ones")
    void should_LoadBundledAssets() throws IOException {
        SoundscapeStyle style = new TextStyleLoader().loadResource("/styles/valle-norte.sound");

        AmbientPlayer player = new AmbientPlayer(style, 1);

        assertEquals(15, player.sampleCount());
        List<String> missing = player.missingSounds();
        assertTrue(missing.isEmpty(), "unexpected missing sounds: " + missing);
    }
}
