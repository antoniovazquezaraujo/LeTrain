package letrain.soundscape.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import letrain.soundscape.SoundGate;
import letrain.soundscape.SoundscapeStyle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Text style loader")
class TextStyleLoaderTest {

    private final TextStyleLoader loader = new TextStyleLoader();

    private static final List<String> MINI = List.of("[climate]",
            "clear = rain 0.0 wind 0.0 storm 0.0", "[sounds]", "waves = sea/waves-*.wav", "[zones]",
            "sea = waves", "[presence]", "waves = 0.5 0.5 0.5 0.5 0.5 0.5 0.5");

    @Test
    @DisplayName("parses the bundled Val del Norte style")
    void should_ParseBundledStyle() throws IOException {
        SoundscapeStyle style = loader.loadResource("/styles/valle-norte.sound");

        assertEquals(3, style.climatePresets().size());
        assertEquals(0.3f, style.climatePresets().get("drizzle").rain(), 1e-6);
        assertTrue(style.zoneSounds("fields").contains("crickets"));
        assertTrue(style.zoneSounds("gold-mine").contains("mine-machinery"));
        assertEquals(7, style.presenceOf("crickets").size());
        assertEquals(-0.8f, style.heightSensitivityOf("crickets"), 1e-6);
        assertEquals(3, style.climateSensitivityOf("dogs").size());
        assertNotNull(style.weatherSounds().get("rain"));
        assertNotNull(style.heightSounds().get("wind"));
        assertTrue(style.gains().isEmpty());
        assertEquals(1f, style.gainOf("cicadas"), 1e-6);
        assertEquals(2, style.gatesOf("cicadas").size());
        assertEquals(new SoundGate("rain", 0.25f), style.gatesOf("crickets").get(0));
    }

    @Test
    @DisplayName("parses silence gates, one condition per line")
    void should_ParseSilenceGates_When_SectionIsPresent() throws IOException {
        SoundscapeStyle style = loader.parse(List.of("[sounds]", "waves = sea/waves-*.wav",
                "[zones]", "sea = waves", "[presence]", "waves = 1 1 1 1 1 1 1", "[silence-when]",
                "waves = rain > 0.25", "waves = wind > 0.6"));

        assertEquals(List.of(new SoundGate("rain", 0.25f), new SoundGate("wind", 0.6f)),
                style.gatesOf("waves"));
        assertTrue(style.gatesOf("seagulls").isEmpty());
    }

    @Test
    @DisplayName("fails on an unknown gate variable")
    void should_Throw_When_GateVariableIsUnknown() {
        List<String> lines = List.of("[sounds]", "waves = sea/waves-*.wav", "[silence-when]",
                "waves = snow > 0.5");
        assertThrows(IOException.class, () -> loader.parse(lines));
    }

    @Test
    @DisplayName("parses the optional [gains] section and defaults to 1.0")
    void should_ParseGains_When_SectionIsPresent() throws IOException {
        SoundscapeStyle style = loader.parse(List.of("[sounds]", "waves = sea/waves-*.wav",
                "[zones]", "sea = waves", "[presence]", "waves = 0.5 0.5 0.5 0.5 0.5 0.5 0.5",
                "[gains]", "waves = 0.4"));

        assertEquals(0.4f, style.gainOf("waves"), 1e-6);
        assertEquals(1f, style.gainOf("seagulls"), 1e-6);
        assertEquals(Map.of("waves", 0.4f), style.gains());
    }

    @Test
    @DisplayName("ignores comments and blank lines")
    void should_IgnoreComments_When_Parsing() throws IOException {
        SoundscapeStyle style = loader.parse(
                List.of("# a comment", "", "[sounds]", "waves = sea/waves-*.wav # inline comment"));
        assertTrue(style.sounds().containsKey("waves"));
        assertEquals(List.of("sea/waves-*.wav"), style.sounds().get("waves").material());
    }

    @Test
    @DisplayName("fails when a zone references an unknown sound")
    void should_Throw_When_ZoneReferencesUnknownSound() {
        List<String> lines = List.of("[zones]", "sea = waves");
        assertThrows(IOException.class, () -> loader.parse(lines));
    }

    @Test
    @DisplayName("fails when a presence curve does not have seven values")
    void should_Throw_When_PresenceCurveIsTooShort() {
        List<String> lines =
                List.of("[sounds]", "waves = sea/waves-*.wav", "[presence]", "waves = 0.5 0.5 0.5");
        assertThrows(IOException.class, () -> loader.parse(lines));
    }

    @Test
    @DisplayName("fails on an unknown section")
    void should_Throw_When_SectionIsUnknown() {
        List<String> lines = List.of("[nonsense]", "waves = sea/waves-*.wav");
        assertThrows(IOException.class, () -> loader.parse(lines));
    }

    @Test
    @DisplayName("parses the mini style used by other tests")
    void should_ParseMiniStyle() throws IOException {
        SoundscapeStyle style = loader.parse(MINI);
        assertEquals(List.of("waves"), style.zoneSounds("sea"));
    }
}
