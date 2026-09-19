package letrain.soundscape.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import letrain.soundscape.Composition;
import letrain.soundscape.CompositionInput;
import letrain.soundscape.SoundscapeStyle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Soundscape composition engine")
class SoundscapeEngineImplTest {

    private final TextStyleLoader loader = new TextStyleLoader();
    private final SoundscapeEngineImpl engine = new SoundscapeEngineImpl();
    private SoundscapeStyle style;

    @BeforeEach
    void setUp() throws IOException {
        style = loader.loadResource("/styles/valle-norte.sound");
    }

    @Test
    @DisplayName("composes a sound as zone x presence x climate x height")
    void should_ComposeSound_When_AllFactorsApply() {
        CompositionInput input = new CompositionInput(LocalTime.of(23, 30), Map.of("fields", 0.7f),
                0.2f, 0.1f, 0.1f, 0f);

        Composition composition = engine.compose(style, input);

        // zone x presence x climate x height x gain 0.55 x distance 1 - 0.6*0.7 (min distance)
        assertEquals(0.0486f, composition.volumeOf("crickets"), 1e-4);
    }

    @Test
    @DisplayName("adds the weather's own sounds at their intensity")
    void should_AddWeatherSounds_When_WeatherHasIntensity() {
        CompositionInput input = new CompositionInput(LocalTime.NOON, Map.of(), 0f, 0.3f, 0.1f, 0f);

        Composition composition = engine.compose(style, input);

        assertEquals(0.3f, composition.volumeOf("weather-rain"), 1e-6);
        assertEquals(0.1f, composition.volumeOf("weather-wind"), 1e-6);
        assertFalse(composition.volumes().containsKey("weather-storm"));
    }

    @Test
    @DisplayName("a zone with zero weight contributes nothing")
    void should_IgnoreZone_When_WeightIsZero() {
        CompositionInput input =
                new CompositionInput(LocalTime.NOON, Map.of("fields", 0f), 0f, 0f, 0f, 0f);

        Composition composition = engine.compose(style, input);

        assertFalse(composition.volumes().containsKey("crickets"));
    }

    @Test
    @DisplayName("unknown zones are ignored")
    void should_IgnoreUnknownZone() {
        CompositionInput input =
                new CompositionInput(LocalTime.NOON, Map.of("atlantis", 1f), 0f, 0f, 0f, 0f);

        Composition composition = engine.compose(style, input);

        assertTrue(composition.volumes().isEmpty());
    }

    @Test
    @DisplayName("heavy rain silences rain-sensitive sounds")
    void should_ClampClimateFactor_When_RainIsHeavy() {
        CompositionInput input =
                new CompositionInput(LocalTime.NOON, Map.of("fields", 1f), 0f, 1f, 0f, 0f);

        Composition composition = engine.compose(style, input);

        assertEquals(0f, composition.volumeOf("cicadas"), 1e-6);
    }

    @Test
    @DisplayName("height boosts high-perspective sounds")
    void should_BoostHawks_When_HeightIsHigh() {
        CompositionInput input =
                new CompositionInput(LocalTime.of(16, 30), Map.of("mountain", 1f), 1f, 0f, 0f, 0f);

        Composition composition = engine.compose(style, input);

        // 0.9 presence * (1 + 0.8) height, then distance (1 - 0.6) at full distance
        assertEquals(0.1728f, composition.volumeOf("hawks"), 1e-4);
    }

    @Test
    @DisplayName("height-provided sounds follow the listening height")
    void should_AddHeightSound_When_HeightIsSet() {
        CompositionInput input = new CompositionInput(LocalTime.NOON, Map.of(), 0.5f, 0f, 0f, 0f);

        Composition composition = engine.compose(style, input);

        assertEquals(0.5f, composition.volumeOf("height-wind"), 1e-6);
        assertFalse(engine
                .compose(style, new CompositionInput(LocalTime.NOON, Map.of(), 0f, 0f, 0f, 0f))
                .volumes().containsKey("height-wind"));
    }

