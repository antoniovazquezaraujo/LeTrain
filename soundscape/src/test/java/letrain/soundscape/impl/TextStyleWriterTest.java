package letrain.soundscape.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import letrain.soundscape.SoundGate;
import letrain.soundscape.SoundscapeStyle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("Text style writer")
class TextStyleWriterTest {

    private static final List<String> SOURCE =
            List.of("# mini style", "", "[sounds]", "waves = sea/waves-*.wav", "[zones]",
                    "sea = waves", "[presence]", "waves = 0.5 0.5 0.5 0.5 0.5 0.5 0.5");

    private final TextStyleWriter writer = new TextStyleWriter();
    private final TextStyleLoader loader = new TextStyleLoader();

    @Test
    @DisplayName("appends a [gains] section without touching the original text")
    void should_AppendGains_When_SourceHasNone() {
        List<String> lines = writer.withGains(SOURCE, Map.of("waves", 0.4f));

        assertEquals(SOURCE, lines.subList(0, SOURCE.size()));
        assertTrue(lines.contains("[gains]"));
        assertTrue(lines.contains("waves = 0.4"));
    }

    @Test
    @DisplayName("replaces an existing [gains] section in place")
    void should_ReplaceGains_When_SourceHasOne() {
        List<String> source = List.of("[sounds]", "waves = sea/waves-*.wav", "", "[gains]",
                "waves = 0.9", "", "[presence]", "waves = 0.5 0.5 0.5 0.5 0.5 0.5 0.5");

        List<String> lines = writer.withGains(source, Map.of("waves", 0.4f));

        assertFalse(lines.contains("waves = 0.9"));
        assertEquals(1, lines.stream().filter("[gains]"::equals).count());
        assertTrue(lines.contains("waves = 0.4"));
        assertTrue(lines.contains("[presence]"));
    }

    @Test
    @DisplayName("drops the section when every gain is 100%")
    void should_DropGains_When_CalibrationIsEmpty() {
        List<String> source =
                List.of("[sounds]", "waves = sea/waves-*.wav", "[gains]", "waves = 0.9");

        List<String> lines = writer.withGains(source, Map.of("waves", 1f));

        assertFalse(lines.contains("[gains]"));
        assertFalse(lines.contains("waves = 0.9"));
        assertTrue(lines.contains("waves = sea/waves-*.wav"));
    }

    @Test
    @DisplayName("writes air sensitivities and silence gates and parses them back")
    void should_RoundTrip_Calibration(@TempDir Path dir) throws IOException {
        List<String> source = loader.readResourceLines("/styles/valle-norte.sound");
        Path file = dir.resolve("calibrated.sound");

        writer.write(file, source, Map.of("cicadas", 0.4f), Map.of("hawks", 0.3f),
                Map.of("crickets", List.of(new SoundGate("rain", 0.4f))));
        SoundscapeStyle reloaded = loader.load(file);

        assertEquals(0.4f, reloaded.gainOf("cicadas"), 1e-6);
        assertEquals(0.3f, reloaded.airSensitivityOf("hawks"), 1e-6);
        assertEquals(List.of(new SoundGate("rain", 0.4f)), reloaded.gatesOf("crickets"));
    }

    @Test
    @DisplayName("removes the sections when everything is back to default")
    void should_DropSections_When_Defaults() {
        List<String> source =
                List.of("[sounds]", "waves = sea/waves-*.wav", "", "[gains]", "waves = 0.9",
                        "[air-by-sound]", "waves = 0.5", "[silence-when]", "waves = rain > 0.5");

        List<String> lines = writer.withCalibration(source, Map.of(), Map.of(), Map.of());

        assertFalse(lines.contains("[gains]"));
        assertFalse(lines.contains("[air-by-sound]"));
        assertFalse(lines.contains("[silence-when]"));
        assertTrue(lines.contains("waves = sea/waves-*.wav"));
    }

    @Test
    @DisplayName("exported text parses back with the same gains")
    void should_RoundTrip_When_Exported(@TempDir Path dir) throws IOException {
        List<String> source = loader.readResourceLines("/styles/valle-norte.sound");
        Path file = dir.resolve("calibrated.sound");

        writer.write(file, source, Map.of("cicadas", 0.4f, "weather-rain", 0.75f));
        SoundscapeStyle reloaded = loader.load(file);

        assertEquals(0.4f, reloaded.gainOf("cicadas"), 1e-6);
        assertEquals(0.75f, reloaded.gainOf("weather-rain"), 1e-6);
        assertEquals(1f, reloaded.gainOf("dogs"), 1e-6);
        assertEquals(3, reloaded.climatePresets().size());
        assertEquals(9, reloaded.zones().size());
    }
}
