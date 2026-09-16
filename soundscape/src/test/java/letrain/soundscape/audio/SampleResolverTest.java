package letrain.soundscape.audio;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URL;
import java.util.List;
import letrain.soundscape.SoundscapeStyle;
import letrain.soundscape.impl.TextStyleLoader;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Sample resolver")
class SampleResolverTest {

    private final SampleResolver resolver = new SampleResolver();

    @Test
    @DisplayName("resolves a wildcard under the sounds root")
    void should_ResolveWildcard() {
        List<URL> urls = resolver.resolve("sea/waves-*.wav");

        assertEquals(1, urls.size());
        assertTrue(urls.get(0).getPath().endsWith("sounds/sea/waves-01.wav"));
    }

    @Test
    @DisplayName("accepts materials that already include the sounds/ prefix")
    void should_ResolvePrefixedMaterial() {
        assertEquals(1, resolver.resolve("sounds/sea/waves-*.wav").size());
    }

    @Test
    @DisplayName("resolves a literal file name")
    void should_ResolveLiteralFile() {
        assertEquals(1, resolver.resolve("sea/seagull-01.wav").size());
    }

    @Test
    @DisplayName("returns an empty list when nothing matches")
    void should_ReturnEmpty_When_NothingMatches() {
        assertTrue(resolver.resolve("atlantis/*.wav").isEmpty());
        assertTrue(resolver.resolve("sea/whale-*.wav").isEmpty());
    }

    @Test
    @DisplayName("all bundled style materials resolve except the industrial placeholders")
    void should_ResolveBundledStyle() throws IOException {
        SoundscapeStyle style = new TextStyleLoader().loadResource("/styles/valle-norte.sound");

        for (var entry : style.sounds().entrySet()) {
            List<URL> urls = new java.util.ArrayList<>();
            entry.getValue().material()
                    .forEach(material -> urls.addAll(resolver.resolve(material)));
            boolean industrial =
                    entry.getKey().contains("machinery") || entry.getKey().contains("thuds");
            if (industrial) {
                assertTrue(urls.isEmpty(), entry.getKey() + " should not have assets yet");
            } else {
                assertTrue(!urls.isEmpty(), entry.getKey() + " should resolve to a file");
            }
        }
    }
}