    @Test
    @DisplayName("a sound shared by two zones accumulates their weights")
    void should_SumSound_When_SharedByTwoZones() throws IOException {
        SoundscapeStyle shared = loader
                .parse(List.of("[sounds]", "waves = sea/waves-*.wav", "[zones]", "north = waves",
                        "south = waves", "[presence]", "waves = 0.5 0.5 0.5 0.5 0.5 0.5 0.5"));

        Composition composition = engine.compose(shared, new CompositionInput(LocalTime.NOON,
                Map.of("north", 0.2f, "south", 0.3f), 0f, 0f, 0f, 0f));

        assertEquals(0.25f, composition.volumeOf("waves"), 1e-6);
    }

    @Test
    @DisplayName("a [gains] entry scales the composed volume")
    void should_ApplyGain_When_StyleDeclaresOne() throws IOException {
        SoundscapeStyle gained = loader
                .parse(List.of("[sounds]", "waves = sea/waves-*.wav", "[zones]", "sea = waves",
                        "[presence]", "waves = 1 1 1 1 1 1 1", "[gains]", "waves = 0.5"));

        Composition composition = engine.compose(gained,
                new CompositionInput(LocalTime.NOON, Map.of("sea", 0.8f), 0f, 0f, 0f, 0f));

        assertEquals(0.4f, composition.volumeOf("waves"), 1e-6);
    }

    @Test
    @DisplayName("gains also apply to weather and height sounds")
    void should_ApplyGain_When_ProvidedSoundHasOne() throws IOException {
        SoundscapeStyle gained = loader.parse(List.of("[sounds]", "waves = sea/waves-*.wav",
                "[zones]", "sea = waves", "[presence]", "waves = 1 1 1 1 1 1 1", "[weather]",
                "rain = weather/rain-*.wav", "[height]", "wind = sky/wind-altitude-*.wav",
                "[gains]", "weather-rain = 0.25", "height-wind = 0.5"));

        Composition composition = engine.compose(gained,
                new CompositionInput(LocalTime.NOON, Map.of("sea", 1f), 1f, 0.8f, 0f, 0f));

        // 0.8 intensity x 0.25 gain, then distance (1 - 0.6) at full distance
        assertEquals(0.08f, composition.volumeOf("weather-rain"), 1e-6);
        // height-provided sounds stay next to the listener: only the gain applies
        assertEquals(0.5f, composition.volumeOf("height-wind"), 1e-6);
    }

    @Test
    @DisplayName("listening height sets the distance distance of zone sounds only")
    void should_SetDistance_When_HeightIsSet() {
        CompositionInput input =
                new CompositionInput(LocalTime.NOON, Map.of("fields", 1f), 0.7f, 0.4f, 0f, 0f);

        Composition composition = engine.compose(style, input);

        assertEquals(0.7f, composition.distanceOf("cicadas"), 1e-6);
        assertEquals(0.7f, composition.distanceOf("weather-rain"), 1e-6);
        assertEquals(0f, composition.distanceOf("height-wind"), 1e-6);
        // cicadas declare a minimum distance, so they stay away even at ground level
        assertEquals(0.7f,
                engine.compose(style,
                        new CompositionInput(LocalTime.NOON, Map.of("fields", 1f), 0f, 0f, 0f, 0f))
                        .distanceOf("cicadas"),
                1e-6);
    }

    @Test
    @DisplayName("weather sounds also recede with the listening height")
    void should_AttenuateWeather_When_HeightIsSet() {
        CompositionInput input = new CompositionInput(LocalTime.NOON, Map.of(), 1f, 0.8f, 0f, 0f);

        Composition composition = engine.compose(style, input);

        assertEquals(1f, composition.distanceOf("weather-rain"), 1e-6);
        assertEquals(0.32f, composition.volumeOf("weather-rain"), 1e-6);
    }

