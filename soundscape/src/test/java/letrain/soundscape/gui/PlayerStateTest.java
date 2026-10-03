package letrain.soundscape.gui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.time.LocalTime;
import letrain.soundscape.SoundscapeStyle;
import letrain.soundscape.SpeedPreset;
import letrain.soundscape.impl.TextStyleLoader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Soundscape GUI player state")
class PlayerStateTest {

    private SoundscapeStyle style;
    private PlayerState state;

    @BeforeEach
    void setUp() throws IOException {
        style = new TextStyleLoader().loadResource("/styles/valle-norte.sound");
        state = new PlayerState(style);
    }

    @Test
    @DisplayName("starts with a demo state and the style zones")
    void should_StartWithDemoState() {
        assertEquals(0.7f, state.zoneWeights().get("fields"), 1e-6);
        assertEquals(0.5f, state.zoneWeights().get("sea"), 1e-6);
        assertEquals(0f, state.zoneWeights().get("gold-mine"), 1e-6);
        assertEquals(LocalTime.of(12, 0), state.time());
    }

    @Test
    @DisplayName("time wraps around midnight")
    void should_WrapTime_When_Advanced() {
        state.setMinuteOfDay(23 * 60 + 50);
        state.advance(20);
        assertEquals(LocalTime.of(0, 10), state.time());
    }

    @Test
    @DisplayName("clamps weights, height and weather to 0..1")
    void should_ClampValues() {
        state.setZoneWeight("fields", 2f);
        state.setHeight(-1f);
        state.setWeather(5f, -2f, 0.4f);

        assertEquals(1f, state.zoneWeights().get("fields"), 1e-6);
        assertEquals(0f, state.height(), 1e-6);
        assertEquals(1f, state.rain(), 1e-6);
        assertEquals(0f, state.wind(), 1e-6);
        assertEquals(0.4f, state.storm(), 1e-6);
    }

    @Test
    @DisplayName("defaults to NORMAL speed and rejects null")
    void should_DefaultToNormalSpeed() {
        assertEquals(SpeedPreset.NORMAL, state.speed());
        state.setSpeed(null);
        assertEquals(SpeedPreset.NORMAL, state.speed());
        state.setSpeed(SpeedPreset.FAST);
        assertEquals(SpeedPreset.FAST, state.speed());
    }

    @Test
    @DisplayName("builds the engine input from the current state")
    void should_BuildInput_When_Composing() {
        state.setMinuteOfDay(23 * 60 + 30);
        state.setHeight(0.2f);
        state.applyPreset(style.climatePresets().get("drizzle"));

        var input = state.toInput();

        assertEquals(LocalTime.of(23, 30), input.time());
        assertEquals(0.2f, input.height(), 1e-6);
        assertEquals(0.3f, input.rain(), 1e-6);
        assertEquals(0.1f, input.wind(), 1e-6);
        assertEquals(0.7f, input.zoneWeights().get("fields"), 1e-6);
    }
}
