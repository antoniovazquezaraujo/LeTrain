package letrain.soundscape;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Speed presets")
class SpeedPresetTest {

    @Test
    @DisplayName("maps day duration to game minutes per real second")
    void should_MapDayDuration_To_Rate() {
        assertEquals(0.4, SpeedPreset.SLOW.gameMinutesPerRealSecond(), 1e-9);
        assertEquals(0.6, SpeedPreset.NORMAL.gameMinutesPerRealSecond(), 1e-9);
        assertEquals(1.2, SpeedPreset.FAST.gameMinutesPerRealSecond(), 1e-9);
    }

    @Test
    @DisplayName("a full day always lasts the configured real minutes")
    void should_KeepDayDuration() {
        for (SpeedPreset preset : SpeedPreset.values()) {
            double realSeconds = 24 * 60 / preset.gameMinutesPerRealSecond();
            assertEquals(preset.dayDurationMinutes() * 60.0, realSeconds, 1e-6);
        }
    }
}