    @Test
    @DisplayName("[distance-by-sound] overrides the distance of a sound")
    void should_UseDistanceSensitivity_When_Declared() throws IOException {
        SoundscapeStyle clear = loader
                .parse(List.of("[sounds]", "waves = sea/waves-*.wav", "[zones]", "sea = waves",
                        "[presence]", "waves = 1 1 1 1 1 1 1", "[distance-by-sound]", "waves = 0"));
        SoundscapeStyle receding = loader.parse(List.of("[sounds]", "waves = sea/waves-*.wav",
                "[zones]", "sea = waves", "[presence]", "waves = 1 1 1 1 1 1 1",
                "[distance-by-sound]", "waves = 0.5"));
        CompositionInput input =
                new CompositionInput(LocalTime.NOON, Map.of("sea", 1f), 1f, 0f, 0f, 0f);

        assertEquals(0f, engine.compose(clear, input).distanceOf("waves"), 1e-6);
        assertEquals(1f, engine.compose(clear, input).volumeOf("waves"), 1e-6);
        assertEquals(0.5f, engine.compose(receding, input).distanceOf("waves"), 1e-6);
        assertEquals(0.7f, engine.compose(receding, input).volumeOf("waves"), 1e-6);
    }

    @Test
    @DisplayName("[min-distance] keeps a sound away even at ground level")
    void should_KeepMinimumDistance_When_Declared() throws IOException {
        SoundscapeStyle away = loader
                .parse(List.of("[sounds]", "waves = sea/waves-*.wav", "[zones]", "sea = waves",
                        "[presence]", "waves = 1 1 1 1 1 1 1", "[min-distance]", "waves = 0.7"));
        CompositionInput input =
                new CompositionInput(LocalTime.NOON, Map.of("sea", 1f), 0f, 0f, 0f, 0f);

        Composition composition = engine.compose(away, input);

        assertEquals(0.7f, composition.distanceOf("waves"), 1e-6);
        assertEquals(0.58f, composition.volumeOf("waves"), 1e-4);
    }

    @Test
    @DisplayName("[zone-gains] scales a whole zone")
    void should_ApplyZoneGain() throws IOException {
        SoundscapeStyle loud = loader
                .parse(List.of("[sounds]", "waves = sea/waves-*.wav", "[zones]", "sea = waves",
                        "[presence]", "waves = 1 1 1 1 1 1 1", "[zone-gains]", "sea = 2"));
        CompositionInput input =
                new CompositionInput(LocalTime.NOON, Map.of("sea", 0.5f), 0f, 0f, 0f, 0f);

        assertEquals(1f, engine.compose(loud, input).volumeOf("waves"), 1e-4);
    }

    @Test
    @DisplayName("gates silence crickets and cicadas when the condition holds")
    void should_SilenceGatedSounds_When_ConditionsHold() {
        Composition dry = engine.compose(style,
                new CompositionInput(LocalTime.of(23, 30), Map.of("fields", 1f), 0f, 0.1f, 0f, 0f));
        Composition wet = engine.compose(style,
                new CompositionInput(LocalTime.of(23, 30), Map.of("fields", 1f), 0f, 0.4f, 0f, 0f));
        Composition windy = engine.compose(style,
                new CompositionInput(LocalTime.NOON, Map.of("fields", 1f), 0f, 0f, 0.7f, 0f));

        assertTrue(dry.volumeOf("crickets") > 0.05f);
        assertEquals(0f, wet.volumeOf("crickets"), 1e-6);
        assertEquals(0f, windy.volumeOf("cicadas"), 1e-6);
    }

    @Test
    @DisplayName("same inputs give the same composition")
    void should_BeDeterministic() {
        CompositionInput input = new CompositionInput(LocalTime.of(13, 10),
                Map.of("gold-mine", 0.7f), 0.2f, 0f, 0.1f, 0f);

        assertEquals(engine.compose(style, input), engine.compose(style, input));
    }
}
